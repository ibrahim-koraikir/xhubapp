package com.xhub.browser.ads

import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class InterstitialAdConfigTest {

    @Test
    fun `default config has expected values`() {
        val config = InterstitialAdConfig()
        assertThat(config.zoneId).isEqualTo("5952204")
        assertThat(config.closeButtonDelayMs).isEqualTo(5000L)
        assertThat(config.autoDismissMs).isEqualTo(15_000L)
        assertThat(config.adProviderUrl).isEqualTo("https://a.pemsrv.com/ad-provider.js")
    }

    @Test
    fun `config can be customized`() {
        val config = InterstitialAdConfig(
            autoDismissMs = 20_000L
        )
        assertThat(config.autoDismissMs).isEqualTo(20_000L)
        assertThat(config.zoneId).isEqualTo("5952204") // unchanged default
    }
}
