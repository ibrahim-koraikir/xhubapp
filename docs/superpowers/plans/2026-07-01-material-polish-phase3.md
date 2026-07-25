# Material Polish — Phase 3 (Consolidation + Remaining Chrome) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the token consolidation — repoint the 3 parallel dark-only color groups' consumers + remaining chrome, then delete 28 dead resources. Finish line for single-source-of-truth.

**Architecture:** Reuse the 20 existing Phase 1/2 `?attr` tokens + 9 category colors. No new tokens. Repoint consumers → delete the now-dead groups.

**Tech Stack:** Android Views + Material Components + XML resources; Kotlin; Robolectric JVM tests.

**Build/test commands (Windows, Git Bash shell, from repo root):**
```bash
./gradlew.bat app:assembleXhubFullDownloadDebug
./gradlew.bat app:testXhubFullDownloadDebugUnitTest
```

**Spec:** `docs/superpowers/specs/2026-07-01-material-polish-phase3-design.md`
**Phase 1 (done):** `docs/superpowers/plans/2026-06-25-material-polish-phase1.md`
**Phase 2 (done):** `docs/superpowers/plans/2026-06-29-material-polish-phase2.md`

**Critical grounding facts (verified in Phase 3 scan):**
- Build task is `app:assembleXhubFullDownloadDebug` (NOT `slions`).
- `/usr/bin/grep` must be used (the bash `grep` wrapper conflicts with combined flags).
- `hb_*` consumers: 3 files, 15 refs. `tab_grid_*` consumers: 4 files, 15 refs. `secondary_color_settings[_dark]`: 2 files, 2 refs. Plus `primary_color[_dark]`: 2 files, 2 refs (toolbar elevates). **Total: 34 refs → then delete 28 dead resources.**
- No new tokens/colors/styles files created in Phase 3.

---

## File Map

**Modify (consolidation — 9 files):**
- `app/src/main/res/layout/item_hb_entry.xml`
- `app/src/main/res/layout/fragment_history_bookmarks.xml`
- `app/src/main/java/com/xhub/browser/fragment/HistoryBookmarksBottomSheet.kt`
- `app/src/main/res/layout/tab_drawer_view.xml`
- `app/src/main/res/layout/tab_list_item.xml`
- `app/src/main/res/layout/tab_list_item_horizontal.xml`
- `app/src/main/res/drawable/bg_tab_grid_card.xml`
- `app/src/main/res/drawable/toolbar_elevate.xml`
- `app/src/main/res/drawable/toolbar_elevate_dark.xml`

**Modify (chrome layouts — 7 files):**
- `item_suggestion_ask.xml`, `search.xml`, `simple_list_item.xml`, `fragment_ask_overlay.xml`, `dialog_list_item.xml`, `site_suggestion_item.xml`, `activity_manage_shortcuts.xml`

**Modify (chrome drawables — read each before editing):**
- `bg_menu_card.xml`, `address_bar_background.xml`, `bg_ask_search_bar.xml`, `tab_item_bg.xml`, `bg_quality_chip.xml`, `bg_bottom_audio_button.xml`, `bg_bottom_assistant_pill.xml`, `bg_add_group_btn.xml`, `bg_shortcut_tile.xml`, `toolbar_chip_bg.xml`, `scrollbar.xml`

**Modify (Kotlin literals — 2 files):**
- `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`
- `app/src/main/java/com/xhub/browser/utils/ThemeUtils.kt`

**Modify (deletion):**
- `app/src/main/res/values/colors.xml` — delete 28 dead resources

---

## Task 1: Repoint `hb_*` consumers (3 files, 15 refs)

**Files:**
- Modify: `app/src/main/res/layout/item_hb_entry.xml`
- Modify: `app/src/main/res/layout/fragment_history_bookmarks.xml`
- Modify: `app/src/main/java/com/xhub/browser/fragment/HistoryBookmarksBottomSheet.kt`

- [ ] **Step 1: Read each file, then apply the repoints.**

For `item_hb_entry.xml` (replace all `@color/hb_*`):
```
@color/hb_foreground        → ?attr/colorOnBackground
@color/hb_muted_foreground  → ?attr/appColorMutedForeground
@color/hb_secondary         → ?attr/appColorCardSurface
@color/hb_divider           → ?attr/colorSurfaceVariant
```

For `fragment_history_bookmarks.xml` (replace all `@color/hb_*`):
```
@color/hb_background        → ?android:attr/colorBackground
@color/hb_foreground        → ?attr/colorOnBackground
@color/hb_muted_foreground  → ?attr/appColorMutedForeground   (×4 instances)
```

For `HistoryBookmarksBottomSheet.kt` — the 3 refs (lines 143, 236, 247):
- `R.color.hb_muted_foreground` → `androidx.core.content.ContextCompat.getColor(requireContext(), com.google.android.material.R.color.m3_default_color_secondary_text)` (the M3 "muted" semantic)
- Actually simpler: use `com.google.android.material.color.MaterialColors.getColor(requireContext(), android.R.attr.textColorTertiary, 0)` for muted foreground
- `R.color.hb_secondary` (×2, lines 236 + 247) → `com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, 0)`

Add import: `import com.google.android.material.color.MaterialColors`

- [ ] **Step 2: Verify zero `hb_*` refs remain**

Run: `/usr/bin/grep -rn "@color/hb_\|R\.color\.hb_" app/src/main/ | /usr/bin/grep -v colors.xml`
Expected: **no output** (all consumers repointed; only colors.xml definitions remain).

- [ ] **Step 3: Verify build + commit**

Run: `./gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/layout/item_hb_entry.xml app/src/main/res/layout/fragment_history_bookmarks.xml app/src/main/java/com/xhub/browser/fragment/HistoryBookmarksBottomSheet.kt
git commit -m "refactor(hb): repoint all hb_* consumers to theme tokens (15 refs)"
```

---

## Task 2: Repoint `tab_grid_*` consumers (4 files, 15 refs)

**Files:**
- Modify: `app/src/main/res/layout/tab_drawer_view.xml`
- Modify: `app/src/main/res/layout/tab_list_item.xml`
- Modify: `app/src/main/res/layout/tab_list_item_horizontal.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_card.xml`

- [ ] **Step 1: Read each, then apply the repoints.**

For `tab_drawer_view.xml` (8 refs):
```
@color/tab_grid_foreground            → ?attr/colorOnBackground            (×3: lines 49, 94, 174)
@color/tab_grid_secondary_foreground  → ?attr/appColorMutedForeground      (×2: lines 71, 77)
@color/tab_grid_muted_foreground      → ?attr/appColorSubtleForeground     (line 145)
@color/tab_grid_secondary             → ?attr/colorSurfaceVariant          (line 134)
@color/tab_grid_background            → ?attr/appColorSheetGlass           (line 159)
```

For `tab_list_item.xml` (5 refs):
```
@color/tab_grid_foreground       → ?attr/colorOnBackground        (line 32)
@color/tab_grid_card_foreground  → ?attr/colorOnBackground        (line 40)
@color/tab_grid_muted_foreground → ?attr/appColorSubtleForeground (line 59)
@color/tab_grid_secondary        → ?attr/colorSurfaceVariant      (line 67)
@color/tab_grid_background       → ?attr/appColorSheetGlass       (line 73)
```

For `tab_list_item_horizontal.xml` (1 ref):
```
@color/tab_grid_foreground → ?attr/colorOnBackground
```

For `bg_tab_grid_card.xml` (1 ref):
```
@color/tab_grid_card → ?attr/appColorCardSurface
```

- [ ] **Step 2: Verify zero `tab_grid_*` refs remain**

Run: `/usr/bin/grep -rn "@color/tab_grid_\|R\.color\.tab_grid_" app/src/main/ | /usr/bin/grep -v colors.xml`
Expected: **no output**.

- [ ] **Step 3: Verify build + commit**

Run: `./gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/layout/tab_drawer_view.xml app/src/main/res/layout/tab_list_item.xml app/src/main/res/layout/tab_list_item_horizontal.xml app/src/main/res/drawable/bg_tab_grid_card.xml
git commit -m "refactor(tabs): repoint all tab_grid_* consumers to theme tokens (15 refs)"
```

---

## Task 3: Repoint toolbar elevates + secondary_color_settings (2 files, 4 refs)

**Files:**
- Modify: `app/src/main/res/drawable/toolbar_elevate.xml`
- Modify: `app/src/main/res/drawable/toolbar_elevate_dark.xml`

- [ ] **Step 1: Repoint both drawables fully.**

`toolbar_elevate.xml`:
```xml
<solid android:color="@color/secondary_color_settings" />  →  <solid android:color="?attr/colorSurface" />
<solid android:color="@color/primary_color" />             →  <solid android:color="?attr/colorPrimary" />
```

`toolbar_elevate_dark.xml`:
```xml
<solid android:color="@color/secondary_color_settings_dark" />  →  <solid android:color="?attr/colorSurface" />
<solid android:color="@color/primary_color_dark" />             →  <solid android:color="?attr/colorPrimary" />
```

- [ ] **Step 2: Verify zero `secondary_color_settings` / `primary_color[_dark]` refs remain**

Run: `/usr/bin/grep -rn "secondary_color_settings\|primary_color_dark\|@color/primary_color\b\|R\.color\.primary_color\b" app/src/main/ | /usr/bin/grep -v colors.xml`
Expected: **no output**.

- [ ] **Step 3: Verify build + commit**

Run: `./gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/drawable/toolbar_elevate.xml app/src/main/res/drawable/toolbar_elevate_dark.xml
git commit -m "refactor(toolbar): repoint toolbar_elevate*.xml to theme tokens (frees secondary_color_settings + primary_color)"
```

---

## Task 4: Delete 28 dead color resources

**Files:**
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Final verification — all 4 groups are now unreferenced**

Run each (all must return only colors.xml definitions, which we're about to delete):
```bash
/usr/bin/grep -rn "@color/hb_\|R\.color\.hb_" app/src/main/ | /usr/bin/grep -v colors.xml
/usr/bin/grep -rn "@color/tab_grid_\|R\.color\.tab_grid_" app/src/main/ | /usr/bin/grep -v colors.xml
/usr/bin/grep -rn "secondary_color_settings" app/src/main/ | /usr/bin/grep -v colors.xml
/usr/bin/grep -rn "primary_color_dark\|@color/primary_color\"\|R\.color\.primary_color\"" app/src/main/ | /usr/bin/grep -v colors.xml
```
Expected: **all four return no output**. If ANY returns a match, STOP — repoint that consumer first.

- [ ] **Step 2: Delete the 28 dead resources from colors.xml**

Remove these lines from `app/src/main/res/values/colors.xml`:
- 11 `hb_*` colors (hb_background through hb_icon_google_blue, lines ~399-409)
- 13 `tab_grid_*` colors (tab_grid_background through tab_grid_card_foreground, lines ~384-396)
- `secondary_color_settings` (line ~24)
- `secondary_color_settings_dark` (line ~25)
- `primary_color` (line ~14)
- `primary_color_dark` (line ~30)

- [ ] **Step 3: Verify build (catches any hidden reference)**

Run: `./gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify deletion**

Run: `/usr/bin/grep -c "hb_background\|tab_grid_background\|secondary_color_settings\|primary_color_dark" app/src/main/res/values/colors.xml`
Expected: `0`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/colors.xml
git commit -m "chore(colors): delete 28 dead hb_*/tab_grid_*/secondary_color_settings*/primary_color* resources (consolidated to tokens)"
```

---

## Task 5: Repoint chrome layouts (7 files, 15 values)

**Files:** `item_suggestion_ask.xml`, `search.xml`, `simple_list_item.xml`, `fragment_ask_overlay.xml`, `dialog_list_item.xml`, `site_suggestion_item.xml`, `activity_manage_shortcuts.xml`

- [ ] **Step 1: For each file, read it, then replace hardcoded hex per the spec §2 mapping:**
```
#FFFFFF / #FFF → ?attr/colorOnBackground
#0b0b0b / #1a1a1a / #171717 → ?attr/appColorSheetGlass
#9A9A9A / #A0A0A0 → ?attr/appColorMutedForeground
#FF007A → ?attr/colorPrimary
```

- [ ] **Step 2: Verify zero hardcoded hex in the 7 files**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/layout/item_suggestion_ask.xml app/src/main/res/layout/search.xml app/src/main/res/layout/simple_list_item.xml app/src/main/res/layout/fragment_ask_overlay.xml app/src/main/res/layout/dialog_list_item.xml app/src/main/res/layout/site_suggestion_item.xml app/src/main/res/layout/activity_manage_shortcuts.xml`
Expected: no output.

- [ ] **Step 3: Verify build + commit**

Run: `./gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/layout/item_suggestion_ask.xml app/src/main/res/layout/search.xml app/src/main/res/layout/simple_list_item.xml app/src/main/res/layout/fragment_ask_overlay.xml app/src/main/res/layout/dialog_list_item.xml app/src/main/res/layout/site_suggestion_item.xml app/src/main/res/layout/activity_manage_shortcuts.xml
git commit -m "refactor(chrome): repoint 7 chrome layouts to theme tokens (15 values)"
```

---

## Task 6: Repoint chrome drawables (~11 files)

**Files:** `bg_menu_card.xml`, `address_bar_background.xml`, `bg_ask_search_bar.xml`, `tab_item_bg.xml`, `bg_quality_chip.xml`, `bg_bottom_audio_button.xml`, `bg_bottom_assistant_pill.xml`, `bg_add_group_btn.xml`, `bg_shortcut_tile.xml`, `toolbar_chip_bg.xml`, `scrollbar.xml`

- [ ] **Step 1: Read each drawable, then repoint hardcoded hex to the appropriate token:**
```
#33FF007A / #FF007A (accents)   → ?attr/colorPrimary
#1A1A1A / #0b0b0b (surfaces)    → ?attr/appColorSheetGlass
#1AFFFFFF / #20FFFFFF (glass)   → ?attr/appColorGlassFill / appColorGlassStroke
#A0A0A0 (muted)                 → ?attr/appColorMutedForeground
```

**Important:** verify each drawable is actually theme-adaptive-appropriate before repointing. If a drawable is dark-only by design (e.g. a press-state overlay that only appears in dark themes), keep it concrete with a comment.

- [ ] **Step 2: Verify zero hardcoded hex in the target drawables**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" <each file>` → no output (or only comments).

- [ ] **Step 3: Verify build + commit**

Run: `./gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/drawable/bg_menu_card.xml app/src/main/res/drawable/address_bar_background.xml app/src/main/res/drawable/bg_ask_search_bar.xml app/src/main/res/drawable/tab_item_bg.xml app/src/main/res/drawable/bg_quality_chip.xml app/src/main/res/drawable/bg_bottom_audio_button.xml app/src/main/res/drawable/bg_bottom_assistant_pill.xml app/src/main/res/drawable/bg_add_group_btn.xml app/src/main/res/drawable/bg_shortcut_tile.xml app/src/main/res/drawable/toolbar_chip_bg.xml app/src/main/res/drawable/scrollbar.xml
git commit -m "refactor(chrome): repoint ~11 chrome drawables to theme tokens"
```

---

## Task 7: Repoint Kotlin UI color literals (2 files, 6 values)

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`
- Modify: `app/src/main/java/com/xhub/browser/utils/ThemeUtils.kt`

- [ ] **Step 1: Repoint the 4 literals in WebBrowserActivity.kt (lines 4144, 4150, 4236, 4240)**

```kotlin
// Line 4144: Color.parseColor("#0b0b0b")
val effectiveColor = com.google.android.material.color.MaterialColors.getColor(this, android.R.attr.windowBackground, android.graphics.Color.BLACK)

// Line 4150: Color.parseColor("#9A9A9A")
searchView.setHintTextColor(com.google.android.material.color.MaterialColors.getColor(this, android.R.attr.textColorHint, android.graphics.Color.GRAY))

// Line 4236: Color.parseColor("#171717")
setSearchBarColors(com.google.android.material.color.MaterialColors.getColor(this, android.R.attr.colorBackground, android.graphics.Color.BLACK))

// Line 4240: Color.parseColor("#1a1a1a")
iBinding.toolbarInclude.progressView.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, android.R.attr.colorBackground, android.graphics.Color.BLACK))
```

- [ ] **Step 2: Repoint the 2 literals in ThemeUtils.kt (lines 262, 281)**

```kotlin
// Line 262: Color.parseColor("#1A1A1A")
com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, android.graphics.Color.BLACK)

// Line 281: Color.parseColor("#222222")
com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, android.graphics.Color.DKGRAY)
```

- [ ] **Step 3: Verify build + commit**

Run: `./gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt app/src/main/java/com/xhub/browser/utils/ThemeUtils.kt
git commit -m "refactor(kotlin): replace Color.parseColor literals with MaterialColors.getColor (6 values)"
```

---

## Task 8: Final verification

- [ ] **Step 1: Clean build**

Run: `./gradlew.bat clean app:assembleXhubFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Full unit test suite**

Run: `./gradlew.bat app:testXhubFullDownloadDebugUnitTest`
Expected: 0 failures.

- [ ] **Step 3: Repo-wide grep — all 4 groups gone from consumers**

Run:
```bash
/usr/bin/grep -rn "@color/hb_\|R\.color\.hb_\|@color/tab_grid_\|R\.color\.tab_grid_\|secondary_color_settings\|primary_color_dark" app/src/main/ | /usr/bin/grep -v colors.xml
```
Expected: **no output**.

- [ ] **Step 4: Dead resources confirmed deleted**

Run: `/usr/bin/grep -c "hb_background\|tab_grid_background\|secondary_color_settings\|primary_color_dark" app/src/main/res/values/colors.xml`
Expected: `0`.

- [ ] **Step 5: Report results to user with fresh evidence.**

---

## Self-Review Notes (plan author)

**Spec coverage:** §1 consolidation → Tasks 1-4 ✓; §2 chrome layouts → Task 5 ✓; §3 chrome drawables → Task 6 ✓; §4 Kotlin → Task 7 ✓; §5 verification → Task 8 ✓.

**New finding during planning:** `toolbar_elevate*.xml` also reference `primary_color[_dark]` (not just `secondary_color_settings[_dark]`). Folded into Task 3, adding 2 more deletions (28 total, up from spec's 26). Noted in the plan.

**Phase 2 lesson applied:** Task 4's deletion is gated on a repo-wide grep that checks ALL 4 groups across ALL files (not just the ones I touched). The scan in this plan is the exhaustive one.

**Placeholder scan:** none. Every step shows the exact mapping or command.
