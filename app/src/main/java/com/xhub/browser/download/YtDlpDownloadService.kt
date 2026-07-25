package com.xhub.browser.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xhub.browser.R
import com.xhub.browser.database.downloads.DownloadEntry
import com.xhub.browser.database.downloads.DownloadsRepository
import com.xhub.browser.di.DatabaseScheduler
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Scheduler
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Background service for downloading videos using yt-dlp via youtubedl-android library.
 * 
 * Handles blob:, HLS (.m3u8), DASH (.mpd) and other adaptive streams that 
 * Android DownloadManager cannot handle.
 * 
 * Uses youtubedl-android library which:
 * - Executes yt-dlp from nativeLibraryDir (Android 10+ compatible)
 * - Includes Python runtime for Android's bionic libc
 * - Provides proper progress callbacks
 * 
 * Foreground Service Type:
 * - Uses dataSync type as user-initiated downloads fall under data synchronization
 * - On Android 14+ (API 34+), dataSync services have time limits in the background
 * - For very long downloads, users should keep the app visible to prevent interruption
 * 
 * Notification Permission:
 * - Checks POST_NOTIFICATIONS permission (required on Android 13+/API 33+) before showing notifications
 * - Falls back to logging when permission is not granted
 */
@AndroidEntryPoint
class YtDlpDownloadService : Service() {
    
    @Inject
    lateinit var downloadsRepository: DownloadsRepository
    
    @Inject
    lateinit var ytDlpManager: YtDlpManager
    
    @Inject
    @DatabaseScheduler
    lateinit var databaseScheduler: Scheduler
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val downloadProcessIds = ConcurrentHashMap<String, String>() // url -> processId
    private val downloadNotificationIds = ConcurrentHashMap<String, Int>() // url -> notificationId
    private val downloadFilenames = ConcurrentHashMap<String, String>() // url -> uniqueFilename
    private val lastProgressUpdate = ConcurrentHashMap<String, Pair<Int, Long>>() // url -> (lastPercent, lastTimeMs)
    private val downloadPageTitles = ConcurrentHashMap<String, String>() // url -> pageTitle (for resume)
    private val downloadFormats = ConcurrentHashMap<String, DownloadFormat>() // url -> selected format (for resume)
    // Paused downloads awaiting resume. Presence of an entry is the signal the coroutine's catch
    // blocks use to distinguish an intentional pause (keep the .part file + tracking) from a
    // cancellation or failure. Keyed by download URL.
    private val pausedDownloads = ConcurrentHashMap<String, PausedDownload>()

    /**
     * State persisted while a download is paused, so [resumeDownload] can relaunch yt-dlp with the
     * exact same output template (and therefore resume from the existing `.part` file).
     */
    private data class PausedDownload(
        val url: String,
        val uniqueFilename: String,
        val pageTitle: String?,
        val notificationId: Int,
        val lastPercent: Int,
        val format: DownloadFormat
    )
    
    private lateinit var notificationManager: NotificationManager
    // AtomicInteger so concurrent downloads get unique ids without a mutex.
    private val nextNotificationId = java.util.concurrent.atomic.AtomicInteger(NOTIFICATION_ID_START)
    
    companion object {
        // Actions
        const val ACTION_START_DOWNLOAD = "com.xhub.browser.ACTION_START_YTDLP_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.xhub.browser.ACTION_CANCEL_YTDLP_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.xhub.browser.ACTION_PAUSE_YTDLP_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.xhub.browser.ACTION_RESUME_YTDLP_DOWNLOAD"

        // Distinct PendingIntent request-code offsets so the pause/resume/cancel actions that share
        // a notification id don't clobber each other's extras under FLAG_UPDATE_CURRENT.
        private const val PAUSE_REQUEST_OFFSET = 100000
        private const val RESUME_REQUEST_OFFSET = 200000
        
        // Extras
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_PAGE_TITLE = "extra_page_title"
        // Carries the selected DownloadFormat by enum name; parsed back via DownloadFormat.fromName.
        const val EXTRA_FORMAT = "extra_format"
        
        // Notifications
        private const val CHANNEL_ID = "ytdlp_downloads"
        private const val CHANNEL_NAME = "Video Downloads"
        private const val NOTIFICATION_ID_FOREGROUND = 1001
        private const val NOTIFICATION_ID_START = 1002
        
        // Progress throttling - minimum interval between notification updates
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        
        // Maximum length of the fallback (traceback) summary shown to the user.
        private const val ERROR_SUMMARY_MAX_LENGTH = 140

        // Matches yt-dlp's speed token in a progress line, e.g. "at 2.50MiB/s", "at  931.00KiB/s",
        // "at 12.3 MB/s". Captures the number and the unit prefix so we can convert to bytes/s.
        private val SPEED_REGEX =
            Regex("""at\s+([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?)i?B/s""", RegexOption.IGNORE_CASE)

        /**
         * Parse the download speed (bytes/second) from a yt-dlp progress line, or -1 if the line
         * contains no recognizable speed token (e.g. "at Unknown B/s" or a non-progress line).
         *
         * yt-dlp prints binary units (KiB/MiB/GiB) so we scale by 1024. This is a best-effort
         * parse extracted as an internal function so it can be unit-tested without the Service.
         */
        internal fun parseSpeedBytesPerSec(line: String): Long {
            val match = SPEED_REGEX.find(line) ?: return -1L
            val value = match.groupValues[1].toDoubleOrNull() ?: return -1L
            val multiplier = when (match.groupValues[2].uppercase()) {
                "K" -> 1024.0
                "M" -> 1024.0 * 1024.0
                "G" -> 1024.0 * 1024.0 * 1024.0
                "T" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
                else -> 1.0
            }
            return (value * multiplier).toLong()
        }

        /**
         * Format an ETA in whole seconds as "MM:SS" (or "H:MM:SS" past an hour). Returns null when
         * [seconds] is negative/unknown so callers can omit the ETA segment.
         */
        internal fun formatEta(seconds: Long): String? {
            if (seconds < 0) return null
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%02d:%02d", m, s)
        }

        /**
         * Condense a raw yt-dlp error message into a short, user-facing summary.
         * yt-dlp failures often include a multi-line Python traceback; we surface the most relevant
         * line and recognise a few common cases (unsupported site, network) so the notification/toast
         * is meaningful rather than a wall of text. The full error is always logged separately.
         *
         * Extracted as an internal, [Context]-parameterised function (rather than a private instance
         * method) so it can be unit tested without instantiating the Service. See
         * YtDlpDownloadServiceTest.
         */
        internal fun summarizeYtDlpError(context: Context, error: String): String {
            val lower = error.lowercase()
            return when {
                lower.contains("unsupported url") || lower.contains("no video formats") ||
                    lower.contains("unable to extract") ->
                    context.getString(R.string.video_download_error_unsupported)
                lower.contains("unable to download") || lower.contains("timed out") ||
                    lower.contains("connection") || lower.contains("network") ->
                    context.getString(R.string.video_download_error_network)
                else -> {
                    // Fall back to the first non-blank line, trimmed to a sane length.
                    val firstLine = error.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                        ?: error.trim()
                    firstLine.removePrefix("ERROR:").trim().take(ERROR_SUMMARY_MAX_LENGTH)
                }
            }
        }

        /**
         * Helper to start a download from any context
         */
        fun startDownload(
            context: Context,
            url: String,
            filename: String? = null,
            pageTitle: String? = null,
            format: DownloadFormat = DownloadFormat.DEFAULT
        ) {
            val intent = Intent(context, YtDlpDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILENAME, filename)
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_FORMAT, format.name)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Start as foreground service with dataSync type
        // Note: On Android 14+ (API 34+), dataSync services have time limits in the background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_FOREGROUND, 
                createForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, createForegroundNotification())
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url.isNullOrBlank()) {
                    Timber.w("ACTION_START_DOWNLOAD received with no URL — stopping service")
                    checkAndStopService()
                    return START_NOT_STICKY
                }
                // Only accept http/https URLs; reject other schemes at the boundary
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Timber.w("Rejecting download URL with unsupported scheme: $url")
                    checkAndStopService()
                    return START_NOT_STICKY
                }
                val filename = intent.getStringExtra(EXTRA_FILENAME)
                val pageTitle = intent.getStringExtra(EXTRA_PAGE_TITLE)
                val format = DownloadFormat.fromName(intent.getStringExtra(EXTRA_FORMAT))
                startDownload(url, filename, pageTitle, format)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) {
                    cancelDownload(url)
                } else {
                    // Cancel with no URL: nothing to cancel, stop if idle
                    Timber.w("ACTION_CANCEL_DOWNLOAD received with no URL")
                    checkAndStopService()
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) pauseDownload(url) else checkAndStopService()
            }
            ACTION_RESUME_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) resumeDownload(url) else checkAndStopService()
            }
            else -> {
                // Null intent (system restart) or unknown action: stop if no jobs are running
                Timber.w("onStartCommand: unrecognised action '${intent?.action}' — checking idle")
                checkAndStopService()
            }
        }
        return START_NOT_STICKY
    }
    
    private fun startDownload(url: String, filename: String?, pageTitle: String?, format: DownloadFormat) {
        // Don't start duplicate downloads
        if (downloadJobs.containsKey(url)) {
            Timber.w("Download already in progress for: $url")
            return
        }

        val notificationId = nextNotificationId.getAndIncrement()

        // Track notification ID for this download so cancellation can clean it up
        downloadNotificationIds[url] = notificationId

        // Sanitize filename and make it unique with timestamp to avoid collisions
        val baseFilename = filename?.let { sanitizeFilename(it) } ?: "video"
        val uniqueFilename = "${baseFilename}_${System.currentTimeMillis()}"

        // Track filename, page title and format for consistent display and resume support
        downloadFilenames[url] = uniqueFilename
        if (pageTitle != null) downloadPageTitles[url] = pageTitle
        downloadFormats[url] = format

        launchDownload(url, uniqueFilename, pageTitle, notificationId, resume = false, format = format)
    }

    /**
     * Resume a previously paused download. Pulls the persisted [PausedDownload] state and relaunches
     * yt-dlp with the SAME output template so `--continue` picks up the existing `.part` file. No-op
     * if the download is already running or was never paused.
     */
    private fun resumeDownload(url: String) {
        if (downloadJobs.containsKey(url)) {
            Timber.w("Resume ignored \u2014 download already running: $url")
            return
        }
        val paused = pausedDownloads.remove(url)
        if (paused == null) {
            Timber.w("Resume ignored \u2014 no paused download for: $url")
            checkAndStopService()
            return
        }
        // Re-assert tracking (kept across pause, but be defensive).
        downloadNotificationIds[url] = paused.notificationId
        downloadFilenames[url] = paused.uniqueFilename
        if (paused.pageTitle != null) downloadPageTitles[url] = paused.pageTitle
        downloadFormats[url] = paused.format
        // Flip the in-app card back to RUNNING (indeterminate) immediately.
        emitProgress(url, paused.uniqueFilename, -1, DownloadProgress.State.RUNNING)
        launchDownload(url, paused.uniqueFilename, paused.pageTitle, paused.notificationId, resume = true, format = paused.format)
    }

    /**
     * Pause a running download: destroy the native yt-dlp process but KEEP its `.part` file and all
     * tracking so [resumeDownload] can continue. The pause marker in [pausedDownloads] is set BEFORE
     * the process is destroyed so the coroutine's catch blocks treat the resulting exception as an
     * intentional pause rather than a failure or cancellation. No-op if nothing is actively running.
     */
    private fun pauseDownload(url: String) {
        val processId = downloadProcessIds[url]
        val notificationId = downloadNotificationIds[url]
        val uniqueFilename = downloadFilenames[url]
        if (processId == null || notificationId == null || uniqueFilename == null) {
            Timber.w("Pause ignored \u2014 no active download for: $url")
            return
        }
        Timber.i("Pausing download: $url")
        val lastPercent = lastProgressUpdate[url]?.first ?: 0
        val pageTitle = downloadPageTitles[url]
        val format = downloadFormats[url] ?: DownloadFormat.DEFAULT
        pausedDownloads[url] = PausedDownload(url, uniqueFilename, pageTitle, notificationId, lastPercent, format)
        // Reset throttle so the first progress line after resume isn't dropped.
        lastProgressUpdate.remove(url)
        // Remove from processIds FIRST so any buffered progress line is swallowed by the guard in
        // showProgressNotification / the emit callback and can't overwrite the paused state.
        downloadProcessIds.remove(url)
        emitProgress(url, uniqueFilename, lastPercent, DownloadProgress.State.PAUSED)
        showPausedNotification(notificationId, uniqueFilename, lastPercent, url)
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            Timber.e(e, "Error destroying process during pause: $processId")
        }
    }

    /**
     * Launch (or relaunch, when [resume] is true) the yt-dlp download coroutine for [url]. On resume
     * the SAME [uniqueFilename] / output template is reused so `--continue` resumes from the
     * existing `.part` file.
     */
    private fun launchDownload(
        url: String,
        uniqueFilename: String,
        pageTitle: String?,
        notificationId: Int,
        resume: Boolean,
        format: DownloadFormat
    ) {
        // Create job with CoroutineStart.LAZY to prevent race condition
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                Timber.i("${if (resume) "Resuming" else "Starting"} yt-dlp download: $url")
                
                // Ensure yt-dlp is initialized
                if (!ytDlpManager.isReady()) {
                    Timber.i("Initializing yt-dlp...")
                    ytDlpManager.ensureInitialized()
                }
                
                // For Android 10+ (API 29+), download to temp location first then publish via MediaStore
                // For Android 9 and below, write directly to the public directory
                val tempDownloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: cacheDir
                
                if (!tempDownloadDir.exists()) {
                    tempDownloadDir.mkdirs()
                }
                
                // Build output path template (yt-dlp will add extension)
                val outputTemplate = File(tempDownloadDir, uniqueFilename).absolutePath
                
                // Generate stable processId for this download to enable cancellation
                val processId = "ytdlp_${System.currentTimeMillis()}_${url.hashCode()}"
                downloadProcessIds[url] = processId
                
                // Variable to capture the actual output filepath from yt-dlp
                var actualOutputPath: String? = null
                
                // Create request with --print after_move:filepath to get exact output path
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist") // Download single video only
                    addOption("-o", outputTemplate) // Output template (temporary location)
                    addOption("--no-mtime") // Don't set file modification time
                    addOption("--continue") // Resume from an existing .part file if present (pause/resume)
                    addOption("--print", "after_move:filepath") // Print actual filepath after download
                    // TLS certificate verification enabled by default for security
                    // Do not add --no-check-certificate globally

                    // Apply the user-selected format (quality / audio-only). Each entry is the
                    // argument(s) for a single addOption call, matching yt-dlp's CLI syntax.
                    format.ytDlpOptions.forEach { option ->
                        when (option.size) {
                            1 -> addOption(option[0])
                            2 -> addOption(option[0], option[1])
                        }
                    }
                }
                
                Timber.d("yt-dlp command: ${request.buildCommand().joinToString(" ")}")
                Timber.d("yt-dlp processId: $processId")
                
                // Show initial notification with cancel action
                showProgressNotification(notificationId, uniqueFilename, 0, url)
                // Emit initial in-app progress (indeterminate until yt-dlp reports a real percent).
                // This is NOT gated by the notification permission — the whole point of the bus is
                // to give visible feedback even when POST_NOTIFICATIONS is denied.
                emitProgress(url, uniqueFilename, -1, DownloadProgress.State.RUNNING)
                
                // Execute download with progress callback and processId.
                // The callback params are (progress: Float 0..100, etaInSeconds: Long, line: String).
                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                    // Capture the filepath output from yt-dlp (printed by --print after_move:filepath)
                    // This line will contain the full path to the downloaded file
                    // Trim the line to remove any trailing newline that would break file validation
                    val trimmed = line.trim()
                    if (trimmed.startsWith("/") && File(trimmed).exists()) {
                        actualOutputPath = trimmed
                        Timber.i("Captured output filepath: $actualOutputPath")
                    }

                    // yt-dlp reports ETA directly (its second callback param). Speed isn't a
                    // callback param, so we parse it out of the human-readable progress line.
                    val speed = parseSpeedBytesPerSec(line)
                    val eta = if (etaInSeconds >= 0) etaInSeconds else -1L

                    // Update notification with progress + speed/ETA.
                    showProgressNotification(notificationId, uniqueFilename, progress.toInt(), url, speed, eta)

                    // Update the in-app progress card. Guard with the same cancellation check the
                    // notification uses so a buffered progress line can't overwrite a CANCELLED
                    // state. StateFlow conflation means the collector only sees the latest value.
                    if (downloadProcessIds.containsKey(url)) {
                        emitProgress(
                            url, uniqueFilename,
                            progress.toInt().coerceIn(0, 100),
                            DownloadProgress.State.RUNNING,
                            speedBytesPerSec = speed,
                            etaSeconds = eta
                        )
                    }
                    
                    Timber.v("Progress: $progress% (eta=${eta}s speed=${speed}B/s) - $line")
                }
                
                // Clean up processId after completion
                downloadProcessIds.remove(url)
                
                // Only proceed if we captured the actual output path from yt-dlp
                // Do not fall back to directory scanning to avoid wrong-file association
                if (actualOutputPath != null) {
                    val tempFile = File(actualOutputPath)
                    if (tempFile.exists()) {
                        // Publish the file to public Downloads
                        val publishedLocation = publishToDownloads(tempFile, url)
                        
                        if (publishedLocation != null) {
                            handleDownloadSuccess(url, publishedLocation, pageTitle, notificationId, uniqueFilename)
                        } else {
                            handleDownloadFailure(url, "Failed to publish file to Downloads", notificationId, uniqueFilename)
                        }
                        
                        // Clean up temp file
                        try {
                            if (tempFile.delete()) {
                                Timber.d("Cleaned up temp file: ${tempFile.absolutePath}")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to delete temp file: ${tempFile.absolutePath}")
                        }
                    } else {
                        handleDownloadFailure(url, "Downloaded file not found at captured path: $actualOutputPath", notificationId, uniqueFilename)
                    }
                } else {
                    handleDownloadFailure(url, "Failed to capture output filepath from yt-dlp", notificationId, uniqueFilename)
                }
                
            } catch (e: CancellationException) {
                if (pausedDownloads.containsKey(url)) {
                    // Intentional pause via job cancellation: keep the partial file and tracking.
                    Timber.i("Download paused (job cancelled), keeping partial: $url")
                    downloadProcessIds.remove(url)
                } else {
                    // Download was cancelled by user - notification already handled by cancelDownload
                    Timber.i("Download cancelled by user: $url")
                    downloadProcessIds.remove(url)
                    downloadNotificationIds.remove(url)
                }
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: YoutubeDLException) {
                if (pausedDownloads.containsKey(url)) {
                    // Process was destroyed by pauseDownload \u2014 intentional pause, not a failure.
                    // Keep the .part file and all tracking so resumeDownload can continue.
                    Timber.i("Download paused (process destroyed), keeping partial: $url")
                    downloadProcessIds.remove(url)
                } else {
                    // Check if this is an expected cancellation (process was destroyed)
                    val wasCancelled = !downloadProcessIds.containsKey(url)
                    if (wasCancelled) {
                        // Process was destroyed by cancelDownload - treat as cancellation, not failure
                        Timber.i("Download exception after process destruction (expected cancellation): $url")
                        downloadNotificationIds.remove(url)
                    } else {
                        // Genuine failure
                        Timber.e(e, "yt-dlp download failed: $url")
                        lastProgressUpdate.remove(url)
                        downloadProcessIds.remove(url)
                        downloadNotificationIds.remove(url)
                        handleDownloadFailure(url, e.message ?: "Download failed", notificationId, uniqueFilename)
                    }
                }
            } catch (e: Exception) {
                if (pausedDownloads.containsKey(url)) {
                    // Process was destroyed by pauseDownload \u2014 intentional pause, not a failure.
                    Timber.i("Download paused (process destroyed), keeping partial: $url")
                    downloadProcessIds.remove(url)
                } else {
                    // Check if this is an expected cancellation (process was destroyed)
                    val wasCancelled = !downloadProcessIds.containsKey(url)
                    if (wasCancelled) {
                        // Process was destroyed by cancelDownload - treat as cancellation, not failure
                        Timber.i("Exception after process destruction (expected cancellation): $url")
                        downloadNotificationIds.remove(url)
                    } else {
                        // Genuine error
                        Timber.e(e, "Unexpected error during download: $url")
                        lastProgressUpdate.remove(url)
                        downloadProcessIds.remove(url)
                        downloadNotificationIds.remove(url)
                        handleDownloadFailure(url, e.message ?: "Unexpected error", notificationId, uniqueFilename)
                    }
                }
            } finally {
                downloadJobs.remove(url)
                // Keep filename/pageTitle/format tracking when paused so resumeDownload can reuse them.
                if (!pausedDownloads.containsKey(url)) {
                    downloadFilenames.remove(url)
                    downloadPageTitles.remove(url)
                    downloadFormats.remove(url)
                }
                checkAndStopService()
            }
        }
        
        // Register job BEFORE starting it to prevent race with finally block
        downloadJobs[url] = job
        
        // Now start the job
        job.start()
    }
    
    private suspend fun handleDownloadSuccess(url: String, location: String, pageTitle: String?, notificationId: Int, filename: String) {
        Timber.i("Download completed successfully: $url")
        
        // Determine filename and size from the location
        var actualFilename: String
        var fileSize: Long
        
        if (location.startsWith("content://")) {
            // MediaStore content URI (Android 10+)
            val uri = Uri.parse(location)
            contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    actualFilename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                    fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE))
                } else {
                    actualFilename = "download"
                    fileSize = 0L
                }
            } ?: run {
                actualFilename = "download"
                fileSize = 0L
            }
        } else {
            // File path (Android 9 and below, or fallback)
            val file = File(location)
            actualFilename = file.name
            fileSize = file.length()
        }
        
        val humanReadableSize = Formatter.formatFileSize(this, fileSize)
        
        // Save to downloads repository - MUST subscribe to trigger the actual database insert
        // The repository returns a RxJava Single which is cold/lazy and won't execute without subscription
        // Store the location (content URI or file path) so it can be resolved later
        downloadsRepository.addDownloadIfNotExists(
            DownloadEntry(
                url = location, // Store location instead of original URL for resolution
                title = actualFilename, // Store actual filename with extension
                contentSize = humanReadableSize // Store human-readable size like "15.2 MB"
            )
        )
        .subscribeOn(databaseScheduler)
        .subscribe({ success ->
            if (!success) {
                Timber.w("Download entry already exists in database or insert failed")
            } else {
                Timber.d("Added download to repository: $actualFilename [$humanReadableSize]")
            }
        }, { error ->
            Timber.e(error, "Failed to add download to repository")
        })
        
        // Show completion notification (must be on main thread)
        withContext(Dispatchers.Main) {
            showSuccessNotification(notificationId, actualFilename, location)
        }

        // Emit COMPLETE to the in-app card regardless of notification permission.
        emitProgress(url, actualFilename, 100, DownloadProgress.State.COMPLETE, location = location)
        
        // Clean up notification tracking
        downloadNotificationIds.remove(url)
        
        // Alternate feedback path when notifications aren't available
        if (!hasNotificationPermission()) {
            Timber.i("Download success (no notification shown): $actualFilename saved to $location")
        }
    }
    
    private suspend fun handleDownloadFailure(url: String, error: String, notificationId: Int, filename: String) {
        // yt-dlp error messages can be very long (full Python traceback). Condense to something
        // user-readable while keeping the full detail in logs.
        val friendlyError = summarizeYtDlpError(error)
        // Emit ERROR to the in-app card regardless of notification permission.
        emitProgress(url, filename, -1, DownloadProgress.State.ERROR, message = friendlyError)
        withContext(Dispatchers.Main) {
            Timber.e("Download failed: $url - $error")
            
            // Show error notification
            showErrorNotification(notificationId, filename, friendlyError)
            
            // Alternate feedback path when notifications aren't available (or are disabled):
            // surface a Toast so the failure is never completely silent. Previously this path
            // only logged, so users saw no feedback at all when a download failed.
            if (!hasNotificationPermission()) {
                android.widget.Toast.makeText(
                    this@YtDlpDownloadService,
                    getString(R.string.video_download_failed, friendlyError),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Instance wrapper around [summarizeYtDlpError] using this service as the [Context].
     */
    private fun summarizeYtDlpError(error: String): String = summarizeYtDlpError(this, error)

    /**
     * Publish a download state change to [DownloadProgressBus] for the in-app progress card.
     * Deliberately independent of the notification-permission check so progress is visible even
     * when POST_NOTIFICATIONS is denied.
     */
    private fun emitProgress(
        url: String,
        filename: String,
        percent: Int,
        state: DownloadProgress.State,
        speedBytesPerSec: Long = -1L,
        etaSeconds: Long = -1L,
        message: String? = null,
        location: String? = null
    ) {
        DownloadProgressBus.update(
            DownloadProgress(
                url = url,
                filename = filename,
                percent = percent,
                state = state,
                speedBytesPerSec = speedBytesPerSec,
                etaSeconds = etaSeconds,
                message = message,
                location = location
            )
        )
    }
    
    private fun cancelDownload(url: String) {
        Timber.i("Cancelling download: $url")
        
        // Get the notification ID and filename before removing them from tracking
        val notificationId = downloadNotificationIds[url]
        val uniqueFilename = downloadFilenames[url] ?: url.substringAfterLast("/").take(50)
        
        // Clean up progress tracking
        lastProgressUpdate.remove(url)

        // Drop any pause marker so a cancel-while-paused fully tears down and lets the service stop.
        pausedDownloads.remove(url)
        downloadPageTitles.remove(url)
        downloadFormats.remove(url)
        // .part files are enabled for resume support, so remove any leftover partial on cancel.
        deletePartialFile(uniqueFilename)
        
        // Destroy the native yt-dlp process first (before removing from downloadProcessIds)
        val processId = downloadProcessIds[url]
        downloadProcessIds.remove(url) // Remove BEFORE destroying so exception handlers know it was cancelled
        
        if (processId != null) {
            try {
                Timber.d("Destroying yt-dlp process: $processId")
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (e: Exception) {
                Timber.e(e, "Error destroying process $processId")
            }
        }
        
        // Emit CANCELLED to the in-app card regardless of notification permission.
        emitProgress(url, uniqueFilename, -1, DownloadProgress.State.CANCELLED)

        // Cancel the coroutine job
        downloadJobs[url]?.cancel()
        downloadJobs.remove(url)
        
        // Handle UI cleanup: cancel the ongoing progress notification and show cancelled notification
        if (notificationId != null) {
            // Cancel the ongoing progress notification
            notificationManager.cancel(notificationId)
            
            // Show cancelled notification with the tracked filename (not URL-derived)
            showCancelledNotification(notificationId, uniqueFilename)
            
            // Clean up notification tracking
            downloadNotificationIds.remove(url)
        }
        
        // Clean up filename tracking
        downloadFilenames.remove(url)
        
        checkAndStopService()
    }
    
    private fun checkAndStopService() {
        // Keep the service alive while any download is running OR paused (awaiting resume).
        if (downloadJobs.isEmpty() && pausedDownloads.isEmpty()) {
            Timber.d("No active or paused downloads, stopping service")
            androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Delete any leftover partial (`.part`) file(s) for [uniqueFilename] in the temp download dir.
     * Called on cancellation now that `--continue` (and therefore `.part` files) is enabled, so a
     * cancelled download doesn't leave partial junk behind.
     */
    private fun deletePartialFile(uniqueFilename: String) {
        try {
            val tempDownloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
            tempDownloadDir.listFiles()?.forEach { f ->
                if (f.name.startsWith(uniqueFilename) && f.name.endsWith(".part")) {
                    if (f.delete()) Timber.d("Deleted partial file: ${f.name}")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete partial file for $uniqueFilename")
        }
    }
    
    /**
     * Check if POST_NOTIFICATIONS permission is granted (required on Android 13+)
     * Returns true on older Android versions or when permission is granted
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required on Android 12 and below
            true
        }
    }
    
    private fun sanitizeFilename(filename: String): String {
        // Remove or replace illegal characters
        return filename
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(200) // Limit length
    }
    
    /**
     * Publish the downloaded file to public Downloads folder.
     * On Android 10+ (API 29+), uses MediaStore for scoped storage.
     * On Android 9 and below, returns the file path directly.
     * 
     * @param tempFile The temporary downloaded file
     * @param url The original download URL  
     * @return The location string (content URI or file path) that can be resolved, or null on failure
     */
    private suspend fun publishToDownloads(tempFile: File, url: String): String? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore for scoped storage
                val filename = tempFile.name
                val mimeType = getMimeType(filename)
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1) // Mark as pending during write
                }
                
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val itemUri = contentResolver.insert(collection, contentValues)
                
                if (itemUri != null) {
                    // Write file content
                    contentResolver.openOutputStream(itemUri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    // Clear IS_PENDING flag to make file visible
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(itemUri, contentValues, null, null)
                    
                    Timber.i("Published file to MediaStore: $itemUri")
                    return@withContext itemUri.toString()
                } else {
                    Timber.e("Failed to create MediaStore entry for: $filename")
                    return@withContext null
                }
            } else {
                // Android 9 and below: Direct file access (but we're already in app-private storage)
                // For pre-Q devices, we could copy to Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)
                // but keeping in app-private storage is safer and still accessible via FileProvider
                Timber.i("Pre-Android 10: Keeping file in app-private storage: ${tempFile.absolutePath}")
                return@withContext tempFile.absolutePath
            }
        } catch (e: Exception) {
            Timber.e(e, "Error publishing file to Downloads")
            return@withContext null
        }
    }
    
    /**
     * Get MIME type for a file based on its extension
     */
    private fun getMimeType(filename: String): String {
        return when (filename.substringAfterLast('.', "").lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "m4v" -> "video/x-m4v"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "video/*" // Default to video
        }
    }
    
    // Notification methods
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of video downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.video_downloading))
        .setSmallIcon(R.drawable.ic_download_outline)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
    
    private fun showProgressNotification(
        notificationId: Int,
        filename: String,
        progress: Int,
        url: String,
        speedBytesPerSec: Long = -1L,
        etaSeconds: Long = -1L
    ) {
        // Guard against posting progress after cancellation
        // If the URL is no longer in downloadProcessIds, the download was cancelled
        // and a buffered progress line should not overwrite the cancelled notification
        if (!downloadProcessIds.containsKey(url)) {
            Timber.v("Skipping progress notification for cancelled download: $url")
            return
        }
        
        // Check notification permission before posting
        if (!hasNotificationPermission()) {
            Timber.w("POST_NOTIFICATIONS permission not granted, skipping progress notification")
            return
        }
        
        // Apply progress throttling: only update if percentage changed or interval elapsed
        val now = System.currentTimeMillis()
        val lastUpdate = lastProgressUpdate[url]
        
        if (lastUpdate != null) {
            val (lastPercent, lastTimeMs) = lastUpdate
            val percentChanged = progress != lastPercent
            val intervalElapsed = (now - lastTimeMs) >= PROGRESS_UPDATE_INTERVAL_MS
            
            // Skip update if neither condition is met
            if (!percentChanged && !intervalElapsed) {
                return
            }
        }
        
        // Record this update
        lastProgressUpdate[url] = Pair(progress, now)
        
        // Clamp progress to [0, 100] — yt-dlp reports -1 before it knows total file size
        val clampedProgress = progress.coerceIn(0, 100)
        
        // Create cancel intent
        val cancelIntent = Intent(this, YtDlpDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_URL, url)
        }
        
        val cancelPendingIntent = PendingIntent.getService(
            this,
            notificationId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause intent. Distinct request code so FLAG_UPDATE_CURRENT doesn't clobber the
        // cancel/resume PendingIntents that share this notification id.
        val pauseIntent = Intent(this, YtDlpDownloadService::class.java).apply {
            action = ACTION_PAUSE_DOWNLOAD
            putExtra(EXTRA_URL, url)
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            notificationId + PAUSE_REQUEST_OFFSET,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(filename)
            .setContentText(buildProgressText(clampedProgress, speedBytesPerSec, etaSeconds))
            .setSmallIcon(R.drawable.ic_download_outline)
            .setProgress(100, clampedProgress, clampedProgress == 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_pause,
                getString(R.string.download_action_pause),
                pausePendingIntent
            )
            .addAction(
                R.drawable.ic_action_delete, // Use existing close/delete icon
                getString(android.R.string.cancel),
                cancelPendingIntent
            )
            .build()
        
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Show the paused-state notification: a static (non-animated) progress bar frozen at [percent],
     * with Resume and Cancel actions. Unlike [showProgressNotification] this has no processId guard
     * because it is posted from [pauseDownload] after the processId has already been removed.
     */
    private fun showPausedNotification(notificationId: Int, filename: String, percent: Int, url: String) {
        if (!hasNotificationPermission()) {
            Timber.w("POST_NOTIFICATIONS permission not granted, skipping paused notification")
            return
        }
        val clamped = percent.coerceIn(0, 100)

        val resumeIntent = Intent(this, YtDlpDownloadService::class.java).apply {
            action = ACTION_RESUME_DOWNLOAD
            putExtra(EXTRA_URL, url)
        }
        val resumePendingIntent = PendingIntent.getService(
            this,
            notificationId + RESUME_REQUEST_OFFSET,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = Intent(this, YtDlpDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_URL, url)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            notificationId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(filename)
            .setContentText(getString(R.string.download_progress_paused, clamped))
            .setSmallIcon(R.drawable.ic_download_outline)
            .setProgress(100, clamped, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_play_arrow,
                getString(R.string.download_action_resume),
                resumePendingIntent
            )
            .addAction(
                R.drawable.ic_action_delete,
                getString(android.R.string.cancel),
                cancelPendingIntent
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * Build the notification content text for an in-progress download, e.g.
     * "Downloading… 45% • 2.5 MB/s • ETA 00:30". Speed and ETA segments are appended only when
     * they are known (>= 0); an unknown percent falls back to a plain "Starting…".
     */
    private fun buildProgressText(percent: Int, speedBytesPerSec: Long, etaSeconds: Long): String {
        val base = if (percent > 0) {
            getString(R.string.download_progress_percent, percent)
        } else {
            getString(R.string.download_progress_starting)
        }
        val parts = mutableListOf(base)
        if (speedBytesPerSec >= 0) {
            val speedStr = Formatter.formatShortFileSize(this, speedBytesPerSec)
            parts.add(getString(R.string.download_progress_speed, speedStr))
        }
        formatEta(etaSeconds)?.let { parts.add(getString(R.string.download_progress_eta, it)) }
        return parts.joinToString(" • ")
    }

    private fun showSuccessNotification(notificationId: Int, filename: String, location: String) {
        // Check notification permission before posting
        if (!hasNotificationPermission()) {
            Timber.w("POST_NOTIFICATIONS permission not granted, skipping success notification")
            return
        }
        
        // Create intent to open file - handle both content URIs and file paths
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (location.startsWith("content://")) {
                // MediaStore content URI (Android 10+)
                val uri = Uri.parse(location)
                setDataAndType(uri, getMimeType(filename))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                // File path (Android 9 and below)
                setDataAndType(
                    androidx.core.content.FileProvider.getUriForFile(
                        this@YtDlpDownloadService,
                        "${packageName}.fileprovider",
                        File(location)
                    ),
                    getMimeType(filename)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(filename)
            .setContentText(getString(R.string.video_download_complete))
            .setSmallIcon(R.drawable.ic_download_outline)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    private fun showErrorNotification(notificationId: Int, filename: String, error: String) {
        // Check notification permission before posting
        if (!hasNotificationPermission()) {
            Timber.w("POST_NOTIFICATIONS permission not granted, skipping error notification")
            return
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(filename)
            .setContentText(getString(R.string.video_download_failed, error))
            .setSmallIcon(R.drawable.ic_download_outline)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    private fun showCancelledNotification(notificationId: Int, filename: String) {
        // Check notification permission before posting
        if (!hasNotificationPermission()) {
            Timber.w("POST_NOTIFICATIONS permission not granted, skipping cancelled notification")
            return
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(filename)
            .setContentText(getString(R.string.video_download_cancelled))
            .setSmallIcon(R.drawable.ic_download_outline)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        
        // Clean up progress tracking
        lastProgressUpdate.clear()
        pausedDownloads.clear()
        downloadPageTitles.clear()
        downloadFormats.clear()
        
        // Clean up any remaining yt-dlp processes
        downloadProcessIds.keys.forEach { url ->
            val processId = downloadProcessIds[url]
            if (processId != null) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (e: Exception) {
                    Timber.e(e, "Error destroying process on service destroy: $processId")
                }
            }
        }
        downloadProcessIds.clear()
        
        Timber.d("Service destroyed")
    }
}
