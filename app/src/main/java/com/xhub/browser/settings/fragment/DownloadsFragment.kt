package com.xhub.browser.settings.fragment

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ContentResolver
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.database.downloads.DownloadEntry
import com.xhub.browser.database.downloads.DownloadsRepository
import com.xhub.browser.download.DownloadProgress
import com.xhub.browser.download.DownloadProgressBus
import com.xhub.browser.download.YtDlpDownloadService
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

/**
 * Where a [DownloadItem] originates from. SYSTEM items are backed by the Android
 * [DownloadManager] and keyed by its numeric id. YTDLP items are yt-dlp video downloads that
 * were published to MediaStore and stored only in the app's own DownloadsRepository — they have
 * no DownloadManager id, so all open/share/remove operations go through a separate code path
 * keyed by their [DownloadItem.location] (a content:// URI or a file path).
 */
enum class DownloadSource { SYSTEM, YTDLP }

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
    val isOrphaned: Boolean,
    val source: DownloadSource = DownloadSource.SYSTEM,
    /** content:// URI or file path for YTDLP items; null for SYSTEM items. */
    val location: String? = null,
    /**
     * Live progress for an in-flight yt-dlp download, sourced from [DownloadProgressBus]. Non-null
     * only for active (RUNNING) yt-dlp downloads that are not yet in the repository. When set, the
     * card renders an inline progress bar + speed/ETA + a cancel button keyed by [activeUrl].
     */
    val progress: DownloadProgress? = null,
    /** The original source URL of an active yt-dlp download, used to issue a cancel request. */
    val activeUrl: String? = null
) {
    /** True when this item is an in-flight yt-dlp download actively downloading. */
    val isActive: Boolean get() = progress != null && progress.state == DownloadProgress.State.RUNNING

    /** True when this item is an in-flight yt-dlp download that the user has paused. */
    val isPaused: Boolean get() = progress != null && progress.state == DownloadProgress.State.PAUSED

    /** True when this item is in-flight (running or paused) \u2014 renders the progress card. */
    val isInFlight: Boolean get() = isActive || isPaused

    /**
     * Stable identity for diffing and thumbnail caching across both sources. Active downloads are
     * keyed by their source URL (they have no location/id yet) so the same card updates in place
     * as progress ticks in.
     */
    val stableKey: String get() = when {
        activeUrl != null -> "active:$activeUrl"
        source == DownloadSource.YTDLP -> "ytdlp:$location"
        else -> "sys:$id"
    }
}

/**
 * MediaStore metadata for a content:// yt-dlp download. Returned by the content-info resolver
 * lambda passed to [mapYtDlpEntry]. A null result means the MediaStore row is gone (orphaned).
 *
 * @param displayName the on-disk file name (DISPLAY_NAME); null falls back to the stored title.
 * @param sizeBytes the file size in bytes (SIZE).
 * @param dateModifiedSeconds last-modified time in **seconds** (DATE_MODIFIED), as MediaStore stores it.
 * @param mimeType the MIME type (MIME_TYPE); null triggers extension-based inference.
 */
data class YtDlpContentInfo(
    val displayName: String?,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val mimeType: String?
)

/**
 * Local-file metadata for a file-path yt-dlp download. Returned by the file-info resolver
 * lambda passed to [mapYtDlpEntry]. A null result means the file no longer exists (orphaned).
 *
 * @param name the file name.
 * @param sizeBytes the file length in bytes.
 * @param lastModifiedMillis last-modified time in **milliseconds**.
 */
data class YtDlpFileInfo(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

/**
 * Pure, dependency-injected mapping of a single yt-dlp [DownloadEntry] location into a
 * [DownloadItem], extracted from [DownloadsFragment.loadDownloads] so it can be unit-tested
 * without Android's DownloadManager/ContentResolver/File. Behavior is identical to the inlined
 * version it replaced.
 *
 * Filtering: returns null for any location that is neither a `content://` URI nor an absolute
 * (`/`-prefixed) file path. Legacy system downloads are also written to the repo with an
 * http(s) url, and those must NOT be surfaced here (DownloadManager already owns them).
 *
 * Orphan detection: if the injected resolver returns null (content row missing / file gone),
 * the item is marked [DownloadItem.isOrphaned] but still returned so the user can remove it.
 *
 * MIME inference: when the resolved MIME type is null/absent, it is inferred from the display
 * name's extension via [mimeFromExtension]; a null inference is left as null.
 *
 * @param entry the repository entry; [DownloadEntry.url] is the location, [DownloadEntry.title]
 *   is the fallback display name.
 * @param nowMillis current time, used as the fallback last-modified for content:// entries
 *   when the resolver succeeds but MediaStore reports no date.
 * @param queryContentInfo resolves MediaStore metadata for a content:// location, or null if gone.
 * @param queryFileInfo resolves file metadata for a path location, or null if the file is gone.
 * @param mimeFromExtension maps a lowercase file extension to a MIME type, or null if unknown.
 * @return the mapped [DownloadItem], or null if the location is not a yt-dlp location.
 */
internal fun mapYtDlpEntry(
    entry: DownloadEntry,
    nowMillis: Long,
    queryContentInfo: (String) -> YtDlpContentInfo?,
    queryFileInfo: (String) -> YtDlpFileInfo?,
    mimeFromExtension: (String) -> String?
): DownloadItem? {
    val loc = entry.url
    if (!loc.startsWith("content://") && !loc.startsWith("/")) return null

    var displayName = entry.title
    var sizeBytes = 0L
    var modified = nowMillis
    var mimeType: String? = null
    var orphaned = false

    if (loc.startsWith("content://")) {
        val info = queryContentInfo(loc)
        if (info == null) {
            orphaned = true
        } else {
            displayName = info.displayName ?: entry.title
            sizeBytes = info.sizeBytes
            modified = info.dateModifiedSeconds * 1000L
            mimeType = info.mimeType
        }
    } else {
        val info = queryFileInfo(loc)
        if (info == null) {
            orphaned = true
        } else {
            displayName = info.name
            sizeBytes = info.sizeBytes
            modified = info.lastModifiedMillis
        }
    }

    if (mimeType == null) {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        mimeType = mimeFromExtension(ext)
    }

    return DownloadItem(
        id = loc.hashCode().toLong(),
        title = displayName,
        status = DownloadManager.STATUS_SUCCESSFUL,
        localUri = loc,
        uri = null,
        bytesDownloaded = sizeBytes,
        totalSize = sizeBytes,
        lastModified = modified,
        mimeType = mimeType,
        isOrphaned = orphaned,
        source = DownloadSource.YTDLP,
        location = loc
    )
}

// Thread-safe static LruCache for download thumbnails, keyed by DownloadItem.stableKey so it
// works for both DownloadManager (numeric id) and yt-dlp (content:// / path) items.
private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(15 * 1024 * 1024) { // 15MB
    override fun sizeOf(key: String, value: Bitmap): Int {
        return value.byteCount
    }
}

/**
 * Extract a video frame thumbnail. Handles both content:// URIs (MediaStore-published yt-dlp
 * videos and Android 10+ system downloads) and plain file paths — the previous file-path-only
 * implementation returned null for content:// URIs, which is why yt-dlp videos showed no preview.
 */
private suspend fun getMediaThumbnail(context: Context, location: String?): Bitmap? = withContext(Dispatchers.IO) {
    if (location == null) return@withContext null
    try {
        val retriever = MediaMetadataRetriever()
        if (location.startsWith("content://")) {
            retriever.setDataSource(context, Uri.parse(location))
        } else {
            val file = File(Uri.parse(location).path ?: return@withContext null)
            if (!file.exists()) return@withContext null
            retriever.setDataSource(file.absolutePath)
        }
        val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * Decode a downscaled image thumbnail. Handles content:// URIs via the ContentResolver stream
 * (BitmapFactory.decodeFile cannot read content:// paths) and plain file paths via decodeFile.
 */
private suspend fun getImageThumbnail(context: Context, location: String?): Bitmap? = withContext(Dispatchers.IO) {
    if (location == null) return@withContext null
    try {
        if (location.startsWith("content://")) {
            val uri = Uri.parse(location)
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions, 240, 180)
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            val file = File(Uri.parse(location).path ?: return@withContext null)
            if (!file.exists()) return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, 240, 180)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
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

    @Inject
    lateinit var downloadsRepository: DownloadsRepository

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var rvDownloads: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvHeaderSummary: TextView
    private lateinit var btnClean: Button
    private lateinit var btnRemoveAll: Button
    private lateinit var btnDeleteAll: Button

    private lateinit var adapter: DownloadAdapter
    private var progressUpdateJob: Job? = null

    /**
     * Latest snapshot of active (RUNNING) yt-dlp downloads from [DownloadProgressBus], keyed by
     * source URL. Merged into the list as live cards at the top on every [loadDownloads] pass and
     * whenever the bus emits. Terminal states (COMPLETE/ERROR/CANCELLED) are dropped here so the
     * card falls back to the repository-backed row once the download finishes.
     */
    @Volatile
    private var activeProgress: Map<String, DownloadProgress> = emptyMap()

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
        adapter = DownloadAdapter(
            requireContext(),
            onOptionsClick = { item -> showDownloadOptionsDialog(item) },
            onCancelClick = { item -> cancelActiveDownload(item) },
            onPauseResumeClick = { item -> pauseOrResumeActiveDownload(item) },
            scope = viewLifecycleOwner.lifecycleScope
        )
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
                showDownloadOptionsDialog(item)
                adapter.notifyItemChanged(position)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(rvDownloads)

        // Setup action listeners
        btnClean.setOnClickListener { showCleanDownloadsDialog() }
        btnRemoveAll.setOnClickListener { showRemoveAllDownloadsDialog() }
        btnDeleteAll.setOnClickListener { showDeleteAllDownloadsDialog() }

        // Collect live yt-dlp download progress and merge in-flight downloads as cards at the top
        // of the list. repeatOnLifecycle keeps this lifecycle-safe: collection stops when the
        // fragment view is not started and resumes (re-reading the latest StateFlow value) after.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadProgressBus.downloads.collect { map ->
                    activeProgress = map.filterValues {
                        it.state == DownloadProgress.State.RUNNING || it.state == DownloadProgress.State.PAUSED
                    }
                    if (isAdded) loadDownloads()
                }
            }
        }

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
        // Capture the ContentResolver up front (on the main thread) so the IO block never calls
        // requireContext(), which would throw IllegalStateException if the fragment detaches mid-load.
        val contentResolver = requireContext().contentResolver
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

                // Merge yt-dlp video downloads from the app's own repository. These are published
                // to MediaStore and stored with url = content:// URI (Android 10+) or a file path
                // (pre-Q). DownloadManager never received them, so they only live in the repo and
                // would otherwise never appear in this list. Legacy system downloads are also
                // written to the repo with an http(s) url, so we only take entries whose url is a
                // content:// or absolute-path location to avoid duplicating DownloadManager items.
                val ytdlpEntries = try {
                    downloadsRepository.getAllDownloads().blockingGet()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load yt-dlp downloads from repository")
                    emptyList()
                }
                // The three side-effecting dependencies (MediaStore query, File probing, MIME
                // lookup) are injected into the pure mapYtDlpEntry() so the mapping stays unit-
                // testable. Behavior is identical to the previous inlined version.
                val queryContentInfo: (String) -> YtDlpContentInfo? = { loc ->
                    try {
                        contentResolver.query(
                            Uri.parse(loc),
                            arrayOf(
                                android.provider.MediaStore.Downloads.DISPLAY_NAME,
                                android.provider.MediaStore.Downloads.SIZE,
                                android.provider.MediaStore.Downloads.DATE_MODIFIED,
                                android.provider.MediaStore.Downloads.MIME_TYPE
                            ),
                            null, null, null
                        )?.use { c ->
                            if (c.moveToFirst()) {
                                YtDlpContentInfo(
                                    displayName = c.getString(c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.DISPLAY_NAME)),
                                    sizeBytes = c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.SIZE)),
                                    dateModifiedSeconds = c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.DATE_MODIFIED)),
                                    mimeType = c.getString(c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.MIME_TYPE))
                                )
                            } else {
                                null
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                val queryFileInfo: (String) -> YtDlpFileInfo? = { loc ->
                    val file = File(loc)
                    if (file.exists()) {
                        YtDlpFileInfo(file.name, file.length(), file.lastModified())
                    } else {
                        null
                    }
                }
                val mimeFromExtension: (String) -> String? = { ext ->
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                }
                val now = System.currentTimeMillis()
                for (entry in ytdlpEntries) {
                    mapYtDlpEntry(entry, now, queryContentInfo, queryFileInfo, mimeFromExtension)
                        ?.let { list.add(it) }
                }

                list.sortByDescending { it.lastModified }

                // Merge in-flight yt-dlp downloads as live cards at the very top. These are not yet
                // in the repository (they only get persisted on completion), so they'd otherwise be
                // invisible until finished. Snapshot activeProgress here (read on the main thread
                // before the IO block would be racy, but the reference read is atomic and the map
                // is immutable once assigned, so reading it here is safe).
                val active = activeProgress
                val activeItems = active.values.map { p ->
                    DownloadItem(
                        id = p.url.hashCode().toLong(),
                        title = p.filename,
                        status = if (p.state == DownloadProgress.State.PAUSED)
                            DownloadManager.STATUS_PAUSED else DownloadManager.STATUS_RUNNING,
                        localUri = null,
                        uri = p.url,
                        bytesDownloaded = 0L,
                        totalSize = 0L,
                        lastModified = Long.MAX_VALUE, // pin to top
                        mimeType = null,
                        isOrphaned = false,
                        source = DownloadSource.YTDLP,
                        location = null,
                        progress = p,
                        activeUrl = p.url
                    )
                }
                (activeItems + list)
            }

            adapter.submitList(items) {
                if (isAdded) {
                    updateUI(items)
                    // Live-update the home hero "Downloads" chip when items are added/removed from
                    // the downloads page while the home overlay is behind it. This is the single
                    // funnel that all add/remove/clean/delete operations in this fragment route
                    // through, so it keeps the count fresh. refreshHomeStatsIfVisible() is a cheap
                    // no-op when the home overlay is not currently visible.
                    (activity as? WebBrowserActivity)?.refreshHomeStatsIfVisible()
                }
            }
        }
    }

    /**
     * Cancel an in-flight yt-dlp download by asking [YtDlpDownloadService] to destroy its process.
     * The service emits a CANCELLED state to [DownloadProgressBus], which drops it from
     * [activeProgress] on the next collect so the card disappears from the list.
     */
    private fun cancelActiveDownload(item: DownloadItem) {
        val url = item.activeUrl ?: return
        val intent = Intent(requireContext(), YtDlpDownloadService::class.java).apply {
            action = YtDlpDownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(YtDlpDownloadService.EXTRA_URL, url)
        }
        try {
            requireContext().startService(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to request download cancellation")
        }
    }

    /**
     * Toggle pause/resume for an in-flight yt-dlp download by asking [YtDlpDownloadService] to
     * destroy (pause) or relaunch (resume) its process. The service emits the new state to
     * [DownloadProgressBus], which re-renders the card on the next collect.
     */
    private fun pauseOrResumeActiveDownload(item: DownloadItem) {
        val url = item.activeUrl ?: return
        val serviceAction = if (item.isPaused) {
            YtDlpDownloadService.ACTION_RESUME_DOWNLOAD
        } else {
            YtDlpDownloadService.ACTION_PAUSE_DOWNLOAD
        }
        val intent = Intent(requireContext(), YtDlpDownloadService::class.java).apply {
            action = serviceAction
            putExtra(YtDlpDownloadService.EXTRA_URL, url)
        }
        try {
            requireContext().startService(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to request download pause/resume")
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

        // Only start the 1s DownloadManager polling loop for *system* downloads that are still
        // running/pending. yt-dlp active cards (item.isActive) are already refreshed by the
        // DownloadProgressBus collector, so polling for them would double the reload rate.
        val hasActiveSystemDownload = items.any {
            it.progress == null &&
            (it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING)
        }
        if (hasActiveSystemDownload) {
            startProgressUpdates()
        }
    }

    /**
     * Entry point for the per-item options dialog. Routes SYSTEM items to the DownloadManager-backed
     * dialog and YTDLP items to a content-URI/FileProvider-backed dialog (Open / Share / Delete).
     */
    private fun showDownloadOptionsDialog(item: DownloadItem) {
        if (item.source == DownloadSource.YTDLP) {
            showYtDlpOptionsDialog(item)
        } else {
            showSystemDownloadOptionsDialog(item.id, item.title)
        }
    }

    /**
     * Options dialog for yt-dlp video downloads (Open / Share / Delete), mirroring the success
     * notification's ACTION_VIEW logic: content:// URIs are viewed directly, file paths go through
     * the app FileProvider. Removal deletes both the file and the DownloadsRepository entry.
     */
    private fun showYtDlpOptionsDialog(item: DownloadItem) {
        val location = item.location ?: return
        val options = mutableListOf<com.xhub.browser.dialog.DialogItem>()

        if (!item.isOrphaned) {
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.open_download) {
                openYtDlpDownload(item)
            })
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.share_file) {
                shareYtDlpDownload(item)
            })
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.remove_and_delete_file) {
                confirmDeleteYtDlpDownload(item)
            })
        } else {
            options.add(com.xhub.browser.dialog.DialogItem(title = R.string.remove_from_list) {
                removeYtDlpFromList(location)
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
                text = item.title,
                items = options.toTypedArray()
            )
        )
    }

    /** Resolve a usable MIME type for a yt-dlp item, falling back to the filename extension. */
    private fun ytDlpMimeType(name: String, mimeType: String?): String {
        if (!mimeType.isNullOrEmpty()) return mimeType
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    /** Resolve the viewable/shareable Uri for a yt-dlp location (content:// direct, path via FileProvider). */
    private fun ytDlpUri(location: String): Uri = if (location.startsWith("content://")) {
        Uri.parse(location)
    } else {
        androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            File(location)
        )
    }

    private fun openYtDlpDownload(item: DownloadItem) {
        val location = item.location ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(ytDlpUri(location), ytDlpMimeType(item.title, item.mimeType))
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            activity?.toast(R.string.error_cant_open_file)
        }
    }

    private fun shareYtDlpDownload(item: DownloadItem) {
        val location = item.location ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = ytDlpMimeType(item.title, item.mimeType)
            putExtra(Intent.EXTRA_STREAM, ytDlpUri(location))
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
        } catch (e: Exception) {}
    }

    private fun confirmDeleteYtDlpDownload(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_remove_and_delete)
            .setMessage(getString(R.string.dialog_message_remove_and_delete, item.title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteYtDlpDownload(item)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteYtDlpDownload(item: DownloadItem) {
        val location = item.location ?: return
        try {
            if (location.startsWith("content://")) {
                requireContext().contentResolver.delete(Uri.parse(location), null, null)
            } else {
                File(location).delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete yt-dlp download file")
        }
        removeYtDlpFromList(location)
    }

    private fun removeYtDlpFromList(location: String) {
        downloadsRepository.deleteDownload(location)
            .subscribeOn(io.reactivex.schedulers.Schedulers.io())
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe({ if (isAdded) loadDownloads() }, { Timber.e(it, "Failed to remove yt-dlp download from list") })
    }

    /** True if a repository entry is a yt-dlp download (content:// URI or absolute file path). */
    private fun DownloadEntry.isYtDlpLocation(): Boolean =
        url.startsWith("content://") || url.startsWith("/")

    /**
     * True if a yt-dlp location no longer has a backing file: the MediaStore row is gone
     * (content://) or the file no longer exists (path). A query failure is treated as orphaned so
     * a broken entry can always be cleaned out.
     */
    private fun isYtDlpLocationOrphaned(location: String, resolver: ContentResolver): Boolean = try {
        if (location.startsWith("content://")) {
            resolver.query(
                Uri.parse(location),
                arrayOf(android.provider.MediaStore.Downloads._ID),
                null, null, null
            )?.use { !it.moveToFirst() } ?: true
        } else {
            !File(location).exists()
        }
    } catch (e: Exception) {
        true
    }

    /** Delete the backing file for a yt-dlp location (content:// via ContentResolver, path via File). */
    private fun deleteYtDlpFile(location: String, resolver: ContentResolver) {
        try {
            if (location.startsWith("content://")) {
                resolver.delete(Uri.parse(location), null, null)
            } else {
                File(location).delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete yt-dlp download file during bulk operation")
        }
    }

    private fun showSystemDownloadOptionsDialog(downloadId: Long, title: String) {
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

            // yt-dlp: remove all repo entries from the list, keeping the files on disk
            // (deleteDownload only drops the DB row, mirroring "remove and keep file").
            try {
                downloadsRepository.getAllDownloads().blockingGet()
                    .filter { it.isYtDlpLocation() }
                    .forEach { downloadsRepository.deleteDownload(it.url).blockingGet() }
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove yt-dlp downloads from list")
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
        // Capture up front (main thread) so the IO block never touches requireContext().
        val contentResolver = requireContext().contentResolver
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

            // yt-dlp: drop repo entries whose backing file / MediaStore row no longer exists.
            // Valid files are left untouched — this mirrors the orphaned/failed cleanup above.
            try {
                downloadsRepository.getAllDownloads().blockingGet()
                    .filter { it.isYtDlpLocation() && isYtDlpLocationOrphaned(it.url, contentResolver) }
                    .forEach { downloadsRepository.deleteDownload(it.url).blockingGet() }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clean orphaned yt-dlp downloads")
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
        // Capture up front (main thread) so the IO block never touches requireContext().
        val contentResolver = requireContext().contentResolver
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

            // yt-dlp: delete the backing file (content:// via ContentResolver, path via File) then
            // drop the repo entry, mirroring DownloadManager.remove() which also deletes the file.
            try {
                downloadsRepository.getAllDownloads().blockingGet()
                    .filter { it.isYtDlpLocation() }
                    .forEach {
                        deleteYtDlpFile(it.url, contentResolver)
                        downloadsRepository.deleteDownload(it.url).blockingGet()
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete yt-dlp downloads")
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
    private val onCancelClick: (DownloadItem) -> Unit,
    private val onPauseResumeClick: (DownloadItem) -> Unit,
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
        val cancelBtn: ImageView = view.findViewById(R.id.btnCancelDownload)
        val pauseResumeBtn: ImageView = view.findViewById(R.id.btnPauseResumeDownload)
        val progressBar: com.google.android.material.progressindicator.LinearProgressIndicator =
            view.findViewById(R.id.progressDownload)
        var thumbnailJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title

        // ── In-flight (running OR paused) yt-dlp download: inline progress bar + speed/ETA +
        // pause/resume + cancel. Both running and paused states render the progress card so the
        // user can toggle or cancel; bindActiveDownload branches on p.state internally.
        if (item.isInFlight) {
            val p = item.progress!!
            bindActiveDownload(holder, item, p)
            return
        }

        // Non-active items: hide the active-only affordances.
        holder.progressBar.visibility = View.GONE
        holder.cancelBtn.visibility = View.GONE
        holder.pauseResumeBtn.visibility = View.GONE
        holder.optionsBtn.visibility = View.VISIBLE

        val sizeStr = Formatter.formatFileSize(context, item.totalSize)
        val dateStr = DateUtils.formatDateTime(
            context,
            item.lastModified,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        )
        holder.meta.text = "$sizeStr • $dateStr"

        holder.statusPill.visibility = View.VISIBLE
        // Soft brand-aligned pill colors (mutate so each card keeps its own tint)
        fun tintPill(color: Int) {
            holder.statusPill.background = holder.statusPill.background?.mutate()?.also { it.setTint(color) }
        }
        when {
            item.isOrphaned -> {
                holder.statusPill.text = context.getString(R.string.download_status_orphaned)
                tintPill(0xFF8E8E93.toInt())
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_SUCCESSFUL -> {
                holder.statusPill.text = context.getString(R.string.download_status_complete)
                tintPill(0xFF34C759.toInt())
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_RUNNING -> {
                val progress = if (item.totalSize > 0) (item.bytesDownloaded * 100 / item.totalSize).toInt() else 0
                holder.statusPill.text = context.getString(R.string.download_status_downloading_percent, progress)
                tintPill(0xFF0A84FF.toInt())
                holder.activeAccent.visibility = View.VISIBLE
            }
            item.status == DownloadManager.STATUS_FAILED -> {
                holder.statusPill.text = context.getString(R.string.download_status_failed, item.status)
                tintPill(0xFFFF453A.toInt())
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_PAUSED -> {
                holder.statusPill.text = context.getString(R.string.download_status_paused)
                tintPill(0xFFFF9F0A.toInt())
                holder.activeAccent.visibility = View.GONE
            }
            item.status == DownloadManager.STATUS_PENDING -> {
                holder.statusPill.text = context.getString(R.string.download_status_pending)
                tintPill(0xFF8E8E93.toInt())
                holder.activeAccent.visibility = View.VISIBLE
            }
            else -> {
                holder.statusPill.text = context.getString(R.string.download_status_unknown)
                tintPill(0xFF8E8E93.toInt())
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
        val thumbLocation = item.location ?: item.localUri

        if ((isVideo || isImage) && thumbLocation != null && !item.isOrphaned) {
            holder.thumbnailJob = scope.launch {
                val cached = thumbnailCache.get(item.stableKey)
                if (cached != null) {
                    holder.containerFileIcon.visibility = View.GONE
                    holder.thumbnail.visibility = View.VISIBLE
                    holder.thumbnail.setImageBitmap(cached)
                    if (isVideo) holder.playOverlay.visibility = View.VISIBLE
                } else {
                    val bitmap = if (isVideo) {
                        getMediaThumbnail(context, thumbLocation)
                    } else {
                        getImageThumbnail(context, thumbLocation)
                    }
                    if (bitmap != null) {
                        thumbnailCache.put(item.stableKey, bitmap)
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

    /**
     * Bind an in-flight yt-dlp download (running OR paused): neon accent, download icon, an inline
     * progress bar and a meta line, plus pause/resume and cancel buttons. Thumbnails/options are
     * hidden because the file doesn't exist on disk yet. Branches on [p].state:
     *  - RUNNING: blue "Downloading…" pill, animated bar, "45% • 2.5 MB/s • ETA 00:30" meta,
     *    Pause icon.
     *  - PAUSED: orange "Paused" pill, frozen bar at last percent, "Paused • 45%" meta, Resume icon.
     */
    private fun bindActiveDownload(holder: ViewHolder, item: DownloadItem, p: DownloadProgress) {
        // Cancel any pending thumbnail load from a recycled row and show the download icon.
        holder.thumbnailJob?.cancel()
        holder.thumbnail.setImageBitmap(null)
        holder.thumbnail.visibility = View.GONE
        holder.playOverlay.visibility = View.GONE
        holder.containerFileIcon.visibility = View.VISIBLE
        holder.fileIcon.setImageResource(R.drawable.ic_download_outline)

        val isPaused = p.state == DownloadProgress.State.PAUSED

        holder.activeAccent.visibility = View.VISIBLE
        holder.optionsBtn.visibility = View.GONE
        holder.cancelBtn.visibility = View.VISIBLE
        holder.cancelBtn.setOnClickListener { onCancelClick(item) }

        // Pause/Resume toggle: icon + content description reflect the current state, tap sends the
        // opposite action to the service.
        holder.pauseResumeBtn.visibility = View.VISIBLE
        if (isPaused) {
            holder.pauseResumeBtn.setImageResource(R.drawable.ic_play_arrow)
            holder.pauseResumeBtn.contentDescription = context.getString(R.string.resume_download)
        } else {
            holder.pauseResumeBtn.setImageResource(R.drawable.ic_pause)
            holder.pauseResumeBtn.contentDescription = context.getString(R.string.pause_download)
        }
        holder.pauseResumeBtn.setOnClickListener { onPauseResumeClick(item) }

        // Status pill — soft brand colors (mutate drawable so rebinds don't bleed tints).
        holder.statusPill.visibility = View.VISIBLE
        val pillBg = holder.statusPill.background?.mutate()
        if (isPaused) {
            holder.statusPill.text = context.getString(R.string.download_status_paused)
            pillBg?.setTint(0xFFFF9F0A.toInt())
        } else {
            holder.statusPill.text = if (p.percent >= 0) {
                context.getString(R.string.download_status_downloading_percent, p.percent)
            } else {
                context.getString(R.string.download_status_downloading)
            }
            pillBg?.setTint(0xFF0A84FF.toInt())
        }
        holder.statusPill.background = pillBg

        // Progress bar: indeterminate until yt-dlp reports a real percent. When paused, freeze the
        // bar at the last known percent (never indeterminate) so it reads as "stopped here".
        holder.progressBar.visibility = View.VISIBLE
        if (p.percent < 0 && !isPaused) {
            holder.progressBar.isIndeterminate = true
        } else {
            holder.progressBar.isIndeterminate = false
            holder.progressBar.setProgressCompat(p.percent.coerceIn(0, 100), !isPaused)
        }

        // Meta line. Paused: "Paused • 45%". Running: "45% • 2.5 MB/s • ETA 00:30" omitting unknowns.
        if (isPaused) {
            holder.meta.text = context.getString(
                R.string.download_progress_paused, p.percent.coerceAtLeast(0)
            )
        } else {
            val parts = mutableListOf<String>()
            if (p.percent >= 0) parts.add("${p.percent}%")
            if (p.speedBytesPerSec >= 0) {
                val speedStr = Formatter.formatShortFileSize(context, p.speedBytesPerSec)
                parts.add(context.getString(R.string.download_progress_speed, speedStr))
            }
            YtDlpDownloadService.formatEta(p.etaSeconds)?.let {
                parts.add(context.getString(R.string.download_progress_eta, it))
            }
            holder.meta.text = if (parts.isEmpty()) {
                context.getString(R.string.download_progress_starting)
            } else {
                parts.joinToString(" • ")
            }
        }

        // In-flight cards are not tappable for options (no file yet); tapping does nothing.
        holder.card.setOnClickListener(null)
        holder.card.isClickable = false
    }

    object DiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean = oldItem.stableKey == newItem.stableKey
        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean = oldItem == newItem
    }
}
