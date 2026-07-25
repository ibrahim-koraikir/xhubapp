# Robust Tab Preview System Fixes Implementation Plan
Goal: Address 4 review comments regarding multi-session reconciliation, asynchronous disk loading, defensive bitmap copying, and KDoc updates.
Architecture: Decouple disk operations, use main-thread defensive copy for async compression, and load all session tab IDs for reconciliation.
Tech Stack: Hilt, Kotlin, Android SDK.
---

## Proposed Changes

### Task 1: TabThumbnailCache.kt Async Disk Read & Defensive Copy Write
File: [TabThumbnailCache.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabThumbnailCache.kt)

Implement:
1. `get` method:
   - Accept optional callback `onLoaded: ((Bitmap?) -> Unit)?`.
   - Maintain a thread-safe registry of pending callbacks for each tab ID to prevent duplicate reads.
   - Run disk decoding asynchronously on `diskExecutor` and use a Handler to post back to the main thread.
2. `put` method:
   - Create a defensive copy on the main thread using `bitmap.copy()`.
   - Compress and recycle the defensive copy in `diskExecutor`.

```kotlin
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

object TabThumbnailCache {

    const val TARGET_WIDTH_PX  = 400
    const val TARGET_HEIGHT_PX = 600

    private const val MAX_CACHE_BYTES = 20 * 1024 * 1024

    private val cache = object : LruCache<Int, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) Timber.d("Evicted thumbnail for tab $key from cache")
        }
    }

    private val versionMap = ConcurrentHashMap<Int, Int>()

    private val diskExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TabThumbnailCache-disk").apply { isDaemon = true }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val pendingCallbacks = ConcurrentHashMap<Int, MutableList<(Bitmap?) -> Unit>>()

    fun getVersion(tabId: Int): Int = versionMap[tabId] ?: 0

    private fun diskDir(): File? = try {
        File(fulguris.app.cacheDir, "tab_thumbnails")
    } catch (_: Exception) {
        null
    }

    /**
     * Retrieves the cached bitmap for [tabId].
     *
     * @param persistable When false, disk lookup is bypassed.
     * @param onLoaded Optional callback invoked asynchronously when the disk load completes.
     *                 Runs on the main UI thread.
     */
    fun get(tabId: Int, persistable: Boolean = true, onLoaded: ((Bitmap?) -> Unit)? = null): Bitmap? {
        val mem = cache.get(tabId)
        if (mem != null && !mem.isRecycled) return mem

        if (!persistable) return null

        if (onLoaded != null) {
            val list = pendingCallbacks.getOrPut(tabId) { mutableListOf() }
            synchronized(list) {
                list.add(onLoaded)
            }
        }

        // Check if a disk load task is already in progress
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

    fun put(tabId: Int, bitmap: Bitmap, persistable: Boolean = true) {
        if (bitmap.isRecycled) return
        cache.put(tabId, bitmap)
        versionMap[tabId] = (versionMap[tabId] ?: 0) + 1
        Timber.d("Cached thumbnail for tab $tabId (v${versionMap[tabId]}, cache ${cache.size() / 1024}KB / ${MAX_CACHE_BYTES / 1024}KB)")

        if (!persistable) return

        val dir = diskDir() ?: return
        val capturedId = tabId

        val defensiveCopy = try {
            if (!bitmap.isRecycled) {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy bitmap for tab $tabId before async compression")
            null
        } ?: return

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

    fun reconcile(liveIds: Set<Int>) {
        val dir = diskDir() ?: return
        diskExecutor.execute {
            try {
                val files = dir.listFiles() ?: return@execute
                var deleted = 0
                for (file in files) {
                    val idStr = file.nameWithoutExtension
                    val id = idStr.toIntOrNull()
                    if (id == null || id !in liveIds) {
                        file.delete()
                        deleted++
                    }
                }
                if (deleted > 0) Timber.d("reconcile: deleted $deleted orphaned thumbnail(s)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reconcile thumbnail cache")
            }
        }
    }

    fun getStats(): Pair<Int, Int> = Pair(cache.size(), MAX_CACHE_BYTES)
}
```

### Task 2: TabsDrawerAdapter.kt Async Binding Hook
File: [TabsDrawerAdapter.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabsDrawerAdapter.kt)

Bind ViewHolder asynchronously on memory miss and ensure tab ID validation:
```kotlin
        val cached = TabThumbnailCache.get(tab.id, persistable = !tab.isIncognito, onLoaded = { bitmap ->
            if (holder.tab?.id == tab.id) {
                updateViewHolderPreview(holder, bitmap)
            }
        })
        updateViewHolderPreview(holder, cached)
```

### Task 3: TabsManager.kt Global Multi-Session Reconciliation
File: [TabsManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/TabsManager.kt)

Collect tab IDs from ALL saved session bundles and pass the unified list to `reconcile()`:
```kotlin
        // Reconcile disk cache with all live tab IDs across all saved sessions
        val liveIds = mutableSetOf<Int>()
        allTabs.forEach { liveIds.add(it.id) }
        sessionsManager.sessions().forEach { session ->
            val filename = sessionsManager.fileNameFromSessionName(session.name)
            val bundle = fulguris.utils.FileUtils.readBundleFromStorage(application, filename)
            if (bundle != null) {
                try {
                    val tabKeys = bundle.keySet().filter { it.startsWith(TAB_KEY_PREFIX) }
                    tabKeys.forEach { key ->
                        bundle.getBundle(key)?.let { tabBundle ->
                            val id = tabBundle.getInt(TabModel.KEY_TAB_ID, -1)
                            if (id != -1) {
                                liveIds.add(id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read tab IDs from session ${session.name}")
                }
            }
        }
        fulguris.browser.tabs.TabThumbnailCache.reconcile(liveIds)
```

### Task 4: WebPageTab.kt KDoc Updates
File: [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt)

1. Merge KDoc above `scheduleDeferredPreviewCapture()`.
2. Update KDoc above `val id`.
