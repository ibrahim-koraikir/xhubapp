package com.xhub.browser.utils

import com.xhub.browser.App
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class UrlSpecialUrlTest {

    @Before
    fun setUp() {
        // isSpecialUrl() reads the global `app` (App.filesDir), so point it at the test app
        val mockApp = mock(App::class.java)
        `when`(mockApp.filesDir).thenReturn(File(RuntimeEnvironment.getApplication().filesDir.path))
        val appKtClass = Class.forName("com.xhub.browser.AppKt")
        val field = appKtClass.getDeclaredField("app")
        field.isAccessible = true
        field.set(null, mockApp)
    }

    @Test
    fun `xhub scheme URLs are special URLs`() {
        listOf(
            "xhub://home",
            "xhub://start",
            "xhub://incognito",
            "xhub://bookmarks",
            "xhub://history",
            "xhub://noop"
        ).forEach { url ->
            assertTrue("$url should be special", url.isSpecialUrl())
        }
    }

    @Test
    fun `ordinary web URLs are not special`() {
        listOf(
            "https://example.com",
            "http://example.com",
            "about:blank",
            "data:text/html,hi",
            "xhub-not-a-scheme"
        ).forEach { url ->
            assertFalse("$url should not be special", url.isSpecialUrl())
        }
    }

    @Test
    fun `file based special pages remain special`() {
        val filesDir = RuntimeEnvironment.getApplication().filesDir.path
        assertTrue("file://$filesDir/history.html".isSpecialUrl())
        assertTrue("file://$filesDir/homepage.html".isSpecialUrl())
    }

    @Test
    fun `null is not special`() {
        val nullUrl: String? = null
        assertFalse(nullUrl.isSpecialUrl())
    }
}
