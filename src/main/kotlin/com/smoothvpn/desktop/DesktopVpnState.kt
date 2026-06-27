package com.smoothvpn.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smoothvpn.core.OutboundParser
import com.smoothvpn.core.Profile
import com.smoothvpn.core.SubscriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/** A saved subscription URL and the friendly name we show for it. */
data class Subscription(
    val id: String,
    val url: String,
    val name: String
)

/**
 * In-memory app state: the server list, saved subscriptions, and the
 * network operations (fetch a subscription, ping servers). All Compose
 * snapshot state, so the UI recomposes automatically.
 *
 * Note: state is not yet persisted across restarts — that's an easy next
 * step (write the list to %APPDATA%/SmoothVPN as JSON).
 */
class DesktopVpnState {
    val profiles = mutableStateListOf<Profile>()
    val subscriptions = mutableStateListOf<Subscription>()
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    init {
        val saved = Persistence.load()
        profiles.addAll(saved.profiles)
        subscriptions.addAll(saved.subscriptions)
    }

    private fun persist() = Persistence.save(profiles.toList(), subscriptions.toList())

    /** Add a single share link (clipboard / manual). Returns false if invalid. */
    fun importLink(raw: String): Boolean {
        val p = OutboundParser.parse(raw) ?: return false
        profiles.add(p)
        status = "Added ${p.remark}"
        persist()
        return true
    }

    fun removeProfile(id: String) {
        profiles.removeAll { it.id == id }
        persist()
    }

    /** Add a subscription URL and fetch it immediately. */
    suspend fun addSubscription(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http", ignoreCase = true)) {
            status = "Subscription must be an http(s) URL"
            return
        }
        val sub = Subscription(UUID.randomUUID().toString(), url, deriveName(url))
        subscriptions.add(sub)
        busy = true
        try {
            val n = fetchInto(sub)
            status = "${sub.name}: $n servers"
            persist()
        } finally {
            busy = false
        }
    }

    /** Re-fetch every saved subscription, replacing their servers. */
    suspend fun updateSubscriptions() {
        if (subscriptions.isEmpty()) {
            status = "No subscriptions to update"
            return
        }
        busy = true
        status = "Updating…"
        try {
            var total = 0
            for (sub in subscriptions.toList()) total += fetchInto(sub)
            status = "Updated ${subscriptions.size} subscription(s) · $total servers"
            persist()
        } finally {
            busy = false
        }
    }

    /**
     * Fetch one subscription and swap in its servers, preserving manually
     * added servers and servers from other subscriptions. Returns the count.
     */
    private suspend fun fetchInto(sub: Subscription): Int {
        return try {
            val body = httpGet(sub.url)
            val parsed = SubscriptionParser.parse(body, sub.id)
            val keep = profiles.filter { it.subscriptionId != sub.id }
            profiles.clear()
            profiles.addAll(keep)
            profiles.addAll(parsed)
            parsed.size
        } catch (e: Exception) {
            status = "Failed to fetch ${sub.name}: ${e.message ?: "error"}"
            0
        }
    }

    /** TCP-connect ping every server concurrently, then update their latency. */
    suspend fun pingAll() {
        if (profiles.isEmpty()) {
            status = "No servers to ping"
            return
        }
        busy = true
        status = "Pinging…"
        try {
            val results = coroutineScope {
                profiles.toList().map { p ->
                    async(Dispatchers.IO) { p.id to tcpPing(p.address, p.port) }
                }.awaitAll()
            }
            for ((id, ms) in results) {
                val i = profiles.indexOfFirst { it.id == id }
                if (i >= 0) profiles[i] = profiles[i].copy(latencyMs = ms)
            }
            val ok = results.count { it.second >= 0 }
            status = "Ping complete · $ok/${results.size} reachable"
            persist()
        } finally {
            busy = false
        }
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "SmoothVPN/1.0")
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
        http.send(req, HttpResponse.BodyHandlers.ofString()).body()
    }

    private suspend fun tcpPing(host: String, port: Int): Int = withContext(Dispatchers.IO) {
        try {
            val start = System.nanoTime()
            Socket().use { s -> s.connect(InetSocketAddress(host, port), 3000) }
            ((System.nanoTime() - start) / 1_000_000).toInt()
        } catch (e: Exception) {
            -1
        }
    }

    private fun deriveName(url: String): String = try {
        URI.create(url).host ?: "Subscription"
    } catch (e: Exception) {
        "Subscription"
    }
}
