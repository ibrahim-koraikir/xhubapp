# Phase Breakdown

## Task 1: Fix download notifications: runtime permission + revive completion pipeline

Restore download notifications (progress, complete, failed) which are currently fully broken.

**1. Request **`POST_NOTIFICATIONS`** at runtime (Android 13+).**

- The permission is already declared in `c:\Users\w\Desktop\Fulguris-main\app\src\main\AndroidManifest.xml` but never requested. Add a runtime request on app/activity startup in `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\activity\WebBrowserActivity.kt` (it already builds the notification channel via `createNotificationChannel()` and holds `CHANNEL_ID`). Use the existing `PermissionsManager` pattern already used elsewhere (see `requestPermissionsIfNecessaryForResult` usage in `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\download\LightningDownloadListener.kt` and `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageChromeClient.kt`) guarded by `Build.VERSION_CODES.TIRAMISU`. Request it once (e.g., on first launch or first download), and degrade gracefully if denied.

**2. Revive the completion/failure pipeline in **`c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\download\LightningDownloadListener.kt`**.**

- The class registers for `DownloadManager.ACTION_DOWNLOAD_COMPLETE` (see `createDownloadListener()` in `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageTab.kt`) but its `onReceive()` is a no-op, and the actual notification logic is stuck inside a dead Fetch2 `AbstractFetchListener`.
- Remove the dead Fetch2 listener dependency for download status. Move the completion notification + "Show" snackbar (which calls `openDownloads()`) and the failure notification into `onReceive()`, driven by the `DownloadManager.Query`/`COLUMN_STATUS` logic that already exists in the `getFileName()` helper. Keep using `WebBrowserActivity.CHANNEL_ID` and `R.drawable.ic_download_outline`.
- Keep the system DownloadManager's own progress notification working (`DownloadHandler` already sets `VISIBILITY_VISIBLE`).

**Constraints:**

- Do not reintroduce Fetch2 for enqueueing — `DownloadHandler` is the single source of truth and uses the system `DownloadManager`.
- Preserve existing toolbar-position gravity logic for snackbars (`configPrefs.toolbarsBottom`).
- Follow the mandated workflow in `c:\Users\w\Desktop\Fulguris-main\AGENTS.md` (systematic-debugging, then verify with `.\gradlew.bat assembleSlionsFullDownloadDebug`).


## Task 2: Ensure downloads reliably persist and appear in the Downloads list

Make every download consistently appear in the in-app Downloads list.

**Investigate and fix list visibility:**

- `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\settings\fragment\DownloadsFragment.kt` queries the system `DownloadManager` (unfiltered) and refreshes via a `ContentObserver` + `ACTION_DOWNLOAD_COMPLETE` receiver in `onResume`/`onPause`. It is shown inside a bottom sheet via `openDownloads()` → `iBottomSheet.setLayout(R.layout.fragment_downloads)` in `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\activity\WebBrowserActivity.kt`. Verify the fragment's lifecycle callbacks (`onResume`/`onViewCreated`) actually run when hosted in that bottom sheet, and that `loadDownloads()` is invoked when the sheet is opened (call a refresh on show if needed).
- Confirm `DownloadHandler.onDownloadStartNoStream` / `onDownloadStartNoStreamWithFilename` in `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\download\DownloadHandler.java` always reach `downloadManager.enqueue(request)` successfully (no silent `IllegalArgumentException` from the `setDestinationUri(FILE + location + filename)` path), and surface real errors instead of swallowing them. Validate against the default `FileUtils.DEFAULT_DOWNLOAD_PATH` (public Downloads).
- Ensure the video download path in `WebPageTab.startDownload()` (`c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageTab.kt`) routes through the same `DownloadHandler` path and produces a `DownloadEntry` so it shows up like any other download.
- Confirm the database persistence (`downloadsRepository.addDownloadIfNotExists(new DownloadEntry(...))`) stays in sync with what the list reads. If the list reads only from `DownloadManager` while some entries exist only in the DB (or vice-versa), reconcile so the user sees a single complete list.

**Note:** There is an existing design note at `spec:` `docs/superpowers/specs/2026-06-01-downloads-fix-and-ui-redesign.md` describing the DownloadManager-vs-Fetch2 mismatch and the RecyclerView redesign (already applied). Use it as background but verify against the current code.

**Verify:** trigger a real direct-file download, open Downloads, confirm it appears with correct status/size and updates live; verify on Android 13+ after the permission fix from the first phase.


## Task 3: Improve video detection and quality picker reliability

Make video detection and the quality picker work reliably for sites that expose real video sources/qualities (lightweight scope — no HLS/DASH muxing).

**Enhance the injected detection script in **`c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageClient.kt`**:**

- Today it only listens for the `playing` event and only on `<video>` whose `currentSrc/src` `startsWith('http')`. Broaden it to actively scan the DOM for downloadable media:
  - Enumerate all `<video>` elements and their `<source>` children, collecting `{label/quality -> url}` from attributes (`label`, `title`, `data-res`, `res`, `size`, `type`) and from `videoHeight` resolution.
  - Also detect direct media URLs (`.mp4`, `.webm`, `.m4v`, etc.) on `<video src>`, `<a href>` download links, and common `data-*` attributes.
  - Re-scan on DOM changes using a `MutationObserver` (debounced) and on media `loadedmetadata`/`playing`, so detection isn't a one-shot.
  - Only report `http(s)` direct URLs as downloadable; flag `blob:`/MSE/HLS (`.m3u8`)/DASH (`.mpd`) sources as a distinct "non-downloadable stream" signal (handled in the next phase).

**Update the bridge and UI:**

- Extend `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\js\VideoJavascriptInterface.kt` (`onVideoPlaying`) and `WebPageTab.onVideoDetected` (`c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageTab.kt`) to accept the richer payload (a list of qualities + a stream-type flag).
- Make `showVideoDownloadSheet()` reliably populate the quality `RadioGroup` (`R.layout.bottom_sheet_video_download`) whenever ≥1 distinct quality is found (not only when `>1 <source>`), defaulting to the highest/auto, and keep the host/filename/quality-badge population.
- Keep `startDownload()` routing through `DownloadHandler` (consistent with the previous phases) and pass a sensible filename + `mimetype`.

**Constraints:** Use only the existing `WebView.addJavascriptInterface(..., "VideoSniffer")` mechanism; no new libraries. Sanitize filenames as currently done. Follow `c:\Users\w\Desktop\Fulguris-main\AGENTS.md` build/verify steps.


## Task 4: Honest handling of non-downloadable adaptive streams (YouTube/HLS/blob)

Stop silently failing on streams the lightweight downloader cannot handle, and tell the user clearly.

- Using the "non-downloadable stream" flag added to the detection script in `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageClient.kt` (for `blob:`, MSE, `.m3u8` HLS, `.mpd` DASH — e.g. YouTube), handle this state in `WebPageTab.onVideoDetected` / `showVideoDownloadSheet()` in `c:\Users\w\Desktop\Fulguris-main\app\src\main\java\fulguris\view\WebPageTab.kt`:
  - If only an adaptive/blob stream is detected (no direct downloadable URL), either keep the FAB hidden or show the sheet with a clear, localized message such as "This video uses adaptive streaming (e.g. YouTube) and can't be downloaded directly" and disable the Download button — instead of showing an empty picker or starting a `DownloadManager` job that can't fetch a `blob:` URL.
  - If both an adaptive stream and a direct fallback exist, offer the direct one and note the limitation.
- Add the new user-facing strings to `c:\Users\w\Desktop\Fulguris-main\app\src\main\res\values\strings.xml` (and follow the L10N workflow in `c:\Users\w\Desktop\Fulguris-main\AGENTS.md` / `subs/l10n/` for translations as appropriate). Reuse the existing `R.string.video_quality_auto` pattern.

**Verify:** On YouTube, the user gets a clear explanation rather than a broken/empty download attempt; on a site with a direct `.mp4`, the normal picker + download still works.