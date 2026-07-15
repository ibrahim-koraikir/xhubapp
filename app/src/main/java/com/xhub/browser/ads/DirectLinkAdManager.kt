package com.xhub.browser.ads

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.xhub.browser.view.WebPageTab
import timber.log.Timber

/**
 * Direct-link ads open as **normal browser navigation** in the current tab.
 * We pre-load the ad in a detached background tab ahead of time so it loads
 * instantly when triggered, bypassing redirect delays.
 *
 *  - **Launch** ([maybeShowLaunchAd]): one silent background tab after cold start.
 *  - **Navigation** ([onUserGestureNavigation]): counts user-gesture navigations.
 *    We preload the next ad in the background. When the threshold (4-7 taps) hits,
 *    we instantly switch to the pre-loaded ad tab. Returns **true** to intercept
 *    the original navigation.
 *
 * @param repo                Supplies random ad URLs from remote config cache.
 * @param openAdTab           Opens [url] as a background tab (used only for launch ad).
 * @param createPreloadedTab  Creates a detached WebPageTab that loads [url] in the background.
 * @param showPreloadedTab    Registers and switches to the preloaded WebPageTab in the foreground.
 */
class DirectLinkAdManager(
    private val repo: AdConfigRepository,
    private val prefs: SharedPreferences,
    private val openAdTab: (url: String, show: Boolean) -> Unit,
    private val createPreloadedTab: (url: String) -> WebPageTab,
    private val showPreloadedTab: (tab: WebPageTab) -> Unit,
) {

    companion object {
        private const val KEY_LAUNCH_DONE = "dlad_launch_done"
        private const val KEY_TAP_COUNT = "dlad_tap_count"
        private const val KEY_TAP_THRESH = "dlad_tap_thresh"

        private const val THRESH_MIN = 4
        private const val THRESH_MAX = 7
    }

    private val handler = Handler(Looper.getMainLooper())
    private var preloadedAdTab: WebPageTab? = null

    /**
     * Start preloading the next ad in the background.
     */
    fun preloadNextAd() {
        if (preloadedAdTab != null) {
            Timber.d("DirectAd: Ad already preloaded — skipping")
            return
        }
        val url = repo.randomAdUrl()
        if (url == null) {
            Timber.w("DirectAd: No cached ad URLs available to preload")
            return
        }
        try {
            preloadedAdTab = createPreloadedTab(url)
            Timber.i("DirectAd: Started preloading next ad in background -> $url")
        } catch (e: Exception) {
            Timber.e(e, "DirectAd: Failed to preload ad")
        }
    }

    /**
     * Opens one background ad tab on first call after activity create,
     * and starts preloading the navigation ad.
     * Safe to call from `onCreate` once tab manager is ready (delayed).
     */
    fun maybeShowLaunchAd() {
        // Start preloading the navigation ad right away so it is ready early
        preloadNextAd()

        if (prefs.getBoolean(KEY_LAUNCH_DONE, false)) {
            Timber.d("DirectAd: launch ad already fired this session — skipping")
            return
        }
        prefs.edit().putBoolean(KEY_LAUNCH_DONE, true).apply()

        handler.postDelayed({
            val url = repo.randomAdUrl()
            if (url == null) {
                Timber.w("DirectAd: no cached ad URLs yet — skipping launch ad")
                return@postDelayed
            }
            try {
                openAdTab(url, false)
                Timber.i("DirectAd: opened launch background tab -> $url")
            } catch (e: Exception) {
                Timber.e(e, "DirectAd: failed to open launch background tab")
            }
        }, 3_000L)
    }

    /** Reset session launch flag; cancel pending posts. Call from Activity.onDestroy. */
    fun onActivityDestroy() {
        prefs.edit().putBoolean(KEY_LAUNCH_DONE, false).apply()
        handler.removeCallbacksAndMessages(null)
        preloadedAdTab = null
    }

    /**
     * Count user-gesture navigations; when threshold hits, load an ad in the current tab.
     * Returns **true** when an ad fires so the original URL is intercepted (the ad replaces it);
     * the user can press Back to return to the previous page. Returns **false** otherwise.
     */
    fun onUserGestureNavigation(url: String): Boolean {
        val count = prefs.getInt(KEY_TAP_COUNT, 0) + 1
        val thresh = prefs.getInt(KEY_TAP_THRESH, nextThreshold())

        Timber.v("DirectAd: gesture nav count=$count thresh=$thresh (destination=$url)")
        prefs.edit().putInt(KEY_TAP_COUNT, count).apply()

        // Ensure we have a preloaded ad loading/loaded in the background
        preloadNextAd()

        if (count < thresh) return false

        prefs.edit()
            .putInt(KEY_TAP_COUNT, 0)
            .putInt(KEY_TAP_THRESH, nextThreshold())
            .apply()

        val adTab = preloadedAdTab
        if (adTab == null) {
            Timber.w("DirectAd: No preloaded ad tab ready — fetching dynamically")
            // Fallback: trigger a preload now so we have one for next time, but return false
            // so we don't block the user's flow with a slow load.
            preloadNextAd()
            return false
        }

        try {
            // Instantly show the preloaded tab!
            showPreloadedTab(adTab)
            Timber.i("DirectAd: Instantly displayed preloaded ad tab")
            preloadedAdTab = null // Consumed
            
            // Queue up the next preload after a brief delay to avoid overloading network/CPU
            handler.postDelayed({ preloadNextAd() }, 3_000L)
        } catch (e: Exception) {
            Timber.e(e, "DirectAd: Failed to display preloaded ad tab")
            preloadedAdTab = null
            return false
        }

        return true
    }

    private fun nextThreshold(): Int = (THRESH_MIN..THRESH_MAX).random()
}
