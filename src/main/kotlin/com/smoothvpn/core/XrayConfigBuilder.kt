package com.smoothvpn.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns a [Profile] into a complete Xray-core config.json string.
 *
 * Layout produced:
 *   - inbound  socks  @ 127.0.0.1:SOCKS_PORT  (tun2socks dials this)
 *   - inbound  http   @ 127.0.0.1:HTTP_PORT   (optional, for app-proxy testing)
 *   - outbound proxy  (the selected server)
 *   - outbound direct + block
 *   - routing  (bypass LAN + China/Iran direct optional, ads blocked)
 *   - dns      (proxied DoH + a direct domestic resolver)
 *
 * Mux is enabled on the proxy outbound for connection reuse, which is the
 * single biggest "feels faster" win a client can add on top of the core.
 */
object XrayConfigBuilder {

    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809

    fun build(p: Profile, options: RoutingOptions = RoutingOptions()): String {
        val root = JSONObject()

        root.put("log", JSONObject().put("loglevel", "info"))
        root.put("inbounds", buildInbounds())
        root.put("outbounds", buildOutbounds(p, options.enableMux))
        root.put("routing", buildRouting(options))

        return root.toString(2)
    }

    // ---- inbounds -----------------------------------------------------------

    private fun buildInbounds(): JSONArray {
        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            .put("routeOnly", false)

        val socks = JSONObject()
            .put("tag", "socks-in")
            .put("port", SOCKS_PORT)
            .put("listen", "127.0.0.1")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
            .put("sniffing", sniffing)

        val http = JSONObject()
            .put("tag", "http-in")
            .put("port", HTTP_PORT)
            .put("listen", "127.0.0.1")
            .put("protocol", "http")
            .put("sniffing", sniffing)

        return JSONArray().put(socks).put(http)
    }

    // ---- outbounds ----------------------------------------------------------

    private fun buildOutbounds(p: Profile, enableMux: Boolean): JSONArray {
        val proxy = JSONObject()
            .put("tag", "proxy")
            .put("protocol", p.protocol.tag)
            .put("settings", buildProxySettings(p))
            .put("streamSettings", buildStreamSettings(p))

        if (enableMux && p.protocol != Protocol.SHADOWSOCKS) {
            // Mux multiplexes many requests over one connection -> less handshake
            // overhead and snappier page loads. Disabled for SS (no benefit / xudp).
            proxy.put(
                "mux",
                JSONObject()
                    .put("enabled", true)
                    .put("concurrency", 8)
                    .put("xudpConcurrency", 16)
                    .put("xudpProxyUDP443", "reject")
            )
        }

        val direct = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))

        val block = JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
            .put("settings", JSONObject().put("response", JSONObject().put("type", "http")))

        return JSONArray().put(proxy).put(direct).put(block)
    }

    private fun buildProxySettings(p: Profile): JSONObject = when (p.protocol) {
        Protocol.VMESS -> {
            val user = JSONObject()
                .put("id", p.userId)
                .put("alterId", p.alterId)
                .put("security", p.encryption.ifBlank { "auto" })
            val vnext = JSONObject()
                .put("address", p.address)
                .put("port", p.port)
                .put("users", JSONArray().put(user))
            JSONObject().put("vnext", JSONArray().put(vnext))
        }
        Protocol.VLESS -> {
            val user = JSONObject()
                .put("id", p.userId)
                .put("encryption", p.encryption.ifBlank { "none" })
            if (p.flow.isNotBlank()) user.put("flow", p.flow)
            val vnext = JSONObject()
                .put("address", p.address)
                .put("port", p.port)
                .put("users", JSONArray().put(user))
            JSONObject().put("vnext", JSONArray().put(vnext))
        }
        Protocol.TROJAN -> {
            val server = JSONObject()
                .put("address", p.address)
                .put("port", p.port)
                .put("password", p.password)
            if (p.flow.isNotBlank()) server.put("flow", p.flow)
            JSONObject().put("servers", JSONArray().put(server))
        }
        Protocol.SHADOWSOCKS -> {
            val server = JSONObject()
                .put("address", p.address)
                .put("port", p.port)
                .put("method", p.method)
                .put("password", p.password)
                .put("uot", true)          // UDP-over-TCP, helps on restrictive nets
            JSONObject().put("servers", JSONArray().put(server))
        }
    }

    // ---- stream settings (transport + tls/reality) --------------------------

    private fun buildStreamSettings(p: Profile): JSONObject {
        val ss = JSONObject().put("network", p.network.value)

        when (p.network) {
            Network.WS -> ss.put(
                "wsSettings",
                JSONObject()
                    .put("path", p.path.ifBlank { "/" })
                    .put("headers", JSONObject().apply {
                        if (p.host.isNotBlank()) put("Host", p.host)
                    })
            )
            Network.HTTPUPGRADE -> ss.put(
                "httpupgradeSettings",
                JSONObject()
                    .put("path", p.path.ifBlank { "/" })
                    .apply { if (p.host.isNotBlank()) put("host", p.host) }
            )
            Network.GRPC -> ss.put(
                "grpcSettings",
                JSONObject()
                    .put("serviceName", p.path)
                    .put("multiMode", false)
            )
            Network.H2 -> ss.put(
                "httpSettings",
                JSONObject()
                    .put("path", p.path.ifBlank { "/" })
                    .put("host", JSONArray().apply { if (p.host.isNotBlank()) put(p.host) })
            )
            Network.KCP -> ss.put(
                "kcpSettings",
                JSONObject()
                    .put("header", JSONObject().put("type", p.headerType.ifBlank { "none" }))
                    .put("seed", p.path)
            )
            Network.TCP -> if (p.headerType == "http") {
                ss.put(
                    "tcpSettings",
                    JSONObject().put(
                        "header",
                        JSONObject()
                            .put("type", "http")
                            .put("request", JSONObject().put(
                                "headers",
                                JSONObject().apply {
                                    if (p.host.isNotBlank())
                                        put("Host", JSONArray().put(p.host))
                                }
                            ))
                    )
                )
            }
        }

        when (p.security) {
            Security.TLS -> ss.put("security", "tls").put("tlsSettings", tlsObject(p))
            Security.REALITY -> ss.put("security", "reality").put("realitySettings", realityObject(p))
            Security.NONE -> ss.put("security", "none")
        }
        return ss
    }

    private fun tlsObject(p: Profile): JSONObject {
        val o = JSONObject()
            .put("serverName", p.sni.ifBlank { p.host.ifBlank { p.address } })
            .put("allowInsecure", false)
        if (p.alpn.isNotBlank())
            o.put("alpn", JSONArray().apply { p.alpn.split(',').forEach { put(it.trim()) } })
        if (p.fingerprint.isNotBlank()) o.put("fingerprint", p.fingerprint)
        return o
    }

    private fun realityObject(p: Profile): JSONObject = JSONObject()
        .put("serverName", p.sni.ifBlank { p.address })
        .put("fingerprint", p.fingerprint.ifBlank { "chrome" })
        .put("publicKey", p.publicKey)
        .put("shortId", p.shortId)
        .put("spiderX", p.spiderX)

    // ---- routing ------------------------------------------------------------

    private fun buildRouting(o: RoutingOptions): JSONObject {
        val rules = JSONArray()

        if (o.bypassLan) {
            // Literal private ranges — no geoip.dat needed, so the core always
            // starts even on a fresh install with no geo assets bundled.
            val privateCidrs = JSONArray()
                .put("10.0.0.0/8").put("172.16.0.0/12").put("192.168.0.0/16")
                .put("127.0.0.0/8").put("169.254.0.0/16").put("224.0.0.0/4")
                .put("::1/128").put("fc00::/7").put("fe80::/10")
            rules.put(
                JSONObject().put("type", "field")
                    .put("ip", privateCidrs).put("outboundTag", "direct")
            )
        }

        // The rules below need geoip.dat / geosite.dat in the asset dir. They are
        // off by default and only safe to enable once those files are present
        // (CI fetches them; see GeoAssets + the workflow).
        if (o.geoAssetsAvailable) {
            if (o.blockAds) {
                rules.put(
                    JSONObject().put("type", "field")
                        .put("domain", JSONArray().put("geosite:category-ads-all"))
                        .put("outboundTag", "block")
                )
            }
            if (o.domesticDirect) {
                rules.put(
                    JSONObject().put("type", "field")
                        .put("domain", JSONArray().put("geosite:category-ir"))
                        .put("outboundTag", "direct")
                )
                rules.put(
                    JSONObject().put("type", "field")
                        .put("ip", JSONArray().put("geoip:ir"))
                        .put("outboundTag", "direct")
                )
            }
        }

        return JSONObject()
            .put("domainStrategy", "AsIs")
            .put("rules", rules)
    }

    // ---- dns ----------------------------------------------------------------

    private fun buildDns(): JSONObject {
        val servers = JSONArray()
            .put("https://1.1.1.1/dns-query")   // proxied, leak-resistant
            .put("8.8.8.8")
        return JSONObject()
            .put("servers", servers)
            .put("queryStrategy", "UseIP")
    }
}
