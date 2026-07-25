# yt-dlp API Compatibility Fix

**Date:** June 14, 2026  
**Status:** ✅ COMPLETED

## Summary

Fixed API level compatibility issue where `stopForeground(STOP_FOREGROUND_REMOVE)` would crash on Android API 21-23 devices. Migrated to `androidx.core.app.ServiceCompat` for backward compatibility.

## Problem Identified

### API Level Mismatch
**Issue:** `checkAndStopService()` called `stopForeground(STOP_FOREGROUND_REMOVE)`:
```kotlin
// BEFORE - CRASHES ON API 21-23
stopForeground(STOP_FOREGROUND_REMOVE)
```

**Why It Failed:**
- `stopForeground(int)` method introduced in **API 24** (Android 7.0)
- `STOP_FOREGROUND_REMOVE` constant introduced in **API 24**
- App's `minSdk = 21` (Android 5.0)
- **Result:** `NoSuchMethodError` crash on API 21-23 devices

### Runtime Failure Scenario
```
Device: Android 5.0-6.0 (API 21-23)
1. User downloads video successfully
2. Service attempts to stop: stopForeground(STOP_FOREGROUND_REMOVE)
3. System throws: NoSuchMethodError
4. Service crashes
5. App may become unstable
```

### Lint Error
```
Call requires API level 24 (current min is 21): 
android.app.Service#stopForeground(int)
```

## Fix Applied

### Migrated to ServiceCompat
Changed to use AndroidX compatibility wrapper:

```kotlin
// BEFORE (API 24+ only)
stopForeground(STOP_FOREGROUND_REMOVE)

// AFTER (API 21+ compatible)
androidx.core.app.ServiceCompat.stopForeground(
    this, 
    androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
)
```

**File:** `app/src/main/java/com/xhub/browser/download/YtDlpDownloadService.kt`
**Method:** `checkAndStopService()` (line ~277)

## Why ServiceCompat Works

### Automatic API Level Handling
`ServiceCompat.stopForeground()` internally handles API differences:

```kotlin
// ServiceCompat implementation (simplified)
fun stopForeground(service: Service, flags: Int) {
    if (Build.VERSION.SDK_INT >= 24) {
        service.stopForeground(flags)  // Use new API
    } else {
        // Convert flags to boolean for old API
        val removeNotification = (flags and STOP_FOREGROUND_REMOVE) != 0
        service.stopForeground(removeNotification)  // Use old API
    }
}
```

### Benefits
1. ✅ **Works on all API levels** - API 21-35+
2. ✅ **No manual version checks** - AndroidX handles it
3. ✅ **Same behavior** - Notification removed on all versions
4. ✅ **Lint clean** - No API level warnings
5. ✅ **Future-proof** - AndroidX updates automatically

## Alternative Approaches (Not Used)

### Option 1: Manual Version Check
```kotlin
// More verbose, but works
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    stopForeground(STOP_FOREGROUND_REMOVE)
} else {
    stopForeground(true)
}
```

**Why not used:** More boilerplate, less maintainable

### Option 2: Raise minSdk to 24
```kotlin
// Simplest but excludes users
android {
    defaultConfig {
        minSdk = 24  // Drops API 21-23 support
    }
}
```

**Why not used:** Would exclude ~2-3% of Android devices still on API 21-23

### Option 3: @TargetApi Annotation
```kotlin
// Suppresses lint but doesn't fix runtime crash
@TargetApi(Build.VERSION_CODES.N)
fun checkAndStopService() {
    stopForeground(STOP_FOREGROUND_REMOVE)  // Still crashes on API 21-23!
}
```

**Why not used:** Suppresses warning but doesn't solve the problem

**Decision:** ServiceCompat is the recommended Android approach for this exact use case.

## API Level Support

### stopForeground Variants

| Method | API Level | Behavior |
|--------|-----------|----------|
| `stopForeground(boolean)` | API 5+ | `true` = remove notification, `false` = keep |
| `stopForeground(int)` | API 24+ | Flags: `STOP_FOREGROUND_REMOVE`, `STOP_FOREGROUND_DETACH` |
| `ServiceCompat.stopForeground()` | API 14+ | Handles both automatically |

### App Support Range
- **minSdk:** 21 (Android 5.0 Lollipop) - ~99.6% of devices
- **targetSdk:** 35 (Android 15) - Latest features
- **compileSdk:** 35 - Build against latest APIs

With ServiceCompat, the app works correctly across all supported API levels.

## Verification

Build completed successfully:
```
BUILD SUCCESSFUL in 1m 22s
76 actionable tasks: 9 executed, 67 up-to-date
```

No lint warnings for API level compatibility.

## Testing Recommendations

Test on devices across API levels:

1. **API 21-23 (Critical):**
   - Android 5.0-6.0 devices or emulators
   - Download a video
   - Let download complete
   - Verify service stops without crash
   - Check notification is removed

2. **API 24-28:**
   - Android 7.0-9.0 devices
   - Same test as above
   - Verify consistent behavior

3. **API 29-35:**
   - Android 10-15 devices
   - Same test as above
   - Verify scoped storage works correctly

Expected result: Service stops cleanly on ALL API levels with notification removed.

## Related API Compatibility Issues

Other AndroidX compat wrappers used in the codebase:

1. ✅ `NotificationCompat` - Notifications (API 4+)
2. ✅ `ActivityCompat` - Permissions (API 4+)
3. ✅ `ContextCompat` - Resources (API 4+)
4. ✅ `ServiceCompat` - Services (API 14+) - **NOW ADDED**

These wrappers ensure the app works across the full minSdk-targetSdk range.

## Related Documents

- `YT_DLP_CRITICAL_FIX_SUMMARY.md` - Android 10+ W^X fix
- `YT_DLP_DATABASE_FIX.md` - RxJava subscription fix
- `YT_DLP_THREAD_SAFETY_FIX.md` - ConcurrentHashMap fix
- `YT_DLP_SCOPED_STORAGE_FIX.md` - Scoped storage fix
- `WEBVIEW_SECURITY_FIX.md` - WebView allowFileAccess fix
- `YT_DLP_ANDROID_LIBRARY_MIGRATION.md` - youtubedl-android migration

## Code References

**File:** `app/src/main/java/com/xhub/browser/download/YtDlpDownloadService.kt`

**Method:** `checkAndStopService()` (lines 274-280)

**Change:**
```kotlin
private fun checkAndStopService() {
    if (downloadJobs.isEmpty()) {
        Timber.d("No active downloads, stopping service")
        androidx.core.app.ServiceCompat.stopForeground(
            this, 
            androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
        )
        stopSelf()
    }
}
```

**AndroidX Dependency:** `androidx.core:core-ktx` (already in build.gradle)
