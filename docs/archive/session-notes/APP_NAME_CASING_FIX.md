# App Name Casing Correction

## Summary

Fixed inconsistent app name casing from lowercase "xhub" to proper branded form "XHub" across all string resources.

---

## Changes Made ✅

### 1. donottranslate.xml

**File:** `app/src/main/res/values/donottranslate.xml`

**Changed:**
```xml
<!-- OLD: -->
<string name="app_name">xhub</string>
<string name="app_name_debug">xhub Debug</string>

<!-- NEW: -->
<string name="app_name">XHub</string>
<string name="app_name_debug">XHub Debug</string>
```

### 2. strings.xml

**File:** `app/src/main/res/values/strings.xml`

**Changed:**
```xml
<!-- OLD: -->
<string name="locale_app_name">xhub Web Browser</string>
<string name="home_brand_name">xHub</string>

<!-- NEW: -->
<string name="locale_app_name">XHub Browser</string>
<string name="home_brand_name">XHub</string>
```

---

## Impact

### User-Visible Changes

These strings appear in:

1. **app_name** - Used in:
   - Android Manifest as application label
   - Settings app list
   - Recent apps / task switcher
   - Notification titles
   - App shortcuts

2. **app_name_debug** - Used in:
   - Debug build variant only
   - Distinguishes debug from release builds

3. **locale_app_name** - Used in:
   - Translatable app name display
   - May appear in various UI contexts
   - Can be localized to different languages

4. **home_brand_name** - Used in:
   - Home screen branding
   - Welcome messages
   - In-app branding elements

### Before vs. After

| Location | Before | After |
|----------|--------|-------|
| App label | xhub | XHub |
| Debug label | xhub Debug | XHub Debug |
| Full name | xhub Web Browser | XHub Browser |
| Brand display | xHub | XHub |

---

## Consistency Notes

### Proper Brand Casing: "XHub"

The brand name should always be written as **XHub** with:
- Capital X
- Capital H
- No space between

**Correct:** XHub, XHub Browser, XHub Debug
**Incorrect:** xhub, xHub, Xhub, X Hub, X-Hub

### Usage Guidelines

- **Short form:** "XHub"
- **Full name:** "XHub Browser"
- **Debug variant:** "XHub Debug"
- **Never lowercase:** Avoid "xhub" except in technical contexts (package names, file names, URLs)

---

## Localization Impact

### Translation Files

The `locale_app_name` string is translatable, so translators should be aware:

- **English:** "XHub Browser"
- **Other languages:** Should translate "Browser" but keep "XHub" unchanged

**Example for German:**
```xml
<string name="locale_app_name">XHub Browser</string>  <!-- Keep XHub as-is -->
```

**NOT:**
```xml
<string name="locale_app_name">xhub Browser</string>  <!-- Wrong casing -->
```

### Files Potentially Affected

If you have locale-specific overrides of these strings in:
- `app/src/main/res/values-*/strings.xml`

You should verify they also use proper "XHub" casing.

---

## Build Verification

**Build Status:** ✅ SUCCESSFUL

```
BUILD SUCCESSFUL in 24s
74 actionable tasks: 1 executed, 73 up-to-date
```

All string references compile correctly with the new casing.

---

## Testing Recommendations

After installing the updated APK:

1. **Check App Label:**
   - Home screen launcher icon text
   - App drawer entry
   - Settings → Apps → App list

2. **Check Task Switcher:**
   - Recent apps / multitasking view
   - Should show "XHub" not "xhub"

3. **Check Notifications:**
   - Any notifications from the app
   - Should use "XHub" in title

4. **Check Settings:**
   - Settings → About
   - App name display

5. **Check Debug Build:**
   - Install debug APK
   - Verify shows "XHub Debug" to distinguish from release

---

## Related Changes

This completes the branding consistency work:

- ✅ Package namespace: `com.xhub.browser`
- ✅ Application ID: `com.xhub.browser.full.*`
- ✅ User-facing strings: "XHub" throughout
- ✅ App name: "XHub" (proper casing)
- ✅ Attribution: "Powered by Fulguris" (CPAL compliance)

---

## Why This Matters

### Brand Consistency

Using inconsistent casing undermines brand identity:
- "xhub" looks unpolished or like a typo
- "XHub" looks professional and intentional
- Consistency across all touchpoints builds brand recognition

### User Perception

Users see the app name in many contexts:
- First impression: App store listing
- Daily use: Home screen icon
- Switching apps: Task switcher
- Troubleshooting: Settings app list

Consistent, proper casing improves perceived quality.

---

## Files Modified

1. `app/src/main/res/values/donottranslate.xml` - Lines 25-26
2. `app/src/main/res/values/strings.xml` - Lines 677, 916

---

## Related Documentation

- `STRINGS_REBRAND_XHUB.md` - Overall string rebranding documentation
- `REBRAND_SESSION_SUMMARY.md` - Complete rebranding progress
- `TODO_XHUB_INFRASTRUCTURE.md` - Pre-release checklist

---

**Completed:** 2026-06-11
**Build Status:** ✅ Successful
**Impact:** User-visible app name casing corrected
