/*
 * Copyright 2014 A.C.R. Development
 */
package com.xhub.browser.download

import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.database.downloads.DownloadsRepository
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.di.configPrefs
import com.xhub.browser.extensions.KDuration
import com.xhub.browser.extensions.makeSnackbar
import com.xhub.browser.extensions.snackbar
import com.xhub.browser.settings.preferences.UserPreferences
import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import android.view.Gravity
import android.webkit.DownloadListener
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.text.parseAsHtml
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.EntryPointAccessors
import com.xhub.browser.app
import com.xhub.browser.extensions.launch
import com.xhub.browser.permissions.PermissionsManager
import com.xhub.browser.permissions.PermissionsResultAction
import timber.log.Timber

//@AndroidEntryPoint
class LightningDownloadListener     //Injector.getInjector(context).inject(this);
    (private val mActivity: Activity) : BroadcastReceiver(),
    DownloadListener {

    // Could not get injection working in broadcast receiver
    private val hiltEntryPoint = EntryPointAccessors.fromApplication(app, HiltEntryPoint::class.java)

    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val downloadHandler: com.xhub.browser.download.DownloadHandler = hiltEntryPoint.downloadHandler
    val downloadManager: DownloadManager = hiltEntryPoint.downloadManager
    val downloadsRepository: DownloadsRepository = hiltEntryPoint.downloadsRepository

    // BroadcastReceiver completed/failed notifications & callback
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return

        val browserActivity = mActivity as? WebBrowserActivity ?: return
        if (browserActivity.isFinishing || browserActivity.isDestroyed) return

        val q = DownloadManager.Query().setFilterById(id)
        downloadManager.query(q)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            // Prefer the local filename we tracked when enqueuing, then the cursor title,
            // then a generic placeholder. Do NOT fall back to a shared mutable field on
            // DownloadHandler, which is racy across concurrent downloads.
            val filename = downloadHandler.getFilenameForDownloadId(id)
                ?: cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.unknown_file)
            // Clean up the map entry now that we've consumed it — the map is a singleton and
            // entries are never removed otherwise, causing it to grow unbounded in long sessions.
            downloadHandler.removeFilenameForDownloadId(id)

            val notifMgr = NotificationManagerCompat.from(browserActivity)
            val channelId = browserActivity.channelId
            val builder = NotificationCompat.Builder(browserActivity, channelId)
                .setSmallIcon(R.drawable.ic_download_outline)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (notifMgr.areNotificationsEnabled()) {
                        notifMgr.notify(id.toInt(), builder
                            .setContentTitle(browserActivity.getString(R.string.download_complete))
                            .setContentText(filename).build())
                    }
                    // Live-update the home hero "Downloads" chip once a download completes so its
                    // count doesn't go stale while the user sits on the home screen. Cheap no-op
                    // when the home overlay isn't visible.
                    browserActivity.refreshHomeStatsIfVisible()
                    browserActivity.makeSnackbar(
                        browserActivity.getString(R.string.download_complete),
                        KDuration,
                        if (browserActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                    ).setAction(R.string.show) {
                        browserActivity.openDownloads()
                    }.show()
                }
                DownloadManager.STATUS_FAILED -> {
                    if (notifMgr.areNotificationsEnabled()) {
                        notifMgr.notify(id.toInt(), builder
                            .setContentTitle(browserActivity.getString(R.string.download_failed))
                            .setContentText(filename).build())
                    }
                    browserActivity.snackbar(
                        browserActivity.getString(R.string.download_failed),
                        if (browserActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                    )
                }
            }
        }
    }



    override fun onDownloadStart(
        url: String, userAgent: String,
        contentDisposition: String, mimetype: String, contentLength: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // No permissions needed anymore from Android 13
            doDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength)
        } else {
            PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(mActivity, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
                object : PermissionsResultAction() {
                    override fun onGranted() {
                        doDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength)
                    }

                    override fun onDenied(permission: String) {
                        //TODO show message
                    }
                })
        }


        // Some download link spawn an empty tab, just close it then
        if (mActivity is WebBrowserActivity) {
            mActivity.closeCurrentTabIfEmpty()
        }
    }

    private fun doDownloadStart(
        url: String, userAgent: String,
        contentDisposition: String, mimetype: String, contentLength: Long
    ) {
        // Get original filename WITHOUT extension changes to check for mismatches
        val originalFileName = com.xhub.browser.utils.guessFileNameWithoutExtensionChange(url, contentDisposition, mimetype, null)

        val downloadSize: String = if (contentLength > 0) {
            Formatter.formatFileSize(mActivity, contentLength)
        } else {
            mActivity.getString(R.string.unknown_file_size)
        }

        val builder = MaterialAlertDialogBuilder(mActivity)

        // Build descriptive message following MD3 guidelines
        // If server sends generic octet-stream, infer MIME type from file extension
        // Notably the case for Fulguris APK download from slions.net
        val mimeTypeDetectedFromExtension = mimetype == "application/octet-stream" || mimetype.isBlank()
        val fileType = when {
            mimeTypeDetectedFromExtension -> {
                val extension = originalFileName.substringAfterLast('.', "").lowercase()
                if (extension.isNotEmpty()) {
                    val detectedMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    Timber.d("Server sent octet-stream for .$extension file, detected MIME type: $detectedMimeType")
                    detectedMimeType ?: mimetype.ifEmpty { mActivity.getString(R.string.unknown_file_type) }
                } else {
                    mimetype.ifEmpty { mActivity.getString(R.string.unknown_file_type) }
                }
            }
            mimetype.isNotEmpty() -> mimetype
            else -> mActivity.getString(R.string.unknown_file_type)
        }

        Timber.d("Final MIME type for display: $fileType (original server MIME: $mimetype)")

        // Only check for extension mismatch if server provided a real MIME type (not octet-stream)
        // If we detected type from extension, don't offer "Download as" button since we already fixed it
        val (hasMismatch, correctedFilename) = if (!mimeTypeDetectedFromExtension) {
            com.xhub.browser.utils.hasExtensionMismatch(originalFileName, mimetype)
        } else {
            Pair(false, null)
        }

        // Parameters: filename, type, size (size is last)
        val message = mActivity.getString(R.string.dialog_download_message, originalFileName, fileType, downloadSize)

        // Use question as title per MD3 guidelines
        val dialog: Dialog = builder.setIcon(R.drawable.ic_download_outline)
            .setTitle(R.string.dialog_download_title)
            .setMessage(message.parseAsHtml())
            .setPositiveButton(
                mActivity.resources.getString(R.string.action_download)
            ) { _, _ ->
                downloadHandler.onDownloadStart(
                    mActivity,
                    userPreferences,
                    url,
                    userAgent,
                    contentDisposition,
                    mimetype,
                    downloadSize,
                    url // Use the page URL as referer
                )
            }
            .apply {
                // Add neutral button if there's an extension mismatch
                if (hasMismatch && correctedFilename != null) {
                    // Extract just the extension for the button label
                    val correctedExt = correctedFilename.substringAfterLast('.').uppercase()
                    setNeutralButton(
                        mActivity.getString(R.string.download_as_format, correctedExt)
                    ) { _, _ ->
                        // Download with corrected filename
                        downloadHandler.onDownloadStartWithFilename(
                            mActivity,
                            userPreferences,
                            url,
                            userAgent,
                            contentDisposition,
                            mimetype,
                            downloadSize,
                            correctedFilename,
                            url // Use the page URL as referer
                        )
                    }
                }
            }
            .setNegativeButton(
                mActivity.resources.getString(R.string.action_cancel)
            ) { _, _ -> }
            .launch()
        Timber.d("Downloading: $originalFileName (mimetype: $mimetype, hasMismatch: $hasMismatch, correctedFilename: $correctedFilename)")
    }

}

