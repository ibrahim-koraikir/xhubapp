/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.browser.tabs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Centralized bounded cache for tab thumbnails using LRU eviction policy.
 *
 * This cache is BYTE-AWARE: [sizeOf] returns the actual byte count of each bitmap,
 * so Android evicts entries as soon as the total exceeds [MAX_CACHE_BYTES].
 * This prevents OOM crashes regardless of screen density (xhdpi, xxhdpi, xxxhdpi…).
 *
 * Thumbnail dimensions are fixed absolute PIXELS (not dp), so a bitmap always
 * costs exactly [TARGET_WIDTH_PX] × [TARGET_HEIGHT_PX] × 4 bytes ≈ 960 KB.
 * Budget: 20 MB → roughly 20 thumbnails at any one time.
 *
 * Each put() also asynchronously writes a JPEG to disk so thumbnails survive process restart.
 * On get() a cache miss falls back to reading from disk.
 *
 * A [versionMap] is maintained so callers can detect changes cheaply via an integer rather
 * than holding Bitmap references in view state objects.
 */
object TabThumbnailCache {

    /**
     * Fixed pixel dimensions for thumbnails – absolute pixels, NOT dp.
     * Callers MUST use these constants instead of a dp-scaled value so that
     * memory usage is predictable across all screen densities.
     *
     * 400 × 600 × 4 bytes (ARGB_8888) = 960 KB per thumbnail.
     */
    const val TARGET_WIDTH_PX  = 400
    const val TARGET_HEIGHT_PX = 600

    /** Total memory budget for all cached thumbnails (20 MB). */
    private const val MAX_CACHE_BYTES = 20 * 1024 * 1024

    private val cache = object : LruCache<Int, Bitmap>(MAX_CACHE_BYTES) {
        /** Return the actual byte size of the bitmap so the budget is respected. */
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) Timber.d("Evicted thumbnail for tab $key from cache")
        }
    }

    /**
     * Thread-safe version counter per tab ID.
     * Incremented on every successful [put] so [TabViewState.previewVersion] can be compared
     * by DiffUtil without holding a Bitmap reference.
     */
    private val versionMap = ConcurrentHashMap<Int, Int>()

    /** Background executor for all disk I/O — single thread ensures ordering. */
    private val diskExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TabThumbnailCache-disk").apply { isDaemon = true }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val pendingCallbacks = ConcurrentHashMap<Int, MutableList<(Bitmap?) -> Unit>>()

    /**
     * @return The current preview version for [tabId], or 0 if none has been stored yet.
     */
    fun getVersion(tabId: Int): Int = versionMap[tabId] ?: 0

    /**
     * Returns the cache directory used for disk persistence, or null if the app context
     * is not yet available (e.g. during unit tests).
     */
    private fun diskDir(): File? = try {
        File(fulguris.app.cacheDir, "tab_thumbnails")
    } catch (_: Exception) {
        null
    }

    /**
     * @return The cached bitmap for [tabId], or null if absent / already recycled.
     *
     * @param persistable When false (incognito tab) the disk fallback is skipped so we never
     *   read a persisted thumbnail that shouldn't exist for this session.
     * @param onLoaded Optional callback invoked asynchronously when the disk load completes.
     *                 Runs on the main UI thread.
     */
    fun get(tabId: Int, persistable: Boolean = true, onLoaded: ((Bitmap?) -> Unit)? = null): Bitmap? {
        val mem = cache.get(tabId)
        if (mem != null && !mem.isRecycled) return mem

        // Memory miss — try disk only for non-incognito tabs
        if (!persistable) return null

        if (onLoaded != null) {
            val list = pendingCallbacks.getOrPut(tabId) { mutableListOf() }
            synchronized(list) {
                list.add(onLoaded)
            }
        }

        // Check if a disk load task is already in progress for this tabId
        val callbacks = pendingCallbacks[tabId]
        if (callbacks != null && synchronized(callbacks) { callbacks.size > 1 }) {
            return null
        }

        val file = diskDir()?.let { File(it, "$tabId.jpg") } ?: return null
        if (!file.exists()) {
            pendingCallbacks.remove(tabId)
            return null
        }

        diskExecutor.execute {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                mainHandler.post {
                    if (bitmap != null) {
                        cache.put(tabId, bitmap)
                        if (versionMap[tabId] == null) versionMap[tabId] = 1
                        Timber.d("Loaded thumbnail for tab $tabId from disk asynchronously")
                    }
                    val list = pendingCallbacks.remove(tabId)
                    if (list != null) {
                        synchronized(list) {
                            for (cb in list) {
                                cb(bitmap)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read thumbnail for tab $tabId from disk asynchronously")
                mainHandler.post {
                    val list = pendingCallbacks.remove(tabId)
                    if (list != null) {
                        synchronized(list) {
                            for (cb in list) {
                                cb(null)
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Store [bitmap] in the cache for [tabId] (no-op if the bitmap is already recycled).
     *
     * @param persistable When false (incognito tab) the bitmap is kept in memory only and
     *   never written to disk, honouring the app's never-persist-in-incognito policy.
     */
    fun put(tabId: Int, bitmap: Bitmap, persistable: Boolean = true) {
        if (bitmap.isRecycled) return
        cache.put(tabId, bitmap)
        versionMap[tabId] = (versionMap[tabId] ?: 0) + 1
        Timber.d("Cached thumbnail for tab $tabId (v${versionMap[tabId]}, cache ${cache.size() / 1024}KB / ${MAX_CACHE_BYTES / 1024}KB)")

        if (!persistable) return  // incognito — memory only, no disk write

        val dir = diskDir() ?: return
        // Capture tabId for the lambda (avoids closure over mutable outer scope)
        val capturedId = tabId

        // Create a defensive copy on the main thread to snapshot the bitmap's current pixel
        // data safely and avoid concurrent recycling issues during background execution.
        val defensiveCopy = try {
            if (!bitmap.isRecycled) {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create defensive copy of bitmap for tab $tabId")
            null
        } ?: return

        // All compression and file I/O runs on the background thread to keep the main thread free.
        diskExecutor.execute {
            try {
                val bos = ByteArrayOutputStream()
                if (!defensiveCopy.isRecycled) {
                    defensiveCopy.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    val bytes = bos.toByteArray()
                    if (!dir.exists()) dir.mkdirs()
                    FileOutputStream(File(dir, "$capturedId.jpg")).use { it.write(bytes) }
                    Timber.d("Saved thumbnail for tab $capturedId to disk (${bytes.size / 1024}KB)")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to write thumbnail for tab $capturedId to disk")
            } finally {
                defensiveCopy.recycle()
            }
        }
    }

    /** Remove the cached thumbnail for [tabId]. */
    fun remove(tabId: Int) {
        cache.remove(tabId)
        versionMap.remove(tabId)
        pendingCallbacks.remove(tabId)
        Timber.d("Removed thumbnail for tab $tabId from cache")
        val dir = diskDir() ?: return
        diskExecutor.execute {
            try { File(dir, "$tabId.jpg").delete() } catch (e: Exception) {
                Timber.e(e, "Failed to delete thumbnail file for tab $tabId")
            }
        }
    }

    /** Evict every cached thumbnail. */
    fun clear() {
        cache.evictAll()
        versionMap.clear()
        pendingCallbacks.clear()
        Timber.d("Cleared all thumbnails from cache")
        val dir = diskDir() ?: return
        diskExecutor.execute {
            try { dir.deleteRecursively() } catch (e: Exception) {
                Timber.e(e, "Failed to clear disk thumbnail cache")
            }
        }
    }

    /**
     * Asynchronously reconciles the disk cache against the set of live tab IDs across all sessions.
     * Runs entirely on [diskExecutor] to keep the startup/UI thread free from disk I/O.
     *
     * All session data must be **pre-snapshotted on the main thread** before calling this method.
     * The background thread must not read any shared mutable state (e.g. SessionsManager fields).
     *
     * @param liveCurrentIds Set of tab IDs currently loaded in the active session (already parsed).
     * @param otherSessions Immutable snapshot of (sessionName → bundleFilename) pairs for every
     *   session *other than* the currently-active one, captured on the main thread before this call.
     * @param application The Application context used for disk I/O via [FileUtils].
     */
    fun reconcileAsync(
        liveCurrentIds: Set<Int>,
        otherSessions: List<Pair<String, String>>,
        currentSessionName: String,
        application: android.app.Application
    ) {
        // Take an immutable copy of the current-session IDs so the lambda captures a plain Set.
        val currentIds = liveCurrentIds.toSet()
        // Take an immutable copy of the session snapshot so the lambda owns its data.
        val sessionsSnapshot = otherSessions.toList()

        diskExecutor.execute {
            try {
                val liveIds = mutableSetOf<Int>()
                liveIds.addAll(currentIds)

                // Read other sessions' bundles on the background thread.
                // We only skip the current session (its IDs are already in currentIds).
                for ((sessionName, filename) in sessionsSnapshot) {
                    if (sessionName == currentSessionName) continue // should not occur, defensive
                    val bundle = fulguris.utils.FileUtils.readBundleFromStorage(application, filename)
                    if (bundle != null) {
                        try {
                            val tabKeys = bundle.keySet().filter { it.startsWith("TAB_") }
                            for (key in tabKeys) {
                                bundle.getBundle(key)?.let { tabBundle ->
                                    val id = tabBundle.getInt("TAB_ID", -1)
                                    if (id != -1) liveIds.add(id)
                                }
                            }
                        } catch (e: Exception) {
                            // Session bundle unreadable — do NOT delete its thumbnails; preserve them.
                            Timber.w(e, "reconcileAsync: cannot read tab IDs from session '$sessionName'; skipping its thumbnails")
                            return@execute
                        }
                    }
                    // If bundle == null the file does not exist yet (new/empty session) — nothing to preserve.
                }

                // Delete on-disk thumbnails whose IDs are not referenced by any known session.
                val dir = diskDir() ?: return@execute
                val files = dir.listFiles() ?: return@execute
                var deleted = 0
                for (file in files) {
                    val id = file.nameWithoutExtension.toIntOrNull()
                    if (id == null || id !in liveIds) {
                        file.delete()
                        deleted++
                    }
                }
                if (deleted > 0) Timber.d("reconcileAsync: deleted $deleted orphaned thumbnail(s)")
            } catch (e: Exception) {
                Timber.e(e, "reconcileAsync: unexpected failure")
            }
        }
    }

    /** @return Pair of (used bytes, max bytes). */
    fun getStats(): Pair<Int, Int> = Pair(cache.size(), MAX_CACHE_BYTES)
}
