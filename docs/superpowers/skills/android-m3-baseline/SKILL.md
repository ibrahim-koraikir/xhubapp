---
name: android-m3-baseline
description: "You MUST use this skill when designing, building, or modifying any UI layout, view, or resource in this Android Kotlin project to ensure strict Material 3 design systems and pixel consistency."
---

Enforces strict Material 3 guidelines, layout grids, consistent typography, and adaptive color token systems in the Fulguris codebase.

## Core Checklist

You MUST verify each of these items before adding or modifying any Android XML layout or style:

1. **The 8dp Spacing Grid**
   - All layout dimensions, margins, paddings, and heights/widths (except icons or borders) MUST be multiples of 8dp (e.g., `8dp`, `16dp`, `24dp`, `32dp`, `48dp`, `64dp`).
   - Exceptions are only permitted for thin borders (e.g., `1dp`, `2dp`) or standard small layouts (like `4dp` micro-spacing).

2. **Adaptive Color Tokens (No Hardcoded Colors)**
   - Never use hardcoded HEX colors (like `#FFFFFF` or `#121212`) in layout XML files or styles.
   - All color values MUST reference Material Design 3 theme color attributes (e.g., `?attr/colorSurface`, `?attr/colorOnSurface`, `?attr/colorPrimary`, `?attr/colorSecondary`, `?attr/colorOutline`) to support perfect adaptive Light and Dark Mode.
   - For custom transparent layers, use theme color references combined with Alpha parameters (e.g., `?android:attr/disabledAlpha` or resolved color overlays).

3. **Material Typography System**
   - Never set arbitrary `android:textSize` and `android:textStyle` values on individual `TextView`s.
   - Always apply standard Material 3 text appearance styles:
     - `style="@style/TextAppearance.Material3.TitleLarge"` (for headers)
     - `style="@style/TextAppearance.Material3.TitleMedium"` (for subheaders)
     - `style="@style/TextAppearance.Material3.BodyMedium"` (for regular text)
     - `style="@style/TextAppearance.Material3.LabelSmall"` (for small detail text)

4. **Component Rounded Corners & Elevation**
   - Use standard Material 3 corner shapes:
     - Extra Small (`4dp` for small badges/chips)
     - Small (`8dp` for regular input fields)
     - Medium (`12dp` for menus, dialogs)
     - Large (`16dp` or `24dp` for bottom sheets)
     - Full (for pill buttons)
   - Elevate containers dynamically using `cardElevation` or `elevation` referencing Material 3 standard values (`1dp` to `8dp`).

## Verification & Guardrails

- Run a visual code review on layout XML files. If any hardcoded hexadecimal color or non-standard margin/padding is found, reject the layout and rewrite it.
- Compile and test in both **Light Theme** and **Dark Theme** to ensure perfect contrast and visual hierarchy.
