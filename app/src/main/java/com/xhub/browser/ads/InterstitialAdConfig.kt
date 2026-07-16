package com.xhub.browser.ads

data class InterstitialAdConfig(
    val zoneId: String = "5952204",
    val closeButtonDelayMs: Long = 5000L,
    val autoDismissMs: Long = 15_000L,
    val adProviderUrl: String = "https://a.pemsrv.com/ad-provider.js"
)
