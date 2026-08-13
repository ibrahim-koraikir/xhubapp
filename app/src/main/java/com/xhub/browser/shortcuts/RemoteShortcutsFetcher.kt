package com.xhub.browser.shortcuts

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the remote `shortcuts.json` we host on GitHub and serve through several independent CDN
 * mirrors, then hands the raw payload to [ShortcutRepository.cacheRemoteGroups] (which validates and
 * caches it).
 *
 * This is what lets us push new sites / groups to every user WITHOUT shipping an app update: edit
 * the JSON in the GitHub repo and everyone picks it up on their next launch. The user's own edits
 * are preserved because they live in a separate overlay (see [ShortcutRepository]).
 *
 * ## Resilience: fallback mirror chain
 *
 * The exact same file is reachable through several independent hosts (see [REMOTE_URLS]). We try
 * them in order until one responds with valid JSON:
 *
 *  1. **jsDelivr** — fast, globally cached, un-rate-limited.
 *  2. **GitHub raw** — the source of truth; works when jsDelivr is down or its cache is stale.
 *  3. **Statically** — a second independent CDN mirror, in case both of the above are blocked in a
 *     given region.
 *
 * Because all three serve the identical file from the same GitHub repo, this costs zero extra
 * maintenance: you still only edit `shortcuts.json` in one place. If every mirror fails, the app
 * silently keeps its last cached list (or the compiled-in defaults) — it never crashes and never
 * shows an empty home screen.
 *
 * jsDelivr caches aggressively (~12h for `@main`). For near-instant updates you can either bump a
 * git tag and point at `@<tag>`, or purge the file via
 * `https://purge.jsdelivr.net/gh/var123321/sites@main/shortcuts.json`.
 */
object RemoteShortcutsFetcher {

    // The same shortcuts.json hosted at https://github.com/var123321/sites (root, main branch),
    // reachable through several independent mirrors. Tried in order until one responds.
    // GitHub raw is tried FIRST so edits to main branch on GitHub appear in the app instantly without CDN caching delays.
    private val REMOTE_URLS = listOf(
        "https://raw.githubusercontent.com/var123321/sites/main/shortcuts.json",
        "https://cdn.jsdelivr.net/gh/var123321/sites@main/shortcuts.json",
        "https://cdn.statically.io/gh/var123321/sites/main/shortcuts.json"
    )

    /** Minimum time between remote refresh attempts. Checked on app launch / home screen. */
    private const val MIN_REFRESH_INTERVAL_MS = 15L * 60L * 1000L // 15 minutes

    private const val PREFS_NAME = "home_shortcuts_prefs"
    private const val KEY_LAST_FETCH = "remote_last_fetch_ms"

    private val client: OkHttpClient by lazy {
        OkHttpClient().newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Blocking network fetch — MUST be called off the main thread (e.g. Schedulers.io()).
     *
     * Tries each mirror in [REMOTE_URLS] in order until one returns a valid payload.
     *
     * @param force skip the [MIN_REFRESH_INTERVAL_MS] throttle (e.g. user pull-to-refresh).
     * @return true if a new, different remote list was fetched and cached (caller should rebuild
     *         the shortcut UI). false on no-op, throttle, all-mirrors-failed, or unchanged content.
     */
    fun refresh(context: Context, force: Boolean = false): Boolean {
        // Guard against an unconfigured placeholder URL so we don't spam failed requests.
        val first = REMOTE_URLS.firstOrNull()
        if (first == null || first.contains("YOUR_GITHUB_USER") || first.contains("YOUR_REPO")) {
            Timber.d("RemoteShortcutsFetcher: REMOTE_URLS not configured, skipping.")
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force) {
            val last = prefs.getLong(KEY_LAST_FETCH, 0L)
            if (now - last < MIN_REFRESH_INTERVAL_MS) return false
        }
        // Record the attempt up front so a persistent failure doesn't retry on every launch.
        prefs.edit().putLong(KEY_LAST_FETCH, now).apply()

        // Walk the mirror chain until one serves valid JSON.
        val body = selectFirstValidBody(REMOTE_URLS) { fetchFrom(it) } ?: return false

        // cacheRemoteGroups re-validates the JSON and only returns true if the content changed.
        val changed = ShortcutRepository.cacheRemoteGroups(context, body)
        if (changed) Timber.d("RemoteShortcutsFetcher: remote shortcuts updated.")
        return changed
    }

    /**
     * Pure selection logic (no I/O of its own — network access is injected via [fetch], so this is
     * directly unit-testable). Returns the body of the first mirror in [urls] that responds with
     * valid shortcut JSON, short-circuiting so later mirrors aren't contacted once one succeeds.
     * Returns null if every mirror fails or serves junk.
     */
    internal fun selectFirstValidBody(urls: List<String>, fetch: (String) -> String?): String? {
        for (url in urls) {
            val body = fetch(url) ?: continue
            if (ShortcutRepository.parseGroupsJson(body) != null) return body
            Timber.w("RemoteShortcutsFetcher: invalid JSON from $url, trying next mirror")
        }
        return null
    }

    /**
     * Fetch a single URL. Returns the response body on HTTP success, or null on any HTTP error /
     * network failure (so the caller can fall through to the next mirror).
     */
    private fun fetchFrom(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("RemoteShortcutsFetcher: HTTP ${response.code} from $url")
                    return null
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            Timber.w(e, "RemoteShortcutsFetcher: fetch failed for $url")
            null
        } catch (e: Exception) {
            Timber.w(e, "RemoteShortcutsFetcher: unexpected error for $url")
            null
        }
    }
}
