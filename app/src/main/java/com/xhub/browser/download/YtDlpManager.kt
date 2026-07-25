package com.xhub.browser.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages yt-dlp initialization using the youtubedl-android library.
 *
 * This uses the yausername/youtubedl-android library which:
 * - Bundles a Python runtime compatible with Android's bionic libc
 * - Places binaries in nativeLibraryDir (executable location per Android security policy)
 * - Handles all platform compatibility issues
 * - Provides progress callbacks and proper Android integration
 *
 * Previous approach of bundling raw Linux binaries doesn't work because:
 * 1. Android 10+ (API 29+) W^X policy prevents execution from app data directory
 * 2. Official yt-dlp_linux_aarch64 is a glibc PyInstaller bundle incompatible with Android's bionic
 * 3. File.setExecutable() may succeed but ProcessBuilder.start() fails with EACCES
 *
 * The only reliable executable location on Android is applicationInfo.nativeLibraryDir,
 * which is exactly what youtubedl-android uses.
 */
@Singleton
class YtDlpManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var isInitialized = false

    /**
     * Coroutine-friendly mutual exclusion around initialization.
     *
     * Previously this class used a java.lang.Object monitor with wait/notify inside a
     * coroutine context (Dispatchers.IO). That blocked the underlying dispatcher thread,
     * which under load can cause thread pool starvation. Mutex.withLock suspends instead
     * of blocking, so the dispatcher thread is free to run other work while waiting.
     */
    private val mutex = Mutex()

    /**
     * The single in-flight initialization job, if any. All concurrent callers await the
     * same Deferred so the heavy YoutubeDL.init() runs exactly once. Null when no
     * initialization is in progress or it has already completed.
     */
    @Volatile
    private var initJob: CompletableDeferred<Unit>? = null

    /**
     * Initialize yt-dlp library if not already initialized.
     * This extracts the Python runtime and yt-dlp binary to nativeLibraryDir.
     *
     * Concurrency: Multiple concurrent calls share a single initialization attempt.
     * Coroutine-friendly — uses Mutex.withLock + CompletableDeferred rather than
     * blocking wait/notify, so dispatcher threads are not pinned while waiting.
     *
     * @throws YoutubeDLException if initialization fails
     */
    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (isInitialized) {
            return@withContext
        }

        // Decide under the lock whether this caller should perform the init work
        // or just join an in-flight job. The lock block returns:
        //   null            -> already initialized, caller can short-circuit
        //   non-null job    -> the shared job to await (and perform if we created it)
        var owner = false
        val job = mutex.withLock {
            if (isInitialized) {
                null
            } else {
                // If another caller already kicked off initialization, join its job.
                initJob?.let { it }
                    // Otherwise create the shared job and mark this caller as the owner.
                    ?: CompletableDeferred<Unit>().also {
                        initJob = it
                        owner = true
                    }
            }
        }

        // Another caller finished initialization before we grabbed the lock.
        if (job == null) {
            return@withContext
        }

        try {
            if (!owner) {
                // Joining an existing job — just await its result.
                job.await()
                return@withContext
            }

            Timber.i("Initializing youtubedl-android library...")

            // Initialize the library - this extracts Python runtime and yt-dlp
            // to context.applicationInfo.nativeLibraryDir which is executable
            YoutubeDL.getInstance().init(context.applicationContext)

            // Initialize ffmpeg too. Required for muxing separate video+audio streams (the `+`
            // format selectors used by the quality picker) and for -x audio extraction (mp3).
            // Without this, merged/height-capped and audio-only downloads fail at post-processing.
            FFmpeg.getInstance().init(context.applicationContext)

            Timber.i("youtubedl-android library initialized successfully")
            isInitialized = true
            job.complete(Unit)
        } catch (e: YoutubeDLException) {
            Timber.e(e, "Failed to initialize youtubedl-android library")
            job.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during initialization")
            val wrapped = YoutubeDLException("Unexpected error: ${e.message}", e)
            job.completeExceptionally(wrapped)
            throw wrapped
        } finally {
            // Clear the in-flight job once it has settled so a later failed init can retry.
            // Callers that arrive after completion will short-circuit on isInitialized above.
            // Only the owner clears initJob; joiners never touch it.
            if (owner) {
                mutex.withLock {
                    if (initJob === job) {
                        initJob = null
                    }
                }
            }
        }
    }

    /**
     * Check if yt-dlp is ready to use.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Update yt-dlp to the latest version.
     * Should be called periodically as platforms change their APIs.
     *
     * @throws YoutubeDLException if update fails
     */
    suspend fun updateYtDlp() = withContext(Dispatchers.IO) {
        try {
            Timber.i("Updating yt-dlp to latest version...")
            YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
            Timber.i("yt-dlp updated successfully")
        } catch (e: YoutubeDLException) {
            Timber.e(e, "Failed to update yt-dlp")
            throw e
        }
    }

    /**
     * Get yt-dlp version information.
     *
     * @return Version string or "Unknown" if it cannot be determined
     */
    suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            YoutubeDL.getInstance().version(context.applicationContext) ?: "Unknown"
        } catch (e: YoutubeDLException) {
            Timber.e(e, "Failed to get yt-dlp version")
            "Unknown"
        }
    }
}
