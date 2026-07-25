# Direct-Link Ads Tweaks Implementation Plan
Goal: Improve direct-link ad loading speed and layout behavior by removing auto-dismiss, adding a loading spinner, and intercepting link navigation.
Architecture: We modify WebBrowser interface to return Boolean when a navigation is intercepted. WebPageClient returns true if intercepted. DirectLinkAdManager handles target URL caching and loads it via tabsManager upon dismissal.
Tech Stack: Android SDK, WebKit WebView.
---

## Proposed Changes

### 1. WebBrowser.kt
Modify interface `onUserGestureNavigation(url: String): Boolean` to return a `Boolean` representing whether the navigation was intercepted by an ad.

### 2. WebPageClient.kt
In `shouldOverrideUrlLoading`, if `onUserGestureNavigation(url)` returns `true`, return `true` to cancel the original load.

### 3. WebBrowserActivity.kt
In `onCreate`:
- Update `DirectLinkAdManager` instantiation to pass two separate tab opener callbacks:
  1. `launchTabOpener = { url -> tabsManager.newTab(UrlInitializer(url), false) }` (silent background tab)
  2. `interstitialTabOpener = { url -> tabsManager.loadUrlInCurrentView(url) }` (foreground tab load)
- Update implementation of `onUserGestureNavigation` to return `Boolean`.

### 4. DirectLinkAdManager.kt
- Modify constructor to accept `launchTabOpener` and `interstitialTabOpener`.
- Modify `onUserGestureNavigation(url: String): Boolean` to return `Boolean`.
- If ad triggers, pass `onDismiss = { interstitialTabOpener(originalUrl) }` to `DirectLinkInterstitial` and return `true`.

### 5. DirectLinkInterstitial.kt
- Add `onDismiss: () -> Unit` parameter to constructor.
- Remove `AUTO_DISMISS_MS` timer.
- Add centered circular `ProgressBar` visible during loading (`onPageStarted` / `onPageFinished`).
