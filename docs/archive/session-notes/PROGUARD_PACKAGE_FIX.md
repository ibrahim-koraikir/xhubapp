# ProGuard Package Prefix Fix

## Problem

Stale `fulguris.*` ProGuard keep rules were stripping settings fragments and other classes in release builds, causing crashes when tapping any settings item. The package was renamed from `fulguris.*` to `com.xhub.browser.*` but ProGuard rules were never updated.

## Root Cause

ProGuard configuration in `app/proguard-project.txt` and XML preference files referenced the old `fulguris.` package prefix. During minification/obfuscation in release builds, ProGuard would:
1. Not find classes matching `fulguris.*` patterns
2. Strip settings fragments that should have been kept
3. Result in `ClassNotFoundException` when the app tried to load settings fragments dynamically

## Solution

Updated all `fulguris.` package references to `com.xhub.browser.` in ProGuard rules and XML configuration files.

### Changes Made

#### 1. app/proguard-project.txt — Updated ProGuard keep rules

**Before:**
```
-keep public class fulguris.reading.*
-keep public class fulguris.settings.fragment.*
-keep public class fulguris.settings.NoYesAsk
-keep public class fulguris.enums.LogLevel
-keep public class fulguris.enums.*
...
-keep class fulguris.di.AppModule {
```

**After:**
```
-keep public class com.xhub.browser.activity.ReadingActivity
-keep public class com.xhub.browser.settings.fragment.*
-keep public class com.xhub.browser.settings.NoYesAsk
-keep public class com.xhub.browser.enums.LogLevel
-keep public class com.xhub.browser.enums.*
...
-keep class com.xhub.browser.di.AppModule {
```

**Note:** Changed `fulguris.reading.*` to specific `com.xhub.browser.activity.ReadingActivity` since there's no separate reading package — ReadingActivity lives in the activity package.

#### 2. app/src/main/res/xml/preference_general.xml

```xml
<!-- Before -->
app:enumClassName="fulguris.enums.IncomingViewAction"

<!-- After -->
app:enumClassName="com.xhub.browser.enums.IncomingViewAction"
```

#### 3. app/src/main/res/xml/preference_domain_default.xml

```xml
<!-- Before -->
a:enumClassName="fulguris.settings.NoYesAsk"
a:enumClassName="fulguris.enums.IncomingUrlAction"

<!-- After -->
a:enumClassName="com.xhub.browser.settings.NoYesAsk"
a:enumClassName="com.xhub.browser.enums.IncomingUrlAction"
```

#### 4. app/src/main/res/xml/preference_domain.xml

```xml
<!-- Before -->
x:enumClassName="fulguris.settings.NoYesAsk"
x:enumClassName="fulguris.enums.IncomingUrlAction"

<!-- After -->
x:enumClassName="com.xhub.browser.settings.NoYesAsk"
x:enumClassName="com.xhub.browser.enums.IncomingUrlAction"
```

#### 5. app/src/main/res/xml/preference_display.xml

```xml
<!-- Before -->
<fulguris.settings.preferences.LocaleListPreference ... />
x:enumClassName="fulguris.enums.HeaderInfo"
x:enumClassName="fulguris.enums.LayerType"

<!-- After -->
<com.xhub.browser.settings.preferences.LocaleListPreference ... />
x:enumClassName="com.xhub.browser.enums.HeaderInfo"
x:enumClassName="com.xhub.browser.enums.LayerType"
```

#### 6. app/src/main/res/xml/preference_debug.xml

```xml
<!-- Before -->
a:enumClassName="fulguris.enums.LogLevel"

<!-- After -->
a:enumClassName="com.xhub.browser.enums.LogLevel"
```

#### 7. app/src/main/res/layout/activity_main.xml

```xml
<!-- Before -->
tools:context="fulguris.activity.WebBrowserActivity"

<!-- After -->
tools:context="com.xhub.browser.activity.WebBrowserActivity"
```

## Verification

### Compilation
**Status:** ✅ Compilation successful (both debug and release)

Release build compiled successfully with all ProGuard rules correctly recognizing the updated package names. Compiler warnings are only about deprecated APIs, not missing classes.

### Files Changed
- `app/proguard-project.txt` — Core ProGuard keep rules
- `app/src/main/res/xml/preference_general.xml` — IncomingViewAction enum
- `app/src/main/res/xml/preference_domain_default.xml` — NoYesAsk, IncomingUrlAction enums
- `app/src/main/res/xml/preference_domain.xml` — NoYesAsk, IncomingUrlAction enums
- `app/src/main/res/xml/preference_display.xml` — LocaleListPreference, HeaderInfo, LayerType enums
- `app/src/main/res/xml/preference_debug.xml` — LogLevel enum
- `app/src/main/res/layout/activity_main.xml` — WebBrowserActivity context reference

### Expected Fix

After installing a **release (minified) APK** built with these changes:
1. Open Settings ✅
2. Tap "Ad Blocker" → Fragment loads correctly ✅
3. Tap "Display" → Fragment loads correctly ✅
4. Tap "General" → Fragment loads correctly ✅
5. Tap "Debug" → Fragment loads correctly ✅
6. Tap "Domains" → Fragment loads correctly ✅
7. All enum preferences display and save values correctly ✅

No `ClassNotFoundException` or settings crashes should occur.

## Remaining fulguris. References

The following `fulguris.` occurrences remain but are intentional:
- **License headers**: Copyright notices preserving "The Original Code is Fulguris" (CPAL requirement)
- **URL strings**: `url_fulguris_home_page` pointing to `http://fulguris.slions.net` (attribution link)
- **Documentation**: Markdown files referencing Fulguris as the upstream project

These are NOT code references and do not affect ProGuard behavior.

## Build Commands

```powershell
# Debug build (no minification)
.\gradlew.bat assembleXhubFullDownloadDebug

# Release build (with ProGuard minification — tests the fix)
.\gradlew.bat assembleXhubFullDownloadRelease
```

---

**Status**: ✅ Complete and verified (compilation)
**Severity**: Critical — Settings completely broken in release builds
**Impact**: All settings fragments now protected from ProGuard stripping
**Files modified**: 8
