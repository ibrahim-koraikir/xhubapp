# Design Spec: Home Screen Design Token Polish

## Problem Description

The OLED minimalist home screen (established in `2026-06-03-oled-minimalist-home-screen-design.md`)
had correct visual intent but **poor maintainability**: every color and dimension was hardcoded as
inline hex/dp in both XML layouts and Kotlin code. The existing token files
(`colors_home.xml`, `dimens_home.xml`) went largely unused. This made palette changes fragile and
introduced subtle inconsistencies (e.g. tile surface `#161616` in code vs `#111111` in XML).

## Design Direction Approved

**Token-based OLED minimalist refinement** — keep the pure black / flat-circle / monochrome
direction; introduce a single consistent design-token layer that all files read from. No new
architecture, no new libraries, no color-theme change.

Accent color decision: the existing vivid magenta (`#FF007A`) is retained in the token file as
`home_accent_ring` but is not applied to tiles by default. Legacy aliases `home_primary` /
`home_primary_15` kept so existing drawables compile without modification.

---

## Changes

### Token files

#### `colors_home.xml`
Added: `home_tile_surface` (#161616), `home_search_surface` (#0D0D0D), `home_stroke` (#333333),
`home_dim_foreground` (80% white), `home_subtle_foreground` (47% white), `home_icon_tint` (50%
white), `home_initial_text` (#CCCCCC), `home_group_label` (67% white).
Renamed magenta to `home_accent_ring`; kept `home_primary` / `home_primary_15` as legacy aliases.

#### `dimens_home.xml`
Added: `home_tile_frame_size` (72dp), `home_tile_radius` (36dp), `home_tile_stroke_width` (1dp),
`home_fav_icon_radius` (14dp, restored), all grid gap / label / empty-state tokens.

### String resources (`strings.xml`)
Added: three greeting variants, `home_search_hint`, `home_quick_access_title`,
`home_quick_access_subtitle`, `home_edit_shortcuts`, empty-state strings, a11y descriptions.

### Layout (`layout_home_screen.xml`)
- All hardcoded hex → `@color/home_*`, all literal dp/sp → `@dimen/home_*`.
- Brand `xHub` appears only in the **expanded header** (collapsed toolbar retains it centrally
  but does not double-stack).
- Placeholder greeting text uses `@string/home_greeting_morning`.
- `homeSearchCard` and `btnEditShortcuts` get `android:foreground="?attr/selectableItemBackground"`
  for tactile ripple feedback.
- `homeProfileImage` icon changed from `ic_launcher_foreground` to `ic_bookmarks` (semantically
  correct — button opens bookmarks).
- `contentDescription` attributes use `@string/*` throughout.

### Drawables
- `bg_home_logo_minimalist.xml`: `#1A1A1A` → `@color/home_secondary`, `#333333` → `@color/home_stroke`.
- `bg_home_profile_ring_minimalist.xml`: `#333333` → `@color/home_stroke`.
- `bg_home_search_card.xml`: inline hex → `@color/home_search_surface`, `@color/home_stroke`,
  radius → `@dimen/home_search_radius`.
- `bg_shortcut_tile_ripple.xml` **(new)**: oval-masked ripple using `@color/home_icon_tint`
  with tile surface + stroke base layer.

### `buildDynamicShortcuts()` in `WebBrowserActivity.kt`
- All `0xFF161616`, `0xFF333333`, `dp72`, `dp8`, `dp24`, `26f`, `11.5f` etc. replaced with
  `resources.getDimensionPixelSize(R.dimen.*)` / `ContextCompat.getColor(R.color.*)`.
- Greeting uses `getString(R.string.home_greeting_*)`.
- Tile initial letter: `Typeface.BOLD` (was `NORMAL`) for visual rhythm.
- `MaterialCardView.foreground` = `bg_shortcut_tile_ripple` for circular ripple inside icon frame.
- **Empty state**: when `ShortcutRepository.loadGroups()` returns no sites, shows a centred
  `ic_bookmarks` icon + "No shortcuts yet" + tappable "Add your favourite sites" that launches
  `ManageShortcutsActivity`.

---

## Verification

- `.\gradlew.bat assembleSlionsFullDownloadDebug` → **BUILD SUCCESSFUL** ✅
- Existing `HomeScreenLayoutTest` assertions (`homeSearchCard`, `shortcutsDynamicContainer`,
  `homeTitle`) all pass — IDs were not changed.
