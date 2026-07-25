# Video Detection Persistence and Robustness Spec

Goal: Ensure that video detection works reliably across multiple video plays, page refreshes, subsequent site navigations, and dynamic video content changes (SPAs/lazy loading).

## Context & Problem
1. **State Persistence**: Once a video is detected, `isVideoDetected` is set to `true` and the FAB is shown. When the user clicks download, the FAB is hidden, but `isVideoDetected` remains `true` and is never cleared upon page refreshes or redirect navigations.
2. **Fragile Lifecycle Injection**: Currently, the JavaScript sniffer is only injected in `onPageFinished` of `WebPageClient`. If `onPageFinishedDone` is already `true` (due to intermediate redirects, caching, or multiple events), the sniffer injection is skipped on the final loaded page.
3. **Dynamic SPA swaps & Lazy Loading**: The current sniffer uses a MutationObserver that only watches for elements being added or removed. If a video player updates the `src` attribute of an existing `<video>` or `<source>` element without recreating the node, or if a video is lazy-loaded with `preload="none"`, the sniffer misses it.

## Proposed Changes

### 1. `app/src/main/java/com/xhub/browser/view/WebPageTab.kt`
- Make `clearVideoDetectedState()` public so that it can be called externally.
- Add `injectVideoSniffer()` function that handles injecting the script into the WebView, guarded by `window._vdInit` to prevent multiple initialization runs.
- The Javascript in `injectVideoSniffer()` will be updated to:
  - Add `if (window._vdInit) return; window._vdInit = true;` at the top.
  - Observe `src` attribute changes: `{ childList: true, subtree: true, attributes: true, attributeFilter: ['src'] }`.
  - Listen to additional HTML5 video events (`play`, `loadeddata`, `canplay`) to catch all player state changes.
  - Add a 2-second periodic fallback scanning interval (`setInterval`).

### 2. `app/src/main/java/com/xhub/browser/view/WebPageClient.kt`
- In `onPageStarted(...)`, call `webPageTab.clearVideoDetectedState()` to ensure navigation or refreshes reset the video state.
- In `onPageFinished(...)`, call `webPageTab.injectVideoSniffer()` instead of executing the inline script block directly.

### 3. `app/src/main/java/com/xhub/browser/view/WebPageChromeClient.kt`
- In `onProgressChanged(...)`, when `newProgress == 100`, call `webPageTab.injectVideoSniffer()` as a secondary injection point.

## Verification Plan

### Automated Build & Tests
- Compile the APK using:
  ```powershell
  taskkill /F /IM java.exe
  timeout /t 3
  .\gradlew.bat assembleXhubFullDownloadDebug
  ```
- Run unit tests:
  ```powershell
  .\gradlew.bat testXhubFullDownloadDebugUnitTest
  ```

### Manual Verification
- Deploy the app to the device.
- Play a video on a website, download it (FAB hides).
- Play a second video on the same site or click refresh, and verify that the download FAB is shown again.
- Navigate to another video site, play a video, and verify that the download FAB is shown.
