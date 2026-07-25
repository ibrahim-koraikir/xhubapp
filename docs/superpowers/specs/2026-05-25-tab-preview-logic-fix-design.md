# 2026-05-25 Tab Preview Logic Fix Design Spec

## Goal
Fix duplicate/stale tab previews in the tab switcher grid and ensure the home screen tab displays a correct native dashboard preview instead of nothing.

## Root Cause Analysis
1. **Duplicate/Repeated Previews:**
   - Previews are currently captured using `WebView.getDrawingCache(false)` synchronously on the active `WebViewEx` when switching tabs or opening the tab switcher.
   - Setting `isDrawingCacheEnabled = true` does not immediately build the cache. Under hardware acceleration (Android 8.0+), `getDrawingCache()` behaves unpredictably. It often returns a shared render buffer containing the visible foreground screen's content for *any* WebView, leading to all tabs displaying the same active foreground tab's preview.
   
2. **Missing Home Screen Preview:**
   - The modernized home screen is built natively inside `WebBrowserActivity` using the `homeScreenOverlay` layout overlay (`layout_home_screen.xml`), which is drawn on top of the web containers in the CoordinatorLayout z-order.
   - When the home screen is active, the tab's `webView` is hidden behind `homeScreenOverlay`. Capturing the `webView` drawing cache thus yields a blank preview.

## Proposed Architecture & Solution
To fix these issues safely and elegantly, we will update the lazy foreground-only capture workflow:

1. **Native View Capture for Home Screen:**
   - When `capturePreviewSync` is called on a tab, we check if the tab's URL matches a home/start/bookmark page.
   - If it is a home screen tab and `homeScreenOverlay` is currently visible (`activity.iBinding.homeScreenOverlay.visibility == View.VISIBLE`), we capture the native `homeScreenOverlay` view instead of the hidden `webView`.
   - Otherwise, we default to capturing the `webView`.

2. **Canvas Rendering instead of `getDrawingCache`:**
   - We will replace the deprecated and buggy `getDrawingCache` API with a direct software `Canvas` draw pass.
   - We will create a small target-sized `Bitmap` (`400`x`600` pixels, which requires exactly 960 KB of memory) and a `Canvas`.
   - We will scale the canvas so the View's contents fit perfectly in the target size, and call `view.draw(canvas)`.
   - This bypasses the shared render cache completely, ensuring that each View (WebView or native home screen overlay) renders its unique visual representation directly to the thumbnail bitmap.
   - Since we only allocate the small 960 KB bitmap and draw directly to it, this is extremely memory-efficient and 100% immune to the OOM crashes that the old full-resolution drawing cache copy caused.

## Verification Plan
1. Compile and package the app using gradle:
   `.\gradlew.bat assembleSlionsFullDownloadDebug`
2. Run JVM unit tests to ensure no regressions:
   `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
