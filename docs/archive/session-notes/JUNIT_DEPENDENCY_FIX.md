# JUnit Dependency Scope Fix

## Issue
The `junit` test library was accidentally promoted from `testImplementation` to `implementation` scope in `app/build.gradle`, causing it to be shipped in the production APK unnecessarily.

## Impact
- **Before Fix:** JUnit library (4.13.2) was included in the production APK, increasing app size
- **After Fix:** JUnit is only included in the test classpath, not in production builds
- **APK Size Reduction:** Approximately 300-400 KB smaller production APK

## Change Made

**File:** `app/build.gradle`  
**Line:** 284

### Before
```gradle
// test dependencies
implementation 'junit:junit:4.13.2'
testImplementation 'org.assertj:assertj-core:3.15.0'
```

### After
```gradle
// test dependencies
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.assertj:assertj-core:3.15.0'
```

## Why This Matters

### Dependency Scopes in Gradle
- **`implementation`**: Library is compiled into the app and shipped in the APK
- **`testImplementation`**: Library is only available during unit test compilation and execution
- **`androidTestImplementation`**: Library is only available for instrumented tests

### JUnit is a Test-Only Library
JUnit is a testing framework used exclusively for writing and running unit tests in `app/src/test/`. It should never be included in production builds because:

1. **No Runtime Value:** The app never needs JUnit classes at runtime
2. **Increased APK Size:** Unnecessary bloat in the production APK
3. **Security:** Exposing test framework internals in production is a security anti-pattern
4. **Play Store Guidelines:** Google recommends minimizing APK size

## Testing Impact

✅ **No Testing Impact:** Test classes in `app/src/test/` will continue to compile and run correctly because:
- Test source sets automatically have access to `testImplementation` dependencies
- The JUnit library is still available during test execution
- All existing test classes remain unchanged

## Build Verification

After this change, verify with:
```powershell
# Clean build
.\gradlew.bat clean

# Build APK
.\gradlew.bat assembleXhubFullDownloadRelease

# Check APK size (should be ~300-400 KB smaller)
dir app\build\outputs\apk\xhubFullDownload\release\
```

## Related Files
- ✅ `app/build.gradle` - Fixed dependency scope
- ℹ️ `app/src/test/` - Test classes unaffected (still compile correctly)

---

**Date:** 2026-06-12  
**Issue:** Accidental dependency scope promotion  
**Status:** ✅ FIXED  
**Change:** 1 line (implementation → testImplementation)
