package com.smoothvpn.desktop

import com.smoothvpn.core.Network
import com.smoothvpn.core.Profile
import com.smoothvpn.core.Protocol
import com.smoothvpn.core.Security
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What we keep on disk between runs. */
data class PersistedState(
    val profiles: List<Profile>,
    val subscriptions: List<Subscription>
)

/**
 * Tiny JSON store at %APPDATA%\SmoothVPN\state.json (or ~/.smoothvpn on
 * other systems). Saves the server list and saved subscriptions so they
 * survive a restart.
 */
object Persistence {

    private val dir: File = run {
        val appData = System.getenv("APPDATA")
        val base = if (!appData.isNullOrBlank()) File(appData, "SmoothVPN")
        else File(System.getProperty("user.home"), ".smoothvpn")
        base.apply { runCatching { mkdirs() } }
    }
    private val file = File(dir, "state.json")

    fun load(): PersistedState {
        if (!file.exists()) return PersistedState(emptyList(), emptyList())
        return try {
            val root = JSONObject(file.readText())
            val profiles = root.optJSONArray("profiles")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { profileFromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } ?: emptyList()
            val subs = root.optJSONArray("subscriptions")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching {
                        val o = arr.getJSONObject(i)
                        Subscription(o.getString("id"), o.getString("url"), o.optString("name"))
                    }.getOrNull()
                }
            } ?: emptyList()
            PersistedState(profiles, subs)
        } catch (e: Exception) {
            PersistedState(emptyList(), emptyList())
        }
    }

    fun save(profiles: List<Profile>, subscriptions: List<Subscription>) {
        try {
            val root = JSONObject()
            root.put("profiles", JSONArray().apply { profiles.forEach { put(profileToJson(it)) } })
            root.put("subscriptions", JSONArray().apply {
                subscriptions.forEach {
                    put(JSONObject().put("id", it.id).put("url", it.url).put("name", it.name))
                }
            })
            file.writeText(root.toString())
        } catch (e: Exception) {
            // Best-effort; never crash the app over a failed save.
        }
    }

    private fun profileToJson(p: Profile): JSONObject = JSONObject().apply {
        put("id", p.id); put("remark", p.remark); put("protocol", p.protocol.name)
        put("address", p.address); put("port", p.port)
        put("userId", p.userId); put("password", p.password); put("method", p.method)
        put("alterId", p.alterId); put("encryption", p.encryption); put("flow", p.flow)
        put("network", p.network.name); put("security", p.security.name)
        put("sni", p.sni); put("host", p.host); put("path", p.path); put("alpn", p.alpn)
        put("fingerprint", p.fingerprint); put("publicKey", p.publicKey)
        put("shortId", p.shortId); put("spiderX", p.spiderX); put("headerType", p.headerType)
        put("subscriptionId", p.subscriptionId ?: JSONObject.NULL)
        put("latencyMs", p.latencyMs)
    }

    private fun profileFromJson(o: JSONObject): Profile = Profile(
        id = o.getString("id"),
        remark = o.optString("remark"),
        protocol = Protocol.valueOf(o.optString("protocol", "VMESS")),
        address = o.optString("address"),
        port = o.optInt("port"),
        userId = o.optString("userId"),
        password = o.optString("password"),
        method = o.optString("method"),
        alterId = o.optInt("alterId"),
        encryption = o.optString("encryption", "none"),
        flow = o.optString("flow"),
        network = Network.valueOf(o.optString("network", "TCP")),
        security = Security.valueOf(o.optString("security", "NONE")),
        sni = o.optString("sni"),
        host = o.optString("host"),
        path = o.optString("path"),
        alpn = o.optString("alpn"),
        fingerprint = o.optString("fingerprint"),
        publicKey = o.optString("publicKey"),
        shortId = o.optString("shortId"),
        spiderX = o.optString("spiderX"),
        headerType = o.optString("headerType", "none"),
        subscriptionId = if (o.isNull("subscriptionId")) null else o.optString("subscriptionId"),
        latencyMs = o.optInt("latencyMs", -1)
    )
}
