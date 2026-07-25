# CPAL License Compliance Guide

## Overview

This project is a fork of **Fulguris Web Browser**, which is licensed under the **Common Public Attribution License (CPAL) Version 1.0**. CPAL is a copyleft license based on Mozilla Public License 1.1 with additional attribution requirements.

## CRITICAL: What You Must NOT Do

### ❌ NEVER Remove or Modify

1. **License Files**
   - `LICENSE` - Main license file
   - `LICENSE-CPAL-1.0` - CPAL 1.0 full text
   - `LICENSE-MPL-2.0` - MPL 2.0 full text (for Lightning Browser heritage)

2. **Copyright Headers**
   - DO NOT delete copyright headers from any existing `.kt`, `.java`, or XML files
   - DO NOT modify the CPAL license block in existing files
   - DO NOT change copyright attributions to Stéphane Lenclud

3. **Attribution Requirements**
   - DO NOT remove "Powered by Fulguris" from About screen
   - DO NOT remove copyright notice "Copyright © 2020 Stéphane Lenclud"
   - DO NOT remove link to http://fulguris.slions.net

## CPAL Requirements Summary

### 1. Source Code Availability (Section 3.2)

**Requirement:** You must make source code available for any modifications distributed over a network.

**Compliance:**
- Host your XHub fork publicly on GitHub, GitLab, or similar
- Update the CPAL URL in `donottranslate.xml` to point to your fork's LICENSE
- Keep source code synchronized with distributed binaries

**Current Status:**
- ⚠️ **ACTION REQUIRED**: Update `donottranslate.xml` CPAL URL when your fork is hosted
- Template: `https://github.com/YOUR_ORG/xhub/blob/main/LICENSE.CPAL-1.0`

### 2. Attribution Requirements (Exhibit B)

**Requirement:** Display attribution to original author in user interface.

**Compliance:** ✅ IMPLEMENTED
- Added "Powered by Fulguris" in About screen (`preference_about.xml`)
- Shows "Copyright © 2020 Stéphane Lenclud" 
- Links to http://fulguris.slions.net
- Placed before licenses section, as required by CPAL Exhibit B

### 3. License Preservation

**Requirement:** All distributed copies must include CPAL notice.

**Compliance:** ✅ IN PLACE
- LICENSE files remain at repository root
- Copyright headers remain in all original source files
- New files add additional copyright block (see below)

## Adding New Files

When you create new source files for XHub, follow this pattern:

### For Kotlin Files (.kt)

```kotlin
/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/YOUR_ORG/xhub/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 * 
 * Modifications for XHub:
 * Copyright © 2026 [Your Name/Organization].
 * All modifications are also licensed under CPAL 1.0.
 */

package fulguris.your.package

// Your code here
```

### For Java Files (.java)

```java
/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/YOUR_ORG/xhub/blob/main/LICENSE.CPAL-1.0.
 * [rest of CPAL header as above]
 * 
 * Modifications for XHub:
 * Copyright © 2026 [Your Name/Organization].
 * All modifications are also licensed under CPAL 1.0.
 */

package fulguris.your.package;

// Your code here
```

### For XML Files (.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
 The contents of this file are subject to the Common Public Attribution License Version 1.0.
 (the "License"); you may not use this file except in compliance with the License.
 You may obtain a copy of the License at:
 https://github.com/YOUR_ORG/xhub/blob/main/LICENSE.CPAL-1.0.
 [rest of CPAL header]
 
 Modifications for XHub:
 Copyright © 2026 [Your Name/Organization].
 All modifications are also licensed under CPAL 1.0.
-->
<resources>
    <!-- Your content here -->
</resources>
```

## Modifying Existing Files

When modifying existing Fulguris files:

1. **Keep the original CPAL header intact**
2. **Add your modification notice below it**:

```kotlin
/*
 * [Original CPAL header - DO NOT MODIFY]
 *
 * Modified by: [Your Name/Organization]
 * Date: [Date]
 * Changes: [Brief description of changes]
 * These modifications are licensed under CPAL 1.0.
 */
```

## Required Updates

### 1. Update CPAL URL in donottranslate.xml

**Current:**
```xml
https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0
```

**Update to:**
```xml
https://github.com/YOUR_ORG/xhub/blob/main/LICENSE.CPAL-1.0
```

**Location:** `app/src/main/res/values/donottranslate.xml` (line 6)

**When:** Before first public distribution or network deployment

### 2. Verify Attribution in About Screen

✅ Already implemented in `app/src/main/res/xml/preference_about.xml`:
- Shows "Powered by Fulguris"
- Shows "Copyright © 2020 Stéphane Lenclud"
- Links to http://fulguris.slions.net

**Location:** Settings → About → Attribution section

## License Compatibility

### Can Use With XHub

✅ **MIT License** - Compatible with CPAL
✅ **Apache 2.0** - Compatible with CPAL  
✅ **BSD Licenses** - Compatible with CPAL
✅ **Public Domain** - Compatible with CPAL

### Cannot Use With XHub

❌ **GPL** - Incompatible with CPAL (copyleft conflict)
❌ **AGPL** - Incompatible with CPAL (copyleft conflict)
❌ **Proprietary** - Cannot combine with CPAL code

## Distribution Checklist

Before distributing XHub (APK, source code, or network deployment):

- [ ] All LICENSE files present in repository
- [ ] Copyright headers intact in all original Fulguris files
- [ ] New files have proper CPAL headers with XHub modification notice
- [ ] Modified files have modification notices
- [ ] CPAL URL in `donottranslate.xml` points to your fork
- [ ] "Powered by Fulguris" attribution visible in About screen
- [ ] Source code is publicly available
- [ ] README or documentation mentions Fulguris heritage
- [ ] No GPL/AGPL code mixed in

## Common Mistakes to Avoid

### ❌ Wrong: Removing Attribution

```kotlin
// BAD - Removed original copyright
/*
 * Copyright © 2026 XHub Team
 */
```

### ✅ Right: Adding to Attribution

```kotlin
// GOOD - Kept original, added modification notice
/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * [... full CPAL header ...]
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 *
 * Modifications for XHub:
 * Copyright © 2026 XHub Team
 * All modifications are also licensed under CPAL 1.0.
 */
```

### ❌ Wrong: Changing License

```kotlin
// BAD - Cannot change the license
/*
 * Licensed under MIT License
 */
```

### ✅ Right: Maintaining License

```kotlin
// GOOD - CPAL remains
/*
 * Licensed under CPAL 1.0
 * [full header]
 */
```

## FAQ

### Q: Can I remove the "Powered by Fulguris" attribution?

**A:** No. CPAL Exhibit B requires this attribution to remain visible in the user interface. Removing it violates the license.

### Q: Can I rebrand XHub without mentioning Fulguris?

**A:** You can rebrand the app name, icon, and marketing materials, but you MUST keep the "Powered by Fulguris" attribution in the About screen and maintain copyright headers in source code.

### Q: Do I need to open-source XHub?

**A:** Yes. CPAL Section 3.2 requires making source code available if you distribute the software or make it available over a network (like through an app store or website).

### Q: Can I sell XHub commercially?

**A:** Yes, but you must still comply with CPAL requirements (source availability, attribution, license preservation).

### Q: What if I only modify build.gradle or configuration files?

**A:** Even non-code configuration changes require CPAL compliance. Distribute source, maintain attribution, keep license files.

### Q: Can I use XHub code in a GPL project?

**A:** No. GPL and CPAL are incompatible due to conflicting copyleft provisions. You cannot mix CPAL code with GPL code.

## Resources

- **CPAL 1.0 Full Text:** `LICENSE-CPAL-1.0` in repository root
- **Original Fulguris:** http://fulguris.slions.net
- **CPAL FAQ:** https://opensource.org/licenses/CPAL-1.0
- **License Comparison:** https://choosealicense.com/licenses/cpal-1.0/

## Compliance Verification

Run these checks before each release:

```bash
# Check for files missing copyright headers
grep -r "package fulguris" app/src --include="*.kt" | while read -r file; do
    if ! grep -q "CPAL" "$file"; then
        echo "Missing CPAL header: $file"
    fi
done

# Verify LICENSE files exist
test -f LICENSE && test -f LICENSE-CPAL-1.0 && test -f LICENSE-MPL-2.0 && echo "✓ All license files present" || echo "✗ Missing license files"

# Check Attribution in About screen
grep -q "Powered by Fulguris" app/src/main/res/xml/preference_about.xml && echo "✓ Attribution present" || echo "✗ Attribution missing"
```

## Contact for License Questions

If you have questions about CPAL compliance:

1. **Read the full CPAL 1.0 text** in `LICENSE-CPAL-1.0`
2. **Consult with legal counsel** for legal interpretation
3. **Contact original author** Stéphane Lenclud through Fulguris project channels

## Summary

**Three Golden Rules:**

1. **NEVER** remove license files, copyright headers, or attribution
2. **ALWAYS** add your modification notices below original headers
3. **MUST** make source code publicly available when distributing

Following these rules ensures you stay compliant with CPAL while building XHub on top of Fulguris.

---

**Remember:** CPAL is a legal requirement, not optional. Non-compliance can result in loss of license rights and legal liability.
