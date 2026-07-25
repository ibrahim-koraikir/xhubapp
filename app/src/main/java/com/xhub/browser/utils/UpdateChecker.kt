package com.xhub.browser.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xhub.browser.BuildConfig
import com.xhub.browser.R
import timber.log.Timber

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/ibrahim-koraikir/xhubapp/releases/latest"

    interface Callback {
        fun onUpdateAvailable(latestVersion: String, releaseUrl: String)
        fun onNoUpdate()
        fun onError(errorMsg: String)
    }

    /**
     * Clean and compare version names segment-by-segment (e.g. 1.0.1 > 1.0.0).
     */
    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.trim().lowercase().removePrefix("v")
        val cleanLatest = latest.trim().lowercase().removePrefix("v")
        if (cleanCurrent == cleanLatest) return false

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currVal = currentParts.getOrElse(i) { 0 }
            val latVal = latestParts.getOrElse(i) { 0 }
            if (latVal > currVal) return true
            if (currVal > latVal) return false
        }
        return false
    }

    fun checkForUpdates(context: Context, callback: Callback) {
        val queue = Volley.newRequestQueue(context.applicationContext)
        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, GITHUB_API_URL, null,
            { response ->
                try {
                    val tagName = response.getString("tag_name")
                    val htmlUrl = response.getString("html_url")
                    val currentVersion = BuildConfig.VERSION_NAME

                    if (isNewerVersion(currentVersion, tagName)) {
                        callback.onUpdateAvailable(tagName, htmlUrl)
                    } else {
                        callback.onNoUpdate()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing update check response")
                    callback.onError(e.localizedMessage ?: "Parsing error")
                }
            },
            { error ->
                val errorMsg = error.localizedMessage ?: "Network error (status code: ${error.networkResponse?.statusCode})"
                Timber.e(error, "Update check request failed: $errorMsg")
                callback.onError(errorMsg)
            }
        ) {
            override fun getHeaders(): Map<String, String> {
                val headers = HashMap<String, String>()
                headers["User-Agent"] = "XHub-Browser-UpdateChecker"
                return headers
            }
        }

        // Tag the request with the object so we can cancel if needed
        jsonObjectRequest.tag = this
        queue.add(jsonObjectRequest)
    }

    fun showUpdateDialog(context: Context, latestVersion: String, releaseUrl: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(context.getString(R.string.update_available_message, latestVersion))
            .setPositiveButton(R.string.yes) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)).apply {
                    putExtra("PACKAGE", context.packageName)
                }
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
