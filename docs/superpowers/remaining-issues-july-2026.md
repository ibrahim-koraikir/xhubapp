# Remaining Issues — July 2026

> P2 quality/UX items deferred from the codebase review. None will crash the app.
> See `codebase-review-july-2026.md` for the full review.

---

## C4 — No yt-dlp execution timeout

**File:** `YtDlpDownloadService.kt:446-478`

`YoutubeDL.execute()` is a blocking call on `Dispatchers.IO` with no timeout. If yt-dlp hangs (stalled stream, frozen process), the coroutine hangs forever with no watchdog.

**Fix idea:** Wrap the execute call in `withTimeout()` from kotlinx.coroutines, or use a `yield()`-based watchdog loop. Kill the process via `destroyProcessById` on timeout.

---

## C5 — Activity leaks in Rx chains

**Files:** `DownloadHandler.java:288`, `LightningDownloadListener.kt:58`

`DownloadHandler` casts `context` to `WebBrowserActivity` and retains it through RxJava chains. If the activity is recreated (config change), the captured reference is stale. Similarly, `LightningDownloadListener.onReceive` keeps a `mActivity` reference from construction.

**Fix idea:** Use `CompositeDisposable` (already added in C6) and clear on activity destroy. Or use `WeakReference<Activity>`. Or pass a callback interface instead of the activity reference.

---

## C7 — Multi-GB file copy through userspace

**File:** `YtDlpDownloadService.kt:847-851`

After yt-dlp finishes downloading to a temp file, the code calls `tempFile.inputStream().copyTo(outputStream)` which copies potentially multi-GB files through userspace. On low-storage devices this doubles write I/O.

**Fix idea:** Use `FileProvider` + `MediaStore.setPending` + atomic rename, or use `ContentResolver.openFile` with `"w"` mode for an fd-based copy.

---

## C3 — One BroadcastReceiver per tab

**File:** `WebPageTab.kt:1116` (TODO comment)

Every tab registers its own `LightningDownloadListener` as a `BroadcastReceiver`. A TODO asks "Do we really need one of those per tab/WebView?" — one global receiver would suffice, filtering by download ID. The 1000-receiver limit is theoretical for most users but a design smell.

**Fix idea:** Register one global `BroadcastReceiver` in the Application or in `WebBrowserActivity.onCreate` and dispatch to the relevant tab by download ID.

---

## B3 — Modify filters do synchronous blocking I/O on WebView IO thread

**File:** `AbpBlockerManager.kt:266-277`

When the adblock engine's modify-response filter matches, the code makes a synchronous `okHttpClient.newCall(newRequest).execute()` call on the WebView IO thread. Each tab has one IO thread, so a slow modify target blocks ALL subresource loading for that tab.

**Fix idea:** Offload the modify request to a coroutine or Rx chain, cache the modified response, and serve from cache on subsequent requests. Or use OkHttp's async `enqueue()` with a `CompletableFuture`.

---

## B4 — Blocklists fail open silently

**File:** `AbpBlockerManager.kt:110`

If filter lists fail to load after the retry backoff (total ~30s), `listsLoaded = true` is set anyway and all ad blocking is silently disabled for the rest of the process lifetime. The only indicator is a `Timber.w` log line.

**Fix idea:** Show a snackbar or notification on permanent failure. Or periodically retry in the background. Or surface the state in the adblock settings UI.

---

## F6 — No indices on any table

**Files:** All 4 `*Database.kt` schema definitions (Bookmark, History, Downloads, UserRules)

No table has any indices. Bookmarks queried by `url` with `OR` (trailing-slash variant), history queried by `time DESC LIMIT 5/100` and `title/url LIKE`, downloads by `url` — all full-table scans. Performance degrades linearly with table size.

**Fix idea:** Add `CREATE INDEX` statements in `onCreate()` (and migrations for existing users). Key candidates:
- Bookmarks: `CREATE INDEX idx_bookmark_url ON bookmark(url)`
- History: `CREATE INDEX idx_history_time ON history(time)`
- Downloads: `CREATE INDEX idx_download_url ON download(url)`
- UserRules: `CREATE INDEX idx_rules_pattern ON rules(pattern)`
