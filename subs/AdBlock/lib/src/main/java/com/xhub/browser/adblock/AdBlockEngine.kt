package com.xhub.browser.adblock

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Lightweight, standalone AdBlock Engine for any Android WebView app.
 *
 * Usage:
 * ```kotlin
 * // Initialize in Application or Activity:
 * val adBlocker = AdBlockEngine.getInstance(context)
 *
 * // Use inside any WebViewClient:
 * webView.webViewClient = object : WebViewClient() {
 *     override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
 *         val response = adBlocker.shouldBlock(request, view.url ?: "")
 *         return response ?: super.shouldInterceptRequest(view, request)
 *     }
 * }
 * ```
 */
class AdBlockEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var isEnabled = true

    companion object {
        @Volatile
        private var INSTANCE: AdBlockEngine? = null

        fun getInstance(context: Context): AdBlockEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdBlockEngine(context).also { INSTANCE = it }
            }
        }

        /** Creates a new independent instance for custom setups. */
        fun create(context: Context): AdBlockEngine = AdBlockEngine(context)
    }

    /** Set whether ad blocking is active. */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun isEnabled(): Boolean = isEnabled

    /**
     * Inspects [request] and returns a [WebResourceResponse] if blocked, or null if allowed.
     */
    suspend fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        if (!isEnabled) return null

        val url = request.url.toString()
        if (url.startsWith("file://") || url.startsWith("data:") || url.startsWith("about:")) {
            return null
        }

        // Return empty response for simple quick blocking if URL matches common ad domain patterns
        if (isCommonAdDomain(url)) {
            return createEmptyResponse(request)
        }

        return null
    }

    private fun isCommonAdDomain(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("googleads") ||
                lower.contains("doubleclick.net") ||
                lower.contains("adservice.google") ||
                lower.contains("pagead2") ||
                lower.contains("adsystem") ||
                lower.contains("adserver") ||
                lower.contains("popads") ||
                lower.contains("admaven") ||
                lower.contains("adsterra")
    }

    private fun createEmptyResponse(request: WebResourceRequest): WebResourceResponse {
        val url = request.url.toString().lowercase()
        val mimeType = when {
            url.endsWith(".js") -> "application/javascript"
            url.endsWith(".css") -> "text/css"
            url.endsWith(".png") -> "image/png"
            url.endsWith(".jpg") || url.endsWith(".jpeg") -> "image/jpeg"
            url.endsWith(".gif") -> "image/gif"
            else -> "text/html"
        }
        return WebResourceResponse(
            mimeType,
            "utf-8",
            ByteArrayInputStream("".toByteArray(StandardCharsets.UTF_8))
        )
    }
}
