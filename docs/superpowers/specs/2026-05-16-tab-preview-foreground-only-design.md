# Foreground-Only Tab Preview Design

## Goal
Eliminate wrong/glitched tab previews and prevent memory crashes by completely disabling asynchronous background capture. We will only capture thumbnails for the foreground tab exactly when the user switches away from it or opens the tab drawer.

## Current State & Problem
- Previews are captured asynchronously in `onPageFinished` using `WebView.getDrawingCache()`.
- For background tabs, `getDrawingCache()` behaves unpredictably. It often grabs a screenshot of the *currently visible* screen (the foreground tab) rather than its own hidden content.
- This causes background tabs to wrongly display the foreground tab's image.
- If it fails to grab anything, it returns null, causing missing images.

## Architecture & Proposed Changes
**Approach: Foreground-Only Lazy Capture**

1. **Remove Background Capture:**
   - Remove `scheduleDeferredPreviewCapture` entirely from `WebPageTab.kt`.
   - Remove the call to `scheduleDeferredPreviewCapture` from `WebPageClient.onPageFinished`.
   - Result: Background tabs will no longer attempt to screenshot themselves.

2. **Reliable Foreground Capture:**
   - `capturePreviewSync` is already invoked securely when the `TabsManager` is accessed or the drawer opens. This guarantees the *current* tab gets a fresh screenshot.
   - We will ensure that when a tab is pushed to the background, its last foreground state is securely locked in the `TabThumbnailCache`.
   - We will still remove the cache via `invalidatePreview()` when a tab begins navigating (`onPageStarted`), ensuring old images are cleared.

3. **Fallback UI Handling:**
   - In `TabsDrawerAdapter.kt`, the fallback logic (`R.drawable.ic_explore_outline` with 0.3f alpha) is already implemented. It will naturally take over for any tabs opened in the background until the user actively switches to them.
   - We will confirm that `TabViewState` correctly utilizes the fallback.

## Trade-offs
- **Pros:** 100% stability. Zero wrong images. Zero OOMs from background capturing.
- **Cons:** If you use "Open in background", that new tab will not have a screenshot in the tab switcher until you visit it for the first time. It will just show the placeholder.

## Next Steps
1. User approves this design.
2. Generate the implementation plan.
3. Write TDD tests to ensure deferred capture is removed and cache is handled correctly.
