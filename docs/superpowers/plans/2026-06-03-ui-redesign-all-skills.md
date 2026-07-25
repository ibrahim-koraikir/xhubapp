# UI Redesign — All Three Skill Approaches

**Goal:** Apply taste-skill, emilkowalski-skill, and impeccable principles to elevate the History/Bookmarks, Settings menu, and all interactive elements across the Fulguris browser.
**Architecture:** Three parallel tracks — A (tactile press feedback everywhere), B (History/Bookmarks premium redesign), C (Settings menu visual hierarchy upgrade). All work in Android XML drawables + layouts. Dark Starfield aesthetic preserved. Accent: #ff007a.
**Tech Stack:** Android XML layouts, StateListAnimator, layer-list drawables, Kotlin (settings fragment for icon tints).

---

## Track A — Tactile Press Feedback (Emil Kowalski)

### Task A1: StateListAnimator for settings rows
- New file: `app/src/main/res/animator/press_scale.xml`
- Modify: `app/src/main/res/layout/item_settings_menu.xml`

### Task A2: StateListAnimator for history/bookmark rows
- Modify: `app/src/main/res/layout/item_hb_entry.xml`

---

## Track B — History/Bookmarks Redesign (All Skills)

### Task B1: New colors for enhanced HB palette
- Modify: `app/src/main/res/values/colors.xml` — add `hb_tab_bg`, `hb_tab_pill`, `hb_icon_pink`

### Task B2: New drawables
- New: `app/src/main/res/drawable/bg_hb_tab_pill_active.xml`
- New: `app/src/main/res/drawable/bg_hb_tab_pill_inactive.xml`
- Modify: `app/src/main/res/drawable/bg_hb_icon_circle.xml` — pink-tinted

### Task B3: Redesign fragment_history_bookmarks.xml
- Floating pill tab switcher instead of flat tabs
- Glassmorphic search bar matching app theme
- Remove hairline dividers, use spacing rhythm

### Task B4: Redesign item_hb_entry.xml
- Wider padding, no hairline dividers (impeccable: banned side stripes)
- Icon circle with #ff007a at 18% opacity
- Subtle press ripple

### Task B5: Upgrade item_hb_header.xml
- Proper date group header style — muted, small caps, breathing room

---

## Track C — Settings Menu Redesign (taste-skill + impeccable)

### Task C1: Upgrade fragment_settings_menu.xml
- Card stroke (1px #2a2a2a) instead of just flat fill
- Better vertical spacing between cards
- Title typography improvement

### Task C2: Upgrade item_settings_menu.xml
- Icon container (36dp circle) with per-category color tints
- Better chevron — use proper forward arrow
- Subtitle text contrast fix (#888 → #a0a0a0 for readability)

### Task C3: Update bg_menu_card.xml
- Add subtle stroke for card definition

---

## Verification
Run: `.\\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: BUILD SUCCESSFUL
