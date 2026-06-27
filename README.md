# SmoothVPN — Desktop (Windows)

## ⬇️ Download

**[Download for Windows](https://github.com/rezaigh/SmoothVPN-Desktop/releases/latest/download/SmoothVPN-windows.zip)**

Extract the zip, then **right-click `SmoothVPN.exe` → Run as administrator** (full VPN mode needs admin).


Windows desktop build of [SmoothVPN](https://github.com/rezaigh/SmoothVPN), the
open-source Xray/V2Ray client. Kotlin + Compose Multiplatform, sharing the same
config/subscription core as the Android app.

## Features
- Branded UI, glowing connect button, server list, ping test, sort by ping
- Subscriptions (add / update) and clipboard import, **persisted** across restarts
- **Full system VPN (TUN mode)** — routes *all* apps (browsers, games, everything)

## How the tunnel works
```
apps → Wintun adapter → tun2socks → SOCKS 127.0.0.1:10808 → xray.exe → server
```
Three bundled engines do the work: **xray.exe** (speaks vmess/vless/trojan/ss to
your server), **tun2socks** (bridges the virtual adapter to xray), and
**wintun.dll** (the virtual network adapter). CI downloads them into
`resources/bin` at build time.

## ⚠️ Run as Administrator
TUN mode creates a network adapter and edits the Windows routing table, which
needs admin rights. **Right-click `SmoothVPN.exe` → Run as administrator.**
If you launch it normally, it will tell you it needs elevation.

## Run
Download the `SmoothVPN-windows` artifact from the green Actions build, extract
the whole folder, then right-click `SmoothVPN.exe` → **Run as administrator**.
Pick a server, press the connect button. Logs (if something goes wrong) are in
`%LOCALAPPDATA%\SmoothVPN\` — `xray.log` and `tun2socks.log`.

## Notes for inside Iran
A perfectly working tunnel can still fail to connect if the server's IP is
blocked. A self-hosted server abroad is usually the most reliable config.

## Licenses
MIT — Mohammadreza Ilchi. Bundled: Xray-core (MPL-2.0), tun2socks (MIT),
Wintun (WireGuard, prebuilt-redistribution license). Each keeps its own license.
