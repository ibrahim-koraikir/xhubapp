# Package Rename Instructions: fulguris → com.xhub.browser

## ⚠️ CRITICAL: Use Android Studio's Refactoring Tool

This is a **massive refactoring** affecting 500+ files. **DO NOT** attempt manual find/replace. Use Android Studio's built-in refactoring tool to ensure safety and correctness.

---

## Prerequisites

1. **Commit all changes** to Git before starting
2. **Close any running builds** or processes
3. **Open project in Android Studio**
4. **Ensure project builds successfully** before refactoring

---

## Step 1: Rename Package Directory in Android Studio

### Option A: Refactor → Rename (Recommended)

1. **Open Android Studio**
2. **Switch to Project view** (not Android view)
3. **Navigate to:** `app/src/main/java/fulguris`
4. **Right-click** on the `fulguris` package folder
5. **Select:** Refactor → Rename
6. **Enter new name:** `com.xhub.browser`
7. **Check options:**
   - ✅ Search in comments and strings
   - ✅ Search for text occurrences
   - ✅ Rename package
8. **Click:** Refactor
9. **Review changes** in the preview window
10. **Click:** Do Refactor

### Option B: Move Package (Alternative)

1. **Create new package structure:**
   - Right-click `app/src/main/java`
   - New → Package
   - Enter: `com.xhub.browser`

2. **Move all files:**
   - Select all files in `fulguris` package
   - Drag to `com.xhub.browser` package
   - When prompted, select "Move" not "Copy"
   - Android Studio will update imports automatically

3. **Delete empty `fulguris` directory**

### What This Updates Automatically

✅ All `package` declarations in ~500+ Kotlin files:
```kotlin
// Before
package fulguris.activity

// After  
package com.xhub.browser.activity
```

✅ All `import` statements across the codebase:
```kotlin
// Before
import fulguris.activity.MainActivity

// After
import com.xhub.browser.activity.MainActivity
```

✅ Fully-qualified class references:
```kotlin
// Before
fulguris.Sponsorship.BRONZE

// After
com.xhub.browser.Sponsorship.BRONZE
```

---

## Step 2: Update build.gradle

**File:** `app/build.gradle`

### Change 1: namespace (line ~63)

**Before:**
```groovy
namespace = 'fulguris'
```

**After:**
```groovy
namespace = 'com.xhub.browser'
```

### Change 2: generatedLocaleListDir (line ~40)

**Before:**
```groovy
def generatedLocaleListDir = layout.buildDirectory.dir('generated/source/locale/fulguris/locale')
```

**After:**
```groovy
def generatedLocaleListDir = layout.buildDirectory.dir('generated/source/locale/com/xhub/browser/locale')
```

### Change 3: Sponsorship buildConfigField (lines 194, 200, 206, 212)

**Before:**
```groovy
buildConfigField "fulguris.Sponsorship", "SPONSORSHIP", "fulguris.Sponsorship.BRONZE"
```

**After:**
```groovy
buildConfigField "com.xhub.browser.Sponsorship", "SPONSORSHIP", "com.xhub.browser.Sponsorship.BRONZE"
```

**All 4 occurrences:**
- Line ~194 (undef flavor)
- Line ~200 (playstore flavor)
- Line ~206 (download flavor)
- Line ~212 (fdroid flavor)

### Change 4: LocaleList package (line ~472)

**Before:**
```groovy
localeList << "package fulguris.locale;" << "\n" << "\n"
```

**After:**
```groovy
localeList << "package com.xhub.browser.locale;" << "\n" << "\n"
```

---

## Step 3: Update AndroidManifest.xml

**File:** `app/src/main/AndroidManifest.xml`

### Update all android:name attributes

**Pattern to find:** `fulguris\.`

**Replace with:** `com.xhub.browser.`

### Specific Updates Needed

#### Application Class (line ~35)

**Before:**
```xml
<application
    android:name="fulguris.App"
```

**After:**
```xml
<application
    android:name="com.xhub.browser.App"
```

#### Activities (~20-30 occurrences)

**Before:**
```xml
<activity android:name="fulguris.activity.MainActivity"
<activity android:name="fulguris.activity.IncognitoActivity"
<activity android:name="fulguris.activity.WebBrowserActivity"
<activity android:name="fulguris.activity.SettingsActivity"
```

**After:**
```xml
<activity android:name="com.xhub.browser.activity.MainActivity"
<activity android:name="com.xhub.browser.activity.IncognitoActivity"
<activity android:name="com.xhub.browser.activity.WebBrowserActivity"
<activity android:name="com.xhub.browser.activity.SettingsActivity"
```

#### Activity Aliases (~4 occurrences)

**Before:**
```xml
<activity-alias
    android:name="fulguris.alias.default.MainActivity"
    android:targetActivity="fulguris.activity.MainActivity"
```

**After:**
```xml
<activity-alias
    android:name="com.xhub.browser.alias.default.MainActivity"
    android:targetActivity="com.xhub.browser.activity.MainActivity"
```

#### Receivers, Services, Providers

**Before:**
```xml
<receiver android:name="fulguris.receiver.NotificationReceiver"
<service android:name="fulguris.download.DownloadService"
<provider android:name="fulguris.database.AppProvider"
```

**After:**
```xml
<receiver android:name="com.xhub.browser.receiver.NotificationReceiver"
<service android:name="com.xhub.browser.download.DownloadService"
<provider android:name="com.xhub.browser.database.AppProvider"
```

#### Parent Activity Names

**Before:**
```xml
android:parentActivityName="fulguris.activity.MainActivity"
```

**After:**
```xml
android:parentActivityName="com.xhub.browser.activity.MainActivity"
```

### Find/Replace in AndroidManifest.xml

```xml
Find:    fulguris\.
Replace: com.xhub.browser.
```

**Verify each replacement** before confirming!

---

## Step 4: Update preference_about.xml

**File:** `app/src/main/res/xml/preference_about.xml`

### Update ACTIVITY and FRAGMENT values in <extra> tags

**Pattern to find:** `fulguris\.`

**Replace with:** `com.xhub.browser.`

### Specific Examples

**Before:**
```xml
<extra
    a:name="ACTIVITY"
    a:value="fulguris.activity.SettingsActivity" />

<extra
    a:name="FRAGMENT"
    a:value="fulguris.settings.fragment.AboutSettingsFragment" />
```

**After:**
```xml
<extra
    a:name="ACTIVITY"
    a:value="com.xhub.browser.activity.SettingsActivity" />

<extra
    a:name="FRAGMENT"
    a:value="com.xhub.browser.settings.fragment.AboutSettingsFragment" />
```

### Find/Replace in preference_about.xml

```xml
Find:    fulguris\.
Replace: com.xhub.browser.
```

---

## Step 5: Update Other XML Preference Files

Search all XML files in `app/src/main/res/xml/` for `fulguris` references:

```powershell
grep -r "fulguris\." app/src/main/res/xml/
```

Update any found references following the same pattern.

---

## Step 6: Clean and Rebuild

### Clean Build

```powershell
.\gradlew.bat clean
```

### Delete Generated Files

```powershell
# Delete build directory
Remove-Item -Recurse -Force app\build

# Delete .gradle cache
Remove-Item -Recurse -Force .gradle

# Delete IDE files (optional)
Remove-Item -Recurse -Force .idea
```

### Rebuild

```powershell
.\gradlew.bat assembleXhubFullDownloadDebug
```

### Expected Errors to Fix

After renaming, you may see:

1. **Generated R class references** - Should auto-resolve after clean build
2. **BuildConfig references** - Should auto-resolve after clean build
3. **ProGuard/R8 rules** - Update in `proguard-project.txt` if needed
4. **Test files** - Update package declarations in test files

---

## Step 7: Update ProGuard Rules (If Applicable)

**File:** `app/proguard-project.txt`

**Before:**
```proguard
-keep class fulguris.** { *; }
```

**After:**
```proguard
-keep class com.xhub.browser.** { *; }
```

---

## Step 8: Update Test Files

### Unit Tests

**Directory:** `app/src/test/java/fulguris/`

1. Rename directory to `app/src/test/java/com/xhub/browser/`
2. Update all `package` declarations in test files
3. Update all `import` statements

### Instrumented Tests

**Directory:** `app/src/androidTest/java/fulguris/`

1. Rename directory to `app/src/androidTest/java/com/xhub/browser/`
2. Update all `package` declarations
3. Update all `import` statements

---

## Verification Checklist

After completing all changes:

- [ ] **Build succeeds:** `.\gradlew.bat assembleXhubFullDownloadDebug`
- [ ] **No compilation errors**
- [ ] **Search for remaining references:** `grep -r "package fulguris" app/src/`
- [ ] **Should return:** 0 results (except in comments)
- [ ] **Search for imports:** `grep -r "import fulguris\." app/src/`
- [ ] **Should return:** 0 results
- [ ] **Install APK** on device
- [ ] **Verify package name:** `adb shell pm list packages | grep xhub`
- [ ] **Should show:** `com.xhub.browser.full.download.debug`
- [ ] **App launches** without crashes
- [ ] **Test navigation** to all main screens
- [ ] **Test settings** screens
- [ ] **Check logcat** for package-related errors

---

## Rollback Procedure

If something goes wrong:

1. **Revert all changes:**
   ```bash
   git reset --hard HEAD
   git clean -fdx
   ```

2. **Clean and rebuild:**
   ```bash
   .\gradlew.bat clean
   .\gradlew.bat assembleXhubFullDownloadDebug
   ```

3. **Try again** following instructions more carefully

---

## Common Issues and Solutions

### Issue 1: "Cannot resolve symbol" errors

**Cause:** Android Studio hasn't updated its cache

**Solution:**
1. File → Invalidate Caches / Restart
2. Clean and rebuild

### Issue 2: R class not found

**Cause:** Namespace not updated in build.gradle

**Solution:**
1. Verify `namespace = 'com.xhub.browser'` in build.gradle
2. Clean and rebuild

### Issue 3: BuildConfig not found

**Cause:** Generated files not rebuilt

**Solution:**
1. Delete `app/build` directory
2. Rebuild project

### Issue 4: Manifest merger failed

**Cause:** Mismatched class names in manifest

**Solution:**
1. Search for `fulguris` in AndroidManifest.xml
2. Replace all occurrences with `com.xhub.browser`

### Issue 5: ProGuard errors

**Cause:** ProGuard rules reference old package

**Solution:**
1. Update `proguard-project.txt`
2. Replace `fulguris` with `com.xhub.browser`

---

## Estimated Time

- **Android Studio Refactoring:** 5-10 minutes (automated)
- **Manual file updates:** 15-30 minutes
- **Clean and rebuild:** 10-15 minutes
- **Testing:** 15-30 minutes

**Total:** 45-85 minutes

---

## Files Affected (Estimated)

- **Kotlin source files:** ~500 files
- **Java source files:** ~50 files
- **XML manifest files:** 1 file (~50 class references)
- **XML preference files:** ~20 files
- **Build files:** 1 file
- **Test files:** ~50 files
- **Generated files:** Will be regenerated

**Total:** ~600+ files

---

## Final Directory Structure

**Before:**
```
app/src/main/java/fulguris/
├── activity/
├── adblock/
├── browser/
├── database/
├── di/
├── download/
├── ...
└── App.kt
```

**After:**
```
app/src/main/java/com/xhub/browser/
├── activity/
├── adblock/
├── browser/
├── database/
├── di/
├── download/
├── ...
└── App.kt
```

---

## Post-Refactoring Steps

1. **Commit changes:**
   ```bash
   git add .
   git commit -m "Refactor: Rename package from fulguris to com.xhub.browser"
   ```

2. **Test thoroughly:**
   - Install debug APK
   - Test all features
   - Check for crashes

3. **Update documentation:**
   - README.md
   - CONTRIBUTING.md
   - Any developer guides

4. **Update CI/CD:**
   - Build scripts
   - Test scripts
   - Deployment scripts

---

## Why This Matters

- **Application Identity:** Package name uniquely identifies the app on Android
- **Play Store:** Package must match exactly
- **Code Organization:** Proper namespacing prevents conflicts
- **Professional:** `com.xhub.browser` follows Android conventions
- **Branding:** Complete separation from Fulguris

---

**REMEMBER: Use Android Studio's refactoring tool for Step 1. Do not attempt manual package renaming!**
