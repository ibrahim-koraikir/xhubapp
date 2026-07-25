# TODO: Update CPAL URL Before Distribution

## URGENT ACTION REQUIRED

Before distributing XHub publicly (app stores, website, or any network deployment), you **MUST** update the CPAL license URL in `donottranslate.xml`.

## What to Update

### File: `app/src/main/res/values/donottranslate.xml`

**Line 6 - Current URL:**
```xml
https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0
```

**Replace with your XHub repository URL:**
```xml
https://github.com/YOUR_ORGANIZATION/xhub/blob/main/LICENSE.CPAL-1.0
```

## Why This Matters

CPAL Section 3.2 requires that:
> "You must include a prominent notice in each Original Code and Modifications, **stating how and where users may obtain a copy of the Source Code**."

The URL in `donottranslate.xml` is embedded in every source file's copyright header comment and must point to where XHub source code is publicly hosted.

## Steps to Complete

1. **Create public repository** for XHub on GitHub, GitLab, or similar platform
2. **Push XHub source code** to that repository
3. **Copy LICENSE files** to repository root:
   - `LICENSE`
   - `LICENSE-CPAL-1.0`
   - `LICENSE-MPL-2.0`
4. **Update the URL** in `donottranslate.xml` line 6
5. **Rebuild the app** to embed the correct URL in source files

## Example URLs

Depending on where you host XHub:

### GitHub
```xml
https://github.com/xhub-team/xhub/blob/main/LICENSE.CPAL-1.0
```

### GitLab
```xml
https://gitlab.com/xhub-team/xhub/-/blob/main/LICENSE.CPAL-1.0
```

### Self-hosted
```xml
https://git.xhub.site/xhub/blob/main/LICENSE.CPAL-1.0
```

## Verification

After updating, verify the change:

```bash
# Check the file was updated
grep "github.com/Slion/Fulguris" app/src/main/res/values/donottranslate.xml
# Should return nothing

# Verify new URL is present
grep "LICENSE.CPAL-1.0" app/src/main/res/values/donottranslate.xml
# Should show your new URL
```

## Do NOT Distribute Without This Update

⚠️ **WARNING:** Distributing XHub with the old Fulguris URL may:
- Confuse users about where to get source code
- Violate CPAL Section 3.2 requirements
- Cause license compliance issues

---

## Checklist

- [ ] XHub repository created and is public
- [ ] XHub source code pushed to repository
- [ ] LICENSE files present in repository root
- [ ] URL in `donottranslate.xml` updated (line 6)
- [ ] Verified old URL no longer present
- [ ] Rebuilt app with new URL
- [ ] Tested that URL works and points to correct license file

---

**After completing these steps, delete this file (TODO_CPAL_URL_UPDATE.md) to avoid confusion.**
