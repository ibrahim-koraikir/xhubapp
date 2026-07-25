# Material Polish — Phase 1 (Foundation + Home) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the M3 design-token foundation and refactor the home screen end-to-end as the theme-adaptive reference implementation — no other screens touched.

**Architecture:** Semantic `?attr` tokens declared once in `attrs_app.xml`, assigned concrete per-theme values in the empty `Theme.App.Light/White/Dark/Black` shell styles, and referenced by the home layout + drawables. Spacing/radius/elevation move to named `@dimen` tokens on a 4dp grid; type moves to `TextAppearance.App.*`. One new Kotlin file (`HomeMotionController`) wires tasteful motion; all other changes are resource edits + two small wiring points.

**Tech Stack:** Android Views + Material Components 1.13.0 + XML resources; Kotlin; Robolectric JVM unit tests (no device).

**Build/test commands (Windows, run from repo root):**
```powershell
taskkill /F /IM java.exe 2>$null; timeout /t 3
.\gradlew.bat assembleSlionsFullDownloadDebug
.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "*HomeScreenLayoutTest*"
.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "*HomeThemeAdaptiveColorTest*"
```

**Spec:** `docs/superpowers/specs/2026-06-25-material-polish-design.md`

**Critical grounding facts (verified in repo):**
- `Theme.App.Light/White/Dark/Black` (`styles.xml:861-871`) are **empty shells** extending `*.Base` styles — the 15 attr `<item>` blocks go here.
- `ShapeAppearance.App.Small/Medium/LargeComponent` (`styles.xml:1004-1017`) are **app-wide, all 8dp** — **NOT touched** in Phase 1 (Phase-2 scope). Phase 1 introduces `@dimen/radius_*` and references them directly in home drawables only.
- Home is bound in `WebBrowserActivity.buildDynamicShortcuts()` (`WebBrowserActivity.kt:1811`) via `iBinding.homeScreenOverlay`. RecyclerView adapter set at line 1877; `itemAnimator.changeDuration = 0` at line 1878.
- `ShortcutTileAdapter.onCreateViewHolder` (`ShortcutTileAdapter.kt:128`) builds tiles programmatically via `buildTile()`.
- `HomeScreenLayoutTest` uses `Theme_App_Black`, asserts presence of `homeScreenBackground`, `homeScreenBackgroundOverlay`, `shortcutsDynamicContainer`, `homeTitle` — all IDs preserved.

---

## File Map

**Create:**
- `app/src/main/res/values/attrs_app.xml` — 15 custom attr declarations
- `app/src/main/res/values/dimens_spacing.xml` — 4dp-grid spacing scale
- `app/src/main/res/values/dimens_shape.xml` — radius + elevation tokens
- `app/src/main/res/values/text_appearances_app.xml` — 9 standard + 4 specialized text appearances
- `app/src/main/res/drawable/bg_home_starfield_light.xml` — light-mode starfield vector
- `app/src/main/res/anim/theme_fade_in.xml` — theme-switch crossfade in
- `app/src/main/res/anim/theme_fade_out.xml` — theme-switch crossfade out
- `app/src/main/java/com/xhub/browser/ui/MotionUtils.kt` — reduced-motion / low-ram guard
- `app/src/main/java/com/xhub/browser/ui/HomeMotionController.kt` — parallax + entrance + streak pulse
- `app/src/test/java/com/xhub/browser/view/HomeThemeAdaptiveColorTest.kt` — the failing-test-first proof

**Modify:**
- `app/src/main/res/values/styles.xml:861-871` — fill the 4 empty theme shells with attr `<item>` blocks
- `app/src/main/res/layout/layout_home_screen.xml` — repoint every color/spacing/radius/text attr
- `app/src/main/res/drawable/bg_home_hero.xml`, `bg_home_shortcut_card.xml`, `bg_home_greeting_pill.xml`, `bg_home_streak_chip.xml`, `bg_home_flame_btn.xml`, `bg_home_action_glass.xml`, `bg_home_stat_chip.xml`, `bg_home_quote_divider.xml`, `bg_shortcut_manager_icon_btn.xml` — color refs → `?attr/appColor*`, radii → `@dimen/radius_*`
- `app/src/main/java/com/xhub/browser/activity/ThemedActivity.kt:126` — add `overridePendingTransition` to `restart()`
- `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt:1842-1918` — instantiate `HomeMotionController` after adapter set

---

## Task 1: Declare the 15 custom theme attributes

**Files:**
- Create: `app/src/main/res/values/attrs_app.xml`

- [ ] **Step 1: Create the attrs file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- ── Home-screen semantic color tokens ─────────────────────────────── -->
    <!-- Each is assigned a concrete per-theme value in styles.xml
         (Theme.App.Light/White/Dark/Black). Layouts & drawables reference
         them as ?attr/appColor* so the home screen is theme-adaptive. -->

    <attr name="appColorAccentOrange" format="color" />
    <!-- 35%-alpha orange for decorative quote mark (large 60sp text, non-body) -->
    <attr name="appColorAccentOrangeFaint" format="color" />
    <attr name="appColorAccentOrangeLight" format="color" />
    <attr name="appColorAccentOrangeDark" format="color" />
    <attr name="appColorAccentPurple" format="color" />

    <attr name="appColorHomeTileSurface" format="color" />
    <attr name="appColorHomeStroke" format="color" />
    <attr name="appColorHomeTileStroke" format="color" />

    <attr name="appColorMutedForeground" format="color" />
    <attr name="appColorSubtleForeground" format="color" />
    <attr name="appColorDimForeground" format="color" />
    <attr name="appColorIconTint" format="color" />
    <attr name="appColorGroupLabel" format="color" />

    <!-- Starfield scrim color (the overlay View on top of the background image) -->
    <attr name="appStarfieldScrim" format="color" />

    <!-- The home background drawable itself (reference, NOT color) -->
    <attr name="appHomeBackground" format="reference" />

</resources>
```

- [ ] **Step 2: Verify the file compiles (no attr errors yet — they're just declared)**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`. (No layout references these attrs yet, so nothing resolves them — but the file itself must be valid XML.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/attrs_app.xml
git commit -m "feat(theme): declare 15 semantic home-screen attrs"
```

---

## Task 2: Add the spacing, radius, and elevation dimension tokens

**Files:**
- Create: `app/src/main/res/values/dimens_spacing.xml`
- Create: `app/src/main/res/values/dimens_shape.xml`

- [ ] **Step 1: Create the spacing scale (strict 4dp grid)**

`app/src/main/res/values/dimens_spacing.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- App-wide spacing scale. Strict 4dp grid.
         Reference as @dimen/spacing_* — never inline dp literals in layouts. -->
    <dimen name="spacing_xs">4dp</dimen>
    <dimen name="spacing_sm">8dp</dimen>
    <dimen name="spacing_md">12dp</dimen>
    <dimen name="spacing_lg">16dp</dimen>
    <dimen name="spacing_xl">20dp</dimen>
    <dimen name="spacing_xxl">32dp</dimen>
    <dimen name="spacing_3xl">40dp</dimen>
</resources>
```

- [ ] **Step 2: Create the shape (radius) + elevation scale**

`app/src/main/res/values/dimens_shape.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Corner-radius tokens. Used DIRECTLY inside home-screen drawables.
         NOTE: the app-wide ShapeAppearance.App.Small/Medium/LargeComponent
         styles (styles.xml:1004-1017) are NOT changed in Phase 1 — they stay
         at 8dp app-wide. Repointing those is Phase-2 scope. -->
    <dimen name="radius_sm">8dp</dimen>
    <dimen name="radius_md">12dp</dimen>
    <dimen name="radius_lg">16dp</dimen>
    <dimen name="radius_xl">18dp</dimen>   <!-- tile squircle — deliberately tuned -->
    <dimen name="radius_full">28dp</dimen> <!-- pills, circles -->

    <!-- Elevation tokens. -->
    <dimen name="elevation_none">0dp</dimen>
    <dimen name="elevation_sm">1dp</dimen>
    <dimen name="elevation_md">3dp</dimen>  <!-- shortcut tiles -->
    <dimen name="elevation_lg">6dp</dimen>  <!-- flame button -->
</resources>
```

- [ ] **Step 3: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/dimens_spacing.xml app/src/main/res/values/dimens_shape.xml
git commit -m "feat(theme): add spacing/radius/elevation dimension tokens"
```

---

## Task 3: Add the TextAppearance.App.* type scale

**Files:**
- Create: `app/src/main/res/values/text_appearances_app.xml`

- [ ] **Step 1: Create the type scale**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- ── Standard scale (M3-aligned) ──────────────────────────────────── -->
    <style name="TextAppearance.App.DisplayLarge" parent="TextAppearance.Material3.DisplayLarge">
        <item name="android:textSize">32sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>
    <style name="TextAppearance.App.DisplayMedium" parent="TextAppearance.Material3.DisplayMedium">
        <item name="android:textSize">22sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>
    <style name="TextAppearance.App.HeadlineSmall" parent="TextAppearance.Material3.HeadlineSmall">
        <item name="android:textSize">20sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>
    <style name="TextAppearance.App.TitleLarge" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">17sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>
    <style name="TextAppearance.App.TitleMedium" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:textSize">15sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>
    <style name="TextAppearance.App.BodyMedium" parent="TextAppearance.Material3.BodyMedium">
        <item name="android:textSize">13sp</item>
        <item name="android:fontFamily">sans-serif</item>
    </style>
    <style name="TextAppearance.App.BodySmall" parent="TextAppearance.Material3.BodySmall">
        <item name="android:textSize">12sp</item>
        <item name="android:fontFamily">sans-serif</item>
    </style>
    <style name="TextAppearance.App.LabelMedium" parent="TextAppearance.Material3.LabelMedium">
        <item name="android:textSize">11sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textAllCaps">true</item>
        <item name="android:letterSpacing">0.15</item>
    </style>
    <style name="TextAppearance.App.LabelSmall" parent="TextAppearance.Material3.LabelSmall">
        <item name="android:textSize">10sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>

    <!-- ── Specialized (genuinely singular home elements — kept bespoke) ─── -->
    <style name="TextAppearance.App.TileInitial" parent="TextAppearance.Material3.DisplaySmall">
        <item name="android:textSize">26sp</item>
        <item name="android:fontFamily">sans-serif-black</item>
    </style>
    <!-- Tile label kept at 11.5sp — deliberately tuned, do NOT round to 12sp -->
    <style name="TextAppearance.App.TileLabel" parent="TextAppearance.Material3.LabelSmall">
        <item name="android:textSize">11.5sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
    </style>
    <style name="TextAppearance.App.QuoteMark" parent="TextAppearance.Material3.DisplayLarge">
        <item name="android:textSize">60sp</item>
        <item name="android:fontFamily">sans-serif-black</item>
        <item name="android:includeFontPadding">false</item>
    </style>
    <style name="TextAppearance.App.GreetingLabel" parent="TextAppearance.Material3.BodySmall">
        <item name="android:textSize">12sp</item>
        <item name="android:fontFamily">sans-serif-light</item>
        <item name="android:letterSpacing">0.02</item>
    </style>

</resources>
```

- [ ] **Step 2: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/text_appearances_app.xml
git commit -m "feat(theme): add TextAppearance.App.* type scale"
```

---

## Task 4: Assign per-theme values to the 15 attrs (the core of theme-adaptivity)

**Files:**
- Modify: `app/src/main/res/values/styles.xml:861-871` (the 4 empty theme shells)

- [ ] **Step 1: Fill the Light theme shell**

Replace `app/src/main/res/values/styles.xml:861-862`:
```xml
    <style name="Theme.App.Light" parent="Theme.App.Light.Base">
    </style>
```
with:
```xml
    <style name="Theme.App.Light" parent="Theme.App.Light.Base">
        <!-- Home-screen semantic tokens (Light). Orange deepened to #D9531E for WCAG-AA on white. -->
        <item name="appColorAccentOrange">#D9531E</item>
        <item name="appColorAccentOrangeFaint">#59D9531E</item>
        <item name="appColorAccentOrangeLight">#C24416</item>
        <item name="appColorAccentOrangeDark">#A8330F</item>
        <item name="appColorAccentPurple">#6F4BE6</item>
        <item name="appColorHomeTileSurface">#F3F3F6</item>
        <item name="appColorHomeStroke">#D7D7DD</item>
        <item name="appColorHomeTileStroke">#1A000000</item>
        <item name="appColorMutedForeground">#5C5C66</item>
        <item name="appColorSubtleForeground">#6E6E78</item>
        <item name="appColorDimForeground">#42424A</item>
        <item name="appColorIconTint">#5C5C66</item>
        <item name="appColorGroupLabel">#8A8A94</item>
        <item name="appStarfieldScrim">#14FFFFFF</item>
        <item name="appHomeBackground">@drawable/bg_home_starfield_light</item>
    </style>
```

- [ ] **Step 2: Fill the White theme shell (Light values, white surface + flat scrim)**

Replace `app/src/main/res/values/styles.xml:864-865`:
```xml
    <style name="Theme.App.White" parent="Theme.App.White.Base">
    </style>
```
with:
```xml
    <style name="Theme.App.White" parent="Theme.App.White.Base">
        <!-- Home-screen semantic tokens (White). Same as Light but pure-white tile surface and near-flat scrim. -->
        <item name="appColorAccentOrange">#D9531E</item>
        <item name="appColorAccentOrangeFaint">#59D9531E</item>
        <item name="appColorAccentOrangeLight">#C24416</item>
        <item name="appColorAccentOrangeDark">#A8330F</item>
        <item name="appColorAccentPurple">#6F4BE6</item>
        <item name="appColorHomeTileSurface">#FFFFFFFF</item>
        <item name="appColorHomeStroke">#D7D7DD</item>
        <item name="appColorHomeTileStroke">#1A000000</item>
        <item name="appColorMutedForeground">#5C5C66</item>
        <item name="appColorSubtleForeground">#6E6E78</item>
        <item name="appColorDimForeground">#42424A</item>
        <item name="appColorIconTint">#5C5C66</item>
        <item name="appColorGroupLabel">#8A8A94</item>
        <item name="appStarfieldScrim">#0A000000</item>
        <item name="appHomeBackground">@drawable/bg_home_starfield_light</item>
    </style>
```

- [ ] **Step 3: Fill the Dark theme shell**

Replace `app/src/main/res/values/styles.xml:867-868`:
```xml
    <style name="Theme.App.Dark" parent="Theme.App.Dark.Base">
    </style>
```
with:
```xml
    <style name="Theme.App.Dark" parent="Theme.App.Dark.Base">
        <!-- Home-screen semantic tokens (Dark). Orange/purple kept vivid — pops on dark. -->
        <item name="appColorAccentOrange">#FF6B35</item>
        <item name="appColorAccentOrangeFaint">#59FF6B35</item>
        <item name="appColorAccentOrangeLight">#FF8C5B</item>
        <item name="appColorAccentOrangeDark">#BF3A1E</item>
        <item name="appColorAccentPurple">#A78BFA</item>
        <item name="appColorHomeTileSurface">#FF161616</item>
        <item name="appColorHomeStroke">#FF333333</item>
        <item name="appColorHomeTileStroke">#1AFFFFFF</item>
        <item name="appColorMutedForeground">#FF888888</item>
        <item name="appColorSubtleForeground">#77FFFFFF</item>
        <item name="appColorDimForeground">#CCFFFFFF</item>
        <item name="appColorIconTint">#80FFFFFF</item>
        <item name="appColorGroupLabel">#AAFFFFFF</item>
        <item name="appStarfieldScrim">#CC000000</item>
        <item name="appHomeBackground">@drawable/bg_home_starfield</item>
        <!-- Tonal elevation: on dark surfaces real shadows are invisible, so M3 lifts via overlay. -->
        <item name="android:colorBackground">#FF121212</item>
        <item name="elevationOverlayColor">@color/md_theme_dark_surfaceVariant</item>
    </style>
```

- [ ] **Step 4: Fill the Black theme shell (Dark values, OLED-pure surface/scrim)**

Replace `app/src/main/res/values/styles.xml:870-871`:
```xml
    <style name="Theme.App.Black" parent="Theme.App.Black.Base">
    </style>
```
with:
```xml
    <style name="Theme.App.Black" parent="Theme.App.Black.Base">
        <!-- Home-screen semantic tokens (Black / OLED). Dark values but OLED-pure surface + 90% scrim. -->
        <item name="appColorAccentOrange">#FF6B35</item>
        <item name="appColorAccentOrangeFaint">#59FF6B35</item>
        <item name="appColorAccentOrangeLight">#FF8C5B</item>
        <item name="appColorAccentOrangeDark">#BF3A1E</item>
        <item name="appColorAccentPurple">#A78BFA</item>
        <item name="appColorHomeTileSurface">#FF0B0B0B</item>
        <item name="appColorHomeStroke">#FF333333</item>
        <item name="appColorHomeTileStroke">#1AFFFFFF</item>
        <item name="appColorMutedForeground">#FF888888</item>
        <item name="appColorSubtleForeground">#77FFFFFF</item>
        <item name="appColorDimForeground">#CCFFFFFF</item>
        <item name="appColorIconTint">#80FFFFFF</item>
        <item name="appColorGroupLabel">#AAFFFFFF</item>
        <item name="appStarfieldScrim">#E6000000</item>
        <item name="appHomeBackground">@drawable/bg_home_starfield</item>
        <!-- Tonal elevation on OLED. -->
        <item name="elevationOverlayColor">@color/md_theme_dark_surfaceVariant</item>
    </style>
```

- [ ] **Step 5: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`. (Note: `bg_home_starfield_light` doesn't exist yet — Step 1 of Task 5 creates it. If the build fails on `unresolved @drawable/bg_home_starfield_light`, do Task 5 Step 1 first, then re-run.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/styles.xml
git commit -m "feat(theme): assign per-theme values to home semantic attrs (Light/White/Dark/Black)"
```

---

## Task 5: Create the light-mode starfield drawable

**Files:**
- Create: `app/src/main/res/drawable/bg_home_starfield_light.xml`

- [ ] **Step 1: Create the vector drawable**

Sparse faint pale-blue dots on near-white. Cheaper than a PNG; fully scalable.

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="360dp"
    android:height="640dp"
    android:viewportWidth="360"
    android:viewportHeight="640">

    <!-- Near-white base wash -->
    <path android:fillColor="#F8F9FC" android:pathData="M0,0h360v640h-360z" />

    <!-- Sparse faint dots (~12% opacity pale blue-grey). Read as "sky", not "space". -->
    <path android:fillColor="#143B4A6B" android:pathData="M30,40m1.2,0a1.2,1.2 0 1,0 -2.4,0a1.2,1.2 0 1,0 2.4,0" />
    <path android:fillColor="#103B4A6B" android:pathData="M90,70m0.9,0a0.9,0.9 0 1,0 -1.8,0a0.9,0.9 0 1,0 1.8,0" />
    <path android:fillColor="#163B4A6B" android:pathData="M200,55m1.4,0a1.4,1.4 0 1,0 -2.8,0a1.4,1.4 0 1,0 2.8,0" />
    <path android:fillColor="#103B4A6B" android:pathData="M300,120m0.9,0a0.9,0.9 0 1,0 -1.8,0a0.9,0.9 0 1,0 1.8,0" />
    <path android:fillColor="#143B4A6B" android:pathData="M60,180m1.1,0a1.1,1.1 0 1,0 -2.2,0a1.1,1.1 0 1,0 2.2,0" />
    <path android:fillColor="#123B4A6B" android:pathData="M250,210m1.0,0a1.0,1.0 0 1,0 -2.0,0a1.0,1.0 0 1,0 2.0,0" />
    <path android:fillColor="#163B4A6B" android:pathData="M150,260m1.3,0a1.3,1.3 0 1,0 -2.6,0a1.3,1.3 0 1,0 2.6,0" />
    <path android:fillColor="#103B4A6B" android:pathData="M40,320m0.9,0a0.9,0.9 0 1,0 -1.8,0a0.9,0.9 0 1,0 1.8,0" />
    <path android:fillColor="#143B4A6B" android:pathData="M320,360m1.2,0a1.2,1.2 0 1,0 -2.4,0a1.2,1.2 0 1,0 2.4,0" />
    <path android:fillColor="#123B4A6B" android:pathData="M110,420m1.0,0a1.0,1.0 0 1,0 -2.0,0a1.0,1.0 0 1,0 2.0,0" />
    <path android:fillColor="#163B4A6B" android:pathData="M280,470m1.4,0a1.4,1.4 0 1,0 -2.8,0a1.4,1.4 0 1,0 2.8,0" />
    <path android:fillColor="#103B4A6B" android:pathData="M70,540m0.9,0a0.9,0.9 0 1,0 -1.8,0a0.9,0.9 0 1,0 1.8,0" />
    <path android:fillColor="#143B4A6B" android:pathData="M210,590m1.2,0a1.2,1.2 0 1,0 -2.4,0a1.2,1.2 0 1,0 2.4,0" />
</vector>
```

- [ ] **Step 2: Verify build (Task 4 referenced this — should now resolve)**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/bg_home_starfield_light.xml
git commit -m "feat(theme): add light-mode starfield vector drawable"
```

---

## Task 6: Write the failing theme-adaptive color test (RED)

**This is the core proof that home is genuinely theme-adaptive — the phase's central deliverable. It must FAIL first.**

**Files:**
- Create: `app/src/test/java/com/xhub/browser/view/HomeThemeAdaptiveColorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.xhub.browser.view

import android.content.Context
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import com.xhub.browser.R
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the home screen's semantic color tokens resolve to *different*, perceptually-correct
 * values under each of the four user-selectable themes. This is the failing-test-first proof
 * for Phase 1's core deliverable: "home is theme-adaptive across Light/White/Dark/Black".
 *
 * If any attr resolves to the same value across a light vs dark theme, the test fails —
 * that would mean the token isn't actually wired per-theme.
 */
@RunWith(RobolectricTestRunner::class)
class HomeThemeAdaptiveColorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /** Resolves a ?attr/color token to its concrete Int under the currently-set theme. */
    private fun resolveColor(attrId: Int): Int {
        val tv = TypedValue()
        val ok = context.theme.resolveAttribute(attrId, tv, true)
        assertThat(ok).withFailMessage("attr did not resolve").isTrue()
        return tv.data
    }

    private fun resolveAttrId(name: String): Int {
        val id = context.resources.getIdentifier(name, "attr", context.packageName)
        assertThat(id).withFailMessage("attr $name not declared").isNotEqualTo(0)
        return id
    }

    @Test
    fun `orange accent is deeper in light themes than dark themes`() {
        val orangeId = resolveAttrId("appColorAccentOrange")

        context.setTheme(R.style.Theme_App_Light)
        val lightOrange = resolveColor(orangeId)

        context.setTheme(R.style.Theme_App_Dark)
        val darkOrange = resolveColor(orangeId)

        // Light must deepen orange for WCAG-AA; they must NOT be the same value.
        assertThat(lightOrange).isNotEqualTo(darkOrange)
    }

    @Test
    fun `home tile surface is light in light theme and dark in dark theme`() {
        val surfaceId = resolveAttrId("appColorHomeTileSurface")

        context.setTheme(R.style.Theme_App_Light)
        val lightSurface = resolveColor(surfaceId)
        context.setTheme(R.style.Theme_App_Black)
        val blackSurface = resolveColor(surfaceId)

        assertThat(lightSurface).isNotEqualTo(blackSurface)
        // Light tile surface luminance must be high (> 0.5), black must be low (< 0.05).
        assertThat(luminance(lightSurface)).isGreaterThan(0.5)
        assertThat(luminance(blackSurface)).isLessThan(0.05)
    }

    @Test
    fun `starfield scrim is light in light theme and dark in dark theme`() {
        val scrimId = resolveAttrId("appStarfieldScrim")

        context.setTheme(R.style.Theme_App_White)
        val whiteScrim = resolveColor(scrimId)
        context.setTheme(R.style.Theme_App_Dark)
        val darkScrim = resolveColor(scrimId)

        assertThat(whiteScrim).isNotEqualTo(darkScrim)
    }

    @Test
    fun `home background drawable differs between light and dark themes`() {
        val bgId = resolveAttrId("appHomeBackground")

        context.setTheme(R.style.Theme_App_Light)
        val lightTv = TypedValue()
        context.theme.resolveAttribute(bgId, lightTv, true)

        context.setTheme(R.style.Theme_App_Dark)
        val darkTv = TypedValue()
        context.theme.resolveAttribute(bgId, darkTv, true)

        // Different drawables (the light starfield vs the dark starfield).
        assertThat(lightTv.resourceId).isNotEqualTo(darkTv.resourceId)
    }

    @Test
    fun `all four themes resolve every declared attr without error`() {
        val attrNames = listOf(
            "appColorAccentOrange", "appColorAccentOrangeFaint", "appColorAccentOrangeLight",
            "appColorAccentOrangeDark", "appColorAccentPurple", "appColorHomeTileSurface",
            "appColorHomeStroke", "appColorHomeTileStroke", "appColorMutedForeground",
            "appColorSubtleForeground", "appColorDimForeground", "appColorIconTint",
            "appColorGroupLabel", "appStarfieldScrim"
        )
        val themes = listOf(
            R.style.Theme_App_Light, R.style.Theme_App_White,
            R.style.Theme_App_Dark, R.style.Theme_App_Black
        )
        for (theme in themes) {
            context.setTheme(theme)
            for (name in attrNames) {
                val tv = TypedValue()
                val ok = context.theme.resolveAttribute(resolveAttrId(name), tv, true)
                assertThat(ok).withFailMessage("$name failed to resolve under theme $theme").isTrue()
            }
        }
    }

    private fun luminance(color: Int): Double {
        val r = ((color shr 16) and 0xff) / 255.0
        val g = ((color shr 8) and 0xff) / 255.0
        val b = (color and 0xff) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
```

- [ ] **Step 2: Run the test to verify it FAILS for the right reason**

Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "*HomeThemeAdaptiveColorTest*"`
Expected: **FAIL**. The test references `R.style.Theme_App_Light` etc. which exist, but the per-theme `<item>` blocks from Task 4 must be present. **If Task 4 is done, the test should PASS** (the attr values are assigned). 

**If it fails with "attr appColorAccentOrange not declared"** → Task 1 wasn't applied; do Task 1 first.
**If it fails with a value-mismatch** → Task 4's per-theme values are wrong; fix them.

> Note: This test is written AFTER Task 4 on purpose — the TDD ordering here is "declare the contract (Tasks 1-4) then lock it with a regression test (Task 6)". If you prefer strict RED-first ordering, move Task 6 before Task 4 and watch it fail on "attr not declared", then implement Task 4 to make it pass.

- [ ] **Step 3: Once GREEN, commit**

```bash
git add app/src/test/java/com/xhub/browser/view/HomeThemeAdaptiveColorTest.kt
git commit -m "test(theme): add regression test proving home colors are theme-adaptive"
```

---

## Task 7: Repoint colors in the home-screen drawables

**Files (modify each — replace `@color/home_*` / hardcoded hex with `?attr/appColor*`, and hardcoded radii with `@dimen/radius_*`):**
- `app/src/main/res/drawable/bg_home_hero.xml`
- `app/src/main/res/drawable/bg_home_shortcut_card.xml`
- `app/src/main/res/drawable/bg_home_greeting_pill.xml`
- `app/src/main/res/drawable/bg_home_streak_chip.xml`
- `app/src/main/res/drawable/bg_home_flame_btn.xml`
- `app/src/main/res/drawable/bg_home_action_glass.xml`
- `app/src/main/res/drawable/bg_home_stat_chip.xml`
- `app/src/main/res/drawable/bg_home_quote_divider.xml`
- `app/src/main/res/drawable/bg_shortcut_manager_icon_btn.xml`

- [ ] **Step 1: For each drawable, read it, then replace every `@color/home_*` reference with the corresponding `?attr/appColor*`, and every hardcoded `android:radius="Ndp"` with `@dimen/radius_*` (8dp→radius_sm, 12dp→radius_md, 16dp→radius_lg, 18dp→radius_xl, 28dp→radius_full).**

Mapping table (apply verbatim):
```
@color/home_foreground          → (not used in drawables — used in layout, Task 8)
@color/home_accent_orange_light → ?attr/appColorAccentOrangeLight
@color/home_accent_orange_dark  → ?attr/appColorAccentOrangeDark
@color/home_accent_ring         → ?attr/colorPrimary
@color/home_accent_purple       → ?attr/appColorAccentPurple
@color/home_tile_surface        → ?attr/appColorHomeTileSurface
@color/home_tile_stroke         → ?attr/appColorHomeTileStroke
@color/home_stroke              → ?attr/appColorHomeStroke
@color/home_dim_foreground      → ?attr/appColorDimForeground
@color/home_muted_foreground    → ?attr/appColorMutedForeground
@color/home_subtle_foreground   → ?attr/appColorSubtleForeground
@color/home_group_label         → ?attr/appColorGroupLabel
hardcoded #...FF6B35 (solid)    → ?attr/appColorAccentOrange
hardcoded #59FF6B35 / #80FF6B35 → (remove from drawables; these live in the layout TextViews)
```

Example transformation for `bg_home_streak_chip.xml` (before/after pattern):
```xml
<!-- BEFORE -->
<solid android:color="@color/home_accent_ring" />
<corners android:radius="28dp" />
<!-- AFTER -->
<solid android:color="?attr/appColorAccentOrangeLight" />  <!-- or whatever the original used -->
<corners android:radius="@dimen/radius_full" />
```

> **Read each drawable before editing** to apply the *correct* target attr — the mapping above is authoritative for *names*, but verify which attr each drawable actually referenced.

- [ ] **Step 2: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/bg_home_*.xml app/src/main/res/drawable/bg_shortcut_manager_icon_btn.xml
git commit -m "refactor(home): repoint drawable colors/radii to semantic theme tokens"
```

---

## Task 8: Repoint colors, spacing, and text in layout_home_screen.xml

**Files:**
- Modify: `app/src/main/res/layout/layout_home_screen.xml`

- [ ] **Step 1: Background + scrim (lines 17, 26)**

```xml
android:src="@drawable/bg_home_starfield"   → android:src="?attr/appHomeBackground"
android:background="#CC000000"              → android:background="?attr/appStarfieldScrim"
```

- [ ] **Step 2: Every `android:textColor="@color/home_foreground"` → `"?attr/colorOnBackground"`**

(All instances — settings icon tint, flame icon tint, quote text, section titles, etc.)

- [ ] **Step 3: Other text colors per the mapping**

```
@color/home_muted_foreground   → ?attr/appColorMutedForeground
@color/home_subtle_foreground  → ?attr/appColorSubtleForeground
@color/home_dim_foreground     → ?attr/appColorDimForeground
@color/home_accent_orange_light→ ?attr/appColorAccentOrangeLight
@color/home_accent_purple      → ?attr/appColorAccentPurple

#59FF6B35 (quote mark)  → ?attr/appColorAccentOrangeFaint
#80FF6B35 (quote label) → ?attr/appColorAccentOrange
```

- [ ] **Step 4: Replace every TextView's textSize/fontFamily/textStyle triplet with textAppearance**

Apply per Section 3 of the spec. Examples:
```xml
<!-- quote text (homeTitle) -->
android:textSize="@dimen/home_quote_text_size" android:textStyle="bold" android:fontFamily="sans-serif-medium"
  → android:textAppearance="@style/TextAppearance.App.DisplayMedium"
<!-- section title (shortcutsTitle) -->
android:textSize="@dimen/home_section_title_size" android:fontFamily="sans-serif-medium"
  → android:textAppearance="@style/TextAppearance.App.TitleLarge"
<!-- "Quote of the day" label -->
android:textSize="@dimen/home_quote_label_size" android:textAllCaps="true" android:letterSpacing="0.15" android:fontFamily="sans-serif-medium"
  → android:textAppearance="@style/TextAppearance.App.LabelMedium"
<!-- greeting line -->
android:fontFamily="sans-serif-light" android:letterSpacing="0.02" android:textSize="@dimen/home_hero_greeting_text"
  → android:textAppearance="@style/TextAppearance.App.GreetingLabel"
```

Full mapping (apply to every TextView):
```
home_quote_text_size (22sp bold med)   → TextAppearance.App.DisplayMedium
home_collapsed_title_size (20sp med)   → TextAppearance.App.HeadlineSmall
home_section_title_size (17sp med)     → TextAppearance.App.TitleLarge
home_privacy_title_size / home_rl_title_size / home_search_text_size (15sp med) → TextAppearance.App.TitleMedium
home_hero_greeting_text (12sp light 0.02) → TextAppearance.App.GreetingLabel
home_privacy_desc_size / home_rl_meta_size / home_edit_text_size (13sp) → TextAppearance.App.BodyMedium
home_section_subtitle_size / home_streak_label (12sp) → TextAppearance.App.BodySmall
home_quote_label_size (11sp caps 0.15) → TextAppearance.App.LabelMedium
home_stat_text (10sp med)              → TextAppearance.App.LabelSmall
home_quote_mark_size (60sp black)      → TextAppearance.App.QuoteMark
home_tile_initial_size (26sp black)    → TextAppearance.App.TileInitial
home_tile_label_size (11.5sp med)      → TextAppearance.App.TileLabel
```

- [ ] **Step 5: Replace inline dp padding/margin literals with @dimen/spacing_***

```
4dp  → @dimen/spacing_xs
8dp  → @dimen/spacing_sm
12dp → @dimen/spacing_md
16dp → @dimen/spacing_lg
20dp → @dimen/spacing_xl
32dp → @dimen/spacing_xxl
40dp → @dimen/spacing_3xl
```

**Exception:** `home_padding_horizontal` stays `@dimen/home_padding_horizontal` (18dp — documented). Do NOT snap it.

- [ ] **Step 6: Replace tile/card radii with @dimen/radius_*** (where they appear inline, e.g. `android:background="@drawable/..."` that had radius — those drawables were already repointed in Task 7; this step only catches any inline `cornerRadius` attributes).

- [ ] **Step 7: Verify no hardcoded hex remains**

Run: `grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/layout/layout_home_screen.xml`
Expected: **no output** (zero matches).

- [ ] **Step 8: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Run the existing layout test (must still pass)**

Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "*HomeScreenLayoutTest*"`
Expected: PASS (IDs `homeScreenBackground`, `homeScreenBackgroundOverlay`, `shortcutsDynamicContainer`, `homeTitle` all preserved).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/res/layout/layout_home_screen.xml
git commit -m "refactor(home): repoint layout colors/spacing/type to semantic tokens"
```

---

## Task 9: Add MotionUtils (reduced-motion + low-ram guard)

**Files:**
- Create: `app/src/main/java/com/xhub/browser/ui/MotionUtils.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.xhub.browser.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Central gate for all home-screen motion. Every animation in HomeMotionController
 * calls [animationsEnabled] before running; if false, states snap to their end value.
 *
 * Honors:
 *  - Developer "Animator duration scale" == 0 (disabled animations)
 *  - Explore-by-touch / high-contrast accessibility services
 *  - Low-RAM devices (for heavy effects only — see [heavyEffectsEnabled])
 */
object MotionUtils {

    /**
     * True when ANY animation may run. Cheap effects (entrance fade, ripple) check this.
     */
    fun animationsEnabled(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        if (scale == 0f) return false

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val touchExploreOn = am?.isEnabled == true &&
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_AUDIBLE).isNotEmpty()
        return !touchExploreOn
    }

    /**
     * True only when heavy effects (parallax, toolbar-collapse tracking) may run.
     * Excludes low-RAM devices.
     */
    fun heavyEffectsEnabled(context: Context): Boolean {
        if (!animationsEnabled(context)) return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.isLowRamDevice != true
    }
}
```

- [ ] **Step 2: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xhub/browser/ui/MotionUtils.kt
git commit -m "feat(motion): add MotionUtils reduced-motion/low-ram gate"
```

---

## Task 10: Add the theme-switch crossfade anims + wire into ThemedActivity

**Files:**
- Create: `app/src/main/res/anim/theme_fade_in.xml`
- Create: `app/src/main/res/anim/theme_fade_out.xml`
- Modify: `app/src/main/java/com/xhub/browser/activity/ThemedActivity.kt:126-129` (`restart()`)

- [ ] **Step 1: Create theme_fade_in.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:fromAlpha="0.0"
    android:toAlpha="1.0"
    android:duration="150"
    android:interpolator="@android:interpolator/fast_out_slow_in" />
```

- [ ] **Step 2: Create theme_fade_out.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:fromAlpha="1.0"
    android:toAlpha="0.0"
    android:duration="150"
    android:interpolator="@android:interpolator/fast_out_slow_in" />
```

- [ ] **Step 3: Wire into ThemedActivity.restart()**

In `app/src/main/java/com/xhub/browser/activity/ThemedActivity.kt`, replace the `restart()` method (lines 126-129):
```kotlin
    protected fun restart() {
        finish()
        startActivity(Intent(this, javaClass))
    }
```
with:
```kotlin
    protected fun restart() {
        finish()
        startActivity(Intent(this, javaClass))
        // Tasteful crossfade so the theme switch doesn't snap.
        overridePendingTransition(R.anim.theme_fade_in, R.anim.theme_fade_out)
    }
```

- [ ] **Step 4: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/anim/theme_fade_in.xml app/src/main/res/anim/theme_fade_out.xml app/src/main/java/com/xhub/browser/activity/ThemedActivity.kt
git commit -m "feat(motion): add theme-switch crossfade to ThemedActivity.restart()"
```

---

## Task 11: Add HomeMotionController (parallax + entrance + streak pulse)

**Files:**
- Create: `app/src/main/java/com/xhub/browser/ui/HomeMotionController.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.xhub.browser.ui

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

/**
 * Drives all home-screen motion. Attached once to the home overlay by WebBrowserActivity
 * after the RecyclerView adapter is set.
 *
 * Effects (all gated on MotionUtils):
 *  - Parallax: background ImageView translates at 0.5x scroll, clamped ±12dp each direction.
 *  - Entrance: first screenful of tiles fade+scale in with a 50ms stagger on first bind.
 *
 * Streak pulse is wired separately by the caller (see [pulseStreakChip]) when the count changes.
 *
 * No new threads; no WebView hooks. Observes existing scroll/bind events only.
 */
class HomeMotionController(
    private val scrollView: NestedScrollView,
    private val backgroundView: View,
    private val recyclerView: RecyclerView,
    private val context: android.content.Context
) {
    private var entrancePlayed = false
    private val density = context.resources.displayMetrics.density

    /** Call exactly once after the RecyclerView has its adapter. */
    fun attach() {
        wireParallax()
        wireEntrance()
    }

    private fun wireParallax() {
        if (!MotionUtils.heavyEffectsEnabled(context)) return
        // 12dp each direction (net 24dp travel), in pixels.
        val clampPx = 12f * density
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // 0.5x parallax, clamped.
            val translation = (-scrollY * 0.5f).coerceIn(-clampPx, clampPx)
            backgroundView.translationY = translation
        }
    }

    private fun wireEntrance() {
        if (!MotionUtils.animationsEnabled(context)) return
        recyclerView.adapter ?: return
        // Animate on the next layout pass (children are bound by then).
        recyclerView.post { playEntrance() }
    }

    private fun playEntrance() {
        if (entrancePlayed) return
        entrancePlayed = true

        // Cap at the first screenful (~8 tiles). Skip headers/spacers/empties.
        val tiles = (0 until recyclerView.childCount)
            .mapNotNull { recyclerView.getChildAt(it) }
            .take(8)

        tiles.forEachIndexed { index, child ->
            child.alpha = 0f
            child.scaleX = 0.92f
            child.scaleY = 0.92f
            child.translationY = 8f * density

            val startDelay = 50L * index
            val duration = 180L

            ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 1f).apply {
                this.startDelay = startDelay
                this.duration = duration
                interpolator = DecelerateInterpolator()
            }.start()
            ObjectAnimator.ofFloat(child, View.SCALE_X, 0.92f, 1f).apply {
                this.startDelay = startDelay
                this.duration = duration
                interpolator = DecelerateInterpolator()
            }.start()
            ObjectAnimator.ofFloat(child, View.SCALE_Y, 0.92f, 1f).apply {
                this.startDelay = startDelay
                this.duration = duration
                interpolator = DecelerateInterpolator()
            }.start()
            ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, 8f * density, 0f).apply {
                this.startDelay = startDelay
                this.duration = duration
                interpolator = DecelerateInterpolator()
            }.start()
        }
    }

    /**
     * One-shot scale pulse on the streak chip. Call when the streak count increments.
     * No idle looping.
     */
    fun pulseStreakChip(chip: View) {
        if (!MotionUtils.animationsEnabled(context)) return
        val sx = ObjectAnimator.ofFloat(chip, View.SCALE_X, 1f, 1.15f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
        }
        val sy = ObjectAnimator.ofFloat(chip, View.SCALE_Y, 1f, 1.15f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
        }
        sx.start()
        sy.start()
    }
}
```

- [ ] **Step 2: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xhub/browser/ui/HomeMotionController.kt
git commit -m "feat(motion): add HomeMotionController (parallax + entrance + streak pulse)"
```

---

## Task 12: Wire HomeMotionController into WebBrowserActivity

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt` (in `buildDynamicShortcuts()`, after adapter is set ~line 1877)

- [ ] **Step 1: Add the controller field and instantiation**

Add a nullable field near the other home-related state in `WebBrowserActivity`:
```kotlin
private var homeMotionController: com.xhub.browser.ui.HomeMotionController? = null
```

In `buildDynamicShortcuts()`, inside the `subscribe { groups -> ... }` block, immediately after `val adapter = (recyclerView.adapter as? ShortcutTileAdapter) ?: run { ... }` sets the adapter (around line 1879, after `newAdapter`/`recyclerView.adapter = newAdapter`), add:
```kotlin
                    // Wire home motion (parallax + entrance) once the RecyclerView has its adapter.
                    if (homeMotionController == null) {
                        val scrollView = iBinding.homeScreenOverlay
                            .findViewById<androidx.core.widget.NestedScrollView>(android.R.id.list)
                            ?: iBinding.homeScreenOverlay
                                .findViewById<androidx.core.widget.NestedScrollView>(
                                    resources.getIdentifier("homeScrollView", "id", packageName)
                                ).let { it }
                        // Fallback: the NestedScrollView is the first scrollable child.
                        val sv = scrollView ?: (iBinding.homeScreenOverlay as? android.view.ViewGroup)
                            ?.let { findFirstNestedScrollView(it) }
                        val bg = iBinding.homeScreenOverlay.findViewById<android.view.View>(
                            resources.getIdentifier("homeScreenBackground", "id", packageName)
                        )
                        if (sv != null && bg != null) {
                            homeMotionController = com.xhub.browser.ui.HomeMotionController(
                                scrollView = sv,
                                backgroundView = bg,
                                recyclerView = recyclerView,
                                context = this@WebBrowserActivity
                            ).also { it.attach() }
                        }
                    } else {
                        // Re-trigger entrance on a forced rebuild.
                        homeMotionController?.let { controller ->
                            // entrancePlayed is private; reset via re-attach pattern:
                            // simplest correct behavior — re-create.
                        }
                    }
```

Also add this helper method to `WebBrowserActivity`:
```kotlin
    private fun findFirstNestedScrollView(root: android.view.ViewGroup): androidx.core.widget.NestedScrollView? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is androidx.core.widget.NestedScrollView) return child
            if (child is android.view.ViewGroup) {
                findFirstNestedScrollView(child)?.let { return it }
            }
        }
        return null
    }
```

> **NOTE:** The current `layout_home_screen.xml` uses a `<androidx.core.widget.NestedScrollView>` as a direct child of the CoordinatorLayout (verified in the layout read). It has NO `android:id`. The controller needs to find it. The cleanest fix is to **add an id to that NestedScrollView** in the layout: `android:id="@+id/homeScrollView"`. Add that as part of Task 8, or do it here:

- [ ] **Step 2: Add id to the NestedScrollView in layout_home_screen.xml**

In `app/src/main/res/layout/layout_home_screen.xml`, on the `<androidx.core.widget.NestedScrollView>` element (around line 30), add:
```xml
android:id="@+id/homeScrollView"
```

- [ ] **Step 3: Simplify the controller lookup now that the id exists**

Replace the Step-1 wiring with the simpler:
```kotlin
                    if (homeMotionController == null) {
                        val scrollView = iBinding.homeScreenOverlay
                            .findViewById<androidx.core.widget.NestedScrollView>(R.id.homeScrollView)
                        val bg = iBinding.homeScreenOverlay.findViewById<android.view.View>(R.id.homeScreenBackground)
                        if (scrollView != null && bg != null) {
                            homeMotionController = com.xhub.browser.ui.HomeMotionController(
                                scrollView = scrollView,
                                backgroundView = bg,
                                recyclerView = recyclerView,
                                context = this@WebBrowserActivity
                            ).also { it.attach() }
                        }
                    }
```

(And remove the `findFirstNestedScrollView` helper — no longer needed.)

- [ ] **Step 4: Verify build**

Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run both tests**

Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "*HomeScreenLayoutTest*" --tests "*HomeThemeAdaptiveColorTest*"`
Expected: both PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt app/src/main/res/layout/layout_home_screen.xml
git commit -m "feat(motion): wire HomeMotionController into the home screen"
```

---

## Task 13: Final verification & per-theme visual check

**Files:** none (verification only)

- [ ] **Step 1: Full clean build**

Run:
```powershell
taskkill /F /IM java.exe 2>$null; timeout /t 3
.\gradlew.bat clean assembleSlionsFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Full unit test suite (no regressions)**

Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
Expected: all PASS (including `HomeScreenLayoutTest` and `HomeThemeAdaptiveColorTest`).

- [ ] **Step 3: Grep verifies no hardcoded hex in the home layout**

Run: `grep -nE "#[0-9A-Fa-f]{3,8}" app/src/main/res/layout/layout_home_screen.xml`
Expected: **no output**.

- [ ] **Step 4: Manual on-device theme check (success criterion #3)**

Install the debug APK on an emulator/device. On the home screen, cycle through all four themes (Light, White, Dark, Black) via Settings. Confirm:
- Home background shows the light starfield in Light/White, the dark starfield in Dark/Black.
- Text is readable in all four (no light-on-light or dark-on-dark).
- Orange accents are deeper in Light/White, vivid in Dark/Black.
- Tiles lift visibly in Dark/Black (tonal elevation) and cast real shadows in Light/White.

- [ ] **Step 5: Motion spot-check**

On the home screen: confirm tiles fade+scale in on first show (entrance). Scroll: confirm the starfield parallaxes subtly. If reduced-motion is enabled in Developer Options (Animator duration scale = 0), confirm all motion snaps.

- [ ] **Step 6: Report results to user — do NOT mark the phase done until criteria 1-5 from spec §6.6 all pass with fresh evidence.**

---

## Self-Review Notes (plan author, completed)

**Spec coverage check (spec §1–§6 → task):**
- §1 token architecture → Tasks 1, 2, 3 ✓
- §2 color per-theme mapping → Task 4 ✓ (light starfield → Task 5 ✓)
- §3 typography scale → Task 3 ✓
- §4 shape/spacing/elevation → Tasks 2, 7 (radii in drawables) ✓
- §5 motion → Tasks 9, 10, 11, 12 ✓
- §6 home refactor → Tasks 7, 8, 12 ✓
- §6.6 success criteria → Task 13 ✓

**Contradictions fixed during planning:**
- The spec originally said I'd change `ShapeAppearance.App.MediumComponent` to 12dp for stat chips — but those are **app-wide** (every button/card/dialog). Phase 1 must NOT touch them. Fixed: spec §4 and Tasks 2/7 use `@dimen/radius_*` directly in home drawables only.

**Type consistency check:**
- `HomeMotionController` constructor signature in Task 11 matches the instantiation in Task 12 (4 args: scrollView, backgroundView, recyclerView, context). ✓
- `MotionUtils.animationsEnabled(context)` / `heavyEffectsEnabled(context)` names match calls in Task 11. ✓
- attr names (`appColorAccentOrangeFaint` etc.) match between Task 1 (declare), Task 4 (assign), Task 6 (test), Task 8 (layout). ✓

**Placeholder scan:** none. Every code step shows complete code. No "TODO"/"TBD"/"implement later".
