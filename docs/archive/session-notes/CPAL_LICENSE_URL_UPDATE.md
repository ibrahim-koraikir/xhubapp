# CPAL License URL Update - CRITICAL ACTION REQUIRED

## ⚠️ MANDATORY BEFORE RELEASE

The CPAL license URLs in this codebase contain placeholders that **MUST** be replaced with your actual GitHub repository URL before any public release or distribution.

---

## Why This Matters

### CPAL Section 3.2 - Source Code Availability Requirement

The Common Public Attribution License Version 1.0 (CPAL) requires that:

> **You must make the Source Code available under the terms of this License to anyone to whom you distribute the Executable version.**

This means:
1. Your fork must be publicly hosted (e.g., on GitHub)
2. The LICENSE-CPAL-1.0 file must be accessible at a public URL
3. The app must link to this public URL so users can access the source code
4. Failure to provide source code access is a **license violation**

---

## Files Updated

### 1. preference_about.xml - About Screen License Link
**File:** `app/src/main/res/xml/preference_about.xml`  
**Line:** ~181

**Current (PLACEHOLDER):**
```xml
<intent
    a:action="android.intent.action.VIEW"
    a:data="https://github.com/REPLACE_WITH_YOUR_GITHUB_USERNAME/xhub/blob/main/LICENSE-CPAL-1.0">
```

**What You Must Do:**
Replace `REPLACE_WITH_YOUR_GITHUB_USERNAME` with your actual GitHub username or organization name.

**Example:**
```xml
<!-- If your GitHub username is "johndoe" -->
<intent
    a:action="android.intent.action.VIEW"
    a:data="https://github.com/johndoe/xhub/blob/main/LICENSE-CPAL-1.0">
```

---

### 2. donottranslate.xml - License Header Comment
**File:** `app/src/main/res/values/donottranslate.xml`  
**Line:** ~6

**Current (PLACEHOLDER):**
```xml
<!--
 The contents of this file are subject to the Common Public Attribution License Version 1.0.
 (the "License"); you may not use this file except in compliance with the License.
 You may obtain a copy of the License at:
 https://github.com/REPLACE_WITH_YOUR_GITHUB_USERNAME/xhub/blob/main/LICENSE-CPAL-1.0.
-->
```

**What You Must Do:**
Replace `REPLACE_WITH_YOUR_GITHUB_USERNAME` with your actual GitHub username or organization name.

**Example:**
```xml
<!-- If your GitHub username is "johndoe" -->
<!--
 You may obtain a copy of the License at:
 https://github.com/johndoe/xhub/blob/main/LICENSE-CPAL-1.0.
-->
```

---

## Complete Pre-Release Checklist

### Step 1: Create Public GitHub Repository
- [ ] Create a public GitHub repository (e.g., `https://github.com/YOUR_USERNAME/xhub`)
- [ ] Push your XHub fork code to this repository
- [ ] Verify the `LICENSE-CPAL-1.0` file exists in the repository root
- [ ] Test that the URL works: `https://github.com/YOUR_USERNAME/xhub/blob/main/LICENSE-CPAL-1.0`

### Step 2: Update preference_about.xml
- [ ] Open `app/src/main/res/xml/preference_about.xml`
- [ ] Find line ~181 with the CPAL license intent
- [ ] Replace `REPLACE_WITH_YOUR_GITHUB_USERNAME` with your actual username
- [ ] Save the file

### Step 3: Update donottranslate.xml
- [ ] Open `app/src/main/res/values/donottranslate.xml`
- [ ] Find line ~6 in the license comment header
- [ ] Replace `REPLACE_WITH_YOUR_GITHUB_USERNAME` with your actual username
- [ ] Save the file

### Step 4: Verify Changes
- [ ] Build the app: `.\gradlew.bat assembleXhubFullDownloadDebug`
- [ ] Install the APK on a test device
- [ ] Navigate to: Settings → About → Licenses → XHub (CPAL entry)
- [ ] Tap the license entry
- [ ] Verify the browser opens your actual GitHub repository URL
- [ ] Verify the LICENSE-CPAL-1.0 file loads correctly

### Step 5: Update Other Files (if needed)
Search the entire codebase for any remaining placeholder references:
```powershell
# Search for placeholders
findstr /s /i "REPLACE_WITH_YOUR_GITHUB_USERNAME" *.xml *.kt *.java *.md
findstr /s /i "YOUR_ORG/xhub" *.xml *.kt *.java *.md
findstr /s /i "your-actual-username" *.xml *.kt *.java *.md
```

---

## Examples for Different Hosting Scenarios

### GitHub (Recommended)
```
https://github.com/johndoe/xhub/blob/main/LICENSE-CPAL-1.0
```

### GitLab
```
https://gitlab.com/johndoe/xhub/-/blob/main/LICENSE-CPAL-1.0
```

### Gitea / Self-Hosted
```
https://git.yourdomain.com/johndoe/xhub/src/branch/main/LICENSE-CPAL-1.0
```

### Bitbucket
```
https://bitbucket.org/johndoe/xhub/src/main/LICENSE-CPAL-1.0
```

---

## What Happens If You Don't Update This?

### 1. App Store Rejection Risk
- Google Play may reject apps with broken or placeholder links
- App stores require functional license links for review

### 2. License Violation
- CPAL requires accessible source code
- Placeholder URLs don't fulfill this requirement
- You could face legal consequences from the original author

### 3. User Confusion
- Users tapping the license link will get a 404 error
- Appears unprofessional and breaks trust
- CPAL Exhibit B requires the link to function properly

### 4. Fork Attribution Requirements
- CPAL requires you to make your modifications public
- Placeholder links don't satisfy this transparency requirement

---

## Additional CPAL Compliance Notes

### The LICENSE-CPAL-1.0 File Must Exist
Ensure your repository contains the original `LICENSE-CPAL-1.0` file at the root level. If it's missing:

1. Copy it from the original Fulguris repository
2. Keep Exhibit A and Exhibit B intact
3. Do NOT modify the license text itself

### README.md Attribution
Your repository's README.md should acknowledge the original:

```markdown
# XHub Browser

XHub is a fork of [Fulguris](https://github.com/Slion/Fulguris), licensed under CPAL-1.0.

## License
This project is licensed under the Common Public Attribution License Version 1.0 (CPAL-1.0).
See the [LICENSE-CPAL-1.0](LICENSE-CPAL-1.0) file for details.

## Original Work
Based on Fulguris by Stéphane Lenclud.  
Copyright © 2020 Stéphane Lenclud. All Rights Reserved.
```

---

## Quick Reference

**Files to update:**
1. `app/src/main/res/xml/preference_about.xml` (line ~181)
2. `app/src/main/res/values/donottranslate.xml` (line ~6)

**Search term:**
```
REPLACE_WITH_YOUR_GITHUB_USERNAME
```

**Replace with:**
```
your-actual-github-username
```

**Verify URL works:**
```
https://github.com/your-actual-github-username/xhub/blob/main/LICENSE-CPAL-1.0
```

---

## Related Documentation

- `TODO_XHUB_INFRASTRUCTURE.md` - Other placeholder URLs that need updating
- `STRINGS_REBRAND_XHUB.md` - CPAL attribution string requirements
- `PREFERENCE_ABOUT_UPDATES.md` - About screen changes summary

---

**Date:** 2026-06-12  
**Priority:** ⚠️ CRITICAL - MUST BE DONE BEFORE RELEASE  
**Impact:** Legal compliance, app store submission, user trust  
**Status:** ⏳ AWAITING USER ACTION - Placeholder URLs in place
