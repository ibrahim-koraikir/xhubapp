package com.xhub.browser.utils

import android.net.Uri
import timber.log.Timber

/**
 * Validates URLs from untrusted sources (intents, clipboard, etc.) to prevent
 * file://, content://, and javascript: scheme attacks.
 */
object UrlValidator {

    private val ALLOWED_SCHEMES = setOf("http", "https", "about", "data")

    /** Internal special URLs (home, bookmarks, etc.). Scheme is [com.xhub.browser.constant.Schemes.Fulguris]. */
    private val INTERNAL_SCHEMES = setOf("xhub", "fulguris")

    private val DANGEROUS_SCHEMES = setOf("file", "content", "javascript")

    /**
     * Validates a URL from an external source (intent, clipboard, etc.).
     * Rejects dangerous schemes that could access local files or content providers.
     *
     * @param url The URL to validate
     * @param allowInternal Whether to allow internal special URLs (xhub://, fulguris://)
     * @return The validated URL, or null if rejected
     */
    fun validateExternalUrl(url: String?, allowInternal: Boolean = false): String? {
        if (url.isNullOrBlank()) return null

        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()

            when {
                scheme in DANGEROUS_SCHEMES -> {
                    Timber.w("Rejected dangerous scheme: $scheme in URL: $url")
                    null
                }
                scheme in ALLOWED_SCHEMES -> url
                allowInternal && scheme in INTERNAL_SCHEMES -> url
                else -> {
                    Timber.w("Rejected unknown scheme: $scheme in URL: $url")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse URL: $url")
            null
        }
    }

    /**
     * Checks if a URL is safe to load in WebView.
     * More permissive than [validateExternalUrl] — allows internal special URLs.
     */
    fun isSafeForWebView(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val scheme = Uri.parse(url).scheme?.lowercase()
            scheme !in DANGEROUS_SCHEMES
        } catch (_: Exception) {
            false
        }
    }
}
