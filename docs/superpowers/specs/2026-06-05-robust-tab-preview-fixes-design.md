# Robust Tab Preview System Fixes Design

Design for addressing review feedback on the robust tab preview system.

## Proposed Changes

### 1. Multi-Session Reconciliation
- **Problem**: `reconcile()` deletes the on-disk previews of every session except the currently loaded one.
- **Solution**: We will load tab IDs of ALL persisted sessions (from `sessionsManager.sessions()`) on startup, and compile a single unified set of live IDs before calling `reconcile()`. This ensures that thumbnails for inactive sessions are preserved.
- **Files**: `TabsManager.kt`

### 2. Asynchronous Disk Loading in Tab Drawer
- **Problem**: Decoding JPEGs from disk runs synchronously on the main thread during ViewHolder binding.
- **Solution**: Update `TabThumbnailCache.get()` to return `null` immediately on memory cache miss and accept an optional callback `onLoaded: ((Bitmap?) -> Unit)?`. If provided, get a background thread from `diskExecutor` to decode the image and post the result to the main thread, triggering the callback.
- **ViewHolder Reuse Safety**: In `TabsDrawerAdapter.onBindViewHolder()`, check if the ViewHolder is still bound to the same `tab.id` when the callback runs.
- **Files**: `TabThumbnailCache.kt`, `TabsDrawerAdapter.kt`

### 3. Concurrency Safety on Bitmap compression
- **Problem**: Passing a live Bitmap reference to `diskExecutor` opens a race condition where the bitmap could be recycled before compression.
- **Solution**: Create a defensive copy via `bitmap.copy(bitmap.config, false)` on the main thread, pass this copy to `diskExecutor`, perform the compression/write, and recycle the copy in the `finally` block of the background task.
- **Files**: `TabThumbnailCache.kt`

### 4. KDoc Clarity
- **Problem**: Stacked KDocs on `scheduleDeferredPreviewCapture` and stale "unique ID of the view" comment.
- **Solution**: Merge and correct the KDoc blocks on `scheduleDeferredPreviewCapture()`, and update KDoc on `id` to specify that it is a persistent logical tab key.
- **Files**: `WebPageTab.kt`
