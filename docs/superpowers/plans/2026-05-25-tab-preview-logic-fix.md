# Tab Preview Logic Fix Implementation Plan
Goal: Fix duplicate tab previews and empty home screen previews in the tab switcher by implementing native home screen view capture and direct canvas rendering.
Architecture: Detect when home screen tab is active and capture native `homeScreenOverlay` instead of `webView`. Re-route `captureWithDrawingOptimized` from deprecated `getDrawingCache` to direct software `Canvas` draw pass.
Tech Stack: Kotlin, Android SDK, Android WebView.
---

## Proposed Changes

### Component: Tab Preview Capturing

#### [MODIFY] [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt)
- Add utility imports for checking home/start/bookmark URLs.
- Update `capturePreviewSync` to detect if the current URL is home/start/bookmark, and if so, capture the native `homeScreenOverlay` View instead of the hidden `webView`.
- Update `captureWithDrawingOptimized` signature to take `view: View` instead of `view: WebView`.
- Re-route `captureWithDrawingOptimized` to draw the View directly onto a small scaled `Canvas` backed by a 960KB `Bitmap` using `view.draw(canvas)`.

```kotlin
// In WebPageTab.kt imports:
import fulguris.utils.isHomeUri
import fulguris.utils.isStartPageUrl
import fulguris.utils.isBookmarkUri
import fulguris.utils.isBookmarkUrl

// Modify capturePreviewSync and captureWithDrawingOptimized inside WebPageTab:
```

## Detailed Tasks

### Task 1: Update WebPageTab Imports and View Detection logic

1. Open [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt).
2. Add imports for URL checking utility extensions.
3. Update `capturePreviewSync` view detection and pass it to `captureWithDrawingOptimized`.
4. Update `captureWithDrawingOptimized` implementation to draw the View directly to a Canvas.

### Task 2: Build and Verification

1. Clean and build the debug APK to verify compilation:
   `.\gradlew.bat clean assembleSlionsFullDownloadDebug`
2. Run JVM unit tests to ensure no regressions:
   `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
