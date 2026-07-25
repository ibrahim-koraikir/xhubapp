# Design Spec: Premium Starfield Home Screen Restoration

## Goal
Restore the premium starfield home screen theme on the browser homepage, including the starry background, vibrant gradients for the logo and brand title, the Tinder-style flame button, and squircle shortcut cards with white backgrounds for favicons.

---

## 1. UI Components & Layouts

### 1.1 Background & Scrim Overlay
* **Starfield Background**: Add an `ImageView` with `@drawable/bg_home_starfield` (scaleType="centerCrop") as the base layer inside `layout_home_screen.xml`.
* **Dark Scrim Overlay**: Add a `View` with `#66000000` (40% transparent black) overlaying the background image for readability and WCAG accessibility compliance.
* **Root Background**: Remove the solid black background from the root CoordinatorLayout in `layout_home_screen.xml`.

### 1.2 Top Bar & Header Branding
* **Left Button (Settings)**: Container `FrameLayout` background set back to `@drawable/bg_home_profile_ring` (warm gold-to-purple gradient border).
* **Right Button (Bookmarks)**: Container `FrameLayout` background set back to `@drawable/bg_home_profile_ring`. Inside, the icon is restored to `@drawable/ic_launcher_foreground` (the Tinder-style gradient flame logo) instead of `@drawable/ic_bookmarks`. The tint `app:tint` is removed so it shows its native colors.
* **Center Logo Badge**: Logo container `FrameLayout` background set back to `@drawable/bg_home_logo_gradient` (warm gold to pink to purple gradient).
* **Brand Text (`xbuh`)**: Restore `homeTitle` typeface and size, and dynamically apply a warm gold-to-purple `LinearGradient` text shader in `WebBrowserActivity.kt`.
* **Greeting Text**: Subtitle styled with `#77FFFFFF` color to maintain proper contrast.

### 1.3 Shortcuts Section & Tiles
* **Shortcuts Card Background**: The shortcuts container `LinearLayout` background set to `@drawable/bg_home_shortcut_card` (a subtle glassmorphic panel).
* **Edit Button**: Simple rounded button with a pencil icon and label "Edit".
* **Shortcut Tile Card Layout**:
  * Size: **64dp x 64dp** (restoring from 72dp).
  * Corners: **18dp** radius (squircle design, restoring from 36dp circular).
  * Elevation: **3dp** (restoring from 0f flat).
  * Stroke width: **1dp** equivalent.
  * Stroke color: `#1AFFFFFF` (thin, semi-transparent white).
  * Background color: default dark surface color when showing letter placeholder.
* **Favicon Display**: When the favicon is successfully loaded, the card background color dynamically sets to **solid white** with a padding of **10dp** around the favicon.
* **Initial Letter Placeholder**: When no favicon is loaded, the letter is centered, styled in bold, colored `#FFFFFFFF`, with the card background as the default dark grey surface.

---

## 2. Proposed Changes

### [Component: UI Drawables]
* **`bg_home_profile_ring.xml`**: Retained as a gold-to-purple gradient outline ring.
* **`bg_home_logo_gradient.xml`**: Retained as the brand logo gradient badge.
* **`bg_home_shortcut_card.xml`**: Modify to restore the translucent glassmorphic look:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#0FFFFFFF" />
      <stroke android:width="1dp" android:color="#18FFFFFF" />
      <corners android:radius="20dp" />
  </shape>
  ```

### [Component: UI Layouts]
* **`layout_home_screen.xml`**:
  * Remove `android:background="#000000"` from root CoordinatorLayout.
  * Add the `ImageView` (`homeScreenBackground`) and dim `View` (`homeScreenBackgroundOverlay`) at the very top.
  * Restore collapsing/collapsed header styling to use the gradient badges, rings, and brand title text.
  * Update the right header icon `homeBookmarksIcon` to use `@drawable/ic_launcher_foreground` and remove tint.
  * Re-apply `bg_home_shortcut_card` background and correct margins.

### [Component: Activity / Code logic]
* **`WebBrowserActivity.kt`**:
  * Apply `LinearGradient` shader dynamically to `homeTitle` text paint.
  * Inside `buildDynamicShortcuts()`, change dimension tokens for shortcut card size back to 64dp, radius to 18dp, and elevation to 3dp.
  * In the favicon loading callback, set the card background to white (`Color.WHITE`) and set padding to 10dp when favicon is loaded. If it's not loaded, keep default background and transparent padding.

---

## 3. Verification Plan

### Automated Tests
* Run unit tests: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
* Compile debug build: `.\gradlew.bat assembleSlionsFullDownloadDebug`

### Manual Verification
* Launch the browser, verify that the home page background is the starry image with the scrim overlay.
* Verify the top header has Settings (left, gradient ring), `xbuh` (center, gradient logo, gradient text), and Tinder flame (right, gradient ring, no tint).
* Verify shortcuts section matches the squircle white-favicon card layout.
