package com.xhub.browser.settings.fragment

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.extensions.copyToClipboard
import com.xhub.browser.extensions.toast
import com.xhub.browser.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class DownloadItem(
    val id: Long,
    val title: String,
    val status: Int,
    val localUri: String?,
    val uri: String?,
    val bytesDownloaded: Long,
    val totalSize: Long,
    val lastModified: Long,
    val mimeType: String?,
    val isOrphaned: Boolean
)

// Thread-safe static LruCache for download thumbnails
private val thumbnailCache = object : android.util.LruCache<Long, Bitmap>(15 * 1024 * 1024) { // 15MB
    override fun sizeOf(key: Long, value: Bitmap): Int {
        return value.byteCount
    }
}

private suspend fun getMediaThumbnail(context: Context, localUri: String?): Bitmap? = withContext(Dispatchers.IO) {
    if (localUri == null) return@withContext null
    try {
        val fileUri = Uri.parse(localUri)
        val file = File(fileUri.path ?: return@withContext null)
        if (!file.exists()) return@withContext null
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bitmap
    } catch (e: Exception) {
        null
    }
}

private suspend fun getImageThumbnail(context: Context, localUri: String?): Bitmap? = withContext(Dispatchers.IO) {
    if (localUri == null) return@withContext null
    try {
        val fileUri = Uri.parse(localUri)
        val file = File(fileUri.path ?: return@withContext null)
        if (!file.exists()) return@withContext null
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, 240, 180)
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(file.absolutePath, options)
    } catch (e: Exception) {
        null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

@AndroidEntryPoint
class DownloadsFragment : Fragment() {

    @Inject
    lateinit var downloadManager: DownloadManager

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var rvDownloads: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvHeaderSummary: TextView
    private lateinit var btnClean: Button
    private lateinit var btnRemoveAll: Button
    private lateinit var btnDeleteAll: Button

    private lateinit var adapter: DownloadAdapter
    private var progressUpdateJob: Job? = null

    // ContentObserver to detect changes in DownloadManager database
    private val downloadObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
            super.onChange(selfChange, uri, flags)
            if (isAdded) {
                loadDownloads()
            }
        }
    }

    // BroadcastReceiver to listen for completed downloads
    private val downloadEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                loadDownloads()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_downloads, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvDownloads = view.findViewById(R.id.rvDownloads)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        tvHeaderSummary = view.findViewById(R.id.tvDownloadsHeaderSummary)
        btnClean = view.findViewById(R.id.btnClean)
        btnRemoveAll = view.findViewById(R.id.btnRemoveAll)
        btnDeleteAll = view.findViewById(R.id.btnDeleteAll)

        rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        adapter = DownloadAdapter(requireContext(), { item ->
            showDownloadOptionsDialog(item.id, item.title)
        }, viewLifecycleOwner.lifecycleScope)
        rvDownloads.adapter = adapter

        // Setup swipe-to-delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val item = adapter.currentList[position]
                showDownloadOptionsDialog(item.id, item.title)
                adapter.notifyItemChanged(position)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(rvDownloads)

        // Setup action listeners
        btnClean.setOnClickListener { showCleanDownloadsDialog() }
        btnRemoveAll.setOnClickListener { showRemoveAllDownloadsDialog() }
        btnDeleteAll.setOnClickListener { showDeleteAllDownloadsDialog() }

        loadDownloads()
    }

    override fun onResume() {
        super.onResume()
        loadDownloads()

        try {
            val downloadUri = "content://downloads/all_downloads".toUri()
            requireContext().contentResolver.registerContentObserver(downloadUri, true, downloadObserver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register download observer")
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(downloadEventReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(downloadEventReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        progressUpdateJob?.cancel()
        try {
            requireContext().contentResolver.unregisterContentObserver(downloadObserver)
        } catch (e: Exception) {
            Timber.d(e, "Observer not registered or already unregistered")
        }
        try {
            requireContext().unregisterReceiver(downloadEventReceiver)
        } catch (e: Exception) {
            Timber.d(e, "Receiver not registered or already unregistered")
        }
    }

    private fun loadDownloads() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val list = mutableListOf<DownloadItem>()
                val query = DownloadManager.Query()
                val cursor: Cursor? = try {
                    downloadManager.query(query)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to query downloads")
                    null
                }
                cursor?.use {
                    if (it.moveToFirst()) {
                        do {
                            val id = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                            val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Unknown"
                            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            val uri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                            val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val totalSize = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val lastModified = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP))
                            val mimeType = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))

                            val isOrphaned = if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                                try {
                                    val uriPath = Uri.parse(localUri)
                                    val file = File(uriPath.path ?: "")
                                    !file.exists()
                                } catch (e: Exception) {
                                    false
                                }
                            } else {
                                false
                            }

                            list.add(
                                DownloadItem(
                                    id = id,
                                    title = title,
                                    status = status,
                                    localUri = localUri,
                                    uri = uri,
                                    bytesDownloaded = bytesDownloaded,
                                    totalSize = totalSize,
                                    lastModified = lastModified,
                                    mimeType = mimeType,
                                    isOrphaned = isOrphaned
                                )
                            )
                        } while (it.moveToNext())
                    }
                }
                list.sortByDescending { it.lastModified }
                list
            }

            adapter.submitList(items) {
                if (isAdded) {
                    updateUI(items)
                }
            }
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val hasActive = adapter.currentList.any {
                    it.status == DownloadManager.STATUS_RUNNING ||
                    it.status == DownloadManager.STATUS_PENDING
                }
                if (!hasActive) break
                delay(1000)
                loadDownloads()
            }
        }
    }

    private fun updateUI(items: List<DownloadItem>) {
        val count = items.size
        val totalSize = items.sumOf { it.totalSize }
        val totalSizeStr = Formatter.formatFileSize(requireContext(), totalSize)
        tvHeaderSummary.text = if (count == 1) {
            "$count download • $totalSizeStr total"
        } else {
            "$count downloads • $totalSizeStr total"
        }

        if (items.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvDownloads.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvDownloads.visibility = View.VISIBLE
        }

        btnClean.isEnabled = items.any { it.isOrphaned || it.status == DownloadManager.STATUS_FAILED }
        btnRemoveAll.isEnabled = items.isNotEmpty()
        btnDeleteAll.isEnabled = items.isNotEmpty()

        val hasActive = items.any {
            it.status == DownloadManager.STATUS_RUNNING ||
            it.status == DownloadManager.STATUS_PENDING
        }
        if (hasActive) {
            startProgressUpdates()
        }
    }

    private fun showDownloadOptionsDialog(downloadId: Long, title: String) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            return
        }

        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        val originalUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
        cursor.close()

        val isOrphaned = if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
            try {
                val fileUri = Uri.parse(localUri)
                val file = File(fileUri.path ?: "")
                !file.exists()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val isSuccessful = status == DownloadManager.STATUS_SUCCESSFUL
        val hasFile = isSuccessful && !isOrphaned
        val isInProgress = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
        val isPaused = status == DownloadManager.STATUS_PAUSED
        val isFailed = status == DownloadManager.STATUS_FAILED
        val hasUrl = originalUri?.isNotEmpty() == true

        val canWriteFile = if (hasFile && localUri != null) {
            try {
                val fileUri = Uri.parse(localUri)
                val file = File(fileUri.path ?: "")
                file.canWrite()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val canReadFile = if (hasFile && localUri != null) {
            try {
                val fileUri = Uri.parse(localUri)
                val file = File(fileUri.path ?: "")
                file.canRead()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val options = mutableListOf<com.xhub.browser.dialog.DialogItem>()

        if (hasFile) {
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.open_download) {
                openDownload(downloadId)
            })
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.remove_and_delete_file) {
                confirmRemoveAndDeleteDownload(downloadId, title)
            })

            if (canReadFile) {
                options.add(com.xhub.browser.dialog.DialogItem(title = R.string.remove_and_keep_file) {
                    confirmRemoveAndKeepFile(downloadId, title)
                })
            }

            if (canWriteFile) {
                options.add(com.xhub.browser.dialog.DialogItem(title = R.string.delete_file) {
                    confirmDeleteFileOnly(downloadId, title)
                })
            }

            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.share_file) {
                shareDownload(downloadId)
            })

            if (localUri != null) {
                val fileUri = Uri.parse(localUri)
                val fileName = fileUri.lastPathSegment
                if (fileName != null) {
                    options.add(com.xhub.browser.dialog.DialogItem(title = R.string.copy_name) {
                        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.copyToClipboard(fileName)
                    })
                }
            }

            if (localUri != null) {
                val filePath = Uri.parse(localUri).path
                if (filePath != null) {
                    options.add(com.xhub.browser.dialog.DialogItem(title = R.string.copy_path) {
                        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.copyToClipboard(filePath)
                    })
                }
            }
        }

        if (isInProgress || isPaused) {
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.cancel_download) {
                removeDownload(downloadId)
            })
        }

        if (isOrphaned || isFailed) {
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.remove_from_list) {
                removeDownload(downloadId)
            })
            if (hasUrl) {
                options.add(com.xhub.browser.dialog.DialogItem(title = R.string.action_download) {
                    redownloadFile(downloadId, originalUri, title)
                })
            }
        }

        if (hasUrl) {
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.share_link) {
                shareLink(originalUri)
            })
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.dialog_copy_link) {
                val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.copyToClipboard(originalUri)
            })
        }

        com.xhub.browser.dialog.BrowserDialog.show(
            requireContext(),
            R.drawable.ic_download_outline,
            null,
            false,
            com.xhub.browser.dialog.DialogTab(
                show = true,
                icon = 0,
                text = title,
                items = options.toTypedArray()
            )
        )
    }

    private fun removeFromListOnly(downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            return false
        }

        val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        cursor.close()

        if (localUriString == null) {
            downloadManager.remove(downloadId)
            return true
        }

        try {
            val fileUri = localUriString.toUri()
            val originalFile = File(fileUri.path ?: return false)

            if (!originalFile.exists()) {
                downloadManager.remove(downloadId)
                return true
            }

            val tempFile = File(originalFile.parentFile, "tmp_${System.currentTimeMillis()}_${originalFile.name}")

            if (originalFile.renameTo(tempFile)) {
                downloadManager.remove(downloadId)
                if (tempFile.renameTo(originalFile)) {
                    return true
                }
                return false
            }

            if (!originalFile.canRead()) {
                return false
            }

            originalFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            downloadManager.remove(downloadId)

            if (tempFile.exists() && tempFile.length() == originalFile.length()) {
                try {
                    tempFile.delete()
                } catch (e: Exception) {
                    Timber.d("Could not delete temp file: ${e.message}")
                }
                return true
            } else {
                tempFile.delete()
                return false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error removing download from list")
            return false
        }
    }

    private fun removeDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        loadDownloads()
    }

    private fun confirmRemoveAndKeepFile(downloadId: Long, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_remove_and_keep)
            .setMessage(getString(R.string.dialog_message_remove_and_keep, title))
            .setPositiveButton(R.string.action_remove) { _, _ ->
                if (removeFromListOnly(downloadId)) {
                    loadDownloads()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemoveAndDeleteDownload(downloadId: Long, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_remove_and_delete)
            .setMessage(getString(R.string.dialog_message_remove_and_delete, title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                removeDownload(downloadId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteFileOnly(downloadId: Long, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_delete_file)
            .setMessage(getString(R.string.dialog_message_delete_file, title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteFileOnly(downloadId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteFileOnly(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            requireContext().toast(R.string.download_not_found)
            return
        }

        val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        cursor.close()

        if (localUriString == null) {
            requireContext().toast(R.string.file_not_found)
            return
        }

        try {
            val fileUri = localUriString.toUri()
            val file = File(fileUri.path ?: "")

            if (!file.exists()) {
                requireContext().toast(R.string.file_does_not_exist)
                loadDownloads()
                return
            }

            if (file.delete()) {
                loadDownloads()
            } else {
                requireContext().toast(R.string.error_deleting_file)
            }
        } catch (e: SecurityException) {
            requireContext().toast(R.string.error_no_permission)
        } catch (e: Exception) {
            requireContext().toast(R.string.error_deleting_file)
        }
    }

    private fun openDownload(downloadId: Long) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            val mimeType = downloadManager.getMimeTypeForDownloadedFile(downloadId)

            if (mimeType == "application/vnd.android.package-archive") {
                if (!canInstallPackages()) {
                    requestInstallPermission()
                    return
                }
            }

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            try {
                startActivity(openIntent)
            } catch (e: Exception) {
                activity?.toast(R.string.error_cant_open_file)
            }
        }
    }

    private fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun requestInstallPermission() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_install_apk)
            .setMessage(R.string.dialog_message_install_apk)
            .setPositiveButton(R.string.action_open) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        activity?.toast(R.string.install_permission_denied)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareDownload(downloadId: Long) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = downloadManager.getMimeTypeForDownloadedFile(downloadId)
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
            } catch (e: Exception) {}
        }
    }

    private fun shareLink(url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_link)))
        } catch (e: Exception) {}
    }

    private fun redownloadFile(downloadId: Long, originalUri: String?, title: String) {
        if (originalUri.isNullOrBlank()) {
            return
        }
        try {
            removeOrphanedDownloadsWithSameUri(title)

            val uri = Uri.parse(originalUri)
            val request = DownloadManager.Request(uri).apply {
                setTitle(title)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    title
                )
            }

            val newDownloadId = downloadManager.enqueue(request)
            if (newDownloadId != -1L) {
                downloadManager.remove(downloadId)
                loadDownloads()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to re-download file")
        }
    }

    private fun removeOrphanedDownloadsWithSameUri(filename: String) {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)

        val idsToRemove = mutableListOf<Long>()
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                    if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                        try {
                            val fileUri = Uri.parse(localUri)
                            val file = File(fileUri.path ?: "")
                            if (!file.exists() && file.name == filename) {
                                idsToRemove.add(id)
                            }
                        } catch (e: Exception) {}
                    }
                } while (it.moveToNext())
            }
        }

        if (idsToRemove.isNotEmpty()) {
            idsToRemove.forEach { downloadManager.remove(it) }
        }
    }

    private fun showRemoveAllDownloadsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete_sweep_outline)
            .setTitle(R.string.dialog_title_remove_all_downloads)
            .setMessage(R.string.dialog_message_remove_all_downloads)
            .setPositiveButton(R.string.action_remove) { _, _ ->
                removeAllDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun removeAllDownloads() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val query = DownloadManager.Query()
            val cursor = downloadManager.query(query)
            val idsToRemove = mutableListOf<Long>()
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        idsToRemove.add(id)
                    } while (it.moveToNext())
                }
            }

            var skippedCount = 0
            idsToRemove.forEach { downloadId ->
                try {
                    if (checkCanRemoveDownload(downloadId)) {
                        removeFromListOnly(downloadId)
                    } else {
                        skippedCount++
                    }
                } catch (e: Exception) {
                    skippedCount++
                }
            }

            withContext(Dispatchers.Main) {
                if (skippedCount > 0) {
                    requireContext().toast(R.string.downloads_could_not_remove)
                }
                loadDownloads()
            }
        }
    }

    private fun checkCanRemoveDownload(downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            return false
        }

        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        cursor.close()

        if (status != DownloadManager.STATUS_SUCCESSFUL || localUriString.isNullOrEmpty()) {
            return true
        }

        return try {
            val fileUri = Uri.parse(localUriString)
            val file = File(fileUri.path ?: return false)
            !file.exists() || file.canRead() || file.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    private fun showCleanDownloadsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_cleaning_services_outline)
            .setTitle(R.string.dialog_title_clean_downloads)
            .setMessage(R.string.dialog_message_clean_downloads)
            .setPositiveButton(R.string.action_clean) { _, _ ->
                cleanDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun cleanDownloads() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val query = DownloadManager.Query()
            val cursor = downloadManager.query(query)
            val idsToRemove = mutableListOf<Long>()
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                        if (status == DownloadManager.STATUS_FAILED) {
                            idsToRemove.add(id)
                        } else if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                            val uri = Uri.parse(localUri)
                            val file = File(uri.path ?: "")
                            if (!file.exists()) {
                                idsToRemove.add(id)
                            }
                        }
                    } while (it.moveToNext())
                }
            }

            idsToRemove.forEach {
                try {
                    downloadManager.remove(it)
                } catch (e: Exception) {}
            }

            withContext(Dispatchers.Main) {
                loadDownloads()
            }
        }
    }

    private fun showDeleteAllDownloadsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete_forever_outline)
            .setTitle(R.string.dialog_title_delete_all_downloads)
            .setMessage(R.string.dialog_message_delete_all_downloads)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteAllDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteAllDownloads() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val query = DownloadManager.Query()
            val cursor = downloadManager.query(query)
            val idsToDelete = mutableListOf<Long>()
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        idsToDelete.add(id)
                    } while (it.moveToNext())
                }
            }

            idsToDelete.forEach {
                try {
                    downloadManager.remove(it)
                } catch (e: Exception) {}
            }

            withContext(Dispatchers.Main) {
                loadDownloads()
            }
        }
    }
}

private class DownloadAdapter(
    private val context: Context,
    private val onOptionsClick: (DownloadItem) -> Unit,
    private val scope: kotlinx.coroutines.CoroutineScope
) : ListAdapter<DownloadItem, DownloadAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.cardDownload)
        val activeAccent: View = view.findViewById(R.id.viewActiveAccent)
        val thumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val containerFileIcon: View = view.findViewById(R.id.containerFileIcon)
        val fileIcon: ImageView = view.findViewById(R.id.imgFileIcon)
        val playOverlay: ImageView = view.findViewById(R.id.imgPlayOverlay)
        val title: TextView = view.findViewById(R.id.tvDownloadTitle)
        val meta: TextView = view.findViewById(R.id.tvDownloadMeta)
        val statusPill: TextView = view.findViewById(R.id.tvStatus)
        val optionsBtn: View = view.findViewById(R.id.btnDownloadOptions)
        var thumbnailJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title

        val sizeStr = Formatter.formatFileSize(context, item.totalSize)
        val dateStr = DateUtils.formatDateTime(
            context,
            item.lastModified,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        )
        holder.meta.text = "$sizeStr • $dateStr"

        holder.statusPill.visibility = View.VISIBLE
        when {
            item.isOrphaned -> {
                holder.statusPill.text = context.getString(R.string.download_status_orphaned)
                holder.statusPill.background.setTint(Color.GRAY)
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_SUCCESSFUL -> {
                holder.statusPill.text = "Complete"
                holder.statusPill.background.setTint(context.getColor(android.R.color.holo_green_dark))
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_RUNNING -> {
                val progress = if (item.totalSize > 0) (item.bytesDownloaded * 100 / item.totalSize).toInt() else 0
                holder.statusPill.text = "Downloading $progress%"
                holder.statusPill.background.setTint(context.getColor(android.R.color.holo_blue_dark))
                holder.activeAccent.visibility = View.VISIBLE
            }
            item.status == DownloadManager.STATUS_FAILED -> {
                holder.statusPill.text = "Failed"
                holder.statusPill.background.setTint(context.getColor(android.R.color.holo_red_dark))
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_PAUSED -> {
                holder.statusPill.text = context.getString(R.string.download_status_paused)
                holder.statusPill.background.setTint(context.getColor(android.R.color.holo_orange_dark))
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_PENDING -> {
                holder.statusPill.text = context.getString(R.string.download_status_pending)
                holder.statusPill.background.setTint(Color.DKGRAY)
                holder.activeAccent.visibility = View.VISIBLE
            }
            else -> {
                holder.statusPill.text = context.getString(R.string.download_status_unknown)
                holder.statusPill.background.setTint(Color.GRAY)
                holder.activeAccent.visibility = View.GONE
            }
        }

        holder.thumbnailJob?.cancel()
        holder.thumbnail.setImageBitmap(null)
        holder.thumbnail.visibility = View.GONE
        holder.containerFileIcon.visibility = View.VISIBLE
        holder.playOverlay.visibility = View.GONE

        val ext = item.title.substringAfterLast('.', "").lowercase()
        val iconRes = when (ext) {
            "pdf" -> R.drawable.ic_unknown_document_outline_error
            "apk" -> R.drawable.ic_apk_document_outline
            "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_unknown_document_outline
            else -> R.drawable.ic_download_outline
        }
        holder.fileIcon.setImageResource(iconRes)

        val isVideo = item.mimeType?.startsWith("video/") == true
        val isImage = item.mimeType?.startsWith("image/") == true

        if ((isVideo || isImage) && item.localUri != null && !item.isOrphaned) {
            holder.thumbnailJob = scope.launch {
                val cached = thumbnailCache.get(item.id)
                if (cached != null) {
                    holder.containerFileIcon.visibility = View.GONE
                    holder.thumbnail.visibility = View.VISIBLE
                    holder.thumbnail.setImageBitmap(cached)
                    if (isVideo) holder.playOverlay.visibility = View.VISIBLE
                } else {
                    val bitmap = if (isVideo) {
                        getMediaThumbnail(context, item.localUri)
                    } else {
                        getImageThumbnail(context, item.localUri)
                    }
                    if (bitmap != null) {
                        thumbnailCache.put(item.id, bitmap)
                        if (isActive) {
                            holder.containerFileIcon.visibility = View.GONE
                            holder.thumbnail.visibility = View.VISIBLE
                            holder.thumbnail.setImageBitmap(bitmap)
                            if (isVideo) holder.playOverlay.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        holder.card.setOnClickListener { onOptionsClick(item) }
        holder.optionsBtn.setOnClickListener { onOptionsClick(item) }
    }

    object DiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean = oldItem == newItem
    }
}
