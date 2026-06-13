package com.xhub.browser.adblock

import android.net.Uri
import com.xhub.browser.adblock.NoOpAdBlocker
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Unit tests for [NoOpAdBlocker].
 */
class NoOpAdBlockerTest {

    @Test
    fun `isAd no-ops`() {
        val noOpAdBlocker = NoOpAdBlocker()
        val request = TestWebResourceRequest(Uri.parse("https://ads.google.com"), false, mapOf())

        assertThat(runBlocking { noOpAdBlocker.shouldBlock(request, "https://google.com") }).isNull()
    }

}
