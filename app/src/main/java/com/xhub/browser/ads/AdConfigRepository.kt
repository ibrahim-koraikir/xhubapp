package com.xhub.browser.ads

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URL

/**
 * Fetches the direct-link ad network config from GitHub and caches the URLs in SharedPreferences.
 *
 * Expected JSON shape:
 * ```json
 * { "networks": [ { "name": "Adsterra", "url": "https://..." }, ... ] }
 * ```
 *
 * URL priority:
 *  1. In-memory set (updated immediately after a successful network fetch in this session)
 *  2. SharedPreferences disk cache (survives app restarts)
 *  3. Hard-coded fallback URL (last resort)
 */
class AdConfigRepository(private val prefs: SharedPreferences) {

    companion object {
        private const val CONFIG_URL =
            "https://raw.githubusercontent.com/ibrahim-koraikir/AhmedHytworker-AdsConfig/main/ad_networks.json"

        /** SharedPreferences key where we persist the set of cached ad URLs. */
        private const val KEY_CACHED_URLS = "direct_ad_urls_v2"

        /**
         * Bump this version string whenever you want to force-wipe the old cache on all devices.
         * e.g. change to "v3" and rebuild → every phone discards stale URLs on next launch.
         */
        private const val CACHE_VERSION = "v2"
        private const val KEY_CACHE_VERSION = "direct_ad_cache_version"

        private val URL_PATTERN = Regex(""""url"\s*:\s*"([^"]+)"""")

        private val DEFAULT_FALLBACK_URLS = setOf(
            "https://www.effectivecpmnetwork.com/z47g8j7e?key=9e9929841fef27bf9e0fb0ef84949514"
        )
    }

    /**
     * In-memory URL set — populated the instant a network fetch succeeds.
     * Avoids stale SharedPreferences reads within the same session.
     */
    @Volatile
    private var liveUrls: Set<String>? = null

    /**
     * Refresh the local cache from the remote JSON in the background.
     * Updates BOTH the in-memory set (instant, same-session) and SharedPreferences (cross-session).
     * Force-wipes old cache keys so users on older APKs never see stale links.
     */
    fun refreshAsync(scope: CoroutineScope) {
        // Force-wipe old cache version so stale URLs from a previous app install never linger.
        if (prefs.getString(KEY_CACHE_VERSION, "") != CACHE_VERSION) {
            prefs.edit()
                .remove("direct_ad_urls_v1") // old key from previous version
                .putString(KEY_CACHE_VERSION, CACHE_VERSION)
                .apply()
        }

        scope.launch(Dispatchers.IO) {
            try {
                val json = URL(CONFIG_URL).readText(Charsets.UTF_8)
                val urls = parseUrls(json)
                if (urls.isNotEmpty()) {
                    // 1. Update in-memory set immediately — same-session randomAdUrl() calls
                    //    pick up the new URLs the instant the fetch finishes.
                    liveUrls = urls.toSet()
                    // 2. Persist so the next launch also starts with fresh URLs.
                    prefs.edit().putStringSet(KEY_CACHED_URLS, urls.toSet()).apply()
                    Timber.i("AdConfig: refreshed ${urls.size} ad URL(s) from network")
                } else {
                    Timber.w("AdConfig: parsed 0 URLs — keeping previous cache")
                }
            } catch (e: Exception) {
                Timber.w(e, "AdConfig: refresh failed — using cached URLs")
            }
        }
    }

    /**
     * Returns a randomly selected ad URL.
     * Priority: in-memory (freshest this session) → SharedPreferences → hard-coded fallback.
     */
    fun randomAdUrl(): String =
        (liveUrls?.takeIf { it.isNotEmpty() }
            ?: prefs.getStringSet(KEY_CACHED_URLS, null)?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FALLBACK_URLS).random()

    // ── internal ──────────────────────────────────────────────────────────────

    private fun parseUrls(json: String): List<String> =
        URL_PATTERN.findAll(json).map { it.groupValues[1] }.toList()
}
