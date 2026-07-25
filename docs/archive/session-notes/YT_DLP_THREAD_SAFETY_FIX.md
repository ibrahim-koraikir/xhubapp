# yt-dlp Thread-Safety Fix

**Date:** June 14, 2026  
**Status:** ✅ COMPLETED

## Summary

Fixed critical thread-safety issues in `YtDlpDownloadService.kt` to prevent `ConcurrentModificationException`, service corruption, and premature service termination.

## Problems Identified

### 1. Non-Thread-Safe Maps
**Issue:** `downloadJobs` was a plain `mutableMapOf` (HashMap) but was accessed concurrently from:
- **Main thread:** `onStartCommand()`, `cancelDownload()`, `checkAndStopService()`, `onDestroy()`
- **IO coroutine:** Inside `startDownload()` coroutine body and `finally` block

**Risk:** Concurrent structural modification of HashMap can:
- Throw `ConcurrentModificationException`
- Corrupt internal HashMap state
- Lead to crashes or a service that never stops

### 2. Race Condition
**Issue:** Job assignment happened AFTER coroutine launch:
```kotlin
// OLD - RACE CONDITION
val job = serviceScope.launch { ... }  // Coroutine starts immediately
downloadJobs[url] = job  // Assignment happens after launch
```

**Race Scenario:**
1. Coroutine launches and starts executing
2. Fast failure occurs (e.g., quick validation error)
3. `finally` block runs: `downloadJobs.remove(url)` + `checkAndStopService()`
4. Main thread assigns: `downloadJobs[url] = job`
5. **Result:** Stale entry remains in map OR service stops prematurely

## Fixes Applied

### Fix 1: Thread-Safe Map
Changed `downloadJobs` from `mutableMapOf` to `ConcurrentHashMap`:

```kotlin
// BEFORE
private val downloadJobs = mutableMapOf<String, Job>()

// AFTER
private val downloadJobs = ConcurrentHashMap<String, Job>()
```

**Import added:**
```kotlin
import java.util.concurrent.ConcurrentHashMap
```

**Why:** `ConcurrentHashMap` provides thread-safe operations without external synchronization, allowing concurrent reads/writes from multiple threads without risk of corruption.

### Fix 2: Lazy Job Start with Pre-Registration
Changed coroutine launch to use `CoroutineStart.LAZY` and register job BEFORE starting:

```kotlin
// BEFORE - RACE CONDITION
val job = serviceScope.launch {
    // ... download logic ...
    finally {
        downloadJobs.remove(url)
        checkAndStopService()
    }
}
downloadJobs[url] = job  // Assignment after launch = RACE!

// AFTER - NO RACE
val job = serviceScope.launch(start = CoroutineStart.LAZY) {
    // ... download logic ...
    finally {
        downloadJobs.remove(url)
        checkAndStopService()
    }
}

// Register job BEFORE starting it
downloadJobs[url] = job

// Now start the job
job.start()
```

**Why:** 
- `CoroutineStart.LAZY` prevents immediate execution when `launch` is called
- Job is registered in the map BEFORE it can possibly finish
- No race between job completion and map registration
- Guarantees `finally` block can never run before job is in the map

## Thread-Safety Guarantees

After these fixes:

1. ✅ **No ConcurrentModificationException:** ConcurrentHashMap handles concurrent access
2. ✅ **No race conditions:** Job is in map before it can complete
3. ✅ **No stale entries:** Job registration/removal is properly ordered
4. ✅ **No premature service stop:** `checkAndStopService()` sees correct map state
5. ✅ **No HashMap corruption:** Thread-safe data structure prevents internal corruption

## Testing Verification

Build completed successfully:
```
BUILD SUCCESSFUL in 2m 3s
76 actionable tasks: 12 executed, 64 up-to-date
```

## Stress Test Recommendations

When testing on Android 10+ device, verify thread-safety with:

1. **Concurrent Downloads:**
   - Start 3-5 downloads simultaneously
   - Verify all complete without crashes
   - Check service stops properly when all finish

2. **Fast Failures:**
   - Download invalid URLs that fail quickly
   - Verify no stale entries in notification tray
   - Check service stops properly

3. **Cancel During Download:**
   - Cancel downloads at various progress stages
   - Verify clean cancellation
   - Check service stops properly when all cancelled

4. **Mixed Scenarios:**
   - Start multiple downloads
   - Cancel some, let others complete
   - Start new downloads while others run
   - Verify correct service lifecycle

## Related Documents

- `YT_DLP_CRITICAL_FIX_SUMMARY.md` - Android 10+ W^X fix
- `YT_DLP_DATABASE_FIX.md` - RxJava subscription fix
- `YT_DLP_BROADCAST_CLEANUP.md` - Broadcast code removal
- `YT_DLP_ANDROID_LIBRARY_MIGRATION.md` - youtubedl-android migration
- `YT_DLP_INTEGRATION_PLAN.md` - Original implementation plan

## Code References

**File:** `app/src/main/java/com/xhub/browser/download/YtDlpDownloadService.kt`

**Key sections:**
- Line ~51: `ConcurrentHashMap` declaration
- Line ~138-208: `startDownload()` with lazy job start and pre-registration
- Line ~213: `finally` block that removes from map
- Line ~247: `cancelDownload()` accessing map
- Line ~253: `checkAndStopService()` checking map.isEmpty()
