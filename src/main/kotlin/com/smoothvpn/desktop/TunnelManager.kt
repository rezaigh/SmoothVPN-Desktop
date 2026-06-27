package com.smoothvpn.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smoothvpn.core.Profile
import com.smoothvpn.core.RoutingOptions
import com.smoothvpn.core.XrayConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress

/**
 * Full system VPN (TUN mode) for Windows.
 *
 *   apps → Wintun adapter → tun2socks → SOCKS 127.0.0.1:10808 → xray.exe → server
 *
 * Requires administrator rights (to create the adapter and edit the routing
 * table). All three binaries (xray.exe, tun2socks.exe, wintun.dll) are bundled
 * in resources/bin and extracted to %LOCALAPPDATA%\SmoothVPN on first use.
 */
class TunnelManager {

    var connected by mutableStateOf(false); private set
    var connecting by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var activeProfile by mutableStateOf<Profile?>(null); private set

    private var xray: Process? = null
    private var t2s: Process? = null
    private var activeServerIp = ""
    private var activeGateway = ""

    private val tunName = "wintun"
    private val tunAddr = "198.18.0.1"
    private val dnsServer = "8.8.8.8"

    suspend fun connect(profile: Profile) = withContext(Dispatchers.IO) {
        if (connected || connecting) return@withContext
        connecting = true
        message = "Starting…"
        try {
            if (!isAdmin()) {
                message = "Please run SmoothVPN as Administrator for VPN mode."
                return@withContext
            }
            val dir = ensureBinaries()

            val server = runCatching { InetAddress.getByName(profile.address).hostAddress }.getOrNull()
            if (server.isNullOrBlank()) { message = "Couldn't resolve ${profile.address}"; return@withContext }

            val gw = defaultGateway()
            if (gw.isBlank()) { message = "Couldn't find your default gateway"; return@withContext }

            // step 1 - xray — local SOCKS that speaks the real protocol to the server
            val cfg = File(dir, "config.json")
            cfg.writeText(XrayConfigBuilder.build(profile, RoutingOptions(enableMux = false)))
            xray = ProcessBuilder(File(dir, "xray.exe").absolutePath, "run", "-c", cfg.absolutePath)
                .directory(dir).redirectErrorStream(true)
                .redirectOutput(File(dir, "xray.log")).start()
            Thread.sleep(900)
            if (xray?.isAlive != true) {
                message = "xray failed to start (see xray.log)"; cleanup(); return@withContext
            }

            // step 2 - tun2socks — creates the Wintun adapter, forwards it into xray
            t2s = ProcessBuilder(
                File(dir, "tun2socks.exe").absolutePath,
                "-device", tunName,
                "-proxy", "socks5://127.0.0.1:${XrayConfigBuilder.SOCKS_PORT}",
                "-loglevel", "info"
            ).directory(dir).redirectErrorStream(true)
                .redirectOutput(File(dir, "tun2socks.log")).start()

            if (!waitForAdapter(tunName, 12000)) {
                message = "Wintun adapter didn't come up (see tun2socks.log)"; cleanup(); return@withContext
            }
            Thread.sleep(600)

            // step 3 - configure the adapter + routing
            activeServerIp = server
            activeGateway = gw
            exec("netsh", "interface", "ip", "set", "address", "name=$tunName",
                "source=static", "addr=$tunAddr", "mask=255.255.255.0")
            exec("netsh", "interface", "ip", "set", "dnsservers", "name=$tunName",
                "static", "address=$dnsServer", "register=none", "validate=no")
            // Capture ALL traffic into the tunnel. Two /1 routes beat the physical
            // 0.0.0.0/0 default by longest-prefix match. They MUST be pinned to the
            // wintun interface index, or Windows binds them to the physical card.
            exec("netsh", "interface", "ipv4", "set", "interface", tunName, "metric=1")
            val ifIndex = adapterIndex(tunName)
            addTunRoute("0.0.0.0", "128.0.0.0", ifIndex)
            addTunRoute("128.0.0.0", "128.0.0.0", ifIndex)
            // …except xray's own connection to the server, which must bypass it
            exec("cmd", "/c", "route", "add", server, "mask", "255.255.255.255", gw, "metric", "1")

            activeProfile = profile
            connected = true
            message = "Connected · full tunnel active"
        } catch (e: Exception) {
            message = "Connect failed: ${e.message}"
            runCatching { cleanup() }
        } finally {
            connecting = false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        cleanup()
        connected = false
        message = "Disconnected"
    }

    private fun cleanup() {
        runCatching { exec("cmd", "/c", "route", "delete", "0.0.0.0", "mask", "128.0.0.0") }
        runCatching { exec("cmd", "/c", "route", "delete", "128.0.0.0", "mask", "128.0.0.0") }
        if (activeServerIp.isNotBlank()) runCatching { exec("cmd", "/c", "route", "delete", activeServerIp) }
        runCatching { t2s?.destroy() }
        runCatching { xray?.destroy() }
        t2s = null
        xray = null
    }

    // ---- helpers ------------------------------------------------------------

    private fun ensureBinaries(): File {
        val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
        val dir = File(base, "SmoothVPN").apply { mkdirs() }
        extract("/bin/xray.exe", File(dir, "xray.exe"))
        extract("/bin/tun2socks.exe", File(dir, "tun2socks.exe"))
        extract("/bin/wintun.dll", File(dir, "wintun.dll"))
        return dir
    }

    private fun extract(resource: String, target: File) {
        if (target.exists() && target.length() > 0) return
        javaClass.getResourceAsStream(resource).use { input ->
            requireNotNull(input) { "Missing bundled $resource" }
            FileOutputStream(target).use { input.copyTo(it) }
        }
    }

    private fun isAdmin(): Boolean = try {
        ProcessBuilder("cmd", "/c", "net session")
            .redirectErrorStream(true).start().waitFor() == 0
    } catch (e: Exception) {
        false
    }

    private fun adapterIndex(name: String): String = capture(
        "powershell", "-NoProfile", "-Command",
        "(Get-NetAdapter -Name '$name' -ErrorAction SilentlyContinue).ifIndex"
    ).lines().map { it.trim() }.firstOrNull { it.toIntOrNull() != null } ?: ""

    private fun addTunRoute(dest: String, mask: String, ifIndex: String) {
        val cmd = mutableListOf("cmd", "/c", "route", "add", dest, "mask", mask, tunAddr, "metric", "1")
        if (ifIndex.isNotBlank()) {
            cmd.add("if")
            cmd.add(ifIndex)
        }
        exec(*cmd.toTypedArray())
    }

    private fun defaultGateway(): String {
        val out = capture(
            "powershell", "-NoProfile", "-Command",
            "(Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1).NextHop"
        )
        return out.lines().map { it.trim() }.firstOrNull { it.count { c -> c == '.' } == 3 } ?: ""
    }

    private fun waitForAdapter(name: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val out = capture(
                "powershell", "-NoProfile", "-Command",
                "Get-NetAdapter -Name '$name' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name"
            )
            if (out.contains(name, ignoreCase = true)) return true
            Thread.sleep(400)
        }
        return false
    }

    private fun exec(vararg cmd: String): Int = try {
        ProcessBuilder(*cmd).redirectErrorStream(true).start().waitFor()
    } catch (e: Exception) {
        -1
    }

    private fun capture(vararg cmd: String): String = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor()
        text.trim()
    } catch (e: Exception) {
        ""
    }
}
