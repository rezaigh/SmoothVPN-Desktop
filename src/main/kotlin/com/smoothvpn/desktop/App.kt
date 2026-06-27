package com.smoothvpn.desktop

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smoothvpn.core.Profile
import kotlinx.coroutines.launch

@Composable
fun App() {
    val tunnel = remember { TunnelManager() }
    val state = remember { DesktopVpnState() }
    val scope = rememberCoroutineScope()

    var selectedId by remember { mutableStateOf<String?>(null) }
    var sortByPing by remember { mutableStateOf(false) }
    var subInput by remember { mutableStateOf("") }

    val shown = if (sortByPing)
        state.profiles.sortedBy { if (it.latencyMs < 0) Int.MAX_VALUE else it.latencyMs }
    else state.profiles

    val selected = state.profiles.firstOrNull { it.id == selectedId } ?: state.profiles.firstOrNull()

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {

            // ---- top bar ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("∿", color = Accent, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text("SmoothVPN", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground)
            }

            // ---- hero connect button ----
            ConnectButton(
                connected = tunnel.connected,
                remark = if (tunnel.connecting) "Connecting…"
                else (selected?.remark ?: "No server selected"),
                onClick = {
                    if (tunnel.connected) scope.launch { tunnel.disconnect() }
                    else selected?.let { p -> scope.launch { tunnel.connect(p) } }
                }
            )
            if (tunnel.message.isNotBlank()) {
                Text(
                    tunnel.message,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
            }

            // ---- servers header + sort ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Servers (${state.profiles.size})", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { sortByPing = !sortByPing }) {
                    Text(
                        if (sortByPing) "Ping ▲" else "Sort by ping",
                        color = if (sortByPing) Accent
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontWeight = if (sortByPing) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // ---- action row: paste / ping / update ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !state.busy, onClick = {
                    val link = readClipboard()
                    if (state.importLink(link)) {
                        if (selectedId == null) selectedId = state.profiles.lastOrNull()?.id
                    } else {
                        state.status = "Clipboard isn't a valid config link"
                    }
                }) { Text("Paste link") }

                TextButton(enabled = !state.busy, onClick = {
                    scope.launch { state.pingAll() }
                }) { Text("Ping") }

                TextButton(enabled = !state.busy, onClick = {
                    scope.launch { state.updateSubscriptions() }
                }) { Text("Update") }
            }

            // ---- subscription input ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = subInput,
                    onValueChange = { subInput = it },
                    placeholder = { Text("Subscription URL", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(enabled = !state.busy, onClick = {
                    val u = subInput
                    subInput = ""
                    scope.launch {
                        state.addSubscription(u)
                        if (selectedId == null) selectedId = state.profiles.firstOrNull()?.id
                    }
                }) { Text("Add") }
            }

            if (state.status.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(state.status, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(8.dp))

            // ---- server list ----
            if (state.profiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Add a subscription URL, or copy a vmess:// / vless:// /\ntrojan:// / ss:// link and press \"Paste link\".",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.id }) { profile ->
                        ServerRow(
                            profile = profile,
                            selected = profile.id == (selected?.id),
                            onClick = { selectedId = profile.id },
                            onDelete = {
                                state.removeProfile(profile.id)
                                if (selectedId == profile.id) selectedId = state.profiles.firstOrNull()?.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(connected: Boolean, remark: String, onClick: () -> Unit) {
    val core = if (connected) Accent else Color(0xFF2A333C)
    val pulse = rememberInfiniteTransition()
    val glow by pulse.animateFloat(
        initialValue = 0.96f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse)
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(212.dp)) {
            Box(
                Modifier.size(200.dp)
                    .scale(if (connected) glow else 1f)
                    .clip(CircleShape)
                    .background(core.copy(alpha = if (connected) 0.16f else 0.05f))
            )
            Box(
                Modifier.size(160.dp).clip(CircleShape)
                    .background(core.copy(alpha = if (connected) 0.20f else 0.09f))
                    .border(1.dp, core.copy(alpha = 0.35f), CircleShape)
            )
            Box(
                Modifier.size(126.dp).clip(CircleShape)
                    .background(
                        if (connected)
                            Brush.verticalGradient(listOf(Color(0xFF4CEBAB), AccentDeep))
                        else
                            Brush.verticalGradient(listOf(Color(0xFF20272E), Color(0xFF151B21)))
                    )
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("∿", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        color = if (connected) Color(0xFF05231A) else Accent)
                    Text(if (connected) "ON" else "OFF",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (connected) Color(0xFF05231A) else Color(0xFF8A97A3))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (connected) "Protected" else "Tap to connect",
            fontWeight = FontWeight.Bold, fontSize = 17.sp,
            color = if (connected) Accent else MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(remark, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
    }
}

@Composable
private fun ServerRow(
    profile: Profile,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .then(
                if (selected) Modifier.border(1.5.dp, Accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Accent.copy(alpha = 0.12f) else Color(0xFF141A20)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (selected) Accent else Color(0xFF3A444E))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.remark, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${profile.protocol.tag.uppercase()} · ${profile.address}:${profile.port}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            LatencyChip(profile.latencyMs)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDelete) {
                Text("✕", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun LatencyChip(ms: Int) {
    val (label, tint) = when {
        ms < 0 -> "—" to Color(0xFF6B7682)
        ms < 150 -> "${ms}ms" to Accent
        ms < 350 -> "${ms}ms" to Color(0xFFE0C341)
        else -> "${ms}ms" to Color(0xFFE06A5A)
    }
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Reads the system clipboard as text (empty string on any failure). */
private fun readClipboard(): String = try {
    val data = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        .getData(java.awt.datatransfer.DataFlavor.stringFlavor)
    (data as? String)?.trim() ?: ""
} catch (e: Exception) {
    ""
}
