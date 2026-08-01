package com.xhub.browser.utils

import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class UrlValidatorTest {

    @Test
    fun `http and https URLs are allowed`() {
        assertEquals("http://example.com", UrlValidator.validateExternalUrl("http://example.com"))
        assertEquals("https://example.com", UrlValidator.validateExternalUrl("https://example.com"))
    }

    @Test
    fun `file scheme is rejected`() {
        assertNull(UrlValidator.validateExternalUrl("file:///sdcard/test.html"))
        assertNull(
            UrlValidator.validateExternalUrl(
                "file:///data/data/com.xhub.browser/databases/bookmarks.db"
            )
        )
    }

    @Test
    fun `content scheme is rejected`() {
        assertNull(
            UrlValidator.validateExternalUrl("content://com.evil.provider/../../sensitive.db")
        )
    }

    @Test
    fun `javascript scheme is rejected`() {
        assertNull(UrlValidator.validateExternalUrl("javascript:alert(document.cookie)"))
    }

    @Test
    fun `internal schemes allowed when flag is true`() {
        assertEquals(
            "xhub://home",
            UrlValidator.validateExternalUrl("xhub://home", allowInternal = true)
        )
        assertEquals(
            "fulguris:home",
            UrlValidator.validateExternalUrl("fulguris:home", allowInternal = true)
        )
        assertNull(UrlValidator.validateExternalUrl("fulguris:home", allowInternal = false))
        assertNull(UrlValidator.validateExternalUrl("xhub://home", allowInternal = false))
    }

    @Test
    fun `null and blank URLs are rejected`() {
        assertNull(UrlValidator.validateExternalUrl(null))
        assertNull(UrlValidator.validateExternalUrl(""))
        assertNull(UrlValidator.validateExternalUrl("   "))
    }

    @Test
    fun `data URLs are allowed`() {
        assertEquals(
            "data:text/html,<h1>Test</h1>",
            UrlValidator.validateExternalUrl("data:text/html,<h1>Test</h1>")
        )
    }

    @Test
    fun `isSafeForWebView allows internal URLs`() {
        assertTrue(UrlValidator.isSafeForWebView("https://example.com"))
        assertTrue(UrlValidator.isSafeForWebView("xhub://home"))
        assertFalse(UrlValidator.isSafeForWebView("file:///etc/passwd"))
        assertFalse(UrlValidator.isSafeForWebView("content://evil"))
        assertFalse(UrlValidator.isSafeForWebView("javascript:alert(1)"))
    }
}
