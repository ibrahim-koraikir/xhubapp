# Robust Tab Preview System Implementation Plan
Goal: Build a highly robust, performant, and memory-safe tab switcher preview system.
Architecture: Use asynchronous PixelCopy for WebView captures, disk-persisted JPEG caching to survive process restarts, and a lightweight view state representation using an integer version counter to prevent memory leaks and trigger DiffUtil updates.
Tech Stack: Android View system, PixelCopy API, Android LruCache, File I/O.
---

## Tasks

### Task 1: Update TabViewState and TabThumbnailCache
* **Files:**
  * [MODIFY] [TabViewState.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabViewState.kt)
  * [MODIFY] [TabThumbnailCache.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabThumbnailCache.kt)
* **Description:**
  1. Remove `preview: Bitmap?` from `TabViewState` and replace it with `previewVersion: Int`.
  2. Implement thread-safe version tracking in `TabThumbnailCache` with disk serialization to `cacheDir/tab_thumbnails/$tabId.jpg`.
  3. Ensure `TabThumbnailCache` handles uninitialized `fulguris.app` state gracefully during unit tests.

#### Complete Code Changes (Task 1)

##### [MODIFY] `app/src/main/java/fulguris/browser/tabs/TabViewState.kt`
```kotlin
package fulguris.browser.tabs

import fulguris.view.WebPageTab
import android.graphics.Bitmap
import android.graphics.Color
import timber.log.Timber

/**
 * @param id The unique id of the tab.
 * @param title The title of the tab.
 * @param favicon The favicon of the tab.
 * @param isForeground True if the tab is in the foreground, false otherwise.
 * @param previewVersion The version/token used for diffing when preview content changes.
 */
data class TabViewState(
    val id: Int = 0,
    val title: String = "",
    val favicon: Bitmap = createDefaultBitmap(),
    val isForeground: Boolean = false,
    val themeColor: Int = Color.TRANSPARENT,
    val isFrozen: Boolean = true,
    val previewVersion: Int = 0
) {
    init {
        // TODO: This is called way too many times from displayTabs() through asTabViewState
        // Find a way to improve this
        //Timber.v("init")
    }
}

/**
 * We used a function to be able to log
 */
private fun createDefaultBitmap() : Bitmap {
    Timber.w("createDefaultBitmap - ideally that should never be called")
    return Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
}


/**
 * Converts a [WebPageTab] to a [TabViewState].
 */
fun WebPageTab.asTabViewState() = TabViewState(
    id = id,
    title = title,
    favicon = favicon,
    isForeground = isForeground,
    themeColor = htmlMetaThemeColor,
    isFrozen = isFrozen,
    previewVersion = TabThumbnailCache.getVersion(id)
)
```

##### [MODIFY] `app/src/main/java/fulguris/browser/tabs/TabThumbnailCache.kt`
```kotlin
package fulguris.browser.tabs

import android.graphics.Bitmap
import android.util.LruCache
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory

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

    private val versionMap = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /** Get the current preview version of [tabId]. */
    fun getVersion(tabId: Int): Int = versionMap[tabId] ?: 0

    private fun getDirectory(): File? {
        val context = try {
            fulguris.app
        } catch (e: Exception) {
            null
        }
        return context?.cacheDir?.let { File(it, "tab_thumbnails") }
    }

    /** @return The cached bitmap for [tabId], or null if absent / already recycled. */
    fun get(tabId: Int): Bitmap? {
        val cached = cache.get(tabId)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val dir = getDirectory() ?: return null
        try {
            val file = File(dir, "$tabId.jpg")
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    cache.put(tabId, bitmap)
                    if (versionMap[tabId] == null) {
                        versionMap[tabId] = 1
                    }
                    Timber.d("Loaded thumbnail for tab $tabId from disk")
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read thumbnail for tab $tabId from disk")
        }

        return null
    }

    /** Store [bitmap] in the cache for [tabId] (no-op if the bitmap is already recycled). */
    fun put(tabId: Int, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(tabId, bitmap)
            versionMap[tabId] = (versionMap[tabId] ?: 0) + 1
            Timber.d("Cached thumbnail for tab $tabId (version ${versionMap[tabId]})")

            val dir = getDirectory()
            if (dir != null) {
                saveToDiskAsync(dir, tabId, bitmap)
            }
        }
    }

    private fun saveToDiskAsync(dir: File, tabId: Int, bitmap: Bitmap) {
        try {
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            val bytes = bos.toByteArray()

            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                try {
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }
                    val file = File(dir, "$tabId.jpg")
                    FileOutputStream(file).use { fos ->
                        fos.write(bytes)
                    }
                    Timber.d("Saved thumbnail for tab $tabId to disk")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to write thumbnail for tab $tabId to disk")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to compress thumbnail for tab $tabId for disk save")
        }
    }

    /** Remove the cached thumbnail for [tabId]. */
    fun remove(tabId: Int) {
        cache.remove(tabId)
        versionMap.remove(tabId)
        val dir = getDirectory() ?: return
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                val file = File(dir, "$tabId.jpg")
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete thumbnail file for tab $tabId")
            }
        }
    }

    /** Evict every cached thumbnail. */
    fun clear() {
        cache.evictAll()
        versionMap.clear()
        val dir = getDirectory() ?: return
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                dir.deleteRecursively()
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear disk thumbnail cache")
            }
        }
    }

    /** @return Pair of (used bytes, max bytes). */
    fun getStats(): Pair<Int, Int> = Pair(cache.size(), MAX_CACHE_BYTES)
}
```

---

### Task 2: Modify TabsDrawerAdapter
* **Files:**
  * [MODIFY] [TabsDrawerAdapter.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabsDrawerAdapter.kt)
* **Description:** Update binding logic to fetch directly from `TabThumbnailCache.get(tab.id)` instead of referencing the heavy `tab.preview` (which has been removed).

#### Complete Code Changes (Task 2)

##### [MODIFY] `app/src/main/java/fulguris/browser/tabs/TabsDrawerAdapter.kt`
```kotlin
<<<<
        // Fetch preview directly from the WebPageTab using tab ID to avoid wrong thumbnails
        updateViewHolderPreview(holder, webPageTab?.getPreviewBitmap())
        // Update our copy so that we can check for changes then
        holder.tab = tab.copy();
====
        // Fetch preview directly from TabThumbnailCache using tab ID to avoid wrong thumbnails and OOMs
        updateViewHolderPreview(holder, TabThumbnailCache.get(tab.id))
        // Update our copy so that we can check for changes then
        holder.tab = tab.copy()
>>>>
```

---

### Task 3: Implement Asynchronous PixelCopy and Delayed Capture in WebPageTab
* **Files:**
  * [MODIFY] [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt)
* **Description:** 
  1. Add imports for `PixelCopy`, `Rect`, and `asTabViewState`.
  2. Implement global debounce logic (2 seconds) inside `capturePreviewSync()`.
  3. Introduce `scheduleDeferredPreviewCapture()` for deferred foreground captures.
  4. Use `PixelCopy` for real `WebView` content on API 26+, checking for empty/white screens before cache storage.
  5. Fallback to optimized canvas capture for native/special overlays.
  6. Call `notifyTabChanged()` when the async copy finishes successfully.

#### Complete Code Changes (Task 3)

##### [MODIFY] `app/src/main/java/fulguris/view/WebPageTab.kt`
(Add imports at the top)
```kotlin
import android.view.PixelCopy
import android.graphics.Rect
import fulguris.browser.tabs.asTabViewState
```

(Replace variable declarations at line 151)
```kotlin
<<<<
    @Volatile
    private var captureSequence = 0
====
    @Volatile
    private var captureSequence = 0

    private var lastCaptureTime = 0L
    private var captureRunnable: Runnable? = null
>>>>
```

(Replace `capturePreviewSync` and related capture methods)
```kotlin
<<<<
    fun capturePreviewSync() {
        val isHome = url.isHomeUri() || url.isStartPageUrl() || url.isBookmarkUri() || url.isBookmarkUrl()
        val viewToCapture: View? = if (isHome) {
            try {
                val browserActivity = activity as? WebBrowserActivity
                val overlay = browserActivity?.iBinding?.homeScreenOverlay
                if (overlay != null && overlay.visibility == View.VISIBLE) {
                    overlay
                } else {
                    webView
                }
            } catch (e: Exception) {
                webView
            }
        } else {
            webView
        }

        val view = viewToCapture ?: return
        
        // Cancel any pending capture task
        cancelPendingCapture()
        
        val currentSequence = captureSequence
        
        try {
            // Ignore views that are tiny or not fully laid out yet
            if (view.width < 100 || view.height < 100) {
                Timber.w("View has invalid dimensions (${view.width}x${view.height}), cannot capture preview")
                return
            }

            // Use fixed-pixel dimensions (not dp-scaled) so memory is predictable on every screen density.
            val targetWidth  = TabThumbnailCache.TARGET_WIDTH_PX
            val targetHeight = TabThumbnailCache.TARGET_HEIGHT_PX
            val scale = targetHeight.toFloat() / view.height.toFloat()

            Timber.d("Capturing preview sync (seq=$currentSequence, tab=$id): View=${view.width}x${view.height}, Target=${targetWidth}x${targetHeight}, Scale=$scale")

            captureWithDrawingOptimized(view, targetWidth, targetHeight, scale, currentSequence)
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture preview sync")
        }
    }

    /**
     * Cancel any pending preview capture operations.
     * Called when navigation starts to prevent stale captures.
     */
    fun cancelPendingCapture() {
        // Increment sequence to invalidate any in-flight captures
        
        // Increment sequence to invalidate any in-flight captures
        captureSequence++
        
        Timber.d("Cancelled pending capture (seq=$captureSequence)")
    }

    /**
     * Capture the View preview efficiently using a direct Canvas draw pass.
     *
     * WHY DIRECT CANVAS DRAW IS BETTER:
     * 1. Bypasses the deprecated and extremely buggy getDrawingCache() API. Under hardware acceleration,
     *    getDrawingCache() can return a shared graphics render buffer containing the active foreground screen,
     *    causing multiple tabs to display duplicate snapshots of the active tab.
     * 2. Draws the View (either a WebView or the native homeScreenOverlay layout) directly to a small, target-sized
     *    960 KB bitmap Canvas, completely avoiding huge full-resolution drawing cache memory OOMs.
     */
    private fun captureWithDrawingOptimized(view: View, targetWidth: Int, targetHeight: Int, scale: Float, expectedSequence: Int) {
        try {
            if (expectedSequence != captureSequence) return

            Timber.d("Capturing tab preview via canvas draw (tab=$id, target=${targetWidth}x${targetHeight})")

            // Allocate only the exact memory we need (960 KB per thumbnail)
            val scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(scaled)

            // Draw clean background to prevent transparency issues
            canvas.drawColor(Color.WHITE)

            // Render the specific View directly to the Canvas
            canvas.save()
            canvas.scale(scale, scale)
            view.draw(canvas)
            canvas.restore()

            TabThumbnailCache.put(id, scaled)
            Timber.d("Captured preview successfully: ${scaled.width}x${scaled.height}, ${scaled.byteCount / 1024}KB")

        } catch (e: Exception) {
            Timber.e(e, "Failed to capture preview for tab=$id")
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "OOM while capturing preview for tab=$id — skipping")
        }
    }
====
    fun scheduleDeferredPreviewCapture() {
        val browserActivity = activity as? WebBrowserActivity
        val isForeground = browserActivity?.tabsManager?.currentTab == this
        if (!isForeground) {
            Timber.d("scheduleDeferredPreviewCapture: skipping background tab=$id")
            return
        }

        cancelPendingCapture()

        val currentSequence = captureSequence
        val runnable = Runnable {
            val stillForeground = browserActivity?.tabsManager?.currentTab == this
            if (stillForeground && currentSequence == captureSequence) {
                capturePreviewSync()
            }
        }
        captureRunnable = runnable
        webView?.postDelayed(runnable, CAPTURE_DELAY_MS)
    }

    fun capturePreviewSync() {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < 2000L) {
            Timber.d("Skipping preview capture for tab=$id — captured less than 2s ago (debounce)")
            return
        }
        lastCaptureTime = now

        val isHome = url.isHomeUri() || url.isStartPageUrl() || url.isBookmarkUri() || url.isBookmarkUrl()
        val browserActivity = activity as? WebBrowserActivity
        val isForeground = browserActivity?.tabsManager?.currentTab == this

        val viewToCapture: View? = if (isHome) {
            if (!isForeground) {
                return
            }
            try {
                val overlay = browserActivity?.iBinding?.homeScreenOverlay
                if (overlay != null && overlay.visibility == View.VISIBLE) {
                    overlay
                } else {
                    webView
                }
            } catch (e: Exception) {
                webView
            }
        } else {
            webView
        }

        val view = viewToCapture ?: return
        
        cancelPendingCapture()
        
        val currentSequence = captureSequence
        
        try {
            if (view.width < 100 || view.height < 100) {
                Timber.w("View has invalid dimensions (${view.width}x${view.height}), cannot capture preview")
                return
            }

            val targetWidth  = TabThumbnailCache.TARGET_WIDTH_PX
            val targetHeight = TabThumbnailCache.TARGET_HEIGHT_PX
            val scale = targetHeight.toFloat() / view.height.toFloat()

            Timber.d("Capturing preview sync (seq=$currentSequence, tab=$id): View=${view.width}x${view.height}, Target=${targetWidth}x${targetHeight}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && view is WebView) {
                captureWithPixelCopy(view, targetWidth, targetHeight, currentSequence)
            } else {
                captureWithDrawingOptimized(view, targetWidth, targetHeight, scale, currentSequence)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture preview sync")
        }
    }

    fun cancelPendingCapture() {
        captureRunnable?.let {
            webView?.removeCallbacks(it)
            captureRunnable = null
        }
        captureSequence++
        Timber.d("Cancelled pending capture (seq=$captureSequence)")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun captureWithPixelCopy(view: WebView, targetWidth: Int, targetHeight: Int, expectedSequence: Int) {
        val window = activity.window ?: return
        val location = IntArray(2)
        view.getLocationInWindow(location)

        val srcRect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val destBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        try {
            PixelCopy.request(
                window,
                srcRect,
                destBitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        if (expectedSequence == captureSequence) {
                            if (!isBitmapEmptyOrAllWhite(destBitmap)) {
                                TabThumbnailCache.put(id, destBitmap)
                                Timber.d("Captured preview successfully via PixelCopy: ${destBitmap.width}x${destBitmap.height}")
                                notifyTabChanged()
                            } else {
                                Timber.d("PixelCopy result empty or all white for tab=$id — skipping cache")
                            }
                        } else {
                            Timber.d("PixelCopy finished but sequence changed for tab=$id")
                        }
                    } else {
                        Timber.w("PixelCopy failed for tab=$id with result: $result")
                    }
                },
                Handler(android.os.Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Timber.e(e, "PixelCopy request failed for tab=$id")
        }
    }

    private fun captureWithDrawingOptimized(view: View, targetWidth: Int, targetHeight: Int, scale: Float, expectedSequence: Int) {
        try {
            if (expectedSequence != captureSequence) return

            Timber.d("Capturing tab preview via canvas draw (tab=$id, target=${targetWidth}x${targetHeight})")

            val scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(scaled)
            canvas.drawColor(Color.WHITE)

            canvas.save()
            canvas.scale(scale, scale)
            view.draw(canvas)
            canvas.restore()

            if (!isBitmapEmptyOrAllWhite(scaled)) {
                TabThumbnailCache.put(id, scaled)
                Timber.d("Captured preview successfully: ${scaled.width}x${scaled.height}")
                notifyTabChanged()
            } else {
                Timber.d("DrawingOptimized result empty or all white for tab=$id — skipping cache")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to capture preview for tab=$id")
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "OOM while capturing preview for tab=$id — skipping")
        }
    }

    private fun isBitmapEmptyOrAllWhite(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true

        val samples = intArrayOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(width / 2, height / 2),
            bitmap.getPixel(width - 1, height - 1),
            bitmap.getPixel(width / 4, height / 4),
            bitmap.getPixel(3 * width / 4, 3 * height / 4),
            bitmap.getPixel(width / 2, height / 4),
            bitmap.getPixel(width / 2, 3 * height / 4),
            bitmap.getPixel(width / 4, height / 2),
            bitmap.getPixel(3 * width / 4, height / 2),
            bitmap.getPixel(width / 3, height / 3),
            bitmap.getPixel(2 * width / 3, 2 * height / 3),
            bitmap.getPixel(width / 5, height / 5),
            bitmap.getPixel(2 * width / 5, 2 * height / 5),
            bitmap.getPixel(3 * width / 5, 3 * height / 5),
            bitmap.getPixel(4 * width / 5, 4 * height / 5)
        )
        return samples.all { it == Color.WHITE || it == Color.TRANSPARENT || it == 0 || (it ushr 24) == 0 }
    }

    private fun notifyTabChanged() {
        (activity as? WebBrowserActivity)?.let { browserActivity ->
            browserActivity.runOnUiThread {
                val tabListView = (browserActivity.tabsView as? android.view.ViewGroup)?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.tabs_list)
                val adapter = tabListView?.adapter as? fulguris.browser.tabs.TabsAdapter
                adapter?.updateTabById(id, asTabViewState())
            }
        }
    }
>>>>
```

---

### Task 4: Re-enable Page-Load Completion Hook in WebPageClient
* **Files:**
  * [MODIFY] [WebPageClient.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageClient.kt)
* **Description:** Re-enable the deferred capture hook so thumbnails are automatically updated once a page finishes loading.

#### Complete Code Changes (Task 4)

##### [MODIFY] `app/src/main/java/fulguris/view/WebPageClient.kt`
```kotlin
<<<<
        // Capture a preview of the page once it's finished loading
        // We removed deferred capture to avoid background tabs grabbing foreground screenshots
        // webPageTab.scheduleDeferredPreviewCapture()
====
        // Capture a preview of the page once it's finished loading
        // Only for the foreground tab, after the page settles
        webPageTab.scheduleDeferredPreviewCapture()
>>>>
```

---

### Task 5: Rebind Current Tab in openTabs
* **Files:**
  * [MODIFY] [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt)
* **Description:** Trigger an immediate rebind for the current tab's ViewHolder when the tab switcher is opened to ensure it reflects the latest scrolled/rendered content.

#### Complete Code Changes (Task 5)

##### [MODIFY] `app/src/main/java/fulguris/activity/WebBrowserActivity.kt`
```kotlin
<<<<
        // Update the current tab's preview synchronously right before we open the switcher
        // so that it reflects the most recent scrolls and dynamic content changes.
        // This is safe now because captureWithDrawingOptimized uses half the memory it used to.
        tabsManager.currentTab?.capturePreviewSync()
====
        // Update the current tab's preview right before we open the switcher
        // so that it reflects the most recent scrolls and dynamic content changes.
        tabsManager.currentTab?.let { currentTab ->
            currentTab.capturePreviewSync()
            // Notify the adapter of the current tab update so it redraws the thumbnail
            val tabListView = (tabsView as? ViewGroup)?.findViewById<RecyclerView>(R.id.tabs_list)
            val adapter = tabListView?.adapter as? fulguris.browser.tabs.TabsAdapter
            adapter?.updateTabById(currentTab.id, currentTab.asTabViewState())
        }
>>>>
```

---

## Verification Plan

### Automated Tests
Run unit tests to ensure no regressions and compilation is correct:
```powershell
# Run TabThumbnailCacheTest and all unit tests
.\gradlew.bat testSlionsFullDownloadDebugUnitTest
```
Expected output: `BUILD SUCCESSFUL`.
