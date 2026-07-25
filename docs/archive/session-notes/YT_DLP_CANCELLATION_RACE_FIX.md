# yt-dlp Cancellation Race Condition Fix - Complete

## Overview
Fixed a race condition where in-flight progress callbacks could overwrite the cancellation notification with a stuck progress bar after the user cancelled a download.

## Status: ✅ BUILD SUCCESSFUL

**Build Command**: `.\gradlew.bat assembleXhubFullDownloadDebug`  
**Build Result**: `BUILD SUCCESSFUL in 1m 15s` (76 actionable tasks)

---

## The Problem

When a user cancels a yt-dlp download:
1. `cancelDownload()` is called
2. Process is destroyed
3. `downloadProcessIds[url]` is removed
4. Progress notification is cancelled
5. Cancelled notification is shown

**BUT**: The yt-dlp native process may have buffered progress callbacks that haven't been delivered yet. These callbacks could arrive AFTER cancellation and call `showProgressNotification()`, which would:
- Post a new progress notification with the same notification ID
- Overwrite the cancelled notification
- Leave a stuck progress bar that never completes

Additionally, the cancelled notification was deriving its title from the URL's last path segment (e.g., `youtube.com/watch?v=xxx` → `watch?v=xxx`), which could differ from the filename shown during progress (e.g., `My_Video_1734567890`).

---

## The Solution

### 1. Added Progress Callback Guard

Added a check at the top of `showProgressNotification()` to skip posting if the download was cancelled:

```kotlin
private fun showProgressNotification(notificationId: Int, filename: String, progress: Int, url: String) {
    // Guard against posting progress after cancellation
    // If the URL is no longer in downloadProcessIds, the download was cancelled
    // and a buffered progress line should not overwrite the cancelled notification
    if (!downloadProcessIds.containsKey(url)) {
        Timber.v("Skipping progress notification for cancelled download: $url")
        return
    }
    
    // ... rest of the method
}
```

**How it works**:
- `cancelDownload()` removes the URL from `downloadProcessIds` BEFORE destroying the process
- Any buffered progress callbacks that arrive after cancellation will see the URL is missing
- The progress callback returns early without posting a notification
- The cancelled notification remains intact

### 2. Added Filename Tracking

Added a new `ConcurrentHashMap` to track the unique filename per download:

```kotlin
private val downloadFilenames = ConcurrentHashMap<String, String>() // url -> uniqueFilename
```

**Lifecycle**:
1. **Track**: Set when download starts (after generating unique filename)
2. **Use**: Retrieved in `cancelDownload()` for cancelled notification
3. **Cleanup**: Removed in `finally` block and `cancelDownload()`

### 3. Updated Cancellation Notification

Changed `showCancelledNotification()` call to use the tracked filename:

```kotlin
// Before
showCancelledNotification(notificationId, url.substringAfterLast("/").take(50))

// After
val uniqueFilename = downloadFilenames[url] ?: url.substringAfterLast("/").take(50)
showCancelledNotification(notificationId, uniqueFilename)
```

**Benefits**:
- Consistent filename display between progress and cancellation
- Fallback to URL-derived name if filename not tracked (defensive)
- User sees the same name they saw during download

---

## Implementation Details

### Changes Made to YtDlpDownloadService.kt

1. **Added `downloadFilenames` map** (line ~80)
   ```kotlin
   private val downloadFilenames = ConcurrentHashMap<String, String>()
   ```

2. **Track filename on download start** (line ~177)
   ```kotlin
   val uniqueFilename = "${baseFilename}_${System.currentTimeMillis()}"
   downloadFilenames[url] = uniqueFilename
   ```

3. **Retrieve filename in coroutine** (line ~185)
   ```kotlin
   val uniqueFilename = downloadFilenames[url] ?: "video_${System.currentTimeMillis()}"
   ```

4. **Add guard in showProgressNotification** (line ~603)
   ```kotlin
   if (!downloadProcessIds.containsKey(url)) {
       Timber.v("Skipping progress notification for cancelled download: $url")
       return
   }
   ```

5. **Use tracked filename in cancelDownload** (line ~420)
   ```kotlin
   val uniqueFilename = downloadFilenames[url] ?: url.substringAfterLast("/").take(50)
   showCancelledNotification(notificationId, uniqueFilename)
   ```

6. **Cleanup in finally block** (line ~320)
   ```kotlin
   } finally {
       downloadJobs.remove(url)
       downloadFilenames.remove(url)
       checkAndStopService()
   }
   ```

7. **Cleanup in cancelDownload** (line ~450)
   ```kotlin
   downloadFilenames.remove(url)
   ```

---

## Race Condition Timeline

### Before Fix (Race Condition Exists)

```
T0: User clicks cancel
T1: cancelDownload() called
T2: downloadProcessIds[url] removed
T3: Process destroyed
T4: notificationManager.cancel(notificationId)
T5: showCancelledNotification(notificationId, "watch?v=xxx")
T6: [Buffered callback arrives] showProgressNotification(notificationId, ..., 85, url)
T7: Progress notification overwrites cancelled notification ❌
```

### After Fix (Race Condition Prevented)

```
T0: User clicks cancel
T1: cancelDownload() called
T2: downloadProcessIds[url] removed ← Guard key action
T3: Process destroyed
T4: notificationManager.cancel(notificationId)
T5: showCancelledNotification(notificationId, "My_Video_1734567890")
T6: [Buffered callback arrives] showProgressNotification checks downloadProcessIds
T7: URL not found, returns early without posting ✅
```

---

## Testing Checklist

### ✅ Build Verification (Completed)
- [x] Clean build passes without errors
- [x] No new compiler warnings introduced
- [x] All 76 tasks complete successfully

### ⏳ Manual Testing Required

**Cancellation Race Condition**:
- [ ] Start a large download (slow connection preferred)
- [ ] Cancel immediately (within first few seconds)
- [ ] Verify cancelled notification appears and stays
- [ ] Verify no progress bar appears after cancellation
- [ ] Check logs for "Skipping progress notification for cancelled download"

**Filename Consistency**:
- [ ] Start a download with a custom filename
- [ ] Cancel the download
- [ ] Verify cancelled notification shows the same filename as progress
- [ ] Verify filename is NOT derived from URL path

**Normal Operation**:
- [ ] Complete a download without cancelling
- [ ] Verify progress notifications still work
- [ ] Verify success notification appears
- [ ] Verify no impact on normal download flow

**Edge Cases**:
- [ ] Cancel multiple simultaneous downloads
- [ ] Cancel very quickly (before first progress callback)
- [ ] Cancel near completion (95%+)
- [ ] Verify cleanup in all cases (check logs for map cleanup)

---

## Technical Notes

### Why Use downloadProcessIds as Guard?

We could have added a dedicated `ConcurrentHashMap<String, Boolean>` for cancelled flags, but reusing `downloadProcessIds` has several advantages:

1. **Single Source of Truth**: Process ID presence already indicates active download
2. **Atomic State**: Removal happens before process destruction (clear ordering)
3. **No Extra Map**: Reduces memory overhead
4. **Existing Pattern**: Already used for cancellation detection in exception handlers
5. **Immediate Effect**: Progress callbacks see cancellation instantly

### Why Track Filename Separately?

The `uniqueFilename` was previously scoped only to the coroutine. By tracking it separately:

1. **Accessible from cancelDownload()**: Can be retrieved synchronously
2. **Consistent Naming**: Same name throughout download lifecycle
3. **User-Friendly**: Shows actual filename, not URL-derived string
4. **Defensive**: Fallback ensures cancelled notification always shows something

### Cleanup Ordering

Cleanup happens in two places:
1. **finally block**: Runs on normal completion or failure
2. **cancelDownload()**: Runs on explicit cancellation

Both clean up `downloadFilenames[url]` to prevent memory leaks.

---

## Benefits

1. **Race Condition Fixed**: Progress callbacks can't overwrite cancelled notification
2. **Consistent UX**: User sees same filename throughout download lifecycle
3. **Clean State**: All tracking maps cleaned up properly
4. **Defensive Code**: Fallback ensures robustness
5. **Minimal Overhead**: One map lookup per progress callback (negligible)

---

## Related Tasks

- Task 1: File path resolution fix ✅
- Task 2: Foreground service type and notification permission ✅
- Task 3: Output path capture line trimming ✅
- Task 4: Download location and metadata fix ✅
- Task 5: Cancellation UI cleanup ✅
- Task 6: Progress throttling implementation ✅
- Task 7: Scoped storage support ✅
- **Task 8: Cancellation race condition fix ✅ (This document)**

All yt-dlp integration tasks are now complete. The implementation is production-ready pending device testing.

---

## Files Modified

1. `app/src/main/java/com/xhub/browser/download/YtDlpDownloadService.kt`
   - Added `downloadFilenames` map for filename tracking
   - Added guard in `showProgressNotification()` to prevent race condition
   - Track filename on download start
   - Retrieve tracked filename in coroutine
   - Use tracked filename in `cancelDownload()` for cancelled notification
   - Clean up `downloadFilenames` in finally block and cancelDownload()

---

## Conclusion

This fix resolves a subtle but annoying race condition that could leave users confused about download state. The solution is clean, minimal, and follows existing patterns in the codebase.
