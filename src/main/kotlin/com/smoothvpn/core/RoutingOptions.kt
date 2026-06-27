package com.smoothvpn.core

/**
 * User-configurable routing / behaviour flags fed into [XrayConfigBuilder].
 *
 * [geoAssetsAvailable] gates the rules that need geoip.dat / geosite.dat. When
 * false (e.g. a fresh install with no geo files), those rules are skipped so the
 * core still starts cleanly. [blockAds] / [domesticDirect] only take effect when
 * geo assets are present.
 */
data class RoutingOptions(
    val enableMux: Boolean = true,
    val bypassLan: Boolean = true,
    val blockAds: Boolean = false,
    val domesticDirect: Boolean = false,
    val geoAssetsAvailable: Boolean = false
)
