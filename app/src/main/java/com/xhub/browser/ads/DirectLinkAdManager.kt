package com.xhub.browser.ads

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.xhub.browser.view.WebPageTab
import timber.log.Timber

/**
 * Direct-link ads load in the **current tab** (same-tab navigation).
 *
 * ### How instant loading works
 * When the user starts browsing, we pre-load the next ad URL in a **detached background
 * [WebPageTab]** that is NOT added to the tab list (invisible to the user). The WebView
 * follows all Adsterra redirect hops silently. By the time the tap threshold fires (4–7
 * taps), the redirect chain is already resolved and the final destination URL is sitting
 * in the background WebView's [android.webkit.WebView.getUrl].
 *
 * When the trigger fires we:
 * 1. Grab `backgroundTab.webView?.url` — the fully-resolved final URL.
 * 2. Destroy the background tab (releases WebView resources; it was never in the tab list).
 * 3. Load the resolved URL in the **current foreground tab** via [loadInCurrentTab].
 * 4. Queue up the next pre-load for the following trigger.
 *
 * The ad blocker bypass ([WebPageTab.isShowingDirectAd]) is set on BOTH the pre-load tab
 * (so redirects follow freely) and the current tab (so the final page loads without being
 * blocked).
 *
 * @param repo              Supplies random ad URLs from remote-config cache.
 * @param prefs             Persists tap counter and session flags.
 * @param openAdTab         Opens [url] as a background tab (launch ad only).
 * @param createPreloadedTab  Creates a detached [WebPageTab] that loads [url] silently.
 * @param loadInCurrentTab  Loads [url] in the active foreground tab (same-tab navigation).
 */
class DirectLinkAdManager(
    private val repo: AdConfigRepository,
    private val prefs: SharedPreferences,
    private val openAdTab: (url: String, show: Boolean) -> Unit,
    private val createPreloadedTab: (url: String) -> WebPageTab,
    private val loadInCurrentTab: (url: String) -> Unit,
) {

    companion object {
        private const val KEY_LAUNCH_DONE = "dlad_launch_done"
        private const val KEY_TAP_COUNT   = "dlad_tap_count"
        private const val KEY_TAP_THRESH  = "dlad_tap_thresh"

        private const val THRESH_MIN = 4
        private const val THRESH_MAX = 7
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Background WebPageTab pre-loading the next ad (never in TabsManager's tab list). */
    private var preloadedAdTab: WebPageTab? = null

    /**
     * The original ad URL that was passed to the pre-load tab.
     * Used to detect whether the background WebView has actually started resolving
     * the redirect chain. If [webView.url] still equals this value when the trigger
     * fires, the redirects haven't started yet and we use it as-is.
     */
    private var preloadingUrl: String? = null

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Start pre-loading the next ad in a background tab if we don't have one already.
     * Safe to call repeatedly — no-ops when a pre-load is already in progress.
     */
    fun preloadNextAd() {
        if (preloadedAdTab != null) {
            Timber.d("DirectAd: pre-load already in progress — skipping")
            return
        }
        // Skip pre-loading if the device is critically low on memory.
        // Creating a hidden WebView on a low-RAM phone is the primary cause of OOM crashes.
        val runtime = Runtime.getRuntime()
        val freeMemMb = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)
        if (freeMemMb < 150) {
            Timber.w("DirectAd: skipping pre-load — only ${freeMemMb}MB heap free (threshold: 150MB)")
            return
        }
        val url = repo.randomAdUrl()
        preloadingUrl = url
        try {
            preloadedAdTab = createPreloadedTab(url)
            Timber.i("DirectAd: pre-loading background tab -> $url (${freeMemMb}MB heap free)")
        } catch (e: Exception) {
            Timber.e(e, "DirectAd: failed to create preload tab")
            preloadingUrl = null
        }
    }

    /**
     * Show one silent background tab on cold start, then queue the first navigation pre-load.
     * Safe to call from `onCreate` once TabsManager is initialised.
     */
    fun maybeShowLaunchAd() {
        // Kick off navigation pre-load immediately so it's warm by the time the user
        // has tapped 4–7 times.
        preloadNextAd()

        if (prefs.getBoolean(KEY_LAUNCH_DONE, false)) {
            Timber.d("DirectAd: launch ad already fired this session — skipping")
            return
        }
        prefs.edit().putBoolean(KEY_LAUNCH_DONE, true).apply()

        handler.postDelayed({
            val url = repo.randomAdUrl()
            try {
                openAdTab(url, false)
                Timber.i("DirectAd: opened launch background tab -> $url")
            } catch (e: Exception) {
                Timber.e(e, "DirectAd: failed to open launch background tab")
            }
        }, 3_000L)
    }

    /** Reset session state. Call from [android.app.Activity.onDestroy]. */
    fun onActivityDestroy() {
        prefs.edit().putBoolean(KEY_LAUNCH_DONE, false).apply()
        handler.removeCallbacksAndMessages(null)
        // Release any background tab that was never consumed.
        preloadedAdTab?.destroy()
        preloadedAdTab = null
    }

    /**
     * Count every user-gesture navigation. When the tap threshold is hit:
     * - If the background pre-load has finished, extract its resolved URL and load it in
     *   the current tab (same-tab, near-instant: redirect chain already done in background).
     * - If the pre-load isn't finished yet, fall back to loading the raw ad URL directly.
     *
     * Returns **true** when an ad fires (intercepts the original navigation);
     * returns **false** otherwise so the original navigation continues normally.
     */
    fun onUserGestureNavigation(url: String): Boolean {
        val count = prefs.getInt(KEY_TAP_COUNT, 0) + 1
        val thresh = prefs.getInt(KEY_TAP_THRESH, nextThreshold())

        Timber.v("DirectAd: gesture nav count=$count thresh=$thresh url=$url")
        prefs.edit().putInt(KEY_TAP_COUNT, count).apply()

        // Ensure a pre-load is running so it has maximum time to resolve before threshold.
        preloadNextAd()

        if (count < thresh) return false

        // Threshold hit — reset counters.
        prefs.edit()
            .putInt(KEY_TAP_COUNT, 0)
            .putInt(KEY_TAP_THRESH, nextThreshold())
            .apply()

        val bgTab = preloadedAdTab
        val initialUrl = preloadingUrl
        preloadedAdTab = null
        preloadingUrl = null

        val resolvedUrl: String = if (bgTab != null) {
            // Prefer the URL the background WebView has already navigated to (redirect resolved).
            // Only accept if it differs from the original preload URL — same URL means
            // the redirect chain hasn't started resolving yet, so use initialUrl as-is.
            val wvUrl = bgTab.webView?.url?.takeIf {
                it.isNotBlank() && it != "about:blank" && it != initialUrl
            }
            // Clean up the background tab — it was never in the tab list.
            try { bgTab.destroy() } catch (e: Exception) { Timber.w(e, "DirectAd: bgTab destroy") }
            // Prefer resolved URL → fallback to original preload URL → fallback to fresh random URL
            wvUrl ?: initialUrl ?: repo.randomAdUrl()
        } else {
            initialUrl ?: repo.randomAdUrl()
        }

        try {
            loadInCurrentTab(resolvedUrl)
            Timber.i("DirectAd: loading ad in current tab -> $resolvedUrl (intercepted: $url)")
        } catch (e: Exception) {
            Timber.e(e, "DirectAd: failed to load ad in current tab")
            return false
        }

        // Queue next pre-load with a short delay to avoid competing with the ad page load.
        handler.postDelayed({ preloadNextAd() }, 3_000L)

        return true
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private fun nextThreshold(): Int = (THRESH_MIN..THRESH_MAX).random()
}
