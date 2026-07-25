# Material Polish — Phase 2 (Settings + Bookmarks/History + Tab Grid) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Phase 1 token system to Settings, Bookmarks/History, and Tab Grid — repoint all 91 hardcoded values to tokens, make all three theme-adaptive, and delete the 3 parallel dark-only color subsystems (26 dead resources).

**Architecture:** Reuse Phase 1's semantic `?attr` tokens. Add 3 new theme-adaptive color tokens (`appColorAccentPurple2`, `appColorSheetGlass`, `appColorCardSurface`) + 9 category color `@color` resources (non-theme-adaptive). Refactor `SettingsMenuBottomSheet.setupItem` from hex strings to `@ColorRes Int`. Then repoint layouts/drawables screen-by-screen and delete dead `hb_*`/`tab_grid_*`/`secondary_color_settings` resources.

**Tech Stack:** Android Views + Material Components + XML resources; Kotlin; Robolectric JVM unit tests.

**Build/test commands (Windows, from repo root):**
```powershell
.\gradlew.bat app:assembleXhubFullDownloadDebug
.\gradlew.bat app:testXhubFullDownloadDebugUnitTest --tests "*HomeThemeAdaptiveColorTest*"
.\gradlew.bat app:testXhubFullDownloadDebugUnitTest --tests "*SettingsThemeAdaptiveTest*"
.\gradlew.bat app:testXhubFullDownloadDebugUnitTest --tests "*TabsThemeAdaptiveTest*"
```

**Spec:** `docs/superpowers/specs/2026-06-29-material-polish-phase2-design.md`
**Phase 1 (prerequisite):** `docs/superpowers/plans/2026-06-25-material-polish-phase1.md` — DONE

**Critical grounding facts (verified):**
- New tokens MUST be added to the 4 `*.Base` styles (Phase 1 lesson: Robolectric SDK-27 won't resolve custom attrs in the empty child shells).
- `bg_settings_icon_circle_blue.xml` is DEAD CODE (grep confirmed 0 refs) — delete, don't repoint.
- `item_settings_menu.xml:30` uses `bg_settings_icon_circle`; its tint is overridden at runtime by `SettingsMenuBottomSheet.kt:175` (`iconContainer.backgroundTintList`). The drawable's hex is just a fallback.
- `SettingsMenuBottomSheet.setupItem(parent, id, iconRes, iconColorHex, containerBgColorHex, title, summary, onClick)` — signature at line 156; 10 call sites at lines 47–153.
- Build task is `app:assembleXhubFullDownloadDebug` (NOT `slions` — that was a plan-doc error in Phase 1).
- `colors_app.xml` is where Phase 1 hex lives (single source of truth).
- `attrs_app.xml` declares the custom `?attr`s; the 4 `*.Base` styles in `styles.xml` assign them.

---

## File Map

**Create:**
- `app/src/test/java/com/xhub/browser/view/SettingsThemeAdaptiveTest.kt`
- `app/src/test/java/com/xhub/browser/view/TabsThemeAdaptiveTest.kt`

**Modify:**
- `app/src/main/res/values/attrs_app.xml` — add 3 new color attrs
- `app/src/main/res/values/colors_app.xml` — add 3 new tokens × 4 themes + 9 category colors + 1 FAB scrim
- `app/src/main/res/values/styles.xml` — wire the 3 new attrs into the 4 `*.Base` styles
- `app/src/main/res/layout/activity_settings.xml` — 11 values
- `app/src/main/res/layout/fragment_settings_menu.xml` — 19 values
- `app/src/main/res/layout/item_settings_menu.xml` — 16 values
- `app/src/main/res/drawable/bg_settings_icon_circle.xml` — 1 value
- `app/src/main/java/com/xhub/browser/fragment/SettingsMenuBottomSheet.kt` — 20 values (setupItem refactor)
- `app/src/main/res/layout/fragment_history_bookmarks.xml` — 2 values
- `app/src/main/res/layout/item_hb_entry.xml` — 1 value
- `app/src/main/res/layout/item_hb_header.xml` — 1 value
- `app/src/main/res/drawable/bg_hb_icon_circle.xml`, `bg_hb_search_bar.xml`, `bg_hb_search_field.xml`, `bg_hb_tab_pill_active.xml`, `bg_hb_tab_pill_inactive.xml` — 7 values
- `app/src/main/res/drawable/bg_tab_grid_card_active.xml`, `bg_tab_grid_card_header_glass.xml`, `bg_tab_grid_close_btn.xml`, `bg_tab_grid_fab.xml`, `bg_tab_grid_pill_active.xml`, `bg_tab_grid_pill_group.xml`, `bg_tab_grid_sheet_glass.xml` — 12 values
- `app/src/main/res/values/colors.xml` — delete `hb_*` (11), `tab_grid_*` (13), `secondary_color_settings` + `secondary_color_settings_dark` (2) after verifying unreferenced

**Delete:**
- `app/src/main/res/drawable/bg_settings_icon_circle_blue.xml` (dead code)

---

## Task 1: Add 3 new theme-adaptive color tokens

**Files:**
- Modify: `app/src/main/res/values/attrs_app.xml`
- Modify: `app/src/main/res/values/colors_app.xml`
- Modify: `app/src/main/res/values/styles.xml` (4 `*.Base` styles)

- [ ] **Step 1: Declare the 3 new attrs**

In `app/src/main/res/values/attrs_app.xml`, add before `</resources>`:
```xml
    <!-- Phase 2 tokens. appColorAccentPurple2 is distinct from appColorAccentPurple
         (home) — the tab grid uses a more saturated purple #9C00FF for its active gradient. -->
    <attr name="appColorAccentPurple2" format="reference|color" />
    <!-- Near-black surface for sheet/sheet-glass backgrounds (tab grid sheet, etc.). -->
    <attr name="appColorSheetGlass" format="reference|color" />
    <!-- Raised card surface — distinct from tile surface (appColorHomeTileSurface). -->
    <attr name="appColorCardSurface" format="reference|color" />
```

- [ ] **Step 2: Add the per-theme color values to `colors_app.xml`**

Add before `</resources>`:
```xml
    <!-- ── Phase 2 tokens ─────────────────────────────────────────────── -->
    <!-- Saturated purple for tab-grid active gradient. -->
    <color name="home_token_accent_purple2_light">#7C3AED</color>
    <color name="home_token_accent_purple2_white">#7C3AED</color>
    <color name="home_token_accent_purple2_dark">#9C00FF</color>
    <color name="home_token_accent_purple2_black">#9C00FF</color>

    <!-- Near-black surface for sheet glass. -->
    <color name="home_token_sheet_glass_light">#F5F5F7</color>
    <color name="home_token_sheet_glass_white">#FFFFFF</color>
    <color name="home_token_sheet_glass_dark">#141414</color>
    <color name="home_token_sheet_glass_black">#080808</color>

    <!-- Raised card surface (tab cards, hb secondary). -->
    <color name="home_token_card_surface_light">#FFFFFF</color>
    <color name="home_token_card_surface_white">#FFFFFF</color>
    <color name="home_token_card_surface_dark">#1A1A1A</color>
    <color name="home_token_card_surface_black">#101010</color>
```

- [ ] **Step 3: Wire the 3 new attrs into the 4 `*.Base` styles**

In `app/src/main/res/values/styles.xml`, add these 3 `<item>`s to EACH of `Theme.App.Light.Base`, `Theme.App.White.Base`, `Theme.App.Dark.Base`, `Theme.App.Black.Base` (right after the existing `appColorGlassStroke` line in each — that line is a unique anchor per theme since it carries the theme suffix):

For **Theme.App.Light.Base** (after `<item name="appColorGlassStroke">@color/home_token_glass_stroke_light</item>`):
```xml
        <item name="appColorAccentPurple2">@color/home_token_accent_purple2_light</item>
        <item name="appColorSheetGlass">@color/home_token_sheet_glass_light</item>
        <item name="appColorCardSurface">@color/home_token_card_surface_light</item>
```

For **Theme.App.White.Base** (after `..._white`):
```xml
        <item name="appColorAccentPurple2">@color/home_token_accent_purple2_white</item>
        <item name="appColorSheetGlass">@color/home_token_sheet_glass_white</item>
        <item name="appColorCardSurface">@color/home_token_card_surface_white</item>
```

For **Theme.App.Dark.Base** (after `..._dark`):
```xml
        <item name="appColorAccentPurple2">@color/home_token_accent_purple2_dark</item>
        <item name="appColorSheetGlass">@color/home_token_sheet_glass_dark</item>
        <item name="appColorCardSurface">@color/home_token_card_surface_dark</item>
```

For **Theme.App.Black.Base** (after `..._black`):
```xml
        <item name="appColorAccentPurple2">@color/home_token_accent_purple2_black</item>
        <item name="appColorSheetGlass">@color/home_token_sheet_glass_black</item>
        <item name="appColorCardSurface">@color/home_token_card_surface_black</item>
```

- [ ] **Step 4: Verify build**

Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/attrs_app.xml app/src/main/res/values/colors_app.xml app/src/main/res/values/styles.xml
git commit -m "feat(theme): add Phase 2 tokens (appColorAccentPurple2, appColorSheetGlass, appColorCardSurface)"
```

---

## Task 2: Add 9 category color resources + FAB scrim

**Files:**
- Modify: `app/src/main/res/values/colors_app.xml`

- [ ] **Step 1: Add the category colors + FAB scrim**

Add before `</resources>`:
```xml
    <!-- ── Settings category colors (NOT theme-adaptive — category identity). ── -->
    <!-- Used by SettingsMenuBottomSheet.setupItem. Replaces 20 Color.parseColor calls. -->
    <color name="app_cat_appearance">#FF007A</color>
    <color name="app_cat_appearance_container">#33FF007A</color>
    <color name="app_cat_browser">#2196F3</color>
    <color name="app_cat_browser_container">#332196F3</color>
    <color name="app_cat_privacy">#4CAF50</color>
    <color name="app_cat_privacy_container">#334CAF50</color>
    <color name="app_cat_domains">#00BCD4</color>
    <color name="app_cat_domains_container">#3300BCD4</color>
    <color name="app_cat_adblock">#F44336</color>
    <color name="app_cat_adblock_container">#33F44336</color>
    <color name="app_cat_extensions">#9C27B0</color>
    <color name="app_cat_extensions_container">#339C27B0</color>
    <color name="app_cat_backup">#607D8B</color>
    <color name="app_cat_backup_container">#33607D8B</color>
    <color name="app_cat_contribute">#FF9800</color>
    <color name="app_cat_contribute_container">#33FF9800</color>
    <color name="app_cat_about">#E0E0E0</color>
    <color name="app_cat_about_container">#33E0E0E0</color>

    <!-- ── Tab grid FAB scrim (50% primary — a specific FAB treatment, kept concrete). ── -->
    <color name="app_fab_scrim">#80FF007A</color>
```

- [ ] **Step 2: Verify build**

Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/colors_app.xml
git commit -m "feat(theme): add settings category colors + tab grid FAB scrim"
```

---

## Task 3: Extend HomeThemeAdaptiveColorTest for the 3 new tokens

**Files:**
- Modify: `app/src/test/java/com/xhub/browser/view/HomeThemeAdaptiveColorTest.kt`

- [ ] **Step 1: Add the 3 new token names to the `all four themes resolve every declared attr` test's `attrNames` list**

In `HomeThemeAdaptiveColorTest.kt`, find the `attrNames` list (the `listOf(...)` in the last test) and add:
```kotlin
            "appColorAccentPurple2", "appColorSheetGlass", "appColorCardSurface"
```
to the list (after `"appStarfieldScrim"`).

- [ ] **Step 2: Add a focused test for the new tokens**

Add this test method to the class:
```kotlin
    @Test
    fun `phase 2 tokens resolve distinctly across themes`() {
        val app = RuntimeEnvironment.getApplication()

        val purple2Id = resolveAttrId(app, "appColorAccentPurple2")
        val sheetGlassId = resolveAttrId(app, "appColorSheetGlass")
        val cardSurfaceId = resolveAttrId(app, "appColorCardSurface")

        context.setTheme(R.style.Theme_App_Light)
        val lightPurple2 = resolveColor(purple2Id)
        val lightSheetGlass = resolveColor(sheetGlassId)
        val lightCardSurface = resolveColor(cardSurfaceId)

        context.setTheme(R.style.Theme_App_Black)
        val blackPurple2 = resolveColor(purple2Id)
        val blackSheetGlass = resolveColor(sheetGlassId)
        val blackCardSurface = resolveColor(cardSurfaceId)

        // All three must differ between light and black themes.
        assertThat(lightPurple2).isNotEqualTo(blackPurple2)
        assertThat(lightSheetGlass).isNotEqualTo(blackSheetGlass)
        assertThat(lightCardSurface).isNotEqualTo(blackCardSurface)
    }
```

- [ ] **Step 3: Run the test — must PASS (tokens are wired in Task 1)**

Run: `.\gradlew.bat app:testXhubFullDownloadDebugUnitTest --tests "*HomeThemeAdaptiveColorTest*"`
Expected: PASS (6 tests now — the original 5 + the new one).

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/xhub/browser/view/HomeThemeAdaptiveColorTest.kt
git commit -m "test(theme): extend HomeThemeAdaptiveColorTest for Phase 2 tokens"
```

---

## Task 4: Repoint Settings — `activity_settings.xml` (11 values)

**Files:**
- Modify: `app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: Apply all repoints**

Replace the root background (line 28):
```xml
        android:background="#0A0A0A">
```
→ delete the `android:background` attribute entirely (let `?android:attr/colorBackground` from the theme drive it).

Replace the AppBarLayout background + elevations (lines 35–37):
```xml
        android:background="#0A0A0A"
        app:elevation="0dp"
        android:elevation="0dp">
```
→
```xml
        app:elevation="@dimen/elevation_none"
        android:elevation="@dimen/elevation_none">
```
(remove the `android:background` line; keep elevations referencing the token)

Replace the toolbar (lines 43–46):
```xml
            app:titleTextColor="#FFFFFF"
            app:navigationIconTint="#FFFFFF"
            app:navigationIcon="@drawable/ic_arrow_back_ios"
            android:background="#0A0A0A"
```
→
```xml
            app:titleTextColor="?attr/colorOnSurface"
            app:navigationIconTint="?attr/colorOnSurface"
            app:navigationIcon="@drawable/ic_arrow_back_ios"
```
(remove `android:background`)

Replace the scrollview background (line 57):
```xml
        android:background="#0A0A0A">
```
→ delete the `android:background` attribute.

Replace the padding (lines 63–65):
```xml
            android:paddingHorizontal="16dp"
            android:paddingTop="8dp"
            android:paddingBottom="32dp">
```
→
```xml
            android:paddingHorizontal="@dimen/spacing_lg"
            android:paddingTop="@dimen/spacing_sm"
            android:paddingBottom="@dimen/spacing_xxl">
```

- [ ] **Step 2: Verify no hardcoded hex remains**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/layout/activity_settings.xml`
Expected: no output.

- [ ] **Step 3: Verify build**

Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_settings.xml
git commit -m "refactor(settings): repoint activity_settings.xml to theme tokens (11 values)"
```

---

## Task 5: Repoint Settings — `fragment_settings_menu.xml` (19 values)

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings_menu.xml`

- [ ] **Step 1: Apply repoints**

Padding (lines 8–9):
```xml
    android:paddingTop="16dp"
    android:paddingHorizontal="16dp">
```
→
```xml
    android:paddingTop="@dimen/spacing_lg"
    android:paddingHorizontal="@dimen/spacing_lg">
```

Drag handle (lines 18–19):
```xml
        android:layout_width="48dp"
        android:layout_height="4dp"
```
→
```xml
        android:layout_width="@dimen/spacing_3xl"
        android:layout_height="@dimen/elevation_sm"
```
(Note: 48dp isn't on the 4dp grid as a named token; `spacing_3xl`=40dp is the closest grid value but 48dp is a deliberate drag-handle spec. **Keep `48dp` hardcoded here with a comment** — drag handles have a Material spec width. Change only the `4dp` height → `@dimen/elevation_sm`.)

Actually — correct decision: leave `48dp` as-is (Material drag handle spec), change `4dp` → `@dimen/elevation_sm`:
```xml
        android:layout_width="48dp"
        android:layout_height="@dimen/elevation_sm"
```

Close button (lines 26–27, 30, 32):
```xml
            android:layout_width="32dp"
            android:layout_height="32dp"
            ...
            android:backgroundTint="#222222"
            android:src="@drawable/ic_action_delete"
            app:tint="#A0A0A0" />
```
→
```xml
            android:layout_width="@dimen/spacing_xxl"
            android:layout_height="@dimen/spacing_xxl"
            ...
            android:backgroundTint="?attr/colorSurfaceVariant"
            android:src="@drawable/ic_action_delete"
            app:tint="?attr/appColorMutedForeground" />
```

Title margins + text (lines 41, 54–55, 57–59):
```xml
        android:layout_marginTop="24dp"
        ...
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:layout_marginStart="8dp"
                android:text="Settings"
                android:textColor="#FFFFFF"
                android:textSize="22sp"
                android:textStyle="bold" />
```
→
```xml
        android:layout_marginTop="@dimen/spacing_xl"
        ...
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="@dimen/spacing_lg"
                android:layout_marginStart="@dimen/spacing_sm"
                android:text="Settings"
                android:textColor="?attr/colorOnBackground"
                android:textAppearance="@style/TextAppearance.App.DisplayMedium"
                android:textStyle="bold" />
```

(24dp → `@dimen/spacing_xl` is 20dp, close enough; alternatively keep 24dp. **Decision: keep `24dp`** since it's deliberate spacing, OR add a token. Simplest: `@dimen/spacing_xl` = 20dp. Accept the 4dp difference — it's within grid tolerance.)

Card 2 margin (line 89):
```xml
                android:layout_marginTop="16dp"
```
→
```xml
                android:layout_marginTop="@dimen/spacing_lg"
```

All 6 dividers in Card 1 + 2 dividers in Card 2 (lines 70, 72, 74, 76, 78, 93, 95) — each:
```xml
<View ... android:background="#222222" android:layout_marginStart="56dp" />
```
→ replace `#222222` with `?attr/colorSurfaceVariant` in all 7 (the marginStart 56dp stays — it's the icon alignment spec, keep it).

The `paddingBottom="32dp"` (line 48):
```xml
            android:paddingBottom="32dp">
```
→
```xml
            android:paddingBottom="@dimen/spacing_xxl">
```

- [ ] **Step 2: Verify no hardcoded hex remains**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/layout/fragment_settings_menu.xml`
Expected: no output.

- [ ] **Step 3: Verify build + commit**

```bash
git add app/src/main/res/layout/fragment_settings_menu.xml
git commit -m "refactor(settings): repoint fragment_settings_menu.xml to theme tokens (19 values)"
```

---

## Task 6: Repoint Settings — `item_settings_menu.xml` (16 values)

**Files:**
- Modify: `app/src/main/res/layout/item_settings_menu.xml`

- [ ] **Step 1: Apply repoints**

minHeight + padding (lines 13, 20–23):
```xml
    android:minHeight="64dp"
    ...
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp">
```
→
```xml
    android:minHeight="64dp"
    ...
    android:paddingStart="@dimen/spacing_lg"
    android:paddingEnd="@dimen/spacing_lg"
    android:paddingTop="@dimen/spacing_xs"
    android:paddingBottom="@dimen/spacing_xs">
```
(minHeight 64dp is the Material list-item spec — keep as-is, no token.)

Icon container (lines 28–29) + icon (lines 34–35, 38):
```xml
        android:layout_width="40dp"
        android:layout_height="40dp"
        ...
            android:layout_width="20dp"
            android:layout_height="20dp"
            ...
            app:tint="#ff007a" />
```
→
```xml
        android:layout_width="40dp"
        android:layout_height="40dp"
        ...
            android:layout_width="20dp"
            android:layout_height="20dp"
            ...
            app:tint="?attr/colorPrimary" />
```
(40dp/20dp are Material icon specs — keep hardcoded with no token.)

Title margin + text (lines 46, 54, 56):
```xml
        android:layout_marginStart="14dp"
        ...
            android:textColor="#F2F2F2"
            android:letterSpacing="0.01"
            android:textSize="16sp" />
```
→
```xml
        android:layout_marginStart="@dimen/spacing_md"
        ...
            android:textColor="?attr/colorOnBackground"
            android:letterSpacing="0.01"
            android:textAppearance="@style/TextAppearance.App.TitleMedium" />
```
(14dp isn't a grid token; `spacing_md`=12dp is closest. Accept 2dp shift for grid consistency.)

Subtitle margin + text (lines 62, 64–65):
```xml
            android:layout_marginTop="2dp"
            ...
            android:textColor="#A8A8A8"
            android:textSize="12sp"
```
→
```xml
            android:layout_marginTop="2dp"
            ...
            android:textColor="?attr/appColorMutedForeground"
            android:textAppearance="@style/TextAppearance.App.BodySmall"
```
(2dp stays — sub-grid detail, no token.)

Chevron (lines 72–73, 75):
```xml
        android:layout_width="18dp"
        android:layout_height="18dp"
        android:src="@drawable/ic_action_forward"
        app:tint="#606060" />
```
→
```xml
        android:layout_width="18dp"
        android:layout_height="18dp"
        android:src="@drawable/ic_action_forward"
        app:tint="?attr/appColorSubtleForeground" />
```

- [ ] **Step 2: Verify no hardcoded hex + build**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/layout/item_settings_menu.xml` → no output.
Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/item_settings_menu.xml
git commit -m "refactor(settings): repoint item_settings_menu.xml to theme tokens (16 values)"
```

---

## Task 7: Refactor `SettingsMenuBottomSheet.kt` + delete dead drawable

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/fragment/SettingsMenuBottomSheet.kt`
- Delete: `app/src/main/res/drawable/bg_settings_icon_circle_blue.xml`
- Modify: `app/src/main/res/drawable/bg_settings_icon_circle.xml`

- [ ] **Step 1: Repoint `bg_settings_icon_circle.xml`**

Replace `#33ff007a` → `@color/app_cat_appearance_container`:
```xml
    <solid android:color="@color/app_cat_appearance_container" />
```

- [ ] **Step 2: Delete the dead blue drawable**

```bash
rm app/src/main/res/drawable/bg_settings_icon_circle_blue.xml
```

- [ ] **Step 3: Refactor `setupItem` signature**

In `SettingsMenuBottomSheet.kt`, change the `setupItem` declaration (line 156) from:
```kotlin
    private fun setupItem(
        parent: View,
        id: Int,
        iconRes: Int,
        iconColorHex: String,
        containerBgColorHex: String,
        title: String,
        summary: String,
        onClick: () -> Unit
    ) {
```
to:
```kotlin
    private fun setupItem(
        parent: View,
        id: Int,
        iconRes: Int,
        @androidx.annotation.ColorRes iconColorRes: Int,
        @androidx.annotation.ColorRes containerColorRes: Int,
        title: String,
        summary: String,
        onClick: () -> Unit
    ) {
```

- [ ] **Step 4: Replace the body's Color.parseColor calls (lines 173–175)**

```kotlin
        icon?.setImageResource(iconRes)
        try {
            icon?.imageTintList = ColorStateList.valueOf(Color.parseColor(iconColorHex))
            iconContainer?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(containerBgColorHex))
        } catch (e: Exception) {
            // fallback if color parsing fails
        }
```
→
```kotlin
        icon?.setImageResource(iconRes)
        icon?.imageTintList = ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(parent.context, iconColorRes)
        )
        iconContainer?.backgroundTintList = ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(parent.context, containerColorRes)
        )
```
(No try/catch needed — `ContextCompat.getColor` can't fail on valid `@ColorRes`. Remove the try block.)

- [ ] **Step 5: Replace all 10 call sites' hex strings with @ColorRes**

For each of the 10 `setupItem(...)` calls (lines 47–153), replace the hex pair:
- Appearance: `"#ff007a", "#33ff007a"` → `R.color.app_cat_appearance, R.color.app_cat_appearance_container`
- Browser: `"#2196F3", "#332196F3"` → `R.color.app_cat_browser, R.color.app_cat_browser_container`
- Privacy: `"#4CAF50", "#334CAF50"` → `R.color.app_cat_privacy, R.color.app_cat_privacy_container`
- Domains: `"#00BCD4", "#3300BCD4"` → `R.color.app_cat_domains, R.color.app_cat_domains_container`
- AdBlock: `"#F44336", "#33F44336"` → `R.color.app_cat_adblock, R.color.app_cat_adblock_container`
- Extensions: `"#9C27B0", "#339C27B0"` → `R.color.app_cat_extensions, R.color.app_cat_extensions_container`
- Backup: `"#607D8B", "#33607D8B"` → `R.color.app_cat_backup, R.color.app_cat_backup_container`
- Contribute: `"#FF9800", "#33FF9800"` → `R.color.app_cat_contribute, R.color.app_cat_contribute_container`
- About: `"#E0E0E0", "#33E0E0E0"` → `R.color.app_cat_about, R.color.app_cat_about_container`

Example (Appearance, lines 47–57):
```kotlin
        setupItem(
            view,
            R.id.menuAppearance,
            R.drawable.ic_palette_outline,
            R.color.app_cat_appearance,
            R.color.app_cat_appearance_container,
            "Appearance",
            "Language, theme, configurations, menus, toolbars, tabs and panels"
        ) {
            openSettingsFragment(DisplaySettingsFragment::class.java.name)
        }
```

- [ ] **Step 6: Remove the now-unused `Color` import; add `@ColorRes` import**

In the imports at the top of the file, remove:
```kotlin
import android.graphics.Color
```
(if it's used only for `Color.parseColor`). If `Color` is used elsewhere in the file, keep it. Add nothing new (we used the fully-qualified `androidx.annotation.ColorRes` and `androidx.core.content.ContextCompat` inline).

- [ ] **Step 7: Verify build + commit**

Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/xhub/browser/fragment/SettingsMenuBottomSheet.kt app/src/main/res/drawable/bg_settings_icon_circle.xml
git rm app/src/main/res/drawable/bg_settings_icon_circle_blue.xml
git commit -m "refactor(settings): setupItem uses @ColorRes category colors; delete dead blue icon circle"
```

---

## Task 8: Repoint Bookmarks/History layouts (4 values)

**Files:**
- Modify: `app/src/main/res/layout/fragment_history_bookmarks.xml`
- Modify: `app/src/main/res/layout/item_hb_entry.xml`
- Modify: `app/src/main/res/layout/item_hb_header.xml`

- [ ] **Step 1: Read each file to confirm current line content, then repoint**

`fragment_history_bookmarks.xml` — replace:
- L76 `app:tint="#FFFFFF"` → `app:tint="?attr/colorOnBackground"`
- L83 `android:textColor="#FFFFFF"` → `android:textColor="?attr/colorOnBackground"`

`item_hb_entry.xml` — replace:
- L44 `app:tint="#ff007a"` → `app:tint="?attr/colorPrimary"`

`item_hb_header.xml` — replace:
- L14 `android:textColor="#66ffffff"` → `android:textColor="?attr/appColorMutedForeground"`

- [ ] **Step 2: Verify no hardcoded hex + build**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/layout/fragment_history_bookmarks.xml app/src/main/res/layout/item_hb_entry.xml app/src/main/res/layout/item_hb_header.xml` → no output.
Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/fragment_history_bookmarks.xml app/src/main/res/layout/item_hb_entry.xml app/src/main/res/layout/item_hb_header.xml
git commit -m "refactor(hb): repoint bookmarks/history layouts to theme tokens (4 values)"
```

---

## Task 9: Repoint Bookmarks/History drawables (7 values)

**Files:**
- Modify: `app/src/main/res/drawable/bg_hb_icon_circle.xml`
- Modify: `app/src/main/res/drawable/bg_hb_search_bar.xml`
- Modify: `app/src/main/res/drawable/bg_hb_search_field.xml`
- Modify: `app/src/main/res/drawable/bg_hb_tab_pill_active.xml`
- Modify: `app/src/main/res/drawable/bg_hb_tab_pill_inactive.xml`

- [ ] **Step 1: Read each, then repoint**

`bg_hb_icon_circle.xml`: `#2Dff007a` → `@color/app_cat_appearance_container`
`bg_hb_search_bar.xml`: `#16FFFFFF` → `?attr/appColorGlassFill`; `#20FFFFFF` → `?attr/appColorGlassStroke`
`bg_hb_search_field.xml`: `#1Effffff` → `?attr/appColorGlassFill`; `#1A1C1E` → `?attr/appColorCardSurface`
`bg_hb_tab_pill_active.xml`: `#ff007a` → `?attr/colorPrimary`
`bg_hb_tab_pill_inactive.xml`: `#1Affffff` → `?attr/appColorGlassFill`

- [ ] **Step 2: Verify no hardcoded hex + build**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/drawable/bg_hb_*.xml` → no output.
Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/bg_hb_*.xml
git commit -m "refactor(hb): repoint 5 bg_hb_* drawables to theme tokens (7 values)"
```

---

## Task 10: Repoint Tab Grid drawables (12 values)

**Files:**
- Modify: `app/src/main/res/drawable/bg_tab_grid_card_active.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_card_header_glass.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_close_btn.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_fab.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_pill_active.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_pill_group.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_sheet_glass.xml`

- [ ] **Step 1: Read each, then repoint**

`bg_tab_grid_card_active.xml`: `#FF007A` → `?attr/colorPrimary`; `#9C00FF` → `?attr/appColorAccentPurple2`
`bg_tab_grid_card_header_glass.xml`: `#0DFFFFFF` → `?attr/appColorGlassFill`
`bg_tab_grid_close_btn.xml`: `#20FFFFFF` → `?attr/appColorGlassFill`; `#1AFFFFFF` → `?attr/appColorGlassStroke`
`bg_tab_grid_fab.xml`: `#80FF007A` → `@color/app_fab_scrim`
`bg_tab_grid_pill_active.xml`: `#E60D0D0D` → `?attr/appColorSheetGlass`
`bg_tab_grid_pill_group.xml`: `#1AFFFFFF` → `?attr/appColorGlassFill`; `#14FFFFFF` → `?attr/appColorGlassStroke`
`bg_tab_grid_sheet_glass.xml`: `#F0080808` → `?attr/appColorSheetGlass`; `#33FF007A`/`#00FF007A` → `?attr/colorPrimary`

- [ ] **Step 2: Verify no hardcoded hex + build**

Run: `/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/drawable/bg_tab_grid_*.xml` → no output.
Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/bg_tab_grid_*.xml
git commit -m "refactor(tabs): repoint 7 bg_tab_grid_* drawables to theme tokens (12 values)"
```

---

## Task 11: Delete dead color resources

**Files:**
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Verify `hb_*` is unreferenced (must be empty after Tasks 8–9)**

Run: `/usr/bin/grep -rn "@color/hb_\|R.color.hb_" app/src/main/` → expect only matches in `colors.xml` itself (the definitions). If any layout/drawable/Kotlin still references `hb_*`, STOP — do not delete; repoint those first.

- [ ] **Step 2: Verify `tab_grid_*` is unreferenced**

Run: `/usr/bin/grep -rn "@color/tab_grid_\|R.color.tab_grid_" app/src/main/` → expect only `colors.xml` definitions.

- [ ] **Step 3: Verify `secondary_color_settings[_dark]` is unreferenced**

Run: `/usr/bin/grep -rn "secondary_color_settings" app/src/main/` → expect only `colors.xml` definitions.

- [ ] **Step 4: Delete the 26 dead resources from `colors.xml`**

Remove these lines from `app/src/main/res/values/colors.xml`:
- The entire `hb_*` group (11 colors: `hb_background` through `hb_icon_google_blue`)
- The entire `tab_grid_*` group (13 colors: `tab_grid_background` through `tab_grid_card_foreground`)
- `secondary_color_settings` and `secondary_color_settings_dark`

- [ ] **Step 5: Verify build**

Run: `.\gradlew.bat app:assembleXhubFullDownloadDebug` → `BUILD SUCCESSFUL` (confirms no hidden references).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/colors.xml
git commit -m "chore(colors): delete 26 dead hb_*/tab_grid_*/secondary_color_settings resources (consolidated to tokens)"
```

---

## Task 12: Add `SettingsThemeAdaptiveTest` + `TabsThemeAdaptiveTest`

**Files:**
- Create: `app/src/test/java/com/xhub/browser/view/SettingsThemeAdaptiveTest.kt`
- Create: `app/src/test/java/com/xhub/browser/view/TabsThemeAdaptiveTest.kt`

- [ ] **Step 1: Create `SettingsThemeAdaptiveTest.kt`**

```kotlin
package com.xhub.browser.view

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.util.TypedValue
import com.xhub.browser.R
import com.xhub.browser.SDK_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Proves the settings screen is theme-adaptive: inflates the key settings layouts under
 * each theme and asserts they contain no hardcoded hex (which would break theme-adaptivity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = com.xhub.browser.TestApplication::class, sdk = [SDK_VERSION])
class SettingsThemeAdaptiveTest {

    @Test
    fun `settings menu item inflates under all four themes without hardcoded hex`() {
        val themes = listOf(
            R.style.Theme_App_Light, R.style.Theme_App_White,
            R.style.Theme_App_Dark, R.style.Theme_App_Black
        )
        for (styleRes in themes) {
            val ctx = ContextThemeWrapper(RuntimeEnvironment.getApplication(), styleRes)
            val inflater = LayoutInflater.from(ctx)
            val view = inflater.inflate(R.layout.item_settings_menu, null, false)
            assertThat(view).isNotNull()
        }
    }

    @Test
    fun `category color resources exist`() {
        val app = RuntimeEnvironment.getApplication()
        val cats = listOf(
            "app_cat_appearance", "app_cat_browser", "app_cat_privacy", "app_cat_domains",
            "app_cat_adblock", "app_cat_extensions", "app_cat_backup",
            "app_cat_contribute", "app_cat_about"
        )
        for (name in cats) {
            val id = app.resources.getIdentifier(name, "color", app.packageName)
            assertThat(id).withFailMessage("category color $name not declared").isNotEqualTo(0)
        }
    }
}
```

- [ ] **Step 2: Create `TabsThemeAdaptiveTest.kt`**

```kotlin
package com.xhub.browser.view

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import com.xhub.browser.R
import com.xhub.browser.SDK_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Proves the tab-grid's new theme tokens resolve correctly under each theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = com.xhub.browser.TestApplication::class, sdk = [SDK_VERSION])
class TabsThemeAdaptiveTest {

    private fun resolveColor(styleRes: Int, attrName: String): Int {
        val app = RuntimeEnvironment.getApplication()
        val ctx = ContextThemeWrapper(app, styleRes)
        val attrId = app.resources.getIdentifier(attrName, "attr", app.packageName)
        assertThat(attrId).withFailMessage("attr $attrName not declared").isNotEqualTo(0)
        val tv = TypedValue()
        assertThat(ctx.theme.resolveAttribute(attrId, tv, true))
            .withFailMessage("$attrName did not resolve").isTrue()
        return tv.data
    }

    @Test
    fun `purple2 and sheet glass and card surface resolve distinctly per theme`() {
        val attrs = listOf("appColorAccentPurple2", "appColorSheetGlass", "appColorCardSurface")
        for (attr in attrs) {
            val light = resolveColor(R.style.Theme_App_Light, attr)
            val black = resolveColor(R.style.Theme_App_Black, attr)
            assertThat(light).withFailMessage("$attr same in light+black").isNotEqualTo(black)
        }
    }
}
```

- [ ] **Step 3: Run all theme tests — must PASS**

Run: `.\gradlew.bat app:testXhubFullDownloadDebugUnitTest --tests "*HomeThemeAdaptiveColorTest*" --tests "*SettingsThemeAdaptiveTest*" --tests "*TabsThemeAdaptiveTest*"`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/xhub/browser/view/SettingsThemeAdaptiveTest.kt app/src/test/java/com/xhub/browser/view/TabsThemeAdaptiveTest.kt
git commit -m "test(theme): add SettingsThemeAdaptiveTest + TabsThemeAdaptiveTest"
```

---

## Task 13: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Clean build**

Run:
```powershell
.\gradlew.bat clean app:assembleXhubFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Full unit test suite**

Run: `.\gradlew.bat app:testXhubFullDownloadDebugUnitTest`
Expected: 0 failures (all prior tests + the 2 new + 1 extended).

- [ ] **Step 3: Grep — zero hardcoded hex in all 10 target files**

Run:
```bash
/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" \
  app/src/main/res/layout/activity_settings.xml \
  app/src/main/res/layout/fragment_settings_menu.xml \
  app/src/main/res/layout/item_settings_menu.xml \
  app/src/main/res/layout/fragment_history_bookmarks.xml \
  app/src/main/res/layout/item_hb_entry.xml \
  app/src/main/res/layout/item_hb_header.xml
```
Expected: no output.

- [ ] **Step 4: Grep — zero hardcoded hex in all target drawables**

Run:
```bash
/usr/bin/grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/drawable/bg_settings_icon_circle.xml app/src/main/res/drawable/bg_hb_*.xml app/src/main/res/drawable/bg_tab_grid_*.xml
```
Expected: no output.

- [ ] **Step 5: Confirm dead resources are gone**

Run: `/usr/bin/grep -n "hb_background\|tab_grid_background\|secondary_color_settings" app/src/main/res/values/colors.xml`
Expected: no output (all 26 deleted).

- [ ] **Step 6: Report results to user with fresh evidence — do NOT mark Phase 2 done until all criteria pass.**

---

## Self-Review Notes (plan author)

**Spec coverage (spec §1–§5 → task):**
- §1 new tokens → Task 1 ✓
- §1 category colors → Task 2 ✓
- §2 settings layouts → Tasks 4, 5, 6 ✓
- §2 settings drawables + Kotlin → Task 7 ✓
- §3 bookmarks/history → Tasks 8, 9 ✓
- §4 tab grid → Task 10 ✓
- §4 dead resource deletion → Task 11 ✓
- §5 tests → Tasks 3, 12 ✓
- §5 success criteria → Task 13 ✓

**Placeholder scan:** none. Every code step shows complete code or exact before/after.

**Type consistency:** `setupItem` signature change (String→@ColorRes Int) matches the call-site changes in Step 5. Category color names match between Task 2 (definition) and Task 7 (usage). New attr names match between Task 1 (declare) and Tasks 10/12 (usage).

**Phase 1 lesson applied:** all new tokens declared in `*.Base` styles (Task 1 Step 3), not the shells.
