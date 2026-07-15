package com.xhub.browser.ads

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import timber.log.Timber

/**
 * Direct-link ads open as **normal browser navigation** in the current tab.
 *
 *  - **Launch** ([maybeShowLaunchAd]): one silent background tab after cold start.
 *  - **Navigation** ([onUserGestureNavigation]): every random 4–7 user-gesture taps, loads
 *    an ad URL in the **current tab** (same-tab navigation). Returns **true** so the
 *    ad intercepts the original link — pressing Back returns to the previous page.
 *
 * @param repo                Supplies random ad URLs from remote config cache.
 * @param openAdTab           Opens [url] as a background tab (used only for launch ad).
 * @param loadInCurrentTab    Loads [url] in the current foreground tab.
 */
class DirectLinkAdManager(
    private val repo: AdConfigRepository,
    private val prefs: SharedPreferences,
    private val openAdTab: (url: String, show: Boolean) -> Unit,
    private val loadInCurrentTab: (url: String) -> Unit,
) {

    companion object {
        private const val KEY_LAUNCH_DONE = "dlad_launch_done"
        private const val KEY_TAP_COUNT = "dlad_tap_count"
        private const val KEY_TAP_THRESH = "dlad_tap_thresh"

        private const val THRESH_MIN = 4
        private const val THRESH_MAX = 7
    }

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Opens one background ad tab on first call after activity create.
     * Safe to call from `onCreate` once tab manager is ready (delayed).
     */
    fun maybeShowLaunchAd() {
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

        if (count < thresh) return false

        prefs.edit()
            .putInt(KEY_TAP_COUNT, 0)
            .putInt(KEY_TAP_THRESH, nextThreshold())
            .apply()

        val adUrl = repo.randomAdUrl()
        if (adUrl == null) {
            Timber.w("DirectAd: no cached URLs — skipping navigation ad")
            return false
        }

        try {
            // Load ad in the current tab (same-tab navigation); return true so the original
            // link is intercepted. The user can press Back to return to the previous page.
            loadInCurrentTab(adUrl)
            Timber.i("DirectAd: loaded ad in current tab -> $adUrl (intercepted nav to $url)")
        } catch (e: Exception) {
            Timber.e(e, "DirectAd: failed to load ad in current tab")
            return false
        }

        return true
    }

    private fun nextThreshold(): Int = (THRESH_MIN..THRESH_MAX).random()
}
