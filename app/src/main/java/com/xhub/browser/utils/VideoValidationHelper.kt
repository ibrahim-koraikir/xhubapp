package com.xhub.browser.utils

import org.json.JSONObject
import timber.log.Timber

object VideoValidationHelper {

    fun isAcceptableMediaUrl(url: String): Boolean {
        if (url.length > 4096) return false
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("blob:")
    }

    fun isDownloadableHttpUrl(url: String): Boolean {
        if (url.length > 4096) return false
        val lower = url.lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
               !lower.contains(".m3u8") &&
               !lower.contains(".mpd")
    }

    /**
     * Parses the qualities JSON string and returns a map of sanitized keys and validated downloadable URLs.
     * Keeps key lookup in JSON valid by using raw keys, while sanitizing/truncating the stored key
     * and limiting results to at most 20 entries.
     */
    fun parseQualitiesJson(qualitiesJson: String?): Map<String, String>? {
        if (qualitiesJson == null || qualitiesJson.length >= 50000) return null
        return try {
            val json = JSONObject(qualitiesJson)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            var count = 0
            while (keys.hasNext() && count < 20) {
                val rawKey = keys.next()
                val value = json.getString(rawKey)
                val sanitizedKey = rawKey.trim().take(50)
                if (isDownloadableHttpUrl(value)) {
                    map[sanitizedKey] = value
                    count++
                }
            }
            map
        } catch (e: Exception) {
            Timber.e(e, "Error parsing video qualities")
            null
        }
    }
}
