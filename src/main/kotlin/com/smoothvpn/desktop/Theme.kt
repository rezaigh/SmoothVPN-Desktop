package com.smoothvpn.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — identical to the Android app & launcher icon.
val Accent = Color(0xFF34E0A1)
val AccentDeep = Color(0xFF0E8E5F)
val BgTop = Color(0xFF13261F)
val BgBottom = Color(0xFF07090C)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF052015),
    secondary = AccentDeep,
    background = BgBottom,
    surface = Color(0xFF141A20),
    surfaceVariant = Color(0xFF1A222B),
    onBackground = Color(0xFFE7ECF1),
    onSurface = Color(0xFFE7ECF1),
    outline = Color(0xFF2C3A44)
)

@Composable
fun SmoothVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
