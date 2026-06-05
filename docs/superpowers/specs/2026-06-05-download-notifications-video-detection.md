# Download Notifications, List Visibility & Video Detection — Design Spec
Date: 2026-06-05

## Problem Statement

Four independent bugs identified through code tracing:

1. **No download notifications (Android 13+)** — `POST_NOTIFICATIONS` is declared in the manifest
   but never requested at runtime. `NotificationManagerCompat.notify()` silently drops all
   notifications. Additionally `LightningDownloadListener.onReceive()` is a no-op; completion
   logic is wired to a Fetch2 listener that never fires because `DownloadHandler` uses the
   system `DownloadManager`, not Fetch2.

2. **Download list has no readable titles** — `DownloadManager.Request.setTitle()` is never
   called in `DownloadHandler`, so entries appear in the list with a raw URL as their title
   rather than the guessed filename.

3. **Video detection is fragile** — The injected JS fires only on the `playing` event (one-shot),
   only accepts `http` URLs (silently drops blob:/MSE streams), and only shows the quality picker
   when `qualities.size > 1`. MutationObserver and `loadedmetadata` events are absent.

4. **Adaptive streams silently fail** — YouTube and similar sites use `blob:` MSE URLs. The
   current code silently drops these in `VideoJavascriptInterface` (the `startsWith("http")`
   guard) and in `DownloadManager` (which cannot fetch `blob:` URLs). Users see nothing.

---

## Root Cause Analysis

### Task 1 — Notifications broken on Android 13+

| Layer | Finding |
|---|---|
| `AndroidManifest.xml:9` | `POST_NOTIFICATIONS` declared ✓ |
| `WebBrowserActivity.initialize()` | Only calls `createNotificationChannel()` — no runtime request |
| `LightningDownloadListener.init{}` | Registers `fetch.addListener()` — Fetch2 listener, never fires |
| `LightningDownloadListener.onReceive()` | Explicitly no-op ("Fetch2 listener in init{} handles...") |
| `DownloadHandler.java:327,432` | Uses `downloadManager.enqueue()` for all paths — Fetch2 not used |

Fix: request the permission once after channel creation; move completion/failure logic into `onReceive()` using a `DownloadManager.Query`; remove the dead Fetch2 listener.

### Task 2 — Download list titles

`DownloadHandler.onDownloadStartNoStream()` builds a full `DownloadManager.Request` but never
calls `request.setTitle(iFilename)`. The system DownloadManager defaults the title to the raw
URL, making the Downloads list unusable. One-line fix in both `onDownloadStartNoStream` and
`onDownloadStartNoStreamWithFilename`.

### Task 3 — Video detection gaps

The injected JS in `WebPageClient.kt:438–478`:
- Single `playing` event listener — misses late-loading videos and page mutations
- `url.startsWith('http')` guard — blob: streams never reported
- Scans only `<source>` children, not `<a href>` download links or `data-*` attributes
- `showVideoDownloadSheet()` requires `qualities.size > 1` — single-quality direct files never show the picker

### Task 4 — Adaptive stream silence

`VideoJavascriptInterface.onVideoPlaying()` has `if (url.startsWith("http"))` — blob: URLs
are silently dropped before reaching Kotlin. When a blob: URL reaches `startDownload()` /
`DownloadManager`, the download silently fails (DownloadManager cannot fetch `blob:` URLs).

---

## Approved Solution

### Task 1: Fix notifications

**A. `WebBrowserActivity.kt` — request `POST_NOTIFICATIONS` at runtime**

In `initialize()`, after `createNotificationChannel()`, add:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(
        this,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        object : PermissionsResultAction() {
            override fun onGranted() { /* notifications now work */ }
            override fun onDenied(permission: String) {
                Timber.d("POST_NOTIFICATIONS denied — notifications suppressed")
            }
        }
    )
}
```

**B. `LightningDownloadListener.kt` — revive `onReceive()`**

Remove the dead `fetch.addListener()` block from `init{}` (keep only the `fetch` field if
still needed elsewhere). Rewrite `onReceive()`:
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
    if (id == -1L) return

    val q = DownloadManager.Query().setFilterById(id)
    val c = downloadManager.query(q)
    c?.use {
        if (!it.moveToFirst()) return
        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val filename = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            ?.takeIf { s -> s.isNotBlank() } ?: downloadHandler.iFilename

        val notifMgr = NotificationManagerCompat.from(mActivity)
        val builder = NotificationCompat.Builder(mActivity, (mActivity as WebBrowserActivity).CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_outline)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                notifMgr.notify(id.toInt(), builder
                    .setContentTitle(mActivity.getString(R.string.download_complete))
                    .setContentText(filename).build())
                mActivity.makeSnackbar(
                    mActivity.getString(R.string.download_complete),
                    KDuration,
                    if (mActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                ).setAction(R.string.show) {
                    (mActivity as WebBrowserActivity).openDownloads()
                }.show()
            }
            DownloadManager.STATUS_FAILED -> {
                notifMgr.notify(id.toInt(), builder
                    .setContentTitle(mActivity.getString(R.string.download_failed))
                    .setContentText(filename).build())
                mActivity.snackbar(mActivity.getString(R.string.download_failed),
                    if (mActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
            }
        }
    }
}
```
Remove Fetch2 imports (`AbstractFetchListener`, `Download`, `Error`) after removing the listener.

### Task 2: Fix download list titles

In `DownloadHandler.java`, in **both** `onDownloadStartNoStream()` and
`onDownloadStartNoStreamWithFilename()`, add `request.setTitle(iFilename)` immediately after
`request.setDescription(...)`:
```java
request.setTitle(iFilename);
```

### Task 3: Enhance video detection JS

Replace the injected script in `WebPageClient.onPageFinished()` with a richer version that:
- Registers `loadedmetadata` + `playing` on all current `<video>` elements
- Uses a debounced `MutationObserver` to re-scan on DOM changes
- Scans `<a href>` links ending in `.mp4/.webm/.m4v/.ogv`
- Classifies each URL as one of: `direct` (http/https direct media file), `blob`, `hls` (.m3u8), `dash` (.mpd)
- Only calls `VideoSniffer.onVideoDetected()` for videos with `direct` URLs **or** to report a non-downloadable stream
- Calls `VideoSniffer.onVideoDetected(url, qualitiesJson, resolution, streamType)` (new `streamType` param)

The JS function `buildQualities(video)` collects from:
- `<source>` children: label/title/data-res/res/size attributes
- `<a href>` siblings/ancestors matching media extensions
- Falls back to `{Default: currentSrc}` if no sources found

### Task 4: Honest adaptive stream handling

**`VideoJavascriptInterface.kt`**: rename method to `onVideoDetected`, add `streamType: String`:
```kotlin
@JavascriptInterface
fun onVideoDetected(url: String, qualitiesJson: String?, resolution: String?, streamType: String) {
    tab.onVideoDetected(url, qualitiesJson, resolution, streamType)
}
```
Keep `onVideoPlaying` as a no-op shim for backward compat if needed (or remove).

**`WebPageTab.kt`**:
- Add `detectedStreamType: String = "direct"` field
- `onVideoDetected()` accepts `streamType` param, stores it
- `showVideoDownloadSheet()`: if `streamType` is `blob`/`hls`/`dash` and no direct URL present:
  - Show sheet with `tvVideoHost` populated
  - Show `tvAdaptiveStreamMessage` (new TextView or reuse existing) with `R.string.video_adaptive_stream_message`
  - Disable `btnVideoDownload`, set text to `R.string.video_cannot_download`
  - Do NOT show quality picker
- Show quality picker for `qualities.size >= 1` (not `> 1`)
- FAB: show for `direct` OR adaptive (user should see a message, not just nothing)

**`strings.xml`** — add:
```xml
<string name="video_adaptive_stream_message">This video uses adaptive streaming (e.g. YouTube) and cannot be downloaded directly by the browser.</string>
<string name="video_cannot_download">Cannot Download</string>
```

**`bottom_sheet_video_download.xml`** — add a `TextView` (`tvAdaptiveStreamMessage`) below the quality picker, initially `GONE`, shown when adaptive.

---

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/fulguris/activity/WebBrowserActivity.kt` | Add `POST_NOTIFICATIONS` runtime request in `initialize()` |
| `app/src/main/java/fulguris/download/LightningDownloadListener.kt` | Rewrite `onReceive()`; remove dead Fetch2 listener from `init{}` |
| `app/src/main/java/fulguris/download/DownloadHandler.java` | Add `request.setTitle(iFilename)` in both download methods |
| `app/src/main/java/fulguris/view/WebPageClient.kt` | Replace video detection JS with richer multi-event, MutationObserver version |
| `app/src/main/java/fulguris/js/VideoJavascriptInterface.kt` | Add `onVideoDetected(url, qualitiesJson, resolution, streamType)` |
| `app/src/main/java/fulguris/view/WebPageTab.kt` | Accept `streamType`, handle adaptive-only in `showVideoDownloadSheet()`, fix quality picker threshold |
| `app/src/main/res/values/strings.xml` | Add `video_adaptive_stream_message`, `video_cannot_download` |
| `app/src/main/res/layout/bottom_sheet_video_download.xml` | Add `tvAdaptiveStreamMessage` TextView |

---

## Verification Plan

1. `.\gradlew.bat assembleSlionsFullDownloadDebug` → `BUILD SUCCESSFUL`
2. On Android 13+ device/emulator: cold start → permission dialog appears for notifications
3. Trigger a real file download → system progress notification visible → on completion, app notification fires + snackbar shows with "Show" action
4. Open Downloads list → entry appears with filename (not raw URL) as title
5. Navigate to a page with a direct `.mp4` `<video>` → FAB appears → sheet shows quality picker → download starts and appears in list
6. Navigate to YouTube → FAB appears → sheet shows "adaptive streaming" message → Download button is disabled
