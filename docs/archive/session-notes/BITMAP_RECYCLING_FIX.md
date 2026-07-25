# Bitmap Recycling Safety Fix

## Problem
Preview bitmaps were being explicitly recycled while still potentially bound to ImageView instances in the RecyclerView. This created a race condition where:

1. A bitmap would be recycled in `WebPageTab`
2. The RecyclerView might still be holding a reference to that bitmap in an ImageView
3. When the RecyclerView tried to redraw, it would crash with "Cannot draw recycled bitmap"

## Root Cause
The code was calling `bitmap.recycle()` in multiple places:
- `invalidatePreview()` - when clearing the preview cache
- `destroyWebView()` - when destroying the tab
- Preview replacement paths - when capturing a new preview

Additionally, recycled ViewHolders in the RecyclerView were not clearing their ImageView references, allowing stale bitmap references to persist.

## Solution

### 1. Removed Explicit Bitmap Recycling in WebPageTab.kt

Changed from explicitly recycling bitmaps to just clearing references, letting Android's lifecycle and garbage collection handle reclamation safely:

**Before:**
```kotlin
fun invalidatePreview() {
    cachedPreview?.recycle()
    cachedPreview = null
}
```

**After:**
```kotlin
fun invalidatePreview() {
    // Don't recycle - let lifecycle/caching ownership handle reclamation
    cachedPreview = null
}
```

Applied the same pattern in:
- `destroyWebView()` - removed `cachedPreview?.recycle()`
- `captureWithPixelCopy()` - removed `cachedPreview?.recycle()` before assignment
- `captureWithDrawing()` - removed `cachedPreview?.recycle()` before assignment

### 2. Clear ImageView References in TabsAdapter.kt

Added cleanup in `onViewRecycled()` to clear the preview ImageView when a ViewHolder is recycled:

**Before:**
```kotlin
override fun onViewRecycled(holder: TabViewHolder) {
    super.onViewRecycled(holder)
    holder.tab = null
}
```

**After:**
```kotlin
override fun onViewRecycled(holder: TabViewHolder) {
    super.onViewRecycled(holder)
    // Clear preview ImageView to prevent stale bitmap references
    holder.preview?.setImageDrawable(null)
    holder.tab = null
}
```

## Benefits

1. **No more recycled bitmap crashes** - Bitmaps are never recycled while potentially in use
2. **Proper lifecycle management** - Android's garbage collector handles bitmap reclamation when no references remain
3. **Cleaner RecyclerView recycling** - ViewHolders don't retain stale bitmap references
4. **Memory safety** - References are cleared explicitly, allowing GC to work properly

## Files Modified
- `app/src/main/java/fulguris/view/WebPageTab.kt` - Removed all explicit `recycle()` calls
- `app/src/main/java/fulguris/browser/tabs/TabsAdapter.kt` - Added ImageView cleanup in `onViewRecycled()`

## Build Status
✅ Build completed successfully: `./gradlew assembleSlionsFullFdroidDebug`

## Technical Notes

The Android documentation recommends letting the system handle bitmap recycling in most cases. Explicit recycling should only be done when you're absolutely certain no references exist. In a RecyclerView scenario with shared bitmaps across multiple tabs, it's safer to rely on garbage collection.

The key insight is that `cachedPreview` is a shared reference - multiple `TabViewState` objects and ImageViews might reference the same bitmap. Recycling it prematurely causes crashes when other components try to use it.
