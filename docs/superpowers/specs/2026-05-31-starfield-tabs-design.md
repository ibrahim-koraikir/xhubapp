# Starfield-Theme Glassmorphic Tabs Switcher Design Spec
**Date:** 2026-05-31
**Topic:** Modernized Glassmorphic and Starfield-Matching Tab Switcher UI

## Goal
Elevate the tab switcher interface (`layout/tab_drawer_view.xml` and `layout/tab_list_item.xml`) to match the new premium Starfield theme. This replaces flat solid elements with a gorgeous dark glassmorphic layout, glowing active card borders, and sleek frosted close buttons.

---

## 1. Main Tab Switcher Layout Overhaul (`layout/tab_drawer_view.xml`)

*   **Glassmorphic Sheet Backdrop:** Replace the solid `@color/tab_grid_background` (`#0b0b0b`) on the main container with a new translucent vertical gradient drawable `bg_tab_grid_sheet_glass`.
    - At the top, the sheet has 85% opacity (`#D9080808`), allowing a blurred version of the space starfield background to peak through when sliding open.
    - At the bottom, the sheet has 98% opacity (`#FA0C0C0C`), providing high contrast for text buttons and actions.
*   **Header Modernization:**
    - Refactor the "Tabs / Private" pills container (`bg_tab_grid_pill_group`) to use a sleek translucent white track tint (`#22FFFFFF`).
    - The active pill (`bg_tab_grid_pill_active`) will use a frosted dark solid shape matching the deep slate color scheme.

---

## 2. Premium Tab Card Styling (`layout/tab_list_item.xml`)

*   **Neon Glowing Active State (`bg_tab_grid_card_active.xml`):**
    - Refactor active card background from a single flat outline to a dual-layered `layer-list`.
    - Layer 1 (Outer border): 2.5dp neon pink stroke (`@color/tab_grid_primary` / `#ff007a`) with a soft glow effect.
    - Layer 2 (Inner card container): Solid card background (`@color/tab_grid_card`) inset by 2.5dp to preserve rounded corners and visual separation.
*   **Micro-Glassmorphic Close Button (`bg_tab_grid_close_btn.xml`):**
    - Refactor the close button container into a translucent circle.
    - Use a solid white glass color with 12% opacity (`#20FFFFFF`) and a soft semi-transparent border (`#1AFFFFFF`).
*   **Frosted Header Bar:**
    - Apply a soft 5% white glass tint background (`#0DFFFFFF`) to the card's header layout inside `tab_list_item.xml`. This separates the favicon and title text visually from the preview image below.
    - Clean up the padding and vertical spacing (`12dp` top/bottom, `14dp` start/end) to follow standard M3 grids.

---

## 3. Drawables Architecture

### [NEW] Translucent Sheet Backdrop
File: `app/src/main/res/drawable/bg_tab_grid_sheet_glass.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#D9080808"
        android:endColor="#FA0C0C0C"
        android:angle="270" />
</shape>
```

### [MODIFY] Glowing Active Card Background
File: `app/src/main/res/drawable/bg_tab_grid_card_active.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Outer Glow Layer -->
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/tab_grid_card" />
            <corners android:radius="16dp" />
            <stroke
                android:width="2.5dp"
                android:color="@color/tab_grid_primary" />
        </shape>
    </item>
    <!-- Inner Card Backdrop -->
    <item
        android:left="2.5dp"
        android:top="2.5dp"
        android:right="2.5dp"
        android:bottom="2.5dp">
        <shape android:shape="rectangle">
            <solid android:color="@color/tab_grid_card" />
            <corners android:radius="13.5dp" />
        </shape>
    </item>
</layer-list>
```

### [MODIFY] Micro-Glassmorphic Close Button Background
File: `app/src/main/res/drawable/bg_tab_grid_close_btn.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#20FFFFFF" />
    <stroke
        android:width="1dp"
        android:color="#1AFFFFFF" />
</shape>
```

### [MODIFY] Glowing New Tab FAB Accent
File: `app/src/main/res/drawable/bg_tab_grid_fab.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/tab_grid_primary" />
    <stroke
        android:width="2.5dp"
        android:color="#80FF007A" />
</shape>
```

---

## 4. Verification Plan

- **Layout Compilation:** Ensure the layout compile is green under gradle.
  `.\gradlew.bat assembleSlionsFullDownloadDebug`
- **Unit Tests:** Execute tests to confirm zero regressions in view bindings or adapter operations.
  `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
