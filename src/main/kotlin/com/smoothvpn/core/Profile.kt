package com.smoothvpn.core

/**
 * A single proxy server profile, normalized across all supported protocols.
 *
 * One Profile == one outbound. The [OutboundParser] turns a share link
 * (vmess://, vless://, trojan://, ss://) into one of these, and
 * [XrayConfigBuilder] turns it into a full Xray config.json.
 */
data class Profile(
    val id: String,                 // stable uuid we generate locally
    val remark: String,             // display name (the #fragment of the link)
    val protocol: Protocol,
    val address: String,            // server host / ip
    val port: Int,

    // credentials (only the relevant ones are set per protocol)
    val userId: String = "",        // uuid for vmess/vless, password for trojan
    val password: String = "",      // ss / trojan password
    val method: String = "",        // ss cipher, e.g. aes-256-gcm
    val alterId: Int = 0,           // vmess legacy alterId (almost always 0)
    val encryption: String = "none",// vless encryption (none) / vmess security (auto)
    val flow: String = "",          // vless flow, e.g. xtls-rprx-vision

    // transport / stream settings
    val network: Network = Network.TCP,   // tcp / ws / grpc / h2 / kcp
    val security: Security = Security.NONE,// none / tls / reality
    val sni: String = "",
    val host: String = "",          // ws/h2 Host header, also fallback SNI
    val path: String = "",          // ws path / h2 path / grpc serviceName
    val alpn: String = "",           // comma separated, e.g. h2,http/1.1
    val fingerprint: String = "",   // uTLS fingerprint, e.g. chrome
    val publicKey: String = "",     // reality public key
    val shortId: String = "",       // reality short id
    val spiderX: String = "",       // reality spiderX
    val headerType: String = "none",// tcp http obfs / kcp header

    // bookkeeping
    val subscriptionId: String? = null,  // which subscription this came from, if any
    val latencyMs: Int = -1               // last measured delay, -1 = untested
) {
    /** host:port pair Xray's libv2ray wants for its domainName field. */
    fun domainPort(): String = "$address:$port"
}

enum class Protocol(val tag: String) {
    VMESS("vmess"),
    VLESS("vless"),
    TROJAN("trojan"),
    SHADOWSOCKS("shadowsocks");

    companion object {
        fun fromScheme(scheme: String): Protocol? = when (scheme.lowercase()) {
            "vmess" -> VMESS
            "vless" -> VLESS
            "trojan" -> TROJAN
            "ss", "shadowsocks" -> SHADOWSOCKS
            else -> null
        }
    }
}

enum class Network(val value: String) {
    TCP("tcp"), WS("ws"), GRPC("grpc"), H2("h2"), KCP("kcp"), HTTPUPGRADE("httpupgrade");

    companion object {
        fun from(v: String?): Network = when (v?.lowercase()) {
            "ws", "websocket" -> WS
            "grpc", "gun" -> GRPC
            "h2", "http" -> H2
            "kcp", "mkcp" -> KCP
            "httpupgrade" -> HTTPUPGRADE
            else -> TCP
        }
    }
}

enum class Security(val value: String) {
    NONE("none"), TLS("tls"), REALITY("reality");

    companion object {
        fun from(v: String?): Security = when (v?.lowercase()) {
            "tls" -> TLS
            "reality" -> REALITY
            else -> NONE
        }
    }
}
