# ExoClick Interstitial Ad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ExoClick JS-based mobile fullpage interstitial ads as a WebView overlay in the `download` flavor, alongside existing Adsterra direct-link ads.

**Architecture:** `InterstitialAdConfig` holds zone ID and timing values. `InterstitialAdManager` creates a full-screen WebView overlay with the ExoClick JS tag, shows it on app launch, reveals a close button after 5s, and auto-dismisses after N seconds. Wired into `WebBrowserActivity` gated by `BuildConfig.ADS_ENABLED`.

**Tech Stack:** Kotlin, Android WebView, Robolectric 4.x, Mockito 4.x, JUnit 4.

## Global Constraints

- All new code in package `com.xhub.browser.ads`
- Only active when `BuildConfig.ADS_ENABLED == true` (i.e. `download` flavor only)
- No changes to existing Adsterra `DirectLinkAdManager` or `AdConfigRepository`
- No new layout XML files — overlay built programmatically
- Platform: minSdk 21, compileSdk 35
- Use Timber for logging (existing pattern)
- Follow same test patterns as `DirectLinkAdManagerTest` (Robolectric + Mockito)

---

### Task 1: InterstitialAdConfig Data Class

**Files:**
- Create: `app/src/main/java/com/xhub/browser/ads/InterstitialAdConfig.kt`
- Create: `app/src/test/java/com/xhub/browser/ads/InterstitialAdConfigTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `InterstitialAdConfig` data class

- [ ] **Step 1: Write the test and config together**

Create `InterstitialAdConfig.kt`:

```kotlin
package com.xhub.browser.ads

data class InterstitialAdConfig(
    val zoneId: String = "5952204",
    val closeButtonDelayMs: Long = 5000L,
    val autoDismissMs: Long = 15_000L,
    val adProviderUrl: String = "https://a.pemsrv.com/ad-provider.js"
)
```

Create `InterstitialAdConfigTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test**

Run: `.\gradlew.bat testXhubFullDownloadDebugUnitTest --tests "com.xhub.browser.ads.InterstitialAdConfigTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xhub/browser/ads/InterstitialAdConfig.kt app/src/test/java/com/xhub/browser/ads/InterstitialAdConfigTest.kt
git commit -m "feat: add InterstitialAdConfig data class"
```

---

### Task 2: InterstitialAdManager

**Files:**
- Create: `app/src/main/java/com/xhub/browser/ads/InterstitialAdManager.kt`
- Create: `app/src/test/java/com/xhub/browser/ads/InterstitialAdManagerTest.kt`

**Interfaces:**
- Consumes: `InterstitialAdConfig` (from Task 1)
- Produces: `InterstitialAdManager` class with `showAfterDelay(delayMs)`, `show()`, `dismiss()`, `onPause()`, `onResume()`, `onDestroy()`, `onBackPressed(): Boolean`

- [ ] **Step 1: Write the failing test**

Create `InterstitialAdManagerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testXhubFullDownloadDebugUnitTest --tests "com.xhub.browser.ads.InterstitialAdManagerTest"`
Expected: FAIL (compilation errors — `InterstitialAdManager` doesn't exist yet)

- [ ] **Step 3: Write minimal implementation**

Create `InterstitialAdManager.kt`:

```kotlin
package com.xhub.browser.ads

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.app.Activity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import timber.log.Timber

class InterstitialAdManager(
    private val activity: Activity,
    private val rootView: CoordinatorLayout,
    private val config: InterstitialAdConfig = InterstitialAdConfig()
) {
    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: FrameLayout? = null
    private var adWebView: WebView? = null
    private var closeButton: ImageButton? = null
    private var isShowing = false

    private var showRunnable: Runnable? = null

    fun showAfterDelay(delayMs: Long) {
        if (isShowing) return
        showRunnable = Runnable { show() }
        handler.postDelayed(showRunnable, delayMs)
    }

    fun show() {
        if (isShowing) return
        isShowing = true

        val overlay = FrameLayout(activity).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val webView = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                loadWithOverviewMode = true
                useWideViewPort = true
                displayZoomControls = false
                builtInZoomControls = false
                setSupportZoom(false)
            }
            loadDataWithBaseURL(
                "https://exoclick.com",
                buildAdHtml(),
                "text/html",
                "UTF-8",
                null
            )
        }
        overlay.addView(webView)
        adWebView = webView

        val btn = ImageButton(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                120, 120
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 48
                endMargin = 24
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel)
            visibility = View.GONE
            setOnClickListener { dismiss() }
        }
        overlay.addView(btn)
        closeButton = btn

        rootView.addView(overlay)
        overlayView = overlay

        // Schedule close button reveal
        handler.postDelayed({
            closeButton?.visibility = View.VISIBLE
        }, config.closeButtonDelayMs)

        // Schedule auto-dismiss
        handler.postDelayed({
            dismiss()
        }, config.autoDismissMs)

        Timber.i("ExoClick interstitial shown")
    }

    fun dismiss() {
        if (!isShowing) return
        isShowing = false

        cancelPendingCallbacks()
        overlayView?.let { rootView.removeView(it) }
        adWebView?.destroy()
        adWebView = null
        closeButton = null
        overlayView = null
        Timber.i("ExoClick interstitial dismissed")
    }

    fun onPause() {
        adWebView?.onPause()
    }

    fun onResume() {
        adWebView?.onResume()
    }

    fun onDestroy() {
        cancelPendingCallbacks()
        dismiss()
    }

    fun onBackPressed(): Boolean {
        if (isShowing) {
            dismiss()
            return true
        }
        return false
    }

    // ── internal ─────────────────────────────────────────────────

    private fun cancelPendingCallbacks() {
        showRunnable?.let { handler.removeCallbacks(it) }
        showRunnable = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun buildAdHtml(): String = """
        <html>
        <body style="margin:0;overflow:hidden;background:#000;width:100vw;height:100vh;">
        <script async src="${config.adProviderUrl}"></script>
        <ins class="eas6a97888e33" data-zoneid="${config.zoneId}"></ins>
        <script>(AdProvider=window.AdProvider||[]).push({"serve":{}});</script>
        </body>
        </html>
    """.trimIndent()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testXhubFullDownloadDebugUnitTest --tests "com.xhub.browser.ads.InterstitialAdManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xhub/browser/ads/InterstitialAdManager.kt app/src/test/java/com/xhub/browser/ads/InterstitialAdManagerTest.kt
git commit -m "feat: add InterstitialAdManager with WebView overlay"
```

---

### Task 3: Wire InterstitialAdManager into WebBrowserActivity

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`
- Test: `app/src/test/java/com/xhub/browser/ads/InterstitialAdManagerTest.kt` (already covered via unit tests; integration behavior tested through existing ad wiring patterns)

**Interfaces:**
- Consumes: `InterstitialAdManager` (from Task 2), `BuildConfig.ADS_ENABLED`
- Produces: Working interstitial on app launch in `download` flavor

- [ ] **Step 1: Add field and initialization in WebBrowserActivity**

Find the existing ad block around line 494-530. After the `tabsManager.doOnceAfterInitialization { directLinkAdManager.maybeShowLaunchAd() }` line, add:

```kotlin
// ExoClick interstitial (download flavor only, alongside direct-link ads)
interstitialAdManager = InterstitialAdManager(
    activity = this,
    rootView = iBinding.coordinatorLayout,
    config = InterstitialAdConfig()
)
tabsManager.doOnceAfterInitialization {
    interstitialAdManager.showAfterDelay(2_000L)
}
```

Also add the field declaration near `directLinkAdManager` (~line 850):

```kotlin
private var interstitialAdManager: InterstitialAdManager? = null
```

- [ ] **Step 2: Add lifecycle hooks in WebBrowserActivity**

Find `onPause()` — add:

```kotlin
override fun onPause() {
    super.onPause()
    interstitialAdManager?.onPause()
}
```

Find `onResume()` — add:

```kotlin
override fun onResume() {
    super.onResume()
    interstitialAdManager?.onResume()
}
```

Find `onDestroy()` — add before `super.onDestroy()`:

```kotlin
interstitialAdManager?.onDestroy()
```

- [ ] **Step 3: Handle back press**

If `onBackPressed()` is overridden, add intercept at the top. Find the existing `onBackPressed()`:

```kotlin
override fun onBackPressed() {
    if (interstitialAdManager?.onBackPressed() == true) return
    super.onBackPressed()
}
```

If there's no override, add one.

- [ ] **Step 4: Build and verify compilation**

Run: `.\gradlew.bat assembleXhubFullDownloadDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt
git commit -m "feat: wire ExoClick interstitial into WebBrowserActivity"
```

---

### Task 4: Verify All Tests Pass

- [ ] **Step 1: Run all ad tests**

Run: `.\gradlew.bat testXhubFullDownloadDebugUnitTest --tests "com.xhub.browser.ads.*"`
Expected: All PASS (both `DirectLinkAdManagerTest` and `InterstitialAdManagerTest` and `AdConfigRepositoryTest` if it exists)

- [ ] **Step 2: Run full unit tests**

Run: `.\gradlew.bat testXhubFullDownloadDebugUnitTest`
Expected: PASS
