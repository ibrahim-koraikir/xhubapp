package com.xhub.browser.js

import android.webkit.JavascriptInterface
import com.xhub.browser.view.WebPageTab
import timber.log.Timber

/**
 * JS bridge for video detection. All inputs from untrusted web content are
 * validated and sanitized at this boundary before reaching application code.
 */
class VideoJavascriptInterface(
    private val onDetected: (
        url: String,
        qualitiesJson: String?,
        resolution: String?,
        streamType: String
    ) -> Unit
) {

    /** Production constructor — forwards to the owning tab after validation. */
    constructor(tab: WebPageTab) : this({ url, qualitiesJson, resolution, streamType ->
        tab.onVideoDetected(url, qualitiesJson, resolution, streamType)
    })

    companion object {
        private const val MAX_URL_LENGTH = 2048
        private const val MAX_JSON_LENGTH = 10_240 // 10KB
        private const val MAX_RESOLUTION_LENGTH = 20
        private const val MAX_STREAM_TYPE_LENGTH = 20
        /** Rate limiting: max 1 call per 500ms per tab */
        private const val MIN_CALL_INTERVAL_MS = 500L
    }

    @Volatile
    private var lastCallTime = 0L

    /**
     * Called by the detection script. Validates all inputs at the JS→Kotlin boundary
     * BEFORE passing to application code to prevent injection/DoS attacks.
     */
    @JavascriptInterface
    fun onVideoDetected(
        url: String?,
        qualitiesJson: String?,
        resolution: String?,
        streamType: String?
    ) {
        try {
            // Rate limiting: prevent malicious pages from spamming calls
            val now = System.currentTimeMillis()
            if (now - lastCallTime < MIN_CALL_INTERVAL_MS) {
                Timber.w("VideoSniffer: rate limit exceeded, ignoring call")
                return
            }
            lastCallTime = now

            // Validate URL (required)
            if (url.isNullOrBlank()) {
                Timber.w("VideoSniffer: null/blank URL rejected")
                return
            }
            if (url.length > MAX_URL_LENGTH) {
                Timber.w("VideoSniffer: URL too long (${url.length} > $MAX_URL_LENGTH)")
                return
            }

            // Validate qualitiesJson size (optional)
            if (qualitiesJson != null && qualitiesJson.length > MAX_JSON_LENGTH) {
                Timber.w("VideoSniffer: JSON too long (${qualitiesJson.length} > $MAX_JSON_LENGTH)")
                return
            }

            // Sanitize resolution for UI display (strip injection first, then cap length)
            val sanitizedResolution = resolution?.let { r ->
                r.replace(Regex("<[^>]*>"), "")
                    .replace(Regex("[\\p{Cntrl}]"), "")
                    .trim()
                    .take(MAX_RESOLUTION_LENGTH)
                    .takeIf { it.isNotBlank() }
            }

            // Sanitize streamType (alphanumeric + _ - only; clean first, then cap)
            val sanitizedStreamType = streamType?.let { st ->
                st.replace(Regex("[^a-zA-Z0-9_-]"), "")
                    .trim()
                    .take(MAX_STREAM_TYPE_LENGTH)
                    .takeIf { it.isNotBlank() }
            } ?: "direct"

            onDetected(url, qualitiesJson, sanitizedResolution, sanitizedStreamType)
        } catch (e: Exception) {
            // Never let JS bridge exceptions crash the app
            Timber.e(e, "VideoSniffer: exception in onVideoDetected")
        }
    }
}
