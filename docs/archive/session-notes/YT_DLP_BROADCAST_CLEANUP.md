# yt-dlp Broadcast Code Cleanup

**Date:** June 14, 2026  
**Status:** ✅ COMPLETED

## Summary

Removed all dead broadcast code from `YtDlpDownloadService.kt`. These broadcasts had no registered receivers and were serving no purpose since the service already shows its own notifications.

## Changes Made

### Removed from `YtDlpDownloadService.kt`:

1. **Progress broadcast** (line ~191):
   ```kotlin
   // REMOVED
   sendBroadcast(Intent(BROADCAST_DOWNLOAD_PROGRESS).apply {
       putExtra(EXTRA_URL, url)
       putExtra(EXTRA_PROGRESS, progress.toInt())
   })
   ```

2. **Success broadcast** (line ~251):
   ```kotlin
   // REMOVED
   sendBroadcast(Intent(BROADCAST_DOWNLOAD_COMPLETE).apply {
       putExtra(EXTRA_URL, url)
       putExtra(EXTRA_FILE_PATH, filePath)
   })
   ```

3. **Failure broadcast** (line ~266):
   ```kotlin
   // REMOVED
   sendBroadcast(Intent(BROADCAST_DOWNLOAD_FAILED).apply {
       putExtra(EXTRA_URL, url)
       putExtra(EXTRA_ERROR, error)
   })
   ```

### Previously Removed Constants:
- `BROADCAST_DOWNLOAD_COMPLETE`
- `BROADCAST_DOWNLOAD_FAILED`
- `BROADCAST_DOWNLOAD_PROGRESS`
- `EXTRA_PROGRESS`
- `EXTRA_FILE_PATH`
- `EXTRA_ERROR`

## Rationale

1. **No Receivers**: A codebase search confirmed no `BroadcastReceiver` was registered for any of the custom `YTDLP_*` actions.

2. **LightningDownloadListener Not Compatible**: The existing `LightningDownloadListener.onReceive()` only handles `DownloadManager.ACTION_DOWNLOAD_COMPLETE` and would early-return for the custom actions.

3. **Service Notifications Work**: The service already shows proper notifications through:
   - `showProgressNotification()` - Progress updates
   - `showSuccessNotification()` - Completion with file open intent
   - `showErrorNotification()` - Failure messages

4. **Misleading Code**: Dead code creates maintenance burden and confusion about the actual flow.

## Verification

Build completed successfully:
```
BUILD SUCCESSFUL in 1m 58s
76 actionable tasks: 13 executed, 63 up-to-date
```

All broadcast references removed from the service.

## Related Documents

- `YT_DLP_CRITICAL_FIX_SUMMARY.md` - Overview of Android 10+ W^X fix
- `YT_DLP_DATABASE_FIX.md` - RxJava subscription fix for persistence
- `YT_DLP_ANDROID_LIBRARY_MIGRATION.md` - Migration to youtubedl-android
- `YT_DLP_INTEGRATION_PLAN.md` - Original implementation plan

## Testing Recommendations

When testing yt-dlp downloads on Android 10+ device:

1. ✅ Progress notification appears and updates
2. ✅ Completion notification appears with "Open" action
3. ✅ Download entry appears in Downloads list (database persistence)
4. ✅ Error notification appears on failure
5. ✅ Service properly stops when all downloads complete

The removal of broadcasts does not affect functionality since:
- Notifications are shown by the service itself
- Database persistence is handled via RxJava subscription
- No in-app components were listening to the broadcasts
