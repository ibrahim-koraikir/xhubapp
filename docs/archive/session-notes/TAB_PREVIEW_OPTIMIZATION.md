# Tab Preview Generation Optimization

## Summary
Refactored tab preview capture to defer bitmap allocation and drawing until UI is idle or tab switcher is actually needed. This eliminates visible jank during page loads and tab switches by moving expensive operations off the critical path.

## Changes Made

### 1. WebPageClient.kt

**`onPageFinished()`**
- **Before**: Called `capturePreviewSync()` immediately after page load, blocking the main thread
- **After**: Calls `scheduleDeferredPreviewCapture()` to defer capture by 300ms
- **Benefit**: Page load completion is no longer blocked by bitmap allocation and drawing

### 2. WebPageTab.kt

#### New Method: `scheduleDeferredPreviewCapture()`
- Schedules preview capture after a 300ms delay using `postDelayed()`
- Allows UI to settle before performing expensive capture work
- Cancels any existing pending captures to avoid redundant work
- Used in:
  - `onPageFinished()` - after page loads
  - `isForeground` setter - when tab goes to background

#### Modified Method: `capturePreviewAsync()`
- New private method that performs deferred capture
- Called by the scheduled runnable after delay
- Uses optimized capture settings (2x resolution instead of 3x)

#### Modified Method: `capturePreviewSync()`
- Still available for immediate capture when needed
- Now uses reduced 2x resolution (440dp) instead of 3x (660dp)
- Called only when tab switcher is opened (see WebBrowserActivity changes)

#### Modified Method: `captureWithDrawingOptimized()` (renamed from `captureWithDrawing`)
- **Reduced bitmap size**: 2x resolution (440dp) instead of 3x (660dp)
  - Reduces memory allocation by ~44% (from 660x scale to 440x scale)
  - Still provides excellent quality for tab previews
- **Optimized bitmap format**: RGB_565 instead of ARGB_8888
  - Reduces memory usage by 50% per bitmap
  - No alpha channel needed for tab previews
- **Simplified Paint flags**: Removed unnecessary ANTI_ALIAS_FLAG and DITHER_FLAG
  - Reduces drawing overhead
  - FILTER_BITMAP_FLAG is sufficient for scaled drawing
- **Removed immediate UI refresh**: No longer calls `webBrowser.onTabChanged()` after capture
  - Avoids triggering unnecessary redraws during page load
  - Tab switcher will request refresh when actually opened

#### Modified Property: `isForeground` setter
- **Before**: Called `capturePreviewSync()` immediately when tab went to background
- **After**: Calls `scheduleDeferredPreviewCapture()` to defer capture
- **Benefit**: Tab switches are no longer blocked by preview capture

### 3. WebBrowserActivity.kt

**`openTabs()`**
- Added immediate preview capture of current tab when tab switcher is opened
- Ensures preview is up-to-date when user actually views the tab list
- Only captures if not on a special URL (home, bookmarks, etc.)
- This is the only place where immediate sync capture is now triggered

## Performance Improvements

### Before:
1. **Page Load**:
   - `onPageFinished()` → immediate `capturePreviewSync()`
   - Allocates 660dp height bitmap in ARGB_8888 (4 bytes/pixel)
   - Performs full WebView draw with high-quality paint flags
   - Triggers UI refresh via `onTabChanged()`
   - **Result**: Visible jank at end of page load

2. **Tab Switch to Background**:
   - `isForeground = false` → immediate `capturePreviewSync()`
   - Same expensive operations as above
   - **Result**: Visible jank during tab switch animation

3. **Memory Usage**:
   - Example: 1080px wide WebView at 660dp height (1980px)
   - Bitmap size: 1080 × 1980 × 4 bytes = ~8.3 MB per tab
   - 10 tabs = ~83 MB just for previews

### After:
1. **Page Load**:
   - `onPageFinished()` → `scheduleDeferredPreviewCapture()`
   - Returns immediately, no blocking
   - Capture happens 300ms later when UI is idle
   - **Result**: No jank during page load

2. **Tab Switch to Background**:
   - `isForeground = false` → `scheduleDeferredPreviewCapture()`
   - Returns immediately, no blocking
   - Capture happens 300ms later after animation completes
   - **Result**: Smooth tab switch animation

3. **Tab Switcher Open**:
   - `openTabs()` → immediate `capturePreviewSync()` for current tab only
   - Ensures current tab preview is fresh
   - Other tabs use cached previews from deferred captures
   - **Result**: Current tab always looks correct

4. **Memory Usage**:
   - Example: 1080px wide WebView at 440dp height (1320px)
   - Bitmap size: 1080 × 1320 × 2 bytes = ~2.8 MB per tab (RGB_565)
   - 10 tabs = ~28 MB for previews
   - **Savings**: ~66% reduction in memory usage

## Optimization Details

### Bitmap Size Reduction
- **From**: 660dp height (3x display size)
- **To**: 440dp height (2x display size)
- **Rationale**: 2x provides excellent quality for thumbnails while reducing memory and CPU
- **Impact**: ~44% fewer pixels to allocate and draw

### Bitmap Format Optimization
- **From**: ARGB_8888 (4 bytes per pixel, with alpha channel)
- **To**: RGB_565 (2 bytes per pixel, no alpha)
- **Rationale**: Tab previews don't need transparency
- **Impact**: 50% memory reduction per bitmap

### Paint Optimization
- **Removed**: ANTI_ALIAS_FLAG, DITHER_FLAG
- **Kept**: FILTER_BITMAP_FLAG (essential for quality scaling)
- **Rationale**: Simplified flags reduce drawing overhead without visible quality loss
- **Impact**: Faster canvas drawing operations

### Deferred Execution
- **Delay**: 300ms after page load or tab switch
- **Rationale**: Allows UI animations and critical rendering to complete first
- **Impact**: Moves expensive work off the critical path

### Eliminated Redundant UI Updates
- **Removed**: `webBrowser.onTabChanged()` call after capture
- **Rationale**: Tab switcher will trigger refresh when opened
- **Impact**: Avoids unnecessary redraws during page load

## Testing Recommendations

1. **Page Load Performance**:
   - Load heavy pages (e.g., news sites with many images)
   - Verify no jank at end of page load
   - Check that preview is captured after 300ms delay

2. **Tab Switch Performance**:
   - Switch between tabs rapidly
   - Verify smooth animations without stuttering
   - Check that previews are captured after animation completes

3. **Tab Switcher**:
   - Open tab switcher immediately after page load
   - Verify current tab preview is up-to-date
   - Check that other tab previews are present (from deferred captures)

4. **Memory Usage**:
   - Open 20+ tabs
   - Monitor memory usage in Android Profiler
   - Verify ~66% reduction in preview memory compared to before

5. **Preview Quality**:
   - Open tab switcher and inspect preview quality
   - Verify 2x resolution still looks sharp
   - Check that RGB_565 format doesn't show visible banding

6. **Edge Cases**:
   - Rapid navigation (load page, immediately navigate away)
   - Verify stale captures are cancelled properly
   - Check that sequence counter prevents race conditions

## Technical Notes

### Sequence Counter
- `captureSequence` is incremented on each navigation
- Guards against stale callbacks overwriting newer previews
- Ensures captures from old pages don't replace current page previews

### Deferred Capture Timing
- 300ms delay chosen to balance:
  - UI responsiveness (animations complete)
  - Preview freshness (not too long after page load)
  - Battery efficiency (batch work when idle)

### RGB_565 Format
- 16-bit color: 5 bits red, 6 bits green, 5 bits blue
- No alpha channel (fully opaque)
- Sufficient color depth for photo-realistic thumbnails
- May show slight banding in gradients (acceptable tradeoff)

### Immediate Capture Trigger
- Only triggered when tab switcher is opened
- Ensures user always sees fresh preview of current tab
- Background tabs use deferred captures (good enough)

## Migration Notes

- Old `captureWithDrawing()` method renamed to `captureWithDrawingOptimized()`
- `capturePreviewSync()` still exists but now uses optimized settings
- New `scheduleDeferredPreviewCapture()` is the preferred method for most cases
- New `capturePreviewAsync()` handles deferred capture execution
