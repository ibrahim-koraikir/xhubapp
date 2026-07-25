I have the following comments after thorough review of file. Implement the comments by following the instructions verbatim.

---
## Comment 1: Ad system auto-opens remote third-party ad URLs in new tabs (even in incognito) and whitelists them past the built-in ad blocker.

Remove the ad-injection system end to end. In `WebPageClient.shouldOverrideUrlLoading()` (file `WebPageClient.kt`), delete the block that calls `adManager.trackAction()` and opens `adManager.getAdUrl()` as a new tab. In `AbpBlockerManager.shouldBlock()` (file `AbpBlockerManager.kt`), remove the `adManager.isAdUrl(it)` clause from the always-allow condition so the blocker is no longer bypassed. Remove the `AdManager` injections/fields from `WebPageClient`, `AbpBlockerManager`, `WebBrowserActivity`, and `EntryPoint`, and delete `AdManager.kt` (including the remote `CONFIG_URL` fetch, the `DEFAULT_NETWORKS` list, and the never-unregistered `ConnectivityManager` network callback). Delete or rewrite `AD_INTEGRATION.md` accordingly. If ads are a hard requirement, integrate a vetted, consented ad SDK in a dedicated, clearly-labeled placement instead — never as injected tabs, never in incognito, and never whitelisted past the ad blocker.

### Relevant Files
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\ads\AdManager.kt
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageClient.kt
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\adblock\AbpBlockerManager.kt
- c:\Users\w\Desktop\Fulguris-main\AD_INTEGRATION.md
---
## Comment 2: Network security config trusts user-installed CAs and permits cleartext globally, exposing the app's own connections to interception.

In `network_security_config.xml`, stop trusting user CAs for first-party traffic: remove `<certificates src="user" />` from the base-config trust anchors, or move first-party hosts into a dedicated `domain-config` that trusts only `system` CAs and forbids cleartext. Keep cleartext permitted only where genuinely required for arbitrary web content, and document why in a comment. Re-verify analytics/Crashlytics and any config/update endpoints still connect after the change.

### Relevant Files
- c:\Users\w\Desktop\Fulguris-main\app\src\main\res\xml\network_security_config.xml
- c:\Users\w\Desktop\Fulguris-main\app\src\main\AndroidManifest.xml
---
## Comment 3: Video quality picker hard-codes a dark text color, making labels invisible on light theme, and uses list index as the radio view id.

In `showVideoDownloadSheet()` within `WebPageTab.kt`, replace the hard-coded `m3_sys_color_dynamic_dark_on_surface` text color with a theme-resolved attribute (resolve `?attr/colorOnSurface` from the inflated sheet's themed context) so it adapts to light and dark themes. Replace `id = index` with ids from `View.generateViewId()` and map the generated id back to the selected quality entry (for example via a tag), instead of indexing by the raw id. Truncate or sanitize page-derived quality labels before setting them on the views.

### Relevant Files
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageTab.kt
---
## Comment 4: Fetch2 download library is configured and instantiated at startup but never used; downloads run entirely through the system DownloadManager.

Remove the `com.github.tonyofrancis.Fetch:fetch2` and `:fetch2okhttp` dependencies from `app/build.gradle`. Delete `providesFetch()` from `AppModule.kt` and the `fetch` declaration from `EntryPoint.kt`. Remove the unused `fetch` field and the now-dead `getFileName()` helper from `LightningDownloadListener.kt`. Rebuild with `.\gradlew.bat assembleSlionsFullDownloadDebug` and exercise a real download to confirm the system DownloadManager path still works end to end.

### Relevant Files
- c:\Users\w\Desktop\Fulguris-main\app\build.gradle
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\di\AppModule.kt
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\di\EntryPoint.kt
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\download\LightningDownloadListener.kt
---
## Comment 5: Thumbnail cache's duplicate-load guard uses a non-atomic size check, allowing redundant disk decodes and occasionally dropped callbacks.

In `TabThumbnailCache.get()`, make the "is a load already in flight?" decision and the callback registration happen under a single lock keyed by `tabId` (or track one in-flight load per tab using a map of futures), so exactly one disk decode runs per tab and every callback attached before completion is invoked. Ensure completion only clears the in-flight marker after notifying all currently-registered callbacks. Consider passing the cache directory in (or injecting a context) instead of reading the global `fulguris.app`, to make the cache testable.

### Relevant Files
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\browser\tabs\TabThumbnailCache.kt
---
## Comment 6: The VideoSniffer JavaScript bridge is injected into every page, exposing an app-side interface to untrusted web content.

Treat all bridge input as untrusted in `WebPageTab.onVideoDetected()`: validate that reported URLs are well-formed http(s) before offering them for download, and cap the number and string length of parsed qualities. Remove the dead `onVideoPlaying` shim from `VideoJavascriptInterface.kt`. Confirm the configured `minSdk` keeps `@JavascriptInterface` enforcement, and consider only registering the interface on tabs/pages where video detection is actually needed rather than globally.

### Relevant Files
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageClient.kt
- c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\js\VideoJavascriptInterface.kt
---
## Comment 7: Ad documentation contradicts the code and many build logs/dumps are committed at the repo root, hurting maintainability.

Delete or relocate transient artifacts from the repo root (build logs, kapt dumps, `logcat.txt`, `fulguris_logs.txt`, `*.diff`, scratch `.bat` files) and add matching patterns to `.gitignore`. Consolidate the many overlapping `*.md` change-notes under `docs/`. Update `AD_INTEGRATION.md` and `AD_IMPLEMENTATION_SUMMARY.md` to match the code, or remove them entirely if the ad system is removed per the critical finding. Scan committed logs for any tokens or personal data before publishing.

### Relevant Files
- c:\Users\w\Desktop\Fulguris-main\AD_INTEGRATION.md
- c:\Users\w\Desktop\Fulguris-main\AD_IMPLEMENTATION_SUMMARY.md
---