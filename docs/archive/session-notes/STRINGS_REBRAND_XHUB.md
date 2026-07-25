# XHub String Resources Rebrand - Complete

## Overview

All user-visible strings have been updated from "Fulguris" to "XHub" across the entire application, including all 40+ locale translations.

---

## ✅ Changes Completed

### 1. Main Strings File
**File:** `app/src/main/res/values/strings.xml`

**Strings Updated:**
- `up_to_date`: "XHub is up to date"
- `dialog_message_updated`: "XHub was updated to v%s"
- `title_welcome`: "Welcome to XHub"
- `pref_summary_contribute`: "Help XHub grow and succeed"
- `pref_title_accept_terms`: "I want to use XHub"
- `pref_summary_no_sponsorship`: "Use XHub from Google Play to sponsor us."
- `pref_summary_free_download`: "XHub is Open Source Software..."
- `pref_summary_contribute_translations`: "You can help translate XHub to your locale language on Crowdin."
- `pref_summary_contribute_share`: "...sharing XHub with them."
- `dialog_message_third_party_app`: "You will leave XHub"
- `intro_welcome_title`: "Welcome to XHub"
- `intro_notification_permission_description`: "Allow XHub to send notifications..."
- `locale_app_name`: "xhub Web Browser"
- `home_brand_name`: "xHub"

**Total:** 14+ user-visible strings updated in main file

---

### 2. Locale Translations
**Files Updated:** 40 locale-specific `strings.xml` files

**Locales Updated:**
- Afrikaans (af-rZA)
- Arabic (ar-rSA)
- Bosnian (bs-rBA)
- Catalan (ca-rES)
- Czech (cs-rCZ)
- Danish (da-rDK)
- German (de-rDE)
- Greek (el-rGR)
- English GB (en-rGB)
- English US (en-rUS)
- Spanish (es-rES)
- Finnish (fi-rFI)
- French (fr-rFR)
- Hindi (hi-rIN)
- Croatian (hr-rHR)
- Hungarian (hu-rHU)
- Indonesian (in-rID)
- Italian (it-rIT)
- Hebrew (iw-rIL)
- Japanese (ja-rJP)
- Korean (ko-rKR)
- Lithuanian (lt-rLT)
- Montenegrin (me-rME)
- Dutch (nl-rNL)
- Norwegian (no-rNO)
- Polish (pl-rPL)
- Portuguese Brazil (pt-rBR)
- Portuguese Portugal (pt-rPT)
- Romanian (ro-rRO)
- Russian (ru-rRU)
- Santali (sat-rIN)
- Serbian Cyrillic (sr-rCS)
- Serbian (sr-rSP)
- Swedish (sv-rSE)
- Thai (th-rTH)
- Turkish (tr-rTR)
- Ukrainian (uk-rUA)
- Vietnamese (vi-rVN)
- Chinese Simplified (zh-rCN)
- Chinese Traditional (zh-rTW)

**Result:** All translated "Fulguris" strings in all 40 locales have been updated to "XHub"

---

## ⚠️ CPAL Compliance - Attribution Preserved

### What Was NOT Changed (Intentionally)

The following strings were **intentionally preserved** to maintain CPAL license compliance:

**Attribution Strings:**
```xml
<string name="fulguris_attribution_title">Powered by Fulguris</string>
<string name="fulguris_attribution_summary">Copyright © 2020 Stéphane Lenclud</string>
```

**Why:** CPAL Exhibit B requires maintaining attribution to the original author. This attribution must remain visible in the About screen.

**Location:** Settings → About → Attribution section

See `CPAL_COMPLIANCE.md` for full legal requirements.

---

## 📝 Verification

### Check for Remaining "Fulguris" References

To verify all changes:

```bash
# Should find ONLY the attribution strings (2 results)
grep -r "Fulguris" app/src/main/res/values*/strings.xml

# Expected results:
# fulguris_attribution_title
# fulguris_attribution_summary (in copyright notice)
```

### Verify XHub Branding

```bash
# Should find all the updated strings
grep -r "XHub" app/src/main/res/values/strings.xml
```

---

## 🌍 Translation Considerations

### Future Translation Updates

When you update strings or add new translations:

1. **New English strings:** Always use "XHub" for brand name
2. **Translation updates via Crowdin:** 
   - Update the Crowdin project from `fulguris-web-browser` to `xhub-browser`
   - Translators will need to retranslate brand name references
3. **Manual translations:** Use "XHub" consistently across all locales

### Localization Script

The project uses a localization script at `subs/l10n/android/strings.py`:

```bash
# Check translation status for a locale
python subs\l10n\android\strings.py --check de-rDE

# Update a single string
python subs\l10n\android\strings.py --set string_id de-rDE "Translation"
```

See `L10N.md` or `.github/copilot-instructions.md` for full localization workflow.

---

## 🔍 Where These Strings Appear

### User-Facing Locations

1. **Welcome Screen** - "Welcome to XHub"
2. **Update Notifications** - "XHub is up to date"
3. **Settings → About** - "Help XHub grow and succeed"
4. **First Launch** - "I want to use XHub"
5. **Sponsorship/Contribute** - References to XHub branding
6. **Third-Party App Prompt** - "You will leave XHub"
7. **Notification Permission** - "Allow XHub to send notifications"
8. **App Name** - Various places showing "xhub" or "XHub"

### Internal/Technical

- App name in launcher
- Recent apps switcher
- System settings

---

## ⚠️ Community/External References Updated

The following community-related strings were updated but may need further review:

### Crowdin Translation Reference
```xml
<string name="pref_summary_contribute_translations">
    You can help translate XHub to your locale language on Crowdin.
</string>
```

**Action Required:** 
- Update `url_crowdin_project` in `donottranslate.xml` (already done)
- Create actual XHub Crowdin project, or remove this preference

### Sponsorship References
```xml
<string name="pref_summary_contribute_share">
    Dazzle your friends and communities by sharing XHub with them.
</string>
```

**Action Required:**
- Verify sponsorship/contribution preferences are relevant to XHub
- Update or remove from `preference_about.xml` as needed

See `TODO_XHUB_INFRASTRUCTURE.md` for complete infrastructure update checklist.

---

## 🔄 Related Changes

This strings rebrand is part of the larger XHub rebranding effort:

1. ✅ **Package namespace:** `fulguris` → `com.xhub.browser`
2. ✅ **Application ID:** `net.slions.fulguris` → `com.xhub.browser`
3. ✅ **URI scheme:** `fulguris://` → `xhub://`
4. ✅ **Build flavor:** `slions` → `xhub`
5. ✅ **APK filename:** `Fulguris-v*` → `XHub-v*`
6. ✅ **User-visible strings:** All updated to "XHub"
7. ✅ **Infrastructure URLs:** Updated (see `TODO_XHUB_INFRASTRUCTURE.md`)
8. ⚠️ **Attribution:** Preserved for CPAL compliance

---

## 📋 String Resource Naming Convention

### Current Convention

String resource IDs were **not renamed** during this update:

```xml
<!-- Resource ID still references "fulguris" but value is "XHub" -->
<string name="fulguris_attribution_title">Powered by Fulguris</string>

<!-- This is intentional and correct -->
```

**Why:** Renaming resource IDs would require:
- Updating all code references (`R.string.fulguris_*`)
- High risk of breaking functionality
- Not necessary for user-facing branding

**Result:** Resource IDs may still contain "fulguris" in their names, but the displayed text values show "XHub"

---

## ✅ Testing Checklist

Before release, verify these strings display correctly:

### Critical User-Facing
- [ ] App name in launcher shows "xhub"
- [ ] Welcome screen shows "Welcome to XHub"
- [ ] Settings → About shows "Help XHub grow and succeed"
- [ ] Update check shows "XHub is up to date"
- [ ] First launch prompt: "I want to use XHub"
- [ ] Attribution section shows "Powered by Fulguris" (CPAL requirement)

### Localized Strings
- [ ] Test at least 3-5 different locales
- [ ] Verify brand name appears as "XHub" in all tested languages
- [ ] Check that attribution remains "Fulguris" in all locales

### Edge Cases
- [ ] Third-party app prompt: "You will leave XHub"
- [ ] Notification permission: "Allow XHub to send notifications"
- [ ] Contribute/share messages reference XHub

---

## 🐛 Known Issues

### None Currently

All "Fulguris" → "XHub" replacements completed successfully.

---

## 📚 Related Documentation

- **CPAL Compliance:** `CPAL_COMPLIANCE.md`
- **Infrastructure Updates:** `TODO_XHUB_INFRASTRUCTURE.md`
- **Package Rebrand:** `PACKAGE_REBRAND_XHUB.md`
- **Localization Guide:** `L10N.md`
- **Build Instructions:** `BUILD_INSTRUCTIONS.md`

---

**Last Updated:** String resources rebrand completion
**Status:** ✅ Complete - All user-visible "Fulguris" references updated to "XHub"
**Files Modified:** 41 files (1 main + 40 locales)
