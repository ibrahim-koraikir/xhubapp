# Starfield-Theme Glassmorphic Tabs Switcher Implementation Plan

**Goal:** Redesign the tab switcher UI and cards to match the custom premium Starfield background using Material Design 3 guidelines.
**Architecture:** Create glassmorphic backdrop and header gradients, layer active borders using `layer-list`, enable `clipToOutline` on the card holder, and modernize padding, spacing, and close button icons.
**Tech Stack:** Android XML layouts, custom drawables, Hilt DI.

---

## Proposed Changes

### Component: Drawables & Assets

#### [NEW] [bg_tab_grid_sheet_glass.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_tab_grid_sheet_glass.xml)
Creates a vertical translucent black-to-charcoal gradient background for the main tab switcher sheet.

#### [NEW] [bg_tab_grid_card_header_glass.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_tab_grid_card_header_glass.xml)
Creates a 5% translucent white glass card header with rounded top corners.

#### [MODIFY] [bg_tab_grid_card_active.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_tab_grid_card_active.xml)
Updates active border to use a dual-layered neon glowing pink stroke layer-list.

#### [MODIFY] [bg_tab_grid_close_btn.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_tab_grid_close_btn.xml)
Updates close button circular background to use a micro-glassmorphic white translucent track.

#### [MODIFY] [bg_tab_grid_fab.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_tab_grid_fab.xml)
Updates FAB button with a neon pink glow stroke.

#### [MODIFY] [bg_tab_grid_pill_group.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_tab_grid_pill_group.xml)
Updates tabs/private pill tracker with a 10% translucent white glass track.

#### [MODIFY] [bg_tab_grid_pill_active.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_tab_grid_pill_active.xml)
Updates active pill toggle with a frosted solid slate-dark shape.

### Component: Layouts & ViewHolders

#### [MODIFY] [tab_drawer_view.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/tab_drawer_view.xml)
Sets root background and bottom bar to translucent glass and transparent layouts respectively.

#### [MODIFY] [tab_list_item.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/tab_list_item.xml)
Updates card margins, applies frosted header, and refines padding/icon layouts.

#### [MODIFY] [TabViewHolder.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabViewHolder.kt)
Enables outline clipping on `iCardView` so child elements are cropped to card corner radii.

---

## Detailed Step-by-Step Tasks

### Task 1: Create Glassmorphic Background and Header Drawables

**Files:**
- Create: `app/src/main/res/drawable/bg_tab_grid_sheet_glass.xml`
- Create: `app/src/main/res/drawable/bg_tab_grid_card_header_glass.xml`

- [ ] **Step 1: Write `bg_tab_grid_sheet_glass.xml`**
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
- [ ] **Step 2: Write `bg_tab_grid_card_header_glass.xml`**
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#0DFFFFFF" />
      <corners
          android:topLeftRadius="16dp"
          android:topRightRadius="16dp"
          android:bottomLeftRadius="0dp"
          android:bottomRightRadius="0dp" />
  </shape>
  ```
- [ ] **Step 3: Verify build compiles**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

---

### Task 2: Modify Existing Theme Drawables

**Files:**
- Modify: `app/src/main/res/drawable/bg_tab_grid_card_active.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_close_btn.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_fab.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_pill_group.xml`
- Modify: `app/src/main/res/drawable/bg_tab_grid_pill_active.xml`

- [ ] **Step 1: Update active card border (`bg_tab_grid_card_active.xml`)**
  Overwrite with:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
      <item>
          <shape android:shape="rectangle">
              <solid android:color="@color/tab_grid_card" />
              <corners android:radius="16dp" />
              <stroke
                  android:width="2.5dp"
                  android:color="@color/tab_grid_primary" />
          </shape>
      </item>
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
- [ ] **Step 2: Update close button circle (`bg_tab_grid_close_btn.xml`)**
  Overwrite with:
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
- [ ] **Step 3: Update New Tab FAB (`bg_tab_grid_fab.xml`)**
  Overwrite with:
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
- [ ] **Step 4: Update pills container tracker (`bg_tab_grid_pill_group.xml`)**
  Overwrite with:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#1AFFFFFF" />
      <corners android:radius="999dp" />
      <stroke
          android:width="1dp"
          android:color="#14FFFFFF" />
  </shape>
  ```
- [ ] **Step 5: Update active pill indicator (`bg_tab_grid_pill_active.xml`)**
  Overwrite with:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#E60D0D0D" />
      <corners android:radius="999dp" />
  </shape>
  ```
- [ ] **Step 6: Verify build compiles**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

---

### Task 3: Overhaul layouts (`tab_drawer_view.xml` and `tab_list_item.xml`)

**Files:**
- Modify: `app/src/main/res/layout/tab_drawer_view.xml`
- Modify: `app/src/main/res/layout/tab_list_item.xml`

- [ ] **Step 1: Update `tab_drawer_view.xml`**
  Modify root and bottom layout lines in `app/src/main/res/layout/tab_drawer_view.xml`:
  - Line 18: Change `android:background="@color/tab_grid_background"` to `android:background="@drawable/bg_tab_grid_sheet_glass"`
  - Line 126: Change `android:background="@color/tab_grid_background"` to `android:background="@android:color/transparent"`
- [ ] **Step 2: Update `tab_list_item.xml`**
  Modify card layout header lines in `app/src/main/res/layout/tab_list_item.xml`:
  - Line 25: Change `android:background="@android:color/transparent"` to `android:background="@drawable/bg_tab_grid_card_header_glass"`
- [ ] **Step 3: Verify build compiles**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

---

### Task 4: Enable Clip-To-Outline on Tab Card ViewHolder

**Files:**
- Modify: `app/src/main/java/fulguris/browser/tabs/TabViewHolder.kt`

- [ ] **Step 1: Set `clipToOutline` on `iCardView`**
  Add outline clipping statement to `init` block of `app/src/main/java/fulguris/browser/tabs/TabViewHolder.kt`:
  ```kotlin
          iCardView?.clipToOutline = true
  ```
  (Place it immediately inside the `init { ... }` block, e.g. at line 42).
- [ ] **Step 2: Run all JVM unit tests**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
  Expected: BUILD SUCCESSFUL (All tests pass)
- [ ] **Step 3: Verify build compiles**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 4: Commit**
  Run: `git add .` and `git commit -m "feat(tabs): implement gorgeous starfield-matching glassmorphic tabs UI"`
