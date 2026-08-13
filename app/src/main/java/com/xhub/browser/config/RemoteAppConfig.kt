package com.xhub.browser.config

import android.content.Context
import com.xhub.browser.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads remote app configuration (URLs for home page, privacy policy, terms, contact, etc.)
 * from GitHub and serves them through CDN mirrors.
 *
 * This prevents broken links if the website domain changes in the future:
 * edit `app_config.json` in repository https://github.com/var123321/RemoteAppConfig
 * and all app installations will automatically resolve the updated URLs without needing an APK update.
 */
object RemoteAppConfig {

    private val REMOTE_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/var123321/RemoteAppConfig@main/app_config.json",
        "https://raw.githubusercontent.com/var123321/RemoteAppConfig/main/app_config.json",
        "https://cdn.statically.io/gh/var123321/RemoteAppConfig/main/app_config.json"
    )

    private const val PREFS_NAME = "remote_app_config_prefs"
    private const val KEY_LAST_FETCH = "remote_config_last_fetch_ms"

    const val KEY_URL_HOME_PAGE = "url_app_home_page"
    const val KEY_URL_PRIVACY_POLICY = "url_privacy_policy"
    const val KEY_URL_TERMS = "url_terms_and_conditions"
    const val KEY_URL_DISCORD = "url_discord"
    const val KEY_URL_CONTACT_US = "url_contact_us"
    const val KEY_URL_UPDATES = "url_app_updates"

    private const val MIN_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

    private val client: OkHttpClient by lazy {
        OkHttpClient().newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Blocking network refresh — call off the main thread.
     */
    fun refresh(context: Context, force: Boolean = false): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force) {
            val last = prefs.getLong(KEY_LAST_FETCH, 0L)
            if (now - last < MIN_REFRESH_INTERVAL_MS) return false
        }
        prefs.edit().putLong(KEY_LAST_FETCH, now).apply()

        val jsonString = selectFirstValidBody(REMOTE_URLS) { fetchFrom(it) } ?: return false

        return try {
            val json = JSONObject(jsonString)
            val editor = prefs.edit()
            listOf(
                KEY_URL_HOME_PAGE,
                KEY_URL_PRIVACY_POLICY,
                KEY_URL_TERMS,
                KEY_URL_DISCORD,
                KEY_URL_CONTACT_US,
                KEY_URL_UPDATES
            ).forEach { key ->
                if (json.has(key) && !json.isNull(key)) {
                    val value = json.getString(key)
                    if (value.isNotBlank()) {
                        editor.putString(key, value)
                    }
                }
            }
            editor.apply()
            Timber.d("RemoteAppConfig: successfully updated config")
            true
        } catch (e: Exception) {
            Timber.w(e, "RemoteAppConfig: failed to parse remote JSON")
            false
        }
    }

    internal fun selectFirstValidBody(urls: List<String>, fetch: (String) -> String?): String? {
        for (url in urls) {
            val body = fetch(url) ?: continue
            try {
                JSONObject(body)
                return body
            } catch (e: Exception) {
                Timber.w("RemoteAppConfig: invalid JSON from $url, trying next mirror")
            }
        }
        return null
    }

    private fun fetchFrom(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("RemoteAppConfig: HTTP ${response.code} from $url")
                    return null
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            Timber.w(e, "RemoteAppConfig: fetch failed for $url")
            null
        } catch (e: Exception) {
            Timber.w(e, "RemoteAppConfig: unexpected error for $url")
            null
        }
    }

    // ── Getters with fallback to hardcoded string resources ──────────────────

    fun getHomePageUrl(context: Context): String =
        getUrl(context, KEY_URL_HOME_PAGE, R.string.url_app_home_page)

    fun getPrivacyPolicyUrl(context: Context): String =
        getUrl(context, KEY_URL_PRIVACY_POLICY, R.string.url_privacy_policy)

    fun getTermsUrl(context: Context): String =
        getUrl(context, KEY_URL_TERMS, R.string.url_terms_and_conditions)

    fun getDiscordUrl(context: Context): String =
        getUrl(context, KEY_URL_DISCORD, R.string.url_discord)

    fun getContactUsUrl(context: Context): String =
        getUrl(context, KEY_URL_CONTACT_US, R.string.url_contact_us)

    fun getUpdatesUrl(context: Context): String =
        getUrl(context, KEY_URL_UPDATES, R.string.url_app_updates)

    private fun getUrl(context: Context, key: String, fallbackResId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(key, null)?.takeIf { it.isNotBlank() }
            ?: context.getString(fallbackResId)
    }
}
