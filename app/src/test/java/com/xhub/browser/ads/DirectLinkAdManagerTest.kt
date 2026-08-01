package com.xhub.browser.ads

import android.content.SharedPreferences
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
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
            loadInCurrentTab = { url -> capturedLoadedUrl = url }
        )
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
    fun `onUserGestureNavigation fires ad when threshold hit with valid URL`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(repo.randomAdUrl()).thenReturn("https://ad.example.com/offer")

        val result = manager.onUserGestureNavigation("https://example.com/page")

        assertThat(result).isTrue()
        assertThat(capturedLoadedUrl).isEqualTo("https://ad.example.com/offer")
    }

    @Test
    fun `onUserGestureNavigation does NOT intercept when no URL available`() {
        // randomAdUrl() returns null when no URLs cached yet
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(repo.randomAdUrl()).thenReturn(null)

        val result = manager.onUserGestureNavigation("https://example.com/page")

        // Must return false — original navigation proceeds normally
        assertThat(result).isFalse()
        assertThat(capturedLoadedUrl).isNull()
    }

    @Test
    fun `onUserGestureNavigation resets counters after firing ad`() {
        `when`(prefs.getInt(eq("dlad_tap_count"), anyInt())).thenReturn(9)
        `when`(prefs.getInt(eq("dlad_tap_thresh"), anyInt())).thenReturn(10)
        `when`(repo.randomAdUrl()).thenReturn("https://any-ad.com/offer")

        manager.onUserGestureNavigation("https://example.com/page")

        org.mockito.Mockito.verify(editor).putInt("dlad_tap_count", 0)
        org.mockito.Mockito.verify(editor).putInt(eq("dlad_tap_thresh"), anyInt())
    }

    // ── maybeShowLaunchAd ──────────────────────────────────────────────────

    @Test
    fun `maybeShowLaunchAd queues background launch ad`() {
        `when`(repo.randomAdUrl()).thenReturn("https://launch-ad.com/offer")

        manager.maybeShowLaunchAd()

        // Launch ad should NOT have fired yet (it's delayed 3 s)
        assertThat(capturedOpenAdUrl).isNull()

        // Advance clock past the 3 s delay
        org.robolectric.shadows.ShadowLooper.shadowMainLooper()
            .idleFor(3, java.util.concurrent.TimeUnit.SECONDS)

        assertThat(capturedOpenAdUrl).isEqualTo("https://launch-ad.com/offer")
        assertThat(capturedOpenAdShow).isFalse()
    }

    @Test
    fun `maybeShowLaunchAd only fires once per session`() {
        `when`(repo.randomAdUrl()).thenReturn("https://launch-ad.com/offer")
        `when`(prefs.getBoolean("dlad_launch_done", false)).thenReturn(true)

        manager.maybeShowLaunchAd()

        org.robolectric.shadows.ShadowLooper.shadowMainLooper()
            .idleFor(3, java.util.concurrent.TimeUnit.SECONDS)
        assertThat(capturedOpenAdUrl).isNull()
    }

    @Test
    fun `maybeShowLaunchAd skips when no URL available`() {
        `when`(repo.randomAdUrl()).thenReturn(null)

        manager.maybeShowLaunchAd()

        org.robolectric.shadows.ShadowLooper.shadowMainLooper()
            .idleFor(3, java.util.concurrent.TimeUnit.SECONDS)

        // No tab should be opened when no valid URL
        assertThat(capturedOpenAdUrl).isNull()
    }

    // ── onActivityDestroy ──────────────────────────────────────────────────

    @Test
    fun `onActivityDestroy resets launch flag`() {
        manager.onActivityDestroy()

        org.mockito.Mockito.verify(editor).putBoolean("dlad_launch_done", false)
        org.mockito.Mockito.verify(editor).apply()
    }
}
