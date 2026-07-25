# Preference About XML Updates - CPAL Compliance

## Changes Made

### 1. CPAL License URL Updated ✅
**File:** `app/src/main/res/xml/preference_about.xml` (Line 181)

**Changed:**
```xml
<!-- OLD: -->
<intent
    a:action="android.intent.action.VIEW"
    a:data="https://github.com/Slion/Fulguris/blob/main/LICENSE-CPAL-1.0">

<!-- NEW: -->
<intent
    a:action="android.intent.action.VIEW"
    a:data="https://github.com/YOUR_ORG/xhub/blob/main/LICENSE-CPAL-1.0">
```

**Reason:** The About screen's licenses section was linking to the original Fulguris GitHub repository. This has been updated to point to XHub's own fork (using a placeholder URL).

**⚠️ ACTION REQUIRED BEFORE RELEASE:**
Replace `YOUR_ORG/xhub` with your actual GitHub organization and repository name:
- Example: `https://github.com/myorg/xhub-browser/blob/main/LICENSE-CPAL-1.0`

---

### 2. Package Name References Verified ✅
**File:** `app/src/main/res/xml/preference_about.xml`

**Status:** All ACTIVITY and FRAGMENT extra values have already been updated from `fulguris.activity.*` to `com.xhub.browser.activity.*`

**Verified locations:**
- All `<extra a:name="ACTIVITY">` tags → `com.xhub.browser.activity.SettingsActivity`
- All `<extra a:name="FRAGMENT">` tags → Correctly updated to `com.xhub.browser.settings.fragment.*`

---

### 3. CPAL Exhibit B Attribution Verified ✅
**File:** `app/src/main/res/xml/preference_about.xml` (Lines 157-175)

**Status:** Attribution section already exists and is correctly configured.

**Attribution Content:**
```xml
<x.PreferenceCategory a:title="@string/pref_category_title_attribution">
    <Preference
        a:summary="@string/fulguris_attribution_summary"
        a:title="@string/fulguris_attribution_title"
        x:icon="@drawable/ic_lightning"
        x:iconSpaceReserved="false"
        x:singleLineTitle="false">

        <intent
            a:action="android.intent.action.VIEW"
            a:data="@string/url_fulguris_home_page">
        </intent>
    </Preference>
</x.PreferenceCategory>
```

**String Resources (from `strings.xml`):**
- `fulguris_attribution_title`: "Powered by Fulguris"
- `fulguris_attribution_summary`: "Copyright © 2020 Stéphane Lenclud"
- `url_fulguris_home_page`: "http://fulguris.slions.net"

**Result:** When users open Settings → About → Attribution, they see:
- **Title:** "Powered by Fulguris"
- **Summary:** "Copyright © 2020 Stéphane Lenclud"
- **Link:** Opens http://fulguris.slions.net

This satisfies CPAL Exhibit B requirements for attribution to the original author.

---

## CPAL License Compliance

### Why This Matters

The CPAL (Common Public Attribution License) Version 1.0 requires:
1. **Exhibit B Attribution:** Visible attribution to the original author in the user interface
2. **Source Availability:** Link to the source code including the license file
3. **License Preservation:** Maintain all copyright notices and license files

### What We've Done

✅ **Attribution Section:** Displays "Powered by Fulguris" with copyright notice in Settings → About
✅ **License Link:** Points to XHub's own fork's CPAL-1.0 license file (placeholder URL)
✅ **String Resources:** Preserved original attribution strings
✅ **Original Homepage Link:** Maintained link to http://fulguris.slions.net

---

## User-Facing Impact

### Settings → About Screen Structure

```
About
├── [XHub Version] (with XHub branding)
├── [WebView DevTools]
├── [Developer Options]
│
├── Contact Us
│   ├── Discord
│   └── Email
│
├── Legal
│   ├── Terms and Conditions
│   └── Privacy Policy
│
├── Attribution  ← CPAL Exhibit B Requirement
│   └── Powered by Fulguris
│       Copyright © 2020 Stéphane Lenclud
│       [Links to: http://fulguris.slions.net]
│
└── Licenses
    ├── XHub (CPAL 1.0)
    │   [Links to: YOUR_ORG/xhub LICENSE-CPAL-1.0]  ← UPDATE THIS
    ├── Lightning Browser (MPL 2.0)
    ├── Yuzu Browser (Apache 2.0)
    ├── Android Open Source Project (Apache 2.0)
    └── jsoup (MIT)
```

---

## Build Verification

### Build Status: ✅ SUCCESSFUL

```
BUILD SUCCESSFUL in 1m 26s
74 actionable tasks: 1 executed, 73 up-to-date
```

All changes have been verified to compile without errors.

---

## Pre-Release Checklist

Before publishing XHub:

- [ ] **Replace Placeholder URL**
  - File: `app/src/main/res/xml/preference_about.xml` (line 181)
  - Find: `https://github.com/YOUR_ORG/xhub/blob/main/LICENSE-CPAL-1.0`
  - Replace with: Your actual GitHub repository URL
  - Example: `https://github.com/myorg/xhub-browser/blob/main/LICENSE-CPAL-1.0`

- [ ] **Verify Attribution Link Works**
  - Open Settings → About → Attribution
  - Tap "Powered by Fulguris"
  - Should open: http://fulguris.slions.net

- [ ] **Verify CPAL License Link Works**
  - Open Settings → About → Licenses → XHub
  - Should open your GitHub repository's LICENSE-CPAL-1.0 file

- [ ] **Test All About Screen Links**
  - Privacy Policy
  - Terms & Conditions
  - Discord (or remove if not used)
  - Email contact
  - All license links

---

## Related Files

- `app/src/main/res/xml/preference_about.xml` - About screen preferences
- `app/src/main/res/values/strings.xml` - Attribution string resources
- `app/src/main/res/values/donottranslate.xml` - URL string resources
- `TODO_XHUB_INFRASTRUCTURE.md` - Complete infrastructure checklist
- `CPAL_COMPLIANCE.md` - CPAL license requirements

---

## Summary

All three requirements from the user's comment have been implemented:

1. ✅ **CPAL license URL updated** to point to XHub's own fork (placeholder: `YOUR_ORG/xhub`)
2. ✅ **All package references verified** - ACTIVITY and FRAGMENT extras already updated to `com.xhub.browser.*`
3. ✅ **CPAL Exhibit B attribution exists** - "Powered by Fulguris" with copyright notice and link to Fulguris homepage

**Next Action:** Replace `YOUR_ORG/xhub` with your actual GitHub repository details before release.

---

**Last Updated:** 2026-06-11
**Build Status:** ✅ Successful
