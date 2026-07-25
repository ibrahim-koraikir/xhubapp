package com.xhub.browser.ads

import android.content.SharedPreferences
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import com.xhub.browser.view.WebPageTab
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class DirectLinkAdManagerTest {

    @Mock lateinit var repo: AdConfigRepository
    @Mock lateinit var prefs: SharedPreferences
    @Mock lateinit var editor: SharedPreferences.Editor
    @Mock lateinit var bgTab: WebPageTab

    private var capturedPreloadedUrl: String? = null
    private var capturedLoadedUrl: String? = null
    private var capturedOpenAdUrl: String? = null
    private var capturedOpenAdShow: Boolean? = null

    private lateinit var manager: DirectLinkAdManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
        `when`(editor.remove(anyString())).thenReturn(editor)

        // Default: first launch
        `when`(prefs.getBoolean(anyString(), anyBoolean())).thenReturn(false)

        manager = DirectLinkAdManager(
            repo = repo,
            prefs = prefs,
            openAdTab = { url, show ->
                capturedOpenAdUrl = url
                capturedOpenAdShow = show
            },
            createPreloadedTab = { url ->
                capturedPreloadedUrl = url
                bgTab
            },
            loadInCurrentTab = { url -> capturedLoadedUrl = url }
        )
    }

    // ── preloadNextAd ──────────────────────────────────────────────────────

    @Test
    fun `preloadNextAd creates background tab when URL available`() {
        `when`(repo.randomAdUrl()).thenReturn("https://ad.example.com/offer")

        manager.preloadNextAd()

        assertThat(capturedPreloadedUrl).isEqualTo("https://ad.example.com/offer")
    }

    @Test
    fun `preloadNextAd skips if already preloading`() {
        `when`(repo.randomAdUrl()).thenReturn("https://ad.example.com/offer")
        manager.preloadNextAd()
        capturedPreloadedUrl = null

        manager.preloadNextAd()

        assertThat(capturedPreloadedUrl).isNull()
    }

    @Test
    fun `preloadNextAd skips if no cached URLs`() {
        `when`(repo.randomAdUrl()).thenReturn(null)

        manager.preloadNextAd()

        assertThat(capturedPreloadedUrl).isNull()
    }

    // ── onUserGestureNavigation ────────────────────────────────────────────

    @Test
    fun `onUserGestureNavigation returns false when count below threshold`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(0)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)

        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isFalse()
        assertThat(capturedLoadedUrl).isNull()
    }

    @Test
    fun `onUserGestureNavigation fires ad when threshold hit using fallback URL`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(bgTab.webView).thenReturn(null)
        `when`(repo.randomAdUrl()).thenReturn("https://fallback-ad.com/offer")

        manager.preloadNextAd()
        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isTrue()
        assertThat(capturedLoadedUrl).isEqualTo("https://fallback-ad.com/offer")
    }

    @Test
    fun `onUserGestureNavigation falls back to repo URL when bgTab webView url is null`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(bgTab.webView).thenReturn(null)
        `when`(repo.randomAdUrl()).thenReturn("https://fallback-ad.com/offer")

        manager.preloadNextAd()
        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isTrue()
    }

    @Test
    fun `onUserGestureNavigation returns false if no ad URL available at trigger time`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(bgTab.webView).thenReturn(null)
        `when`(repo.randomAdUrl()).thenReturn(null)

        manager.preloadNextAd()
        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isFalse()
    }

    @Test
    fun `onUserGestureNavigation resets counters after firing ad`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(bgTab.webView).thenReturn(null)
        `when`(repo.randomAdUrl()).thenReturn("https://any-ad.com/offer")

        manager.preloadNextAd()
        manager.onUserGestureNavigation("https://example.com/page")

        org.mockito.Mockito.verify(editor).putInt("dlad_tap_count", 0)
        org.mockito.Mockito.verify(editor).putInt(eq("dlad_tap_thresh"), anyInt())
    }

    @Test
    fun `onUserGestureNavigation does not fire if preload not ready`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(bgTab.webView).thenReturn(null)

        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isFalse()
        assertThat(capturedLoadedUrl).isNull()
    }

    // ── maybeShowLaunchAd ──────────────────────────────────────────────────

    @Test
    fun `maybeShowLaunchAd starts preload immediately and queues background launch`() {
        `when`(repo.randomAdUrl()).thenReturn("https://launch-ad.com/offer")

        manager.maybeShowLaunchAd()

        // Preload should fire immediately
        assertThat(capturedPreloadedUrl).isEqualTo("https://launch-ad.com/offer")

        // Launch ad should NOT have fired yet (it's delayed)
        assertThat(capturedOpenAdUrl).isNull()

        // Advance clock past the 3s delay
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idleFor(3, java.util.concurrent.TimeUnit.SECONDS)

        assertThat(capturedOpenAdUrl).isEqualTo("https://launch-ad.com/offer")
        assertThat(capturedOpenAdShow).isFalse()
    }

    @Test
    fun `maybeShowLaunchAd only fires launch ad once per session`() {
        `when`(repo.randomAdUrl()).thenReturn("https://launch-ad.com/offer")
        // Second call: launch already done
        `when`(prefs.getBoolean("dlad_launch_done", false)).thenReturn(true)

        manager.maybeShowLaunchAd()

        // Preload should still fire
        assertThat(capturedPreloadedUrl).isNotNull

        // Launch ad should NOT fire (already done)
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idleFor(3, java.util.concurrent.TimeUnit.SECONDS)
        assertThat(capturedOpenAdUrl).isNull()
    }

    // ── onActivityDestroy ──────────────────────────────────────────────────

    @Test
    fun `onUserGestureNavigation uses resolved URL from preloaded WebView when available`() {
        val mockWebView = org.mockito.Mockito.mock(com.xhub.browser.view.WebViewEx::class.java)
        `when`(mockWebView.url).thenReturn("https://resolved-ad.com/final-offer")
        `when`(bgTab.webView).thenReturn(mockWebView)
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(repo.randomAdUrl()).thenReturn("https://ad.example.com/offer")

        manager.preloadNextAd()
        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isTrue()
        assertThat(capturedLoadedUrl).isEqualTo("https://resolved-ad.com/final-offer")
    }

    @Test
    fun `onUserGestureNavigation falls back when preload WebView has not resolved yet`() {
        val mockWebView = org.mockito.Mockito.mock(com.xhub.browser.view.WebViewEx::class.java)
        `when`(mockWebView.url).thenReturn("https://ad.example.com/offer") // same as preload URL
        `when`(bgTab.webView).thenReturn(mockWebView)
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(repo.randomAdUrl()).thenReturn("https://ad.example.com/offer")

        manager.preloadNextAd()
        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isTrue()
        assertThat(capturedLoadedUrl).isEqualTo("https://ad.example.com/offer")
    }

    @Test
    fun `onActivityDestroy resets launch flag and destroys preloaded tab`() {
        `when`(repo.randomAdUrl()).thenReturn("https://ad.example.com/offer")

        manager.preloadNextAd()
        manager.onActivityDestroy()

        org.mockito.Mockito.verify(editor).putBoolean("dlad_launch_done", false)
        org.mockito.Mockito.verify(editor).apply()
    }
}
