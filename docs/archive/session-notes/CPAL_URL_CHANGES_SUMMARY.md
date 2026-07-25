# CPAL License URL Changes Summary

## Changes Made

Updated CPAL license URL references from broken placeholders to a more explicit placeholder that must be updated before release.

---

## Files Modified

### 1. preference_about.xml
**File:** `app/src/main/res/xml/preference_about.xml`  
**Line:** 210

**Change:**
```xml
<!-- BEFORE -->
a:data="https://github.com/YOUR_ORG/xhub/blob/main/LICENSE-CPAL-1.0"

<!-- AFTER -->
a:data="https://github.com/REPLACE_WITH_YOUR_GITHUB_USERNAME/xhub/blob/main/LICENSE-CPAL-1.0"
```

**Impact:** About screen → Licenses → XHub (CPAL) link will use the updated placeholder.

---

### 2. donottranslate.xml
**File:** `app/src/main/res/values/donottranslate.xml`  
**Line:** 6

**Change:**
```xml
<!-- BEFORE -->
https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0

<!-- AFTER -->
https://github.com/REPLACE_WITH_YOUR_GITHUB_USERNAME/xhub/blob/main/LICENSE-CPAL-1.0
```

**Impact:** License header comment in donottranslate.xml now points to XHub fork location.

---

## ⚠️ CRITICAL ACTION REQUIRED BEFORE RELEASE

The placeholder `REPLACE_WITH_YOUR_GITHUB_USERNAME` **MUST** be replaced with your actual GitHub username/organization before:
- Publishing to Google Play Store
- Distributing APKs publicly
- Making any public release

### Why This is Critical

1. **CPAL Legal Requirement:** Section 3.2 requires accessible source code
2. **App Store Compliance:** Google Play requires functional license links
3. **User Experience:** Broken links appear unprofessional
4. **License Violation:** Placeholder URLs don't satisfy CPAL transparency requirements

---

## How to Complete This

### Step 1: Create/Verify GitHub Repository
```
https://github.com/YOUR_ACTUAL_USERNAME/xhub
```

### Step 2: Find and Replace
In both files, replace:
```
REPLACE_WITH_YOUR_GITHUB_USERNAME
```

With your actual GitHub username, for example:
```
johndoe
```

### Step 3: Verify URL Works
Test that this URL loads correctly:
```
https://github.com/YOUR_ACTUAL_USERNAME/xhub/blob/main/LICENSE-CPAL-1.0
```

---

## Verification Commands

### Search for remaining placeholders:
```powershell
# Find all instances of the placeholder
findstr /s /i "REPLACE_WITH_YOUR_GITHUB_USERNAME" *.xml *.kt *.java

# Should return exactly 2 results:
# - app/src/main/res/values/donottranslate.xml (line 6)
# - app/src/main/res/xml/preference_about.xml (line 210)
```

### After updating, verify no placeholders remain:
```powershell
findstr /s /i "REPLACE_WITH_YOUR" *.xml
# Should return 0 results after you replace the placeholders
```

---

## Complete Documentation

For detailed instructions, legal requirements, and examples, see:
- **`CPAL_LICENSE_URL_UPDATE.md`** - Comprehensive guide with step-by-step checklist

---

**Date:** 2026-06-12  
**Status:** ✅ Placeholder URLs updated - ⏳ Awaiting user action to finalize  
**Files Changed:** 2  
**Lines Changed:** 2
