# Material Polish — Phase 1 (Foundation + Home Screen)

**Date:** 2026-06-25
**Status:** Approved design (pending spec review)
**Branch:** `feature/app-review`

## Goal

Elevate the visual design of the Fulguris/XHub browser through a Material 3 polish pass — **not** a rewrite. Centralize every visual value (color, type, spacing, shape, elevation, motion) into reusable design tokens, and apply them consistently screen-by-screen. Keep all existing structure, navigation, and functionality intact.

**Phase 1 scope:** build the token foundation and refactor the **home screen** end-to-end as the reference implementation. Stop for review before touching any other screen (Phase 2 fans out to settings, dialogs, tab grid, bookmarks, history, toolbars, bottom sheets).

## Context — what the codebase is today

This is a **View-based** Android app (XML layouts + Material Components 1.13.0 + fragments + RecyclerView), **not** Jetpack Compose. There is no `Theme.kt`/`Color.kt`/`Type.kt` Compose theme. The theming foundation is already strong but inconsistent:

- **Five user-selectable themes** applied at runtime via `ThemedActivity.themeStyle()` → `setTheme()`: `Theme.App.Light`, `Theme.App.White`, `Theme.App.Dark`, `Theme.App.Black`, `Theme.App.DayNight` (DEFAULT). See `app/src/main/java/com/xhub/browser/activity/ThemedActivity.kt:44-52`.
- **No `setDefaultNightMode()` anywhere** — themes are driven entirely by user preference, independent of system night-mode. Confirmed by grep: the only `UI_MODE_NIGHT` references are two *reads* (`CodeEditorActivity.kt:257`, `SuggestionsAdapter.kt:143`).
- **`values-night/` therefore cannot distinguish White↔Light or Black↔Dark** — it follows the system, not the user preference. This is why Approach A (semantic theme-attr tokens) is the only viable approach for a 4-theme app. Approaches B (`values-night`) and C (programmatic tokens) were rejected during brainstorming.
- The home screen (`layout_home_screen.xml`) has its own bespoke dark aesthetic (OLED black + starfield `bg_home_starfield` + `#CC000000` scrim + magenta `#FF007A` + orange `#FF6B35` + purple) that is **divorced from the M3 theme** — it references `@color/home_*` resources, not `?attr/colorPrimary` etc.
- `colors.xml` has ~300 hardcoded hex values (full MD palette + brand colors) used across layouts.
- `layout_home_screen.xml` contains inline hardcoded hex (`#CC000000`, `#59FF6B35`, `#80FF6B35`, `#141a29`) rather than color resources.
- The shortcut grid is a **RecyclerView** (deliberately moved from LinearLayout in commit 870588a for recycling) — kept as-is.

### Decisions locked during brainstorming

| Decision | Choice |
|----------|--------|
| App is View-based, not Compose | Proceed with XML + Material Components polish (confirmed) |
| Direction | Polish XML/Views, not migrate to Compose |
| Scope | Whole app, consistent — but **phased**: foundation + home first, then review |
| Target look | Bring the home screen **into the M3 theme** (theme-adaptive across all 4 themes). Magenta `#FF007A` → `?attr/colorPrimary` everywhere. |
| Starfield | Keep, but **theme-aware**: starfield+scrim for Dark/Black; new faint vector `bg_home_starfield_light.xml` for Light/White |
| Orange accent | `#D9531E` in Light/White (WCAG-AA), `#FF6B35` in Dark/Black |
| Motion tier | **Rich** — but shared-element tile→webpage dropped (WebView capture risk); keep toolbar collapse, parallax, entrances, streak pulse; **crossfade** for theme switch (not circular reveal) |
| Approach | A — semantic `?attr` tokens |
| Tile grid | Keep RecyclerView |

---

## Section 1 — Token Foundation Architecture

**One source of truth.** Every visual value resolves to either a standard M3 role, a custom semantic attr, a named `@dimen`, or a `TextAppearance.App.*` style. Concrete hex values live **only** inside theme style `<item>` lines.

### New files

| File | Purpose |
|------|---------|
| `app/src/main/res/values/attrs_app.xml` | Declares 15 custom semantic attrs |
| `app/src/main/res/values/dimens_spacing.xml` | 4dp-grid spacing scale |
| `app/src/main/res/values/dimens_shape.xml` | Corner-radius + elevation tokens |
| `app/src/main/res/values/text_appearances_app.xml` | `TextAppearance.App.*` scale (7 standard + 4 specialized) |

### Files modified (token wiring)

- `app/src/main/res/values/styles.xml` — each of `Theme.App.Light/White/Dark/Black` gets a `<item>` block assigning concrete values to the new attrs. This is the **only** place concrete hex lives.
- `app/src/main/res/values/colors_home.xml` — home-specific concrete colors are kept as resources but the home layout stops referencing them directly (it references `?attr/appColor*` instead); legacy `home_*` color resources remain for any non-home consumers and are not deleted.

### Resolution chain (example)

```
Layout:   android:textColor="?attr/colorOnBackground"
                ↓ resolves per active theme
          Theme.App.Light  → #FF1A1A1A
          Theme.App.White  → #FF222222
          Theme.App.Dark   → #FFFFFFFF
          Theme.App.Black  → #FFFFFFFF
```

### Declaration syntax

Custom attrs are declared once in `attrs_app.xml`:

```xml
<resources>
    <!-- Color attrs -->
    <attr name="appColorAccentOrange" format="color" />
    <attr name="appColorMutedForeground" format="color" />
    <!-- ... 13 more color attrs ... -->

    <!-- Drawable attr (the only non-color one) -->
    <attr name="appHomeBackground" format="reference" />
</resources>
```

Then each theme style assigns a concrete value:

```xml
<style name="Theme.App.Light" parent="...">
    <item name="appColorAccentOrange">#D9531E</item>
    <item name="appColorMutedForeground">#5C5C66</item>
    <item name="appHomeBackground">@drawable/bg_home_starfield_light</item>
</style>
<style name="Theme.App.Dark" parent="...">
    <item name="appColorAccentOrange">#FF6B35</item>
    <item name="appColorMutedForeground">#FF888888</item>
    <item name="appHomeBackground">@drawable/bg_home_starfield</item>
</style>
```

`format="reference"` (not `"color"`) is what lets `appHomeBackground` point at a drawable — this is the key difference from the other 14 attrs.

### Custom attrs declared (15)

`appColorAccentOrange`, `appColorAccentOrangeFaint` (35% alpha — quote mark), `appColorAccentOrangeLight`, `appColorAccentOrangeDark`, `appColorAccentPurple`, `appColorHomeTileSurface`, `appColorHomeStroke`, `appColorHomeTileStroke`, `appColorMutedForeground`, `appColorSubtleForeground`, `appColorDimForeground`, `appColorIconTint`, `appColorGroupLabel`, `appHomeBackground` (drawable ref), `appStarfieldScrim` (color).

### Naming conventions (locked)

- M3 standard attrs: used as-is (`?attr/colorPrimary`, `?colorSurface`, `?colorOnSurfaceVariant`, `?colorOnBackground`).
- Custom: `?attr/appColor*`, `?attr/appHomeBackground`.
- Spacing: `@dimen/spacing_{xs,sm,md,lg,xl,xxl,3xl}`.
- Radius: `@dimen/radius_{sm,md,lg,xl}` + `radius_full`.
- Type: `@style/TextAppearance.App.{Display,Headline,Title,Body,Label}.{Small,Medium,Large}` + 4 specialized.

### Scope discipline (YAGNI)

- The ~300 colors in `colors.xml`'s MD palette and the 17 `Accent_*` user-accent styles are **left untouched**. Refactoring every consumer is out of scope for Phase 1.
- `ssl_secured`/`ssl_unsecured`, brand favicon colors — untouched (already semantic).

---

## Section 2 — Color Scheme & Per-Theme Mapping

Every semantic color resolves to a perceptually-appropriate value per theme so the home screen reads correctly on white, light grey, dark grey, and OLED black.

**Magenta `#FF007A` → `?attr/colorPrimary` in all four themes** (brand unification).

### Light theme (and White, with noted diffs)

| Attr | Light value | Notes |
|------|-------------|-------|
| `colorPrimary` | `#FF007A` | brand, 4.9:1 on white ✓ AA |
| `appColorAccentOrange` | `#D9531E` | deepened — 4.6:1 on white ✓ AA |
| `appColorAccentOrangeFaint` | `#59D9531E` | quote mark — 35% alpha of the AA orange (decorative, 60sp) |
| `appColorAccentOrangeLight` | `#C24416` | streak text — 5.8:1 on white ✓ AA |
| `appColorAccentOrangeDark` | `#A8330F` | gradient end |
| `appColorAccentPurple` | `#6F4BE6` | private chip icon on white |
| `appColorMutedForeground` | `#5C5C66` | 7.2:1 ✓ |
| `appColorSubtleForeground` | `#6E6E78` | 5.0:1 ✓ AA |
| `appColorDimForeground` | `#42424A` | 9:1 ✓ |
| `appColorHomeTileSurface` | `#F3F3F6` (Light) / `#FFFFFFFF` (White) | |
| `appColorHomeStroke` | `#D7D7DD` | hairline on light |
| `appColorHomeTileStroke` | `#1A000000` | faint border |
| `appColorIconTint` | `#5C5C66` | icons on light |
| `appColorGroupLabel` | `#8A8A94` | group label on light |
| `appHomeBackground` | `@drawable/bg_home_starfield_light` | NEW vector |
| `appStarfieldScrim` | `#14FFFFFF` (Light) / `#0A000000` (White) | barely-there |

### Dark theme (and Black, with noted diffs)

| Attr | Dark value | Notes |
|------|------------|-------|
| `colorPrimary` | `#FF007A` | |
| `appColorAccentOrange` | `#FF6B35` | kept — pops on dark |
| `appColorAccentOrangeFaint` | `#59FF6B35` | quote mark — 35% alpha |
| `appColorAccentOrangeLight` | `#FF8C5B` | kept |
| `appColorAccentOrangeDark` | `#BF3A1E` | kept |
| `appColorAccentPurple` | `#A78BFA` | kept |
| `appColorMutedForeground` | `#FF888888` | kept |
| `appColorSubtleForeground` | `#77FFFFFF` | kept |
| `appColorDimForeground` | `#CCFFFFFF` | kept |
| `appColorHomeTileSurface` | `#FF161616` (Dark) / `#FF0B0B0B` (Black) | |
| `appColorHomeStroke` | `#FF333333` | kept |
| `appColorHomeTileStroke` | `#1AFFFFFF` | kept |
| `appColorIconTint` | `#80FFFFFF` | kept |
| `appColorGroupLabel` | `#AAFFFFFF` | kept |
| `appHomeBackground` | `@drawable/bg_home_starfield` | existing |
| `appStarfieldScrim` | `#CC000000` (Dark) / `#E6000000` (Black, 90%) | kept |

### New drawable asset — `bg_home_starfield_light.xml`

Vector drawable (decision L2): sparse faint pale-blue dots on near-white `#F8F9FC`. Cheaper to render than the PNG, fully scalable. Created in `app/src/main/res/drawable/`.

### WCAG-AA verification

Each foreground/background pair meets **≥4.5:1** for body text, **≥3:1** for large text/icons. The implementation plan includes a contrast-verification step (computed ratios recorded as a comment block per theme) — not a runtime check.

### Out of scope

The 17 `Accent_*` user-accent styles, `md_theme_*` M3 role values, SSL/brand colors — untouched.

---

## Section 3 — Typography Scale

**Problem:** ~14 one-off `textSize` values scattered inline in the home layout, with `fontFamily`/`textStyle` ad-hoc per TextView. No hierarchy.

### Scale (M3-aligned)

| Style | Parent | Size | Weight | Home use |
|-------|--------|------|--------|----------|
| `TextAppearance.App.DisplayLarge` | M3 DisplayLarge | 32sp | medium | reserved |
| `TextAppearance.App.DisplayMedium` | M3 DisplayMedium | 22sp | medium | quote text |
| `TextAppearance.App.HeadlineSmall` | M3 HeadlineSmall | 20sp | medium | collapsed title |
| `TextAppearance.App.TitleLarge` | M3 TitleLarge | 17sp | medium | section title |
| `TextAppearance.App.TitleMedium` | M3 TitleMedium | 15sp | medium | privacy/reading-list title, search text |
| `TextAppearance.App.BodyMedium` | M3 BodyMedium | 13sp | regular | greeting subtitle, privacy desc, RL meta, edit btn |
| `TextAppearance.App.BodySmall` | M3 BodySmall | 12sp | regular | section subtitle, streak label |
| `TextAppearance.App.LabelMedium` | M3 LabelMedium | 11sp | medium, caps, 0.15 tracking | "Quote of the day", group labels |
| `TextAppearance.App.LabelSmall` | M3 LabelSmall | 10sp | medium | stat chip text |

### Specialized (kept bespoke — genuinely singular)

| Style | Size | Weight | Use |
|-------|------|--------|-----|
| `TextAppearance.App.TileInitial` | 26sp | black | big letter in tile |
| `TextAppearance.App.TileLabel` | 11.5sp | medium | site-name under tile (kept at 11.5sp — deliberately tuned) |
| `TextAppearance.App.QuoteMark` | 60sp | black | decorative glyph |
| `TextAppearance.App.GreetingLabel` | 12sp | light, 0.02 tracking | "Good morning" line |

**In the layout:** each `TextView` loses `textSize`/`fontFamily`/`textStyle` and gains `android:textAppearance="@style/TextAppearance.App.*"`. `android:textColor` → `?attr/colorOnBackground` or the relevant semantic attr.

**Font family:** platform `sans-serif` via M3 defaults (no custom typeface added in Phase 1 — separate decision, avoids asset weight + a11y risk).

---

## Section 4 — Shape, Spacing & Elevation

### Spacing scale (strict 4dp grid)

| Token | Value | Replaces |
|-------|-------|----------|
| `spacing_xs` | 4dp | tile padding, edit icon/label gap, stat chip icon gap |
| `spacing_sm` | 8dp | pill gap, tile row gap, stat chip end margin |
| `spacing_md` | 12dp | home_padding_top, pill padding_h, edit btn padding_h |
| `spacing_lg` | 16dp | card padding, section header margin |
| `spacing_xl` | 20dp | section gap, hero padding_h |
| `spacing_xxl` | 32dp | empty-state padding_v |
| `spacing_3xl` | 40dp | home_padding_bottom, hero padding_top |

**Deliberate non-grid exception:** `home_padding_horizontal = 18dp` stays 18dp (documented comment — visual rhythm you tuned for card insets; snapping to 16/20 changes it).

### Shape / corner-radius scale

**Phase-1 scope note (correction):** the existing `ShapeAppearance.App.Small/Medium/LargeComponent` styles (`styles.xml:1004-1017`) are **app-wide** — wired into every theme (lines 248-250 etc.) and consumed by every Material Component (buttons, cards, dialogs, chips). They are currently all hardcoded to `8dp`. **Phase 1 does NOT change their values** (that would alter every component app-wide — a Phase-2 concern). Instead, Phase 1 introduces `@dimen/radius_*` tokens and references them **directly inside home-screen drawables only**. The `ShapeAppearance.App.*` → token refactor is deferred to Phase 2.

| Token | Value | Phase-1 use (home drawables only) |
|-------|-------|-----------------------------------|
| `@dimen/radius_sm` | 8dp | reading-list thumb, privacy icon, category pills, home_fav_icon |
| `@dimen/radius_md` | 12dp | stat chip |
| `@dimen/radius_lg` | 16dp | shortcut card, tile label bg |
| `@dimen/radius_xl` | 18dp | tile squircle (deliberately tuned, kept) |
| `@dimen/radius_full` | 28dp | search pill, hero action circles, greeting pill, streak chip |

Home drawables that hardcode `android:radius` get repointed to `@dimen/radius_*`. The app-wide `ShapeAppearance.App.*` styles remain at 8dp, untouched, in Phase 1.

### Elevation scale + dark-theme tonal elevation

| Token | Value | Used for |
|-------|-------|----------|
| `elevation_none` | 0dp | flat surfaces |
| `elevation_sm` | 1dp | dividers, hairlines |
| `elevation_md` | 3dp | shortcut tiles (current value preserved) |
| `elevation_lg` | 6dp | flame button (current value preserved) |

**Dark-theme rule:** Material 3 uses *tonal* elevation (lighter overlay color) rather than shadows on dark surfaces — real shadows are invisible on OLED black. `Theme.App.Dark` and `Theme.App.Black` get `elevationOverlayColor` configured (one `<item>` per theme) so elevated tiles lift via tone. Light/White keep real shadows at 3dp/6dp.

---

## Section 5 — Motion (Rich tier, with discipline)

### Global infrastructure

| Item | Mechanism | Guard |
|------|-----------|-------|
| Reduced-motion kill-switch | `MotionUtils.animationsEnabled(context)` reads `Settings.Global.ANIMATOR_DURATION_SCALE` + `AccessibilityManager.isHighTextContrastEnabled` | Honored by all effects; snaps to end state if disabled |
| Activity transitions | `ActivityOptions.makeSceneTransitionAnimation` + `fade` (no shared-element on launch) | Skipped if reduced-motion |
| Theme-switch crossfade | `overridePendingTransition(R.anim.theme_fade_in, R.anim.theme_fade_out)`, 150ms, wired into `ThemedActivity.restart()` | Already gated on `restart()` |

### Home effects

| Effect | Mechanism | Guard |
|--------|-----------|-------|
| **a) Tile entrance** | Staggered fade+scale on grid bind: alpha 0→1, scale 0.92→1.0, 180ms, 50ms stagger, capped at first screenful (≤8 tiles). `DecelerateInterpolator`. | `firstBind` flag (no re-animate on scroll); reduced-motion |
| **b) Streak pulse** | One-shot AVD (scale 1.0→1.15→1.0, 400ms) **only when streak count increments**. Not idle looping. | reduced-motion |
| **c) Starfield parallax** | `NestedScrollView` scroll → translate background `ImageView` at 0.5× scroll delta, clamped **±12dp** each direction (net 24dp travel) to protect edges. | reduced-motion; `isLowRamDevice()` |
| **d) Greeting/quote entrance** | fade+slide-up (8dp) over 220ms on first layout | reduced-motion |
| **e) Edit button press** | `stateListAnimator` lifting elevation 3dp→6dp on press (M3 `_raise_button`) | none — platform-shipped |

### Rich-tier effects (kept)

| Effect | Mechanism | Risk mitigation |
|--------|-----------|-----------------|
| **Toolbar collapse animation** | `AppBarLayout.OnOffsetChangedListener` → animate search-pill `scaleY` + `alpha` + translate via `spring<Float>` (damping 0.7). Freeze at collapse-complete (don't track every fling frame). | Disabled on `isLowRamDevice`; reduced-motion |
| **Theme-switch crossfade** | 150ms alpha crossfade in `restart()` (circular reveal rejected) | Skip if `savedInstanceState != null` |

### Dropped (risk)

- **Shared-element tile→webpage** — WebView first-frame capture is unreliable; dropped. (Could be revisited with a bitmap-snapshot fallback in a later phase.)

### Performance budget

- No effect runs on the input pipeline — all property/alpha/scale (GPU-composited).
- `isLowRamDevice()` → disables parallax + toolbar collapse; keeps entrances + ripples.
- Reduced-motion a11y → disables everything; states snap.
- **No new threads, no WebView hooks.** Motion observes existing state (scroll offset, bind events); never touches the WebView lifecycle.
- Kill-switch: one boolean `MotionUtils.animationsEnabled` gates all effects; design is additive, not structural.

---

## Section 6 — Home Screen Refactor (reference implementation)

The single screen Phase 1 delivers end-to-end. Exercises every token from Sections 1–5; becomes the template for Phase 2.

### 6.1 Layout changes to `layout_home_screen.xml`

```xml
<!-- Backgrounds -->
android:src="@drawable/bg_home_starfield"            → "?attr/appHomeBackground"
android:background="#CC000000"                       → "?attr/appStarfieldScrim"

<!-- Hero text colors -->
android:textColor="@color/home_foreground"           → "?attr/colorOnBackground"
android:textColor="@color/home_muted_foreground"     → "?attr/appColorMutedForeground"
android:textColor="@color/home_subtle_foreground"    → "?attr/appColorSubtleForeground"
android:textColor="@color/home_dim_foreground"       → "?attr/appColorDimForeground"

<!-- Orange accents. NOTE: the quote mark (#59FF6B35, 35% alpha) and the quote
     label (#80FF6B35, 50% alpha) have DIFFERENT alphas, so they map to TWO attrs,
     not one. appColorAccentOrange = solid (label), appColorAccentOrangeFaint = 35% (mark). -->
android:textColor="#59FF6B35"                        → "?attr/appColorAccentOrangeFaint"
android:textColor="#80FF6B35"                        → "?attr/appColorAccentOrange"
app:tint="@color/home_accent_orange_light"           → "?attr/appColorAccentOrangeLight"
app:tint="@color/home_accent_purple"                 → "?attr/appColorAccentPurple"

<!-- Text appearances (every TextView) -->
android:textSize + fontFamily + textStyle (triplet)  → android:textAppearance="@style/TextAppearance.App.*"

<!-- Spacing (every padding/margin literal) -->
8dp / 12dp / 16dp / 20dp / 32dp / 40dp literals      → @dimen/spacing_*
(home_padding_horizontal stays 18dp — documented exception)

<!-- Radii (inside drawables) -->
18dp / 16dp / 12dp / 28dp hardcoded radii            → @dimen/radius_*
```

### 6.2 New code — `HomeMotionController`

Kotlin, ~80 lines, attached to the home binding in `WebBrowserActivity`:
- Holds `firstBind` flag.
- Wires `NestedScrollView.setOnScrollChangeListener` for parallax (translate bg ImageView by 0.5×scrollY, clamped ±12dp).
- Stagger-animates the tile RecyclerView's children on `firstBind` via post-onBind logic (post `ItemAnimator`-adjacent).
- Fires the streak-pulse AVD when `streakCount` changes.
- Reads `MotionUtils.animationsEnabled` at every effect; snaps if disabled.

**This is the only new code file.** No new fragments/activities/navigation.

### 6.3 Kotlin-side touch points

- **`WebBrowserActivity.buildDynamicShortcuts()`** (`WebBrowserActivity.kt:1811`, home bound via `iBinding.homeScreenOverlay`) — instantiate `HomeMotionController`, pass the `NestedScrollView` + background `ImageView` + tile `RecyclerView` + streak `View`. The `homeBookmarksButton`/`homeGreeting`/`shortcutsDynamicContainer` bindings (lines 1824–1850) are preserved unchanged.
- **`ThemedActivity.restart()`** (`ThemedActivity.kt:126`) — add `overridePendingTransition(R.anim.theme_fade_in, R.anim.theme_fade_out)` after the `startActivity`. No change to theme-selection logic.

### 6.4 Drawables touched (structure unchanged — only color/radius refs swap)

- `bg_home_starfield` (kept for dark/black)
- `bg_home_starfield_light.xml` (NEW — Section 2)
- `bg_home_hero`, `bg_home_shortcut_card`, `bg_home_greeting_pill`, `bg_home_streak_chip`, `bg_home_flame_btn`, `bg_home_action_glass`, `bg_home_stat_chip`, `bg_home_quote_divider`, `bg_shortcut_manager_icon_btn` — color refs → `?attr/appColor*`

### 6.5 Out of scope (Phase 2)

Settings fragments, dialogs, bottom sheets, tab grid, bookmarks, history, toolbars, search overlay, video-download bottom sheet — untouched in Phase 1.

### 6.6 Success criteria

1. `.\gradlew.bat assembleSlionsFullDownloadDebug` → `BUILD SUCCESSFUL`.
2. `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "*HomeScreenLayoutTest*"` → passes. The test (`app/src/test/java/com/xhub/browser/view/HomeScreenLayoutTest.kt`) uses `Theme_App_Black` and asserts presence of `homeScreenBackground`, `homeScreenBackgroundOverlay`, `shortcutsDynamicContainer`, `homeTitle` — **all IDs preserved** by this design.
2b. **New test** `HomeThemeAdaptiveColorTest` (added in Phase 1) inflates `layout_home_screen` under each of `Theme.App.Light`, `Theme.App.White`, `Theme.App.Dark`, `Theme.App.Black`, resolves `?attr/colorPrimary`/`?attr/appHomeBackground`/`?attr/appColorAccentOrange` from each, and asserts the resolved values differ appropriately (e.g. `appColorAccentOrange` is `#D9531E`-family under Light but `#FF6B35`-family under Dark). This is the failing-test-first proof that the home is genuinely theme-adaptive — the core deliverable of this phase.
3. Switching among Light/White/Dark/Black shows the home screen correctly adapted in all four (no light-on-light or dark-on-dark).
4. Zero hardcoded hex color literals remain in `layout_home_screen.xml` (verified by grep for `#[0-9A-Fa-f]` in that file — should return nothing). `colors.xml`'s MD palette and `Accent_*` styles are out of scope and may still contain hex.
5. Parallax + entrances visible on a debug build; snapping correctly when reduced-motion is on.

---

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| `?attr/appColor*` not resolvable in drawables referenced via `android:src` (drawables can't always resolve theme attrs depending on inflation path) | Use `app:tint` / drawable's own `<solid android:color="?attr/...">` (theme-attr resolution works inside vector/shape drawables); verify with the layout test which inflates under `Theme_App_Black` |
| Tonal elevation overlay misconfig makes dark tiles invisible | Verify each dark theme renders tiles before commit; gate on the layout test |
| Parallax jank on fling | Clamped translation + disabled on `isLowRamDevice` + spring freeze |
| Toolbar collapse drift on fling | Freeze at collapse-complete, don't track every frame |
| `HomeScreenLayoutTest` breakage | All referenced IDs preserved by design; no `findViewById` calls change |
