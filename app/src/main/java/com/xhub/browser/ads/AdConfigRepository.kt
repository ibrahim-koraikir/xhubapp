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
 * { "networks": [ { "name": "AdMaven", "url": "https://..." }, ... ] }
 * ```
 *
 * URL priority:
 *  1. In-memory set (updated immediately after a successful network fetch in this session)
 *  2. SharedPreferences disk cache (survives app restarts)
 *  3. Fallback set (used on initial launch before network fetch completes)
 */
class AdConfigRepository(private val prefs: SharedPreferences) {

    companion object {
        private const val CONFIG_URL =
            "https://raw.githubusercontent.com/ibrahim-koraikir/AhmedHytworker-AdsConfig/main/ad_networks.json"

        /** SharedPreferences key where the cached ad URL set is persisted. */
        private const val KEY_CACHED_URLS = "direct_ad_urls_v3"

        /**
         * Bump this constant whenever you want to force-wipe the cache on all devices.
         */
        private const val CACHE_VERSION = "v3"
        private const val KEY_CACHE_VERSION = "direct_ad_cache_version"

        private val URL_PATTERN = Regex(""""url"\s*:\s*"([^"]+)"""")

        private val DEFAULT_FALLBACK_URLS = setOf(
            "https://ythestarsarequ.com?FSXW8=1467247"
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
     * On version mismatch, wipes ALL previous cache keys before writing fresh data.
     */
    fun refreshAsync(scope: CoroutineScope) {
        if (prefs.getString(KEY_CACHE_VERSION, "") != CACHE_VERSION) {
            // Wipe every known previous key so stale URLs never linger across APK updates.
            prefs.edit()
                .remove("direct_ad_urls_v1")
                .remove("direct_ad_urls_v2")
                .remove(KEY_CACHED_URLS)          // also wipe current key — version changed
                .putString(KEY_CACHE_VERSION, CACHE_VERSION)
                .apply()
            liveUrls = null
            Timber.i("AdConfig: cache wiped — version bumped to $CACHE_VERSION")
        }

        scope.launch(Dispatchers.IO) {
            try {
                val json = URL(CONFIG_URL).readText(Charsets.UTF_8)
                val urls = parseUrls(json)
                if (urls.isNotEmpty()) {
                    liveUrls = urls.toSet()
                    prefs.edit().putStringSet(KEY_CACHED_URLS, urls.toSet()).apply()
                    Timber.i("AdConfig: refreshed ${urls.size} ad URL(s) from network")
                } else {
                    Timber.w("AdConfig: parsed 0 valid URLs — keeping previous cache")
                }
            } catch (e: Exception) {
                Timber.w(e, "AdConfig: refresh failed — using cached URLs")
            }
        }
    }

    /**
     * Returns a randomly selected ad URL.
     * Priority: in-memory (freshest this session) → SharedPreferences → default fallback.
     */
    fun randomAdUrl(): String? =
        (liveUrls?.takeIf { it.isNotEmpty() }
            ?: prefs.getStringSet(KEY_CACHED_URLS, null)?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FALLBACK_URLS).randomOrNull()

    // ── internal ──────────────────────────────────────────────────────────────

    /**
     * Extract URLs from JSON and validate each one.
     * Accepts http:// and https:// web URLs.
     */
    private fun parseUrls(json: String): List<String> =
        URL_PATTERN.findAll(json)
            .map { it.groupValues[1].trim() }
            .filter { url ->
                val valid = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
                if (!valid) Timber.w("AdConfig: dropped invalid web URL from config: $url")
                valid
            }
            .toList()
}
