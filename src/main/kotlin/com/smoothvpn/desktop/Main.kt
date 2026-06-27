package com.smoothvpn.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val state = rememberWindowState(width = 430.dp, height = 760.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "SmoothVPN",
        state = state,
        icon = painterResource("icon.png")
    ) {
        SmoothVpnTheme { App() }
    }
}
