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

    /** @return The cached bitmap for [tabId], or null if absent / already recycled. */
    fun get(tabId: Int): Bitmap? {
        val mem = cache.get(tabId)
        if (mem != null && !mem.isRecycled) return mem

        // Memory miss — try disk
        val file = diskDir()?.let { File(it, "$tabId.jpg") } ?: return null
        if (!file.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                cache.put(tabId, bitmap)
                if (versionMap[tabId] == null) versionMap[tabId] = 1
                Timber.d("Loaded thumbnail for tab $tabId from disk")
            }
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to read thumbnail for tab $tabId from disk")
            null
        }
    }

    /** Store [bitmap] in the cache for [tabId] (no-op if the bitmap is already recycled). */
    fun put(tabId: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        cache.put(tabId, bitmap)
        versionMap[tabId] = (versionMap[tabId] ?: 0) + 1
        Timber.d("Cached thumbnail for tab $tabId (v${versionMap[tabId]}, cache ${cache.size() / 1024}KB / ${MAX_CACHE_BYTES / 1024}KB)")

        // Async JPEG write — compress on calling thread to snapshot current pixel data safely
        val dir = diskDir() ?: return
        try {
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            val bytes = bos.toByteArray()
            diskExecutor.execute {
                try {
                    if (!dir.exists()) dir.mkdirs()
                    FileOutputStream(File(dir, "$tabId.jpg")).use { it.write(bytes) }
                    Timber.d("Saved thumbnail for tab $tabId to disk (${bytes.size / 1024}KB)")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to write thumbnail for tab $tabId to disk")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to compress thumbnail for tab $tabId")
        }
    }

    /** Remove the cached thumbnail for [tabId]. */
    fun remove(tabId: Int) {
        cache.remove(tabId)
        versionMap.remove(tabId)
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
        Timber.d("Cleared all thumbnails from cache")
        val dir = diskDir() ?: return
        diskExecutor.execute {
            try { dir.deleteRecursively() } catch (e: Exception) {
                Timber.e(e, "Failed to clear disk thumbnail cache")
            }
        }
    }

    /** @return Pair of (used bytes, max bytes). */
    fun getStats(): Pair<Int, Int> = Pair(cache.size(), MAX_CACHE_BYTES)
}

