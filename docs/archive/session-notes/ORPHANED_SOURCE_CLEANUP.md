# Orphaned Source Directory Cleanup

## Overview
Cleaned up orphaned and misplaced source directories from the XHub rebrand, removing legacy package paths and ensuring all flavor-specific code uses the correct package structure.

---

## Changes Made

### 1. Deleted Orphaned `styx` Directory
**Directory Removed:** `app/src/styx/`

**Reason:** The styx product flavor is disabled in the build configuration, making this entire source directory orphaned and unused.

**What was removed:**
- `app/src/styx/java/fulguris/settings/fragment/SponsorshipSettingsFragment.kt`
- Entire `app/src/styx/` directory tree

**Impact:**
- Cleaner codebase with no unused code
- Eliminates confusion about which flavors are active
- Reduces repository size
- No functional impact (styx flavor was already disabled)

---

### 2. Fixed F-Droid Flavor Package Path
**File Moved:**
```
FROM: app/src/fdroid/java/acr/browser/lightning/settings/fragment/SponsorshipSettingsFragment.kt
TO:   app/src/fdroid/java/com/xhub/browser/settings/fragment/SponsorshipSettingsFragment.kt
```

**Package Declaration:** Already correct as `com.xhub.browser.settings.fragment`

**Issue:** The file had the correct package declaration but was located in a legacy directory structure (`acr/browser/lightning/*`) instead of matching the actual package path.

**Impact:**
- Directory structure now matches package declaration
- Consistent with Kotlin/Java best practices (directory structure = package structure)
- Easier for IDEs to navigate and autocomplete
- Prevents future confusion about package locations

**Old Legacy Directory Removed:**
- `app/src/fdroid/java/acr/` (entire tree removed after file moved)

---

## Product Flavor Context

### Active Flavors (XHub)
After rebranding, these are the active product flavors:

1. **xhubFullDownload** - Download distribution channel
2. **xhubFullPlaystore** - Google Play Store distribution  
3. **xhubFullFdroid** - F-Droid distribution

### Disabled/Removed Flavors
- **slions*** - Old flavor prefix (renamed to xhub)
- **styx** - Disabled flavor (source directory now removed)

---

## File Structure Verification

### Before Cleanup
```
app/src/
├── styx/
│   └── java/
│       └── fulguris/                          ❌ Orphaned, old package
│           └── settings/
│               └── fragment/
│                   └── SponsorshipSettingsFragment.kt
│
└── fdroid/
    └── java/
        └── acr/                               ❌ Legacy package path
            └── browser/
                └── lightning/
                    └── settings/
                        └── fragment/
                            └── SponsorshipSettingsFragment.kt
```

### After Cleanup
```
app/src/
└── fdroid/
    └── java/
        └── com/                               ✅ Correct package path
            └── xhub/
                └── browser/
                    └── settings/
                        └── fragment/
                            └── SponsorshipSettingsFragment.kt
```

---

## Build Impact

### No Breaking Changes
- The fdroid file already had the correct package declaration (`com.xhub.browser.settings.fragment`)
- Only the directory structure changed to match the package
- Kotlin compiler uses package declaration, not directory structure
- No code changes needed

### Build Commands Still Work
```powershell
# F-Droid variant
.\gradlew.bat assembleXhubFullFdroidDebug

# Download variant  
.\gradlew.bat assembleXhubFullDownloadDebug

# Playstore variant
.\gradlew.bat assembleXhubFullPlaystoreDebug
```

---

## Related Rebranding Changes

This cleanup is part of the comprehensive XHub rebranding. Related package changes:

1. **Main Package Rename**
   - Old: `fulguris.*` → New: `com.xhub.browser.*`
   - Old: `acr.browser.lightning.*` → New: `com.xhub.browser.*`

2. **Application ID**
   - Old: `fulguris.browser.app.*` → New: `com.xhub.browser.*`

3. **Product Flavors**
   - Old: `slions*` → New: `xhub*`

4. **URI Scheme**
   - Old: `fulguris://` → New: `xhub://`

---

## Verification

### Verify styx directory removed:
```powershell
Test-Path "app\src\styx"
# Should return: False
```

### Verify fdroid file in correct location:
```powershell
Test-Path "app\src\fdroid\java\com\xhub\browser\settings\fragment\SponsorshipSettingsFragment.kt"
# Should return: True
```

### Verify old fdroid path removed:
```powershell
Test-Path "app\src\fdroid\java\acr"
# Should return: False
```

### Search for remaining legacy packages:
```powershell
# Should find no results in source files
findstr /s /i "package fulguris" app\src\*.kt
findstr /s /i "package acr.browser.lightning" app\src\*.kt
```

---

## SponsorshipSettingsFragment Details

### Purpose
Flavor-specific implementation for F-Droid distribution. The F-Droid variant redirects users to Google Play Store for sponsorship/subscription features.

### File Content
```kotlin
package com.xhub.browser.settings.fragment

/**
 * Sponsorship settings for Fdroid variant.
 * We just redirect users to Google Play Store if they want to sponsor us.
 */
class SponsorshipSettingsFragment : RedirectSponsorshipSettingsFragment()
```

### Why This Exists
F-Droid has strict policies against in-app purchases and subscription features. This variant-specific class provides an alternative implementation that redirects users to the Play Store version if they want to access premium features.

---

## Git Operations Recommended

Since these are file moves and deletions:

```bash
# Stage the deletion
git rm -r app/src/styx/

# Stage the move (Git will auto-detect)
git add app/src/fdroid/java/com/xhub/browser/settings/fragment/SponsorshipSettingsFragment.kt
git rm app/src/fdroid/java/acr/browser/lightning/settings/fragment/SponsorshipSettingsFragment.kt

# Verify Git detected the move
git status
# Should show: "renamed: app/src/fdroid/java/acr/.../SponsorshipSettingsFragment.kt -> app/src/fdroid/java/com/.../SponsorshipSettingsFragment.kt"

# Commit
git commit -m "Clean up orphaned styx directory and fix fdroid package path

- Remove unused app/src/styx/ directory (styx flavor disabled)
- Move fdroid SponsorshipSettingsFragment to correct package path
- Remove legacy acr.browser.lightning directory structure"
```

---

## Summary

✅ **Deleted:** `app/src/styx/` (orphaned styx flavor source)  
✅ **Moved:** F-Droid `SponsorshipSettingsFragment.kt` to correct package path  
✅ **Removed:** Legacy `acr/browser/lightning/` directory structure  
✅ **Result:** Clean, consistent package structure across all active flavors

---

**Date:** 2026-06-12  
**Status:** ✅ COMPLETE  
**Files Deleted:** 1 directory tree (`styx/`)  
**Files Moved:** 1 file (fdroid SponsorshipSettingsFragment.kt)  
**Directories Cleaned:** 2 (styx, acr)
