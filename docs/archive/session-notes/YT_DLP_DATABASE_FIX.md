# yt-dlp Download Database Persistence Fix

## Issue Fixed

**Critical Bug:** Downloads were never persisting to the database, so completed videos would not appear in the Downloads list.

### Root Cause

In `YtDlpDownloadService.handleDownloadSuccess()`, the code called:
```kotlin
downloadsRepository.addDownloadIfNotExists(entry)
```

**But this was wrong** because:
1. `addDownloadIfNotExists()` returns a `Single<Boolean>` (RxJava)
2. RxJava `Single` is **cold/lazy** - it won't execute until subscribed
3. Without calling `.subscribe()`, the lambda body never runs
4. The database `INSERT` never happens
5. Downloads disappear after completion

### The Fix

Following the pattern in `DownloadHandler.java`, the fix:

1. **Injected `@DatabaseScheduler`:**
   ```kotlin
   @Inject
   @DatabaseScheduler
   lateinit var databaseScheduler: Scheduler
   ```

2. **Subscribed to the Single:**
   ```kotlin
   downloadsRepository.addDownloadIfNotExists(entry)
       .subscribeOn(databaseScheduler)  // Run DB work off main thread
       .subscribe({ success ->
           if (!success) {
               Timber.w("Download entry already exists or insert failed")
           } else {
               Timber.d("Added download to repository: ${file.name}")
           }
       }, { error ->
           Timber.e(error, "Failed to add download to repository")
       })
   ```

3. **Moved off Dispatchers.Main:**
   - Database subscription now runs on `databaseScheduler` (IO thread)
   - Only UI operations (broadcast, notifications) run on main thread

## Files Modified

### YtDlpDownloadService.kt

**Before:**
```kotlin
private suspend fun handleDownloadSuccess(...) {
    withContext(Dispatchers.Main) {
        // ...
        downloadsRepository.addDownloadIfNotExists(entry) // ❌ Never executes!
        Timber.d("Added download to repository")
    }
}
```

**After:**
```kotlin
@Inject
@DatabaseScheduler
lateinit var databaseScheduler: Scheduler

private suspend fun handleDownloadSuccess(...) {
    // Database work (properly subscribed)
    downloadsRepository.addDownloadIfNotExists(entry)
        .subscribeOn(databaseScheduler) // ✅ Runs on IO thread
        .subscribe({ success ->
            if (!success) {
                Timber.w("Download already exists or insert failed")
            } else {
                Timber.d("Added download to repository: ${file.name}")
            }
        }, { error ->
            Timber.e(error, "Failed to add download to repository")
        })
    
    // UI work (runs on main thread)
    withContext(Dispatchers.Main) {
        sendBroadcast(...)
        showSuccessNotification(...)
    }
}
```

## Verification

### Expected Behavior (After Fix)
1. User downloads video via yt-dlp
2. Download completes successfully
3. `handleDownloadSuccess()` called
4. `addDownloadIfNotExists()` subscribed and executes
5. Database `INSERT` happens on IO thread
6. Entry appears in Downloads list (menu → Downloads)
7. Log shows: `"Added download to repository: video.mp4"`

### Testing Steps
```bash
# Install APK
adb install app/build/outputs/apk/xhubFullDownload/debug/*.apk

# Monitor logs
adb logcat | grep -i "download\|repository"

# Test:
1. Navigate to YouTube
2. Download a video
3. Wait for completion
4. Open Downloads (menu → Downloads)
5. Verify video appears in list

# Expected logs:
# YtDlpDownloadService: Download completed successfully
# YtDlpDownloadService: Added download to repository: video.mp4
```

## Technical Details

### Why RxJava Singles Are Cold

From RxJava documentation:
> A Single is lazy, it only executes when subscribed to.

This is different from Kotlin Coroutines:
- Coroutines: `launch { }` starts immediately
- RxJava Single: `.subscribe()` required to start

### Pattern Match with DownloadHandler

This fix mirrors `DownloadHandler.onDownloadStartNoStream()`:

```java
// DownloadHandler.java (line 399)
downloadsRepository.addDownloadIfNotExists(new DownloadEntry(url, filename, contentSize))
    .subscribeOn(databaseScheduler)
    .subscribe(aBoolean -> {
        if (!aBoolean) {
            Timber.d("error saving download to database");
        }
    });
```

## Other Fixes in This Commit

1. **Android 10+ Compatibility** (Previous issue)
   - Migrated to `youtubedl-android` library
   - Binaries now in `nativeLibraryDir` (executable)
   - Removed incompatible Linux binary

2. **Null Safety**
   - Fixed `YtDlpManager.getVersion()` return type
   - Added `?: "Unknown"` for null case

3. **Coroutine Context**
   - Used `runBlocking()` in progress callback (not suspend context)
   - Moved `withContext(Dispatchers.Main)` to correct scope

## Impact

**Before:** Every yt-dlp download was lost after completion (database never updated)  
**After:** Downloads persist properly and appear in Downloads list

This was a **critical data loss bug** that made the feature appear broken to users.

---

**Status:** ✅ FIXED  
**Build:** ✅ SUCCESS  
**Testing:** Required on device  
**Priority:** Critical - Data Loss Issue
