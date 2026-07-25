# Material Polish — Phase 3 (Consolidation + Remaining Chrome)

**Date:** 2026-07-01
**Status:** Approved design (pending spec review)
**Branch:** `feature/app-review`
**Builds on:** `2026-06-25-material-polish-design.md` (Phase 1) + `2026-06-29-material-polish-phase2-design.md` (Phase 2)

## Goal

Complete the token consolidation story. (1) Repoint the ~25 remaining consumers of the 3 parallel dark-only color groups (`hb_*`, `tab_grid_*`, `secondary_color_settings[_dark]`) to the Phase 1/2 tokens, then delete the 26 dead resources. (2) Repoint the remaining UI chrome — search bars, dialogs, toolbar drawables, Kotlin color literals — to tokens. **Finish line** for "the token layer is the single source of truth."

**Out of scope (legitimately hardcoded):** vector icon internals (`ic_*.xml` — multi-color path fills for logos/launcher icons), the Sora code editor's `EditorColorScheme` syntax-highlighting palette in `CodeEditorActivity.kt` (IntelliJ/Darcula — theme-independent by design), dynamic favicon colors in `SearchOverlayFragment.kt`.

## Context — Phases 1 + 2 recap

The token foundation is built and verified across home + settings + bookmarks + tab-grid drawables:
- 20 custom `?attr` tokens (colors + drawables + glass + purple2 + sheet-glass + card-surface) wired into 4 `*.Base` themes
- 9 category `@color` resources + FAB scrim in `colors_app.xml`
- `dimens_spacing.xml` / `dimens_shape.xml` / `text_appearances_app.xml`
- `HomeThemeAdaptiveColorTest` (6), `SettingsThemeAdaptiveTest` (2), `TabsThemeAdaptiveTest` (1) — all green

**Phase 2 lesson applied:** every deletion gated on a *repo-wide* grep (not just the files I touched). Phase 2's scan missed consumers; Phase 3's scan (this spec) is exhaustive and verified.

---

## Section 1 — Consolidation: the 3 color groups (32 refs → delete 26 resources)

### 1.1 `hb_*` group (15 refs across 3 files)

**`app/src/main/res/layout/item_hb_entry.xml`** (4 refs):
- `@color/hb_foreground` → `?attr/colorOnBackground`
- `@color/hb_muted_foreground` → `?attr/appColorMutedForeground`
- `@color/hb_secondary` → `?attr/appColorCardSurface`
- `@color/hb_divider` → `?attr/colorSurfaceVariant` (M3 standard divider tone — same as Phase 2 settings dividers)

**`app/src/main/res/layout/fragment_history_bookmarks.xml`** (8 refs):
- `@color/hb_background` → `?android:attr/colorBackground`
- `@color/hb_foreground` → `?attr/colorOnBackground`
- `@color/hb_muted_foreground` → `?attr/appColorMutedForeground` (×4 instances)

**`app/src/main/java/com/xhub/browser/fragment/HistoryBookmarksBottomSheet.kt`** (3 refs):
- `R.color.hb_muted_foreground` → resolve via `MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, ...)` OR add a small helper that resolves `?attr/appColorMutedForeground`. **Decision: use `MaterialColors.getColor` with `android.R.attr.textColorSecondary`** — the standard M3 way to get "muted foreground" in Kotlin without needing the custom attr resolvable at runtime (avoids the Robolectric Base-theme constraint leaking into Kotlin).
- `R.color.hb_secondary` (×2) → `MaterialColors.getColor(context, androidx.appcompat.R.attr.colorBackground, ...)` for the card surface.

### 1.2 `tab_grid_*` group (15 refs across 4 files)

**`app/src/main/res/layout/tab_drawer_view.xml`** (8 refs):
- `@color/tab_grid_foreground` (×3) → `?attr/colorOnBackground`
- `@color/tab_grid_secondary_foreground` (×2) → `?attr/appColorMutedForeground`
- `@color/tab_grid_muted_foreground` → `?attr/appColorSubtleForeground`
- `@color/tab_grid_secondary` → `?attr/colorSurfaceVariant` (divider line — M3 standard)
- `@color/tab_grid_background` → `?attr/appColorSheetGlass`

**`app/src/main/res/layout/tab_list_item.xml`** (5 refs):
- `@color/tab_grid_foreground` → `?attr/colorOnBackground`
- `@color/tab_grid_card_foreground` → `?attr/colorOnBackground`
- `@color/tab_grid_muted_foreground` → `?attr/appColorSubtleForeground`
- `@color/tab_grid_secondary` → `?attr/colorSurfaceVariant`
- `@color/tab_grid_background` → `?attr/appColorSheetGlass`
- `@color/tab_grid_background` → `?attr/appColorSheetGlass`

**`app/src/main/res/layout/tab_list_item_horizontal.xml`** (1 ref):
- `@color/tab_grid_foreground` → `?attr/colorOnBackground`

**`app/src/main/res/drawable/bg_tab_grid_card.xml`** (1 ref):
- `@color/tab_grid_card` → `?attr/appColorCardSurface`

### 1.3 `secondary_color_settings[_dark]` (2 refs)

**`app/src/main/res/drawable/toolbar_elevate.xml`:** `@color/secondary_color_settings` → `?attr/colorSurface`
**`app/src/main/res/drawable/toolbar_elevate_dark.xml`:** `@color/secondary_color_settings_dark` → `?attr/colorSurface`

### 1.4 Deletion (after all 32 refs repointed)

After grep confirms zero remaining consumers of each group, delete from `app/src/main/res/values/colors.xml`:
- 11 `hb_*` colors
- 13 `tab_grid_*` colors
- 2 `secondary_color_settings[_dark]` colors
- **= 26 dead resources removed**

---

## Section 2 — Remaining chrome layouts (15 values across 7 files)

| File | Values | Mapping |
|------|--------|---------|
| `item_suggestion_ask.xml` | 4 | `#FFFFFF`→`?attr/colorOnBackground`; `#1A1A1A`→`?attr/appColorSheetGlass`; `#FF007A`→`?attr/colorPrimary`; `#A0A0A0`→`?attr/appColorMutedForeground` |
| `search.xml` | 3 | `#0b0b0b`/`#1a1a1a`→`?attr/appColorSheetGlass`; `#9A9A9A`→`?attr/appColorMutedForeground` |
| `simple_list_item.xml` | 2 | `#FFFFFF`→`?attr/colorOnBackground`; `#A0A0A0`→`?attr/appColorMutedForeground` |
| `fragment_ask_overlay.xml` | 2 | `#0b0b0b`→`?attr/appColorSheetGlass`; `#FFFFFF`→`?attr/colorOnBackground` |
| `dialog_list_item.xml` | 2 | `#FFFFFF`→`?attr/colorOnBackground`; `#A0A0A0`→`?attr/appColorMutedForeground` |
| `site_suggestion_item.xml` | 1 | `#FFFFFF`→`?attr/colorOnBackground` |
| `activity_manage_shortcuts.xml` | 1 | `#FFFFFF`→`?attr/colorOnBackground` |

---

## Section 3 — Remaining chrome drawables (~12 files, ~30 values)

Repoint the UI drawables (NOT `ic_*.xml` vector icons — those stay). Key files:
- `bg_menu_card.xml` (5) → glass/card tokens
- `address_bar_background.xml` (4) → sheet-glass/glass tokens
- `bg_ask_search_bar.xml` (4) → glass tokens
- `tab_item_bg.xml` (5) → glass tokens
- `bg_quality_chip.xml`, `bg_bottom_audio_button.xml`, `bg_bottom_assistant_pill.xml`, `bg_add_group_btn.xml`, `bg_shortcut_tile.xml` (2 each) → glass/card tokens
- `toolbar_chip_bg.xml` (1), `splash_background.xml` (1), `scrollbar.xml` (1) → tokens

**Note:** some of these may be dark-only drawables used in contexts where a theme token is appropriate. Each will be read before editing to apply the correct token (same discipline as Phase 1/2).

---

## Section 4 — Kotlin UI color literals (6 values)

| File | Line | Current | New |
|------|------|---------|-----|
| `WebBrowserActivity.kt` | 4144 | `Color.parseColor("#0b0b0b")` | `MaterialColors.getColor(this, android.R.attr.windowBackground, Color.BLACK)` |
| `WebBrowserActivity.kt` | 4150 | `Color.parseColor("#9A9A9A")` | `MaterialColors.getColor(this, android.R.attr.textColorHint, Color.GRAY)` |
| `WebBrowserActivity.kt` | 4236 | `Color.parseColor("#171717")` | `MaterialColors.getColor(this, android.R.attr.colorBackground, Color.BLACK)` |
| `WebBrowserActivity.kt` | 4240 | `Color.parseColor("#1a1a1a")` | `MaterialColors.getColor(this, android.R.attr.colorBackground, Color.BLACK)` |
| `ThemeUtils.kt` | 262 | `Color.parseColor("#1A1A1A")` | resolve via `?attr/colorSurface` equivalent |
| `ThemeUtils.kt` | 281 | `Color.parseColor("#222222")` | resolve via `?attr/colorSurfaceVariant` equivalent |

`SearchOverlayFragment.kt:129` (`Color.parseColor(site.color)`) is **dynamic** (parses a runtime favicon color) — leave as-is.

`CodeEditorActivity.kt:202-239` (30 literals) — **code editor syntax scheme, out of scope**.

---

## Section 5 — Verification plan

### Tests

1. **Extend `SettingsThemeAdaptiveTest` / `TabsThemeAdaptiveTest`** — add assertions that `hb_*`/`tab_grid_*` resource names no longer resolve (proves deletion). **Note:** can't easily test "resource absent" in Robolectric (the lookup just returns 0). Instead: a grep-based check in Task 13 is the verification.
2. **No new tokens needed** — Phase 3 reuses the existing 20 attrs + 9 category colors. No new attrs/colors/styles files created.

### Success criteria

1. `clean app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`
2. Full unit test suite → 0 failures
3. Repo-wide grep: `/usr/bin/grep -rn "@color/hb_\|R\.color\.hb_\|@color/tab_grid_\|R\.color\.tab_grid_\|secondary_color_settings" app/src/main/` → **only matches in `colors.xml` definitions** (which will then be deleted, leaving zero)
4. After deletion: the 3 groups' first entries are gone — `/usr/bin/grep -c "hb_background\|tab_grid_background\|secondary_color_settings" app/src/main/res/values/colors.xml` → prints `0`. (Other unrelated entries in colors.xml, like the MD palette, remain untouched.)
5. Grep on the 7 chrome layouts + 12 chrome drawables: zero hardcoded hex
6. On-device: search overlay, dialogs, toolbar, tab drawer all render correctly in Light + Dark

---

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Deletion breaks a hidden consumer | Repo-wide grep (Section 1.4) before each deletion; the scan in this spec is exhaustive and verified against the actual files |
| `MaterialColors.getColor` with framework attrs returns wrong value in some themes | Use specific M3 attrs (`com.google.android.material.R.attr.colorSurfaceVariant`) where the framework attr is ambiguous; verify on-device |
| `?attr/` in `toolbar_elevate*.xml` doesn't resolve (toolbar may use a different theme overlay) | Test inflation; if it fails, fall back to a concrete `@color` (acceptable — toolbar elevation is a niche case) |
| Some chrome drawables are referenced by both light and dark code paths expecting different behavior | Read each drawable's consumers before repointing; if a drawable is dark-only by design, keep it concrete with a comment |
