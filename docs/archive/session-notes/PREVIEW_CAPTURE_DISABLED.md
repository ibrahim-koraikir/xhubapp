# Tab Preview Capture - TEMPORARILY DISABLED

## Problem
The app continues to crash when clicking the tabs button, even after reverting the session restoration optimization. The crash appears to be related to the tab preview capture functionality.

## Root Cause
The tab preview optimization (Task 2) that deferred preview capture may be causing crashes due to:
1. Bitmap allocation issues during preview capture
2. WebView drawing issues when capturing previews
3. Timing issues with deferred capture callbacks
4. Memory pressure from RGB_565 bitmap creation

## Solution
Temporarily disabled all tab preview capture functionality to isolate the crash:
1. Disabled immediate preview capture in `openTabs()`
2. Disabled deferred preview capture in `onPageFinished()`
3. Disabled deferred preview capture when tab goes to background

## Changes Made

### 1. WebBrowserActivity.kt - openTabs()
**Disabled immediate preview capture**:

```kotlin
// Capture preview of current tab immediately when tab switcher is opened
// This ensures the preview is up-to-date when the user sees the tab list
// TEMPORARILY DISABLED - may be causing crashes
/*
tabsManager.currentTab?.let { tab ->
    if (!tab.url.isSpecialUrl()) {
        tab.capturePreviewSync()
    }
}
*/
```

### 2. WebPageClient.kt - onPageFinished()
**Disabled deferred preview capture after page load**:

```kotlin
// Defer preview capture until UI is idle to avoid blocking during page load
// Preview will be captured when tab switcher is opened or after a delay
// TEMPORARILY DISABLED - may be causing crashes
// webPageTab.scheduleDeferredPreviewCapture()
```

### 3. WebPageTab.kt - isForeground setter
**Disabled deferred preview capture when tab goes to background**:

```kotlin
} else {
    // Tab is going to background - schedule deferred preview capture
    // But only if we're not on a special page (home, bookmarks, etc.)
    // TEMPORARILY DISABLED - may be causing crashes
    /*
    if (!url.isSpecialUrl()) {
        scheduleDeferredPreviewCapture()
    }
    */
    // A tab sent to the background is not so new anymore
    iIntent = null
    activity.runOnUiThread { hideDownloadFab() }
}
```

## Impact

### User Experience
- **No tab previews**: Tab list will not show preview thumbnails
- **Faster tab switching**: No time spent capturing previews
- **Reduced memory usage**: No bitmap allocations for previews
- **App stability**: Should eliminate crashes when opening tabs view

### Technical
- Preview capture code remains in place but is commented out
- Can be easily re-enabled once root cause is identified
- All preview-related methods are still present

## Testing Recommendations

1. **Basic Tab Operations**:
   - Open app
   - Click tabs button - should NOT crash
   - Tab list should open (without previews)
   - Switch between tabs - should work

2. **Multiple Tabs**:
   - Open several tabs
   - Navigate to different sites
   - Click tabs button
   - Should show tab list without previews

3. **Memory Usage**:
   - Monitor memory usage
   - Should be lower without preview bitmaps
   - No memory leaks from bitmap allocations

## Next Steps

If disabling preview capture fixes the crash:
1. Investigate the preview capture implementation
2. Check for issues with:
   - Bitmap.Config.RGB_565 compatibility
   - WebView.draw() on certain devices
   - Canvas operations
   - Memory allocation
3. Consider alternative preview capture approaches:
   - Use ARGB_8888 instead of RGB_565
   - Reduce preview resolution further
   - Add try-catch around bitmap operations
   - Check WebView state before capturing

If crash persists:
1. The issue is not related to preview capture
2. Need to investigate other recent changes:
   - Tab metadata update optimization
   - Session save serialization
   - Max tab entitlement enforcement
   - LinkedHashSet change

## Files Modified
- `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\activity\WebBrowserActivity.kt`
- `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageClient.kt`
- `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageTab.kt`

## Verification
All modified files compile without errors (verified with getDiagnostics).
