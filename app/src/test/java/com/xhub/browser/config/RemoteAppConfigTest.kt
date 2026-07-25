package com.xhub.browser.config

import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class RemoteAppConfigTest {

    @Test
    fun `selectFirstValidBody returns body when valid JSON is returned`() {
        val validJson = """
            {
                "url_app_home_page": "https://newdomain.site",
                "url_privacy_policy": "https://newdomain.site/privacy"
            }
        """.trimIndent()

        val urls = listOf("https://mirror1.com/config.json", "https://mirror2.com/config.json")
        val result = RemoteAppConfig.selectFirstValidBody(urls) { url ->
            if (url.contains("mirror1")) validJson else null
        }

        assertThat(result).isEqualTo(validJson)
    }

    @Test
    fun `selectFirstValidBody falls back to next mirror if first returns invalid JSON`() {
        val validJson = """{ "url_app_home_page": "https://newdomain.site" }"""

        val urls = listOf("https://mirror1.com/config.json", "https://mirror2.com/config.json")
        val result = RemoteAppConfig.selectFirstValidBody(urls) { url ->
            if (url.contains("mirror1")) "NOT_VALID_JSON" else validJson
        }

        assertThat(result).isEqualTo(validJson)
    }

    @Test
    fun `selectFirstValidBody returns null when all mirrors fail`() {
        val urls = listOf("https://mirror1.com/config.json", "https://mirror2.com/config.json")
        val result = RemoteAppConfig.selectFirstValidBody(urls) { null }

        assertThat(result).isNull()
    }
}
