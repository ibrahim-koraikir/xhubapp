package com.xhub.browser.ads

import android.app.Activity
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class InterstitialAdManagerTest {

    private lateinit var activity: Activity
    private lateinit var rootView: CoordinatorLayout
    private lateinit var config: InterstitialAdConfig
    private lateinit var manager: InterstitialAdManager

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        rootView = CoordinatorLayout(activity)
        config = InterstitialAdConfig(
            closeButtonDelayMs = 5000L,
            autoDismissMs = 15_000L
        )
        manager = InterstitialAdManager(activity, rootView, config)
    }

    @Test
    fun `show creates WebView overlay and adds to rootView`() {
        manager.show()

        assertThat(rootView.childCount).isEqualTo(1)
        val overlay = rootView.getChildAt(0) as ViewGroup
        assertThat(overlay).isInstanceOf(ViewGroup::class.java)
        // Should contain WebView + close button container
        assertThat(countWebViews(overlay)).isEqualTo(1)
        assertThat(countCloseButtons(overlay)).isEqualTo(1)
    }

    @Test
    fun `close button is GONE before delay, VISIBLE after`() {
        manager.show()

        val closeButton = findCloseButton(rootView)
        assertThat(closeButton).isNotNull
        // Extract the close button ImageButton from the overlay
        val btn = closeButton!!
        assertThat(btn.visibility).isEqualTo(ViewGroup.GONE)

        // Advance past close button delay
        ShadowLooper.shadowMainLooper().idleFor(config.closeButtonDelayMs, TimeUnit.MILLISECONDS)

        assertThat(btn.visibility).isEqualTo(ViewGroup.VISIBLE)
    }

    @Test
    fun `dismiss removes overlay and destroys WebView`() {
        manager.show()
        assertThat(rootView.childCount).isEqualTo(1)

        manager.dismiss()

        assertThat(rootView.childCount).isEqualTo(0)
    }

    @Test
    fun `dismiss is safe to call multiple times`() {
        manager.show()
        manager.dismiss()
        manager.dismiss() // no crash
        assertThat(rootView.childCount).isEqualTo(0)
    }

    @Test
    fun `double show is idempotent`() {
        manager.show()
        manager.show() // no crash, still one overlay
        assertThat(rootView.childCount).isEqualTo(1)
    }

    @Test
    fun `onBackPressed returns true and dismisses when showing`() {
        manager.show()
        assertThat(manager.onBackPressed()).isTrue()
        assertThat(rootView.childCount).isEqualTo(0)
    }

    @Test
    fun `onBackPressed returns false when not showing`() {
        assertThat(manager.onBackPressed()).isFalse()
    }

    @Test
    fun `showAfterDelay posts delayed show`() {
        manager.showAfterDelay(2_000L)
        assertThat(rootView.childCount).isEqualTo(0) // not shown yet

        ShadowLooper.shadowMainLooper().idleFor(2_000L, TimeUnit.MILLISECONDS)

        assertThat(rootView.childCount).isEqualTo(1) // now shown
    }

    @Test
    fun `onDestroy cancels pending handlers and dismisses`() {
        manager.showAfterDelay(10_000L)
        assertThat(rootView.childCount).isEqualTo(0)

        manager.onDestroy()

        // After advancing past the delay, it should NOT have shown
        ShadowLooper.shadowMainLooper().idleFor(10_000L, TimeUnit.MILLISECONDS)
        assertThat(rootView.childCount).isEqualTo(0)
    }

    // ── helpers ────────────────────────────────────────────────

    private fun countWebViews(parent: ViewGroup): Int {
        var count = 0
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is WebView) count++
            if (child is ViewGroup) count += countWebViews(child)
        }
        return count
    }

    private fun countCloseButtons(parent: ViewGroup): Int {
        var count = 0
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ImageButton) count++
            if (child is ViewGroup) count += countCloseButtons(child)
        }
        return count
    }

    private fun findCloseButton(root: ViewGroup): ImageButton? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ImageButton) return child
            if (child is ViewGroup) {
                val found = findCloseButton(child)
                if (found != null) return found
            }
        }
        return null
    }
}
