package com.xhub.browser.ads

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import timber.log.Timber

/**
 * Manages direct-link ad triggers upon user gesture navigation.
 *
 * When the user navigates 4–7 times, fetches a fresh validated ad URL from [repo]
 * and opens it in a new foreground tab via [loadInCurrentTab].
 * If no valid URL is available (network not yet fetched, config empty), the
 * navigation is NOT intercepted and the user proceeds normally.
 *
 * Launch ad: one background tab is opened 3 s after cold-start, once per session,
 * controlled by [ADS_ENABLED] in the caller (WebBrowserActivity).
 *
 * @param repo             Supplies random ad URLs from remote-config cache (nullable).
 * @param prefs            Persists tap counter and session flags.
 * @param openAdTab        Opens [url] as a (potentially hidden) background tab.
 * @param loadInCurrentTab Opens [url] in a new foreground tab.
 */
class DirectLinkAdManager(
    private val repo: AdConfigRepository,
    private val prefs: SharedPreferences,
    private val openAdTab: (url: String, show: Boolean) -> Unit,
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

    /**
     * No-op. Pre-loading via background WebView was removed because it caused:
     * - Freezing (double WebView RAM/CPU usage)
     * - Blank screens (ad network redirect tokens consumed before the user saw them)
     *
     * Fresh URLs are fetched directly from [repo] at trigger time instead.
     */
    @Deprecated(
        message = "Pre-loading via background WebView was removed. Fresh URLs are fetched directly from repo at trigger time.",
        level = DeprecationLevel.WARNING
    )
    fun preloadNextAd() {
        Timber.d("DirectAd: preloadNextAd() is a no-op — direct URL fetch is used instead")
    }

    /**
     * Show one silent background tab on cold start (once per session).
     * Only called when BuildConfig.ADS_ENABLED is true in WebBrowserActivity.
     * The per-session gate is [KEY_LAUNCH_DONE] persisted in SharedPreferences.
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
                Timber.w("DirectAd: no valid ad URL available for launch ad — skipping")
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

    /** Reset session state. Call from [android.app.Activity.onDestroy]. */
    fun onActivityDestroy() {
        prefs.edit().putBoolean(KEY_LAUNCH_DONE, false).apply()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Count every user-gesture navigation. When the tap threshold is hit:
     * - Fetches a fresh validated ad URL from [repo].
     * - If no URL is available, does NOT intercept — user navigation continues normally.
     * - Opens the ad in a new foreground tab via [loadInCurrentTab].
     *
     * Returns **true** when an ad fires (intercepts the original navigation);
     * returns **false** otherwise so the original navigation continues normally.
     */
    fun onUserGestureNavigation(url: String): Boolean {
        val count = prefs.getInt(KEY_TAP_COUNT, 0) + 1
        val thresh = prefs.getInt(KEY_TAP_THRESH, nextThreshold())

        Timber.v("DirectAd: gesture nav count=$count thresh=$thresh url=$url")
        prefs.edit().putInt(KEY_TAP_COUNT, count).apply()

        if (count < thresh) return false

        // Threshold hit — reset counters.
        prefs.edit()
            .putInt(KEY_TAP_COUNT, 0)
            .putInt(KEY_TAP_THRESH, nextThreshold())
            .apply()

        val adUrl = repo.randomAdUrl()
        if (adUrl == null) {
            Timber.w("DirectAd: no valid ad URL available at trigger — not intercepting navigation")
            return false
        }

        return try {
            loadInCurrentTab(adUrl)
            Timber.i("DirectAd: opened ad tab -> $adUrl (user nav: $url)")
            true
        } catch (e: Exception) {
            Timber.e(e, "DirectAd: failed to open ad tab")
            false
        }
    }

    private fun nextThreshold(): Int = (THRESH_MIN..THRESH_MAX).random()
}
