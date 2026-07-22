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
 * The repository is intentionally dependency-free (no Gson / Moshi) — the JSON is simple enough
 * that a regex parse suffices and avoids adding a new library.
 */
class AdConfigRepository(private val prefs: SharedPreferences) {

    companion object {
        private const val CONFIG_URL =
            "https://raw.githubusercontent.com/ibrahim-koraikir/AhmedHytworker-AdsConfig/main/ad_networks.json"

        /** SharedPreferences key where we persist the set of cached ad URLs. */
        private const val KEY_CACHED_URLS = "direct_ad_urls_v1"

        private val URL_PATTERN = Regex(""""url"\s*:\s*"([^"]+)"""")

        private val DEFAULT_FALLBACK_URLS = setOf(
            "https://www.effectivecpmnetwork.com/z47g8j7e?key=9e9929841fef27bf9e0fb0ef84949514"
        )
    }

    /**
     * Refresh the local cache from the remote JSON in the background.
     * Fire-and-forget — errors are logged and silently swallowed so the caller is never affected.
     */
    fun refreshAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = URL(CONFIG_URL).readText(Charsets.UTF_8)
                val urls = parseUrls(json)
                if (urls.isNotEmpty()) {
                    prefs.edit().putStringSet(KEY_CACHED_URLS, urls.toSet()).apply()
                    Timber.i("AdConfig: cached ${urls.size} ad URL(s)")
                } else {
                    Timber.w("AdConfig: parsed 0 URLs — keeping previous cache")
                }
            } catch (e: Exception) {
                Timber.w(e, "AdConfig: refresh failed — using cached URLs")
            }
        }
    }

    /**
     * Returns a randomly selected ad URL from the local cache or fallback defaults.
     */
    fun randomAdUrl(): String =
        (prefs.getStringSet(KEY_CACHED_URLS, null)
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FALLBACK_URLS).random()

    // ── internal ──────────────────────────────────────────────────────────────

    private fun parseUrls(json: String): List<String> =
        URL_PATTERN.findAll(json).map { it.groupValues[1] }.toList()
}
