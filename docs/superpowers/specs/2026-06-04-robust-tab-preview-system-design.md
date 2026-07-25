# Robust Tab Preview System Design Specification

## Overview
This specification details the architecture and implementation plan for resolving the tab switcher preview issues (blank thumbnails, wrong/duplicate thumbnails, memory leaks, and UI jank) in the Fulguris browser.

---

## Proposed Approaches

### Approach 1: Synchronous Fallback with Full In-Memory Binding (Simple)
* **Description:** Optimize the existing Canvas-based capture path without moving to asynchronous PixelCopy or disk caching. Unify binding by mapping the live cache directly to the ViewHolders.
* **Pros:** Low complexity, minimal files modified.
* **Cons:** Still suffers from blank thumbnails on hardware-accelerated WebViews. Causes main-thread jank during tab switcher transition due to synchronous draw calls and allocation. Bitmaps do not survive app restarts.

### Approach 2: Asynchronous PixelCopy, Disk Persistence, and Lightweight TabViewState (Recommended)
* **Description:** 
  1. Use asynchronous `PixelCopy.request` on API 26+ for real WebView content to avoid main-thread jank and blank/duplicate thumbnails.
  2. Implement an async disk-persistence cache in `TabThumbnailCache` (saving PNGs to a `tab_thumbnails` subfolder of cache directory) so thumbnails survive process restarts.
  3. Remove the heavy `preview: Bitmap?` reference from `TabViewState` to prevent memory leaks in the adapter/view holders; instead, diff items using a lightweight `previewVersion: Int` counter, and load the bitmap directly from the cache on bind.
  4. Guard against storing empty/all-white images in the cache.
  5. Re-introduce a post-load deferred capture for the foreground tab.
  6. Implement a global 2-second debounce for `capturePreviewSync`.
* **Pros:** Eliminates all memory leaks, UI jank, wrong thumbnails, and missing thumbnails. Survives app restarts.
* **Cons:** Larger change surface, modifying 8-9 files.

---

## Detailed Component Changes (Approach 2)

### 1. `TabThumbnailCache.kt`
* Maintain a thread-safe `versionMap` (`ConcurrentHashMap<Int, Int>`) mapping tab IDs to an integer version.
* Implement `getVersion(tabId: Int): Int`.
* On `put(tabId: Int, bitmap: Bitmap)`, increment the version and asynchronously write the compressed PNG bytes to `cacheDir/tab_thumbnails/$tabId.png`.
* On `get(tabId: Int)`, fallback to reading and decoding the file from disk if absent in the LRU memory cache.
* Handle cleanup on `remove` and `clear`.
* Safeguard against unit tests where `fulguris.app` global context may be uninitialized.

### 2. `TabViewState.kt`
* Drop `val preview: Bitmap? = null` property.
* Add `val previewVersion: Int = 0`.
* In `WebPageTab.asTabViewState()`, map `previewVersion = TabThumbnailCache.getVersion(id)`.

### 3. `TabsDrawerAdapter.kt`
* In `onBindViewHolder()`, retrieve the preview bitmap directly from the cache using `TabThumbnailCache.get(tab.id)`.
* Drop the copy call or ensure `holder.tab = tab.copy()` does not hold any bitmap references.

### 4. `WebPageTab.kt`
* Add `private var lastCaptureTime = 0L` to debounce. Return early in `capturePreviewSync` if captured < 2 seconds ago.
* Add `private var captureRunnable: Runnable? = null` to track post-load delayed runs.
* Implement `scheduleDeferredPreviewCapture()` using `webView?.postDelayed` (800ms delay) that executes only if the tab is still the foreground tab.
* On API 26+, use `PixelCopy.request` for WebViews:
  * Obtain location in window, create `srcRect`.
  * Pass callback that runs on Main Looper.
  * In the callback, verify the sequence number hasn't changed.
  * Check if the bitmap is empty or all-white. If valid, put in cache and notify the adapter.
* Fall back to `captureWithDrawingOptimized` (direct Canvas draw) for native home overlays or API < 26.
* Clean up sequence counter comments.

### 5. `WebPageClient.kt`
* Re-enable page-load completion hook: call `webPageTab.scheduleDeferredPreviewCapture()` inside `onPageFinished`.

### 6. `WebBrowserActivity.kt`
* In `openTabs()`, after triggering `capturePreviewSync()`, locate the recycler view's adapter and call `adapter.updateTabById(currentTab.id, currentTab.asTabViewState())` to force the current tab view holder to rebind immediately.

---

## Verification Plan

### Automated Verification
* Run existing `TabThumbnailCacheTest`.
* Run layout and navigation tests: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`.

### Manual Verification
* Open multiple home/start tabs and verify they do not show identical thumbnails.
* Scroll a content-heavy page, open the tab switcher, and verify that the thumbnail matches the current scrolled state.
* Terminate/restart the application and check that background/tab switcher thumbnails are preserved (disk cache test).
