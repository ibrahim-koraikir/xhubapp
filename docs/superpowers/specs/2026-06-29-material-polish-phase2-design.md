# Material Polish — Phase 2 (Settings + Bookmarks/History + Tab Grid)

**Date:** 2026-06-29
**Status:** Approved design (pending spec review)
**Branch:** `feature/app-review`
**Builds on:** `2026-06-25-material-polish-design.md` (Phase 1 — foundation + home screen)

## Goal

Extend the Phase 1 token system to the three remaining "dark-only island" screens — **Settings**, **Bookmarks/History**, and **Tab Grid** — repointing all 91 hardcoded values to the centralized `?attr` / `@color` tokens and making all three screens theme-adaptive across Light/White/Dark/Black. Consolidate the three parallel dark-only color subsystems (`hb_*`, `tab_grid_*`, `secondary_color_settings[_dark]`) into the Phase 1 token layer so there is a single source of truth.

**Out of scope for Phase 2:** dialogs (other than the ones nested in settings), bottom sheets other than settings menu, search overlay, video-download sheet, the app-wide `ShapeAppearance.App.*` styles (still Phase 3). Settings/bookmarks/tabs only.

## Context — Phase 1 recap (what's already in place)

The token foundation is built and verified:
- `attrs_app.xml` — 17 custom `?attr` tokens (colors + 2 drawable refs + 2 glass tokens)
- `colors_app.xml` — single source of truth for hex, named `home_token_<role>_<theme>` × 4 themes
- `styles.xml` — the 4 `*.Base` themes assign per-theme values (must live in Base for Robolectric resolution — learned in Phase 1)
- `dimens_spacing.xml` / `dimens_shape.xml` — 4dp grid + radii + elevation
- `text_appearances_app.xml` — 9 standard + 4 specialized `TextAppearance.App.*`
- `HomeThemeAdaptiveColorTest` — regression test proving theme-adaptivity

**Phase 1 constraint that carries forward:** custom theme attrs MUST be declared in the `*.Base` styles (not the empty shells) for Robolectric SDK-27 resolution. Any new attrs added in Phase 2 follow this rule.

## Decisions locked during brainstorming

| Decision | Choice |
|----------|--------|
| Phase 2 scope | Settings + Bookmarks/History + Tab Grid together (all 3 dark-only islands unified) |
| Per-category icon colors (10) | Named `@color/app_cat_*` resources, NOT theme-adaptive (category identity ≠ theme chrome) |
| Architecture | Reuse Phase 1's semantic `?attr` tokens; add new tokens only where a genuine gap exists |

---

## Section 1 — New tokens needed

Most hardcoded values map to *existing* Phase 1 tokens. A few genuine gaps require new tokens:

### New color tokens (theme-adaptive)

| Attr | Why | Light | Dark | Black |
|------|-----|-------|------|-------|
| `appColorAccentPurple2` | Tab-grid active gradient uses `#9C00FF` (distinct from the home `appColorAccentPurple` `#A78BFA`) | `#7C3AED` | `#9C00FF` | `#9C00FF` |
| `appColorSheetGlass` | Tab-grid sheet glass `#080808` / `#F0080808` — a near-black surface for sheet backgrounds | `#F5F5F7` | `#141414` | `#080808` |
| `appColorCardSurface` | Card backgrounds (`#141414` tab cards, `#171515` hb secondary) — the "raised card" surface, distinct from tile surface | `#FFFFFF` | `#1A1A1A` | `#101010` |

(White theme aliases Light values unless noted.)

### New category color resources (NOT theme-adaptive — concrete `@color` only)

Defined in `colors_app.xml` as plain resources (no `?attr` wrapper, no per-theme variants):
```
app_cat_appearance  = #ff007a  (was inline in SettingsMenuBottomSheet.kt:51)
app_cat_browser     = #2196F3  (was :62)
app_cat_privacy     = #4CAF50  (was :73)
app_cat_domains     = #00BCD4  (was :84)
app_cat_adblock     = #F44336  (was :95)
app_cat_extensions  = #9C27B0  (was :106)
app_cat_backup      = #607D8B  (was :117)
app_cat_contribute  = #FF9800  (was :128)
app_cat_about       = #E0E0E0  (was :139)
```
Each gets a paired `app_cat_<name>_container` at 20% alpha (the `#33...` values) generated via `alpha` modifier in code OR a second concrete resource. Decision: **second concrete resource** (simpler, no runtime alpha math, matches the existing drawable pattern).

These replace both the 20 `Color.parseColor` calls in `SettingsMenuBottomSheet.kt` AND the 2 `bg_settings_icon_circle*.xml` drawables' hardcoded hex.

---

## Section 2 — Settings screen refactor (68 values)

### 2.1 Layouts (46 values across 3 files)

**`activity_settings.xml` (11 values):**
- `#0A0A0A` backgrounds (×4: root, AppBar, toolbar, scrollview) → `?android:attr/colorBackground` (the theme already sets this correctly via the `*.Base` styles; the layout was *overriding* it with hardcoded black — removing the override lets the theme drive it)
- `#FFFFFF` title/icon tints (×2) → `?attr/colorOnSurface` (M3 standard — AppBar text)
- `0dp` elevations (×2) → `@dimen/elevation_none`
- `16dp`/`8dp`/`32dp` padding (×3) → `@dimen/spacing_lg`/`spacing_sm`/`spacing_xxl`

**`fragment_settings_menu.xml` (19 values):**
- `#222222` backgrounds (×7: close button + 6 dividers) → `?attr/colorSurfaceVariant` (M3 divider/surface tone)
- `#A0A0A0` tint → `?attr/appColorMutedForeground` (reuse Phase 1 token)
- `#FFFFFF` text → `?attr/colorOnBackground`
- `22sp` title with no style → `@style/TextAppearance.App.HeadlineSmall`
- dp literals (×9) → `@dimen/spacing_*`

**`item_settings_menu.xml` (16 values):**
- `#ff007a` icon tint → `?attr/colorPrimary`
- `#F2F2F2` title text → `?attr/colorOnBackground`
- `#A8A8A8` subtitle text → `?attr/appColorMutedForeground`
- `#606060` chevron tint → `?attr/appColorSubtleForeground`
- `16sp`/`12sp` text sizes → `@style/TextAppearance.App.TitleMedium` / `BodySmall`
- dp literals (×11) → `@dimen/spacing_*`

### 2.2 Drawables (2 files)

**`bg_settings_icon_circle.xml`:** `#33ff007a` → `@color/app_cat_appearance_container` (this is the only icon-circle drawable actually referenced — `item_settings_menu.xml:30` uses it for every category icon, with the Kotlin layer setting the per-category tint at runtime via `SettingsMenuBottomSheet.kt`).
**`bg_settings_icon_circle_blue.xml`:** **DELETE** — verified unreferenced anywhere in `app/src` (grep returned 0 matches across layouts AND Kotlin). It's dead code from an earlier design.

### 2.3 Kotlin (20 values — `SettingsMenuBottomSheet.kt`)

**Mechanism (verified by reading lines 156–182):** the `setupItem(parent, id, iconRes, iconColorHex, containerBgColorHex, title, summary, onClick)` helper takes two hex strings and applies them via `Color.parseColor` → `imageTintList` (icon) and `backgroundTintList` (icon container). There are 10 call sites (lines 47–153), each passing a category color + its 20%-alpha container variant.

**Refactor:**
1. Change `setupItem`'s parameter types from `iconColorHex: String, containerBgColorHex: String` to `@ColorRes iconColorRes: Int, @ColorRes containerColorRes: Int`.
2. Replace the body's `Color.parseColor(iconColorHex)` / `Color.parseColor(containerBgColorHex)` with `ContextCompat.getColor(requireContext(), iconColorRes)` / `ContextCompat.getColor(requireContext(), containerColorRes)`.
3. At each of the 10 call sites, replace the `"#ff007a"`/`"#33ff007a"` string pairs with `R.color.app_cat_appearance`/`R.color.app_cat_appearance_container` etc.
4. Remove the now-unused `Color.parseColor` import; add `androidx.annotation.ColorRes` and `androidx.core.content.ContextCompat` imports.

The icon-container background is set programmatically (not via the `bg_settings_icon_circle` drawable's tint), so the drawable's `#33ff007a` (§2.2) is actually a *fallback/default* that gets overridden. Repoint it to `@color/app_cat_appearance_container` for consistency (so the default matches the first category).

---

## Section 3 — Bookmarks/History refactor (11 values)

### 3.1 Layouts (4 values, 3 files)

**`fragment_history_bookmarks.xml`:**
- L76 `#FFFFFF` tint → `?attr/colorOnBackground`
- L83 `#FFFFFF` text → `?attr/colorOnBackground`

**`item_hb_entry.xml`:**
- L44 `#ff007a` → `?attr/colorPrimary`

**`item_hb_header.xml`:**
- L14 `#66ffffff` (40% white) → `?attr/appColorMutedForeground`. Note: this drops the alpha-overlay approach in favor of the solid muted token (`#5C5C66` light / `#888888` dark), which is perceptually equivalent for "dimmed section header" and is the correct M3 pattern (M3 uses solid muted-on-background tones, not alpha overlays, for text).

### 3.2 Drawables (7 values, 5 files)

**`bg_hb_icon_circle.xml`:** `#2Dff007a` → `@color/app_cat_appearance_container` (or a dedicated `hb_primary_container` if cleaner — but reusing the cat token avoids proliferation)
**`bg_hb_search_bar.xml`:** `#16FFFFFF`/`#20FFFFFF` → `?attr/appColorGlassFill`/`appColorGlassStroke` (Phase 1 glass tokens — these are exactly the "translucent overlay" pattern)
**`bg_hb_search_field.xml`:** `#1Effffff`/`#1A1C1E` → `?attr/appColorGlassFill` / `?attr/appColorCardSurface`
**`bg_hb_tab_pill_active.xml`:** `#ff007a` → `?attr/colorPrimary`
**`bg_hb_tab_pill_inactive.xml`:** `#1Affffff` → `?attr/appColorGlassFill`

### 3.3 Consolidate `hb_*` group

The 11 `hb_*` color resources in `colors.xml` (L399–409) are referenced by only 5 layouts + 5 drawables. After repointing those to Phase 1 tokens, the `hb_*` definitions become **unreferenced** and can be deleted from `colors.xml`. Verify with grep before deleting.

---

## Section 4 — Tab Grid refactor (12 values)

### 4.1 Drawables (12 values, 7 files — all the work is here)

**`bg_tab_grid_card_active.xml`:** `#FF007A` → `?attr/colorPrimary`; `#9C00FF` → `?attr/appColorAccentPurple2` (new token from §1)
**`bg_tab_grid_card_header_glass.xml`:** `#0DFFFFFF` → `?attr/appColorGlassFill`
**`bg_tab_grid_close_btn.xml`:** `#20FFFFFF`/`#1AFFFFFF` → `?attr/appColorGlassFill`/`appColorGlassStroke`
**`bg_tab_grid_fab.xml`:** `#80FF007A` (50% primary) → keep as a concrete `@color` (`app_fab_primary_50`) since 50%-alpha-primary is a specific FAB treatment, OR introduce `?attr/colorPrimary` with code-side alpha. **Decision: concrete `@color/app_fab_scrim`** (simpler, drawable-only).
**`bg_tab_grid_pill_active.xml`:** `#E60D0D0D` → `?attr/appColorSheetGlass`
**`bg_tab_grid_pill_group.xml`:** `#1AFFFFFF`/`#14FFFFFF` → `?attr/appColorGlassFill`/`appColorGlassStroke`
**`bg_tab_grid_sheet_glass.xml`:** `#F0080808` → `?attr/appColorSheetGlass`; `#33FF007A`/`#00FF007A` → `?attr/colorPrimary` (gradient endpoints — accept the alpha change as part of theme-adaptivity)

### 4.2 Consolidate `tab_grid_*` group

The 13 `tab_grid_*` resources are referenced by 3 layouts + 8 drawables. After repointing, verify unreferenced and delete from `colors.xml`.

### 4.3 Consolidate `secondary_color_settings[_dark]`

Grep confirmed these 2 values (`colors.xml` L24–25) are unreferenced anywhere except their own definition — delete outright.

---

## Section 5 — Verification plan

### New/extended tests

1. **`SettingsThemeAdaptiveTest`** (new) — inflates `activity_settings.xml` + `fragment_settings_menu.xml` + `item_settings_menu.xml` under all 4 themes; asserts zero hardcoded hex resolves and that backgrounds adapt (Light settings bg ≠ Dark settings bg).
2. **`TabsThemeAdaptiveTest`** (new) — inflates `touch_tab_switcher.xml` + key `bg_tab_grid_*` drawables under all 4 themes; asserts `appColorAccentPurple2` and `appColorSheetGlass` resolve distinctly.
3. **Extend `HomeThemeAdaptiveColorTest`** — add assertions for the 3 new tokens (`appColorAccentPurple2`, `appColorSheetGlass`, `appColorCardSurface`).

### Success criteria

1. `clean app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`
2. Full unit test suite → 0 failures (including the 2 new + 1 extended test)
3. Grep verifies zero hardcoded hex in:
   - `layout/activity_settings.xml`, `fragment_settings_menu.xml`, `item_settings_menu.xml`
   - `layout/fragment_history_bookmarks.xml`, `item_hb_entry.xml`, `item_hb_header.xml`
   - all `bg_hb_*.xml`, `bg_tab_grid_*.xml`, `bg_settings_icon_circle*.xml` drawables
4. `hb_*` (11), `tab_grid_*` (13), `secondary_color_settings[_dark]` (2) = **26 dead color resources deleted** from `colors.xml`, verified unreferenced before deletion
5. On-device: Settings, Bookmarks/History, and Tab Grid all render correctly in Light + Dark themes (manual check)

---

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Deleting `hb_*`/`tab_grid_*` breaks a hidden consumer | Grep the entire repo (including Kotlin `R.color.*` refs) before each deletion; keep resources if any ref found |
| `?attr/` in drawable `<gradient>` endpoints behaves differently than `<solid>` (some Android versions) | Verified pattern in Phase 1 (quote divider uses `?attr` in gradient); reuse the same approach |
| Category colors not theme-adaptive may look wrong in Light theme (e.g. `#E0E0E0` "about" category invisible on white) | Acceptable trade-off per user decision; the category *icon* sits in a tinted circle so the color reads against the circle, not the background |
| `bg_settings_icon_circle_blue.xml` near-duplicates `bg_settings_icon_circle.xml` | Verify which category each serves before consolidating; if both exist for a reason, keep both but repoint hex |
