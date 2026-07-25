# XHub Package Rebrand - Complete

## Overview

Successfully rebranded the Fulguris browser to XHub with proper Android application ID and package naming.

## Changes Made

### 1. Application ID Changed

**File:** `app/build.gradle`

**Before:**
```groovy
slions {
    dimension "BRAND"
    applicationId "net.slions.fulguris"
    versionCode libs.versions.slions.version.code.get().toInteger()
    versionName libs.versions.slions.version.name.get()
}
```

**After:**
```groovy
xhub {
    dimension "BRAND"
    applicationId "com.xhub.browser"
    versionCode libs.versions.slions.version.code.get().toInteger()
    versionName libs.versions.slions.version.name.get()
}
```

**New Application IDs:**
- `com.xhub.browser.full.download` - Download channel (unlimited tabs)
- `com.xhub.browser.full.playstore` - Google Play Store channel
- `com.xhub.browser.full.fdroid` - F-Droid channel
- Debug variants add `.debug` suffix

### 2. Flavor Names Updated

**Removed:**
- `slions` flavor → Replaced with `xhub`
- `styx` flavor → Commented out (XHub is sole brand)

**Active Flavors:**
- **BRAND:** `xhub`
- **VERSION:** `full`
- **PUBLISHER:** `download`, `playstore`, `fdroid`

**Valid Build Variants:**
- `xhubFullDownload` (Debug/Release)
- `xhubFullPlaystore` (Debug/Release)
- `xhubFullFdroid` (Debug/Release)

### 3. APK Output Names Changed

**File:** `app/build.gradle` (lines ~255-265)

**Before:**
```groovy
if (variant.flavorName.contains("slions")) {
    outputFileName = outputFileName.replaceFirst("^${base.archivesName.get()}", "Fulguris-v$versionName")
}
```

**After:**
```groovy
if (variant.flavorName.contains("xhub")) {
    outputFileName = outputFileName.replaceFirst("^${base.archivesName.get()}", "XHub-v$versionName")
}
```

**New APK Names:**
- `XHub-v1.17.0-xhubFullDownload-debug.apk`
- `XHub-v1.17.0-xhubFullDownload-release.apk`
- `XHub-v1.17.0-xhubFullPlaystore-release.apk`
- `XHub-v1.17.0-xhubFullFdroid-release.apk`

### 4. Variant Filter Updated

**File:** `app/build.gradle`

**Before:**
```groovy
variantFilter { variant ->
    def names = variant.flavors*.name
    // slions variant do not support base version and undef publisher
    if ((names.contains("slions") && (names.contains("undef") || names.contains("base")))
            // styx variant only support base version and undef publisher
            || (names.contains("styx") && !(names.contains("undef") && names.contains("base")))) {
        setIgnore(true)
    }
}
```

**After:**
```groovy
variantFilter { variant ->
    def names = variant.flavors*.name
    // xhub variant does not support base version and undef publisher
    if (names.contains("xhub") && (names.contains("undef") || names.contains("base"))) {
        setIgnore(true)
    }
}
```

### 5. URI Scheme Changed from `fulguris://` to `xhub://`

#### AndroidManifest.xml Changes

**File:** `app/src/main/AndroidManifest.xml`

Updated 4 intent-filter blocks (lines 133, 214, 271, 293):

**Before:**
```xml
<data android:scheme="fulguris" />
```

**After:**
```xml
<data android:scheme="xhub" />
```

**Affected Activities:**
- `fulguris.activity.MainActivity` (2 intent-filters)
- `fulguris.alias.default.IncognitoActivity`
- `fulguris.activity.IncognitoActivity`

#### Kotlin Code Changes

**File:** `app/src/main/java/fulguris/utils/SessionRecovery.kt`

**Line 291 - Before:**
```kotlin
"fulguris://",
```

**Line 291 - After:**
```kotlin
"xhub://",
```

**Line 365 - Before:**
```kotlin
str.startsWith("fulguris://") ||
```

**Line 365 - After:**
```kotlin
str.startsWith("xhub://") ||
```

## Impact

### Application Identification

- **Old:** `net.slions.fulguris.full.download`
- **New:** `com.xhub.browser.full.download`

This is a **completely new application** from Android's perspective:
- Cannot update existing Fulguris installations
- Users must uninstall Fulguris and install XHub fresh
- All data (bookmarks, history, settings) from Fulguris will NOT transfer

### Deep Links

- **Old:** `fulguris://settings`
- **New:** `xhub://settings`

Any external apps, bookmarks, or shortcuts using `fulguris://` URIs will no longer work.

### Build Commands

**Old:**
```bash
.\gradlew.bat assembleSlionsFullDownloadDebug
.\gradlew.bat assembleSlionsFullDownloadRelease
```

**New:**
```bash
.\gradlew.bat assembleXhubFullDownloadDebug
.\gradlew.bat assembleXhubFullDownloadRelease
```

### APK Location

**Path:** `app/build/outputs/apk/xhubFullDownload/debug/`

**Files:**
- `XHub-v1.17.0-xhubFullDownload-debug.apk`
- `XHub-v1.17.0-xhubFullDownload-release.apk`

## Verification

### Build Verification

✅ **BUILD SUCCESSFUL**
```
.\gradlew.bat assembleXhubFullDownloadDebug
BUILD SUCCESSFUL in 10m 35s
```

### Package Verification

To verify the application ID in built APK:

```powershell
# Using aapt (from Android SDK build-tools)
aapt dump badging app\build\outputs\apk\xhubFullDownload\debug\XHub-v*.apk | findstr package

# Expected output:
# package: name='com.xhub.browser.full.download.debug' versionCode='1170000' versionName='1.17.0'
```

### URI Scheme Verification

Check AndroidManifest in APK:

```powershell
# Extract APK (it's a ZIP file)
# Open AndroidManifest.xml (binary, need tool)
# Or check during runtime with adb logcat
```

## Migration Guide for Users

### Cannot Update from Fulguris

XHub is a **new application** with a different package name. Users cannot update from Fulguris to XHub through normal app updates.

### Installation Steps

1. **Backup Fulguris Data** (if needed):
   - Export bookmarks
   - Note important settings
   - Save session tabs list

2. **Install XHub:**
   - Download XHub APK
   - Install (may show "Do you want to install this app?")
   - Grant permissions

3. **Configure XHub:**
   - Import bookmarks manually
   - Reconfigure settings
   - Re-add shortcuts/widgets

4. **Optional - Uninstall Fulguris:**
   - Settings → Apps → Fulguris → Uninstall
   - This frees up storage space

### Data Does NOT Transfer

- ❌ Bookmarks
- ❌ History
- ❌ Settings
- ❌ Session tabs
- ❌ Downloads history
- ❌ Cookies/cache

Users start fresh with XHub.

## Google Play Store Considerations

### New Listing Required

- Cannot use existing Fulguris Play Store listing
- Must create **new app** in Google Play Console
- New package name = new application

### Steps for Play Store:

1. **Create new app** in Play Console
2. **Package name:** `com.xhub.browser.full.playstore`
3. **Upload APK/Bundle:** `XHub-v*.apk`
4. **Fill out store listing** (title, description, screenshots)
5. **Set pricing** (free or paid)
6. **Submit for review**

### Cannot Migrate Users

- Existing Fulguris users will NOT auto-update to XHub
- They must discover and install XHub as a new app
- Consider:
  - In-app notification in Fulguris about XHub
  - Blog post/announcement
  - Email to users (if available)

## F-Droid Considerations

### New Package

- F-Droid also treats this as a new application
- Cannot update existing Fulguris from F-Droid repository
- Must submit as new application

### Metadata Changes

Update F-Droid metadata:
- **Package ID:** `com.xhub.browser.full.fdroid`
- **App Name:** XHub
- **Summary:** [New description]
- **Icon:** XHub icon
- **Screenshots:** XHub screenshots

## Testing Checklist

Before distribution:

- [ ] Build debug APK successfully
- [ ] Install debug APK on device
- [ ] Verify package name: `adb shell pm list packages | grep xhub`
- [ ] Test deep links: `adb shell am start -a android.intent.action.VIEW -d "xhub://settings"`
- [ ] Test web intents: `adb shell am start -a android.intent.action.VIEW -d "https://example.com"`
- [ ] Verify app appears in default browser selection
- [ ] Check app icon shows correctly
- [ ] Verify app name displays as "xhub" (from strings.xml)
- [ ] Build release APK successfully
- [ ] Sign release APK with keystore
- [ ] Verify signed APK installs correctly

## Rollback Procedure

If you need to revert to Fulguris branding:

1. **Revert build.gradle:**
   ```groovy
   slions {
       dimension "BRAND"
       applicationId "net.slions.fulguris"
       ...
   }
   ```

2. **Revert AndroidManifest.xml:**
   ```xml
   <data android:scheme="fulguris" />
   ```

3. **Revert SessionRecovery.kt:**
   ```kotlin
   "fulguris://",
   ```

4. **Rebuild:**
   ```bash
   .\gradlew.bat clean
   .\gradlew.bat assembleSlionsFullDownloadDebug
   ```

## Related Files Modified

- `app/build.gradle` - Flavor names, application ID, output filename, variant filter
- `app/src/main/AndroidManifest.xml` - URI schemes (4 locations)
- `app/src/main/java/fulguris/utils/SessionRecovery.kt` - URI scheme validation

## Documentation Files

- `PACKAGE_REBRAND_XHUB.md` - This file (implementation summary)
- `CPAL_COMPLIANCE.md` - License compliance (still applies)
- `BUILD_INSTRUCTIONS.md` - Build commands (needs update)

## Next Steps

1. **Update build scripts** to use new variant names
2. **Update CI/CD** pipelines with new variant names
3. **Update documentation** with new package names
4. **Create migration announcement** for Fulguris users
5. **Prepare Play Store** listing for XHub
6. **Test extensively** before public release
7. **Consider** data migration tool for users

---

**Status:** ✅ Complete - XHub rebrand fully implemented and building successfully.

**Build Variant:** `assembleXhubFullDownloadDebug` / `assembleXhubFullDownloadRelease`

**Package Name:** `com.xhub.browser.full.download[.debug]`

**APK Output:** `XHub-v1.17.0-xhubFullDownload-{debug|release}.apk`
