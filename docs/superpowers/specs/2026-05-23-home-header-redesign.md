# Home Screen Header Redesign Design Specification

**Date:** 2026-05-23
**Goal:** Redesign the top section of the home screen to match a premium horizontal layout, integrating Settings and Profile (Bookmarks) shortcut buttons.
**Target Component:** Home Screen Header layout and click bindings.

---

## Proposed Layout Architecture

The design transitions from a vertical stack (app icon above the app title and greeting) to a balanced, horizontal `ConstraintLayout` header bar.

```
+-------------------------------------------------------------+
|  [Settings]            ( Logo ) xbuh            [Profile]   |
|                        Good evening                         |
+-------------------------------------------------------------+
```

### Components:
1. **Settings Shortcut (Left):** Circular `ImageButton` utilizing the existing `bg_shortcut_tile` background and `ic_settings` drawable, launching `SettingsActivity`.
2. **Branding Title Group (Center):** Vertical `LinearLayout` containing:
   - Horizontal branding container: Logo box with custom gradient background (`bg_home_logo_gradient`) containing `ic_comet_logo` next to the text `"xbuh"` (`homeTitle`).
   - Dynamically generated greeting text (`homeGreeting`).
3. **Profile Avatar Shortcut (Right):** FrameLayout container with custom orange-stroke circular ring (`bg_home_profile_ring`) containing a round cropped `ShapeableImageView` profile avatar. Clicking it launches the Bookmarks drawer (`openBookmarks()`).

---

## Resources & Files Affected

### [NEW] `bg_home_logo_gradient.xml`
Path: `app/src/main/res/drawable/bg_home_logo_gradient.xml`
- A shape drawable with rounded corners (`8dp`) and a gradient from `#FF8C00` to `#FF007A`.

### [NEW] `bg_home_profile_ring.xml`
Path: `app/src/main/res/drawable/bg_home_profile_ring.xml`
- An oval shape drawable with a `2dp` stroke of `#FF8C00` (Orange-400 equivalent).

### [MODIFY] `layout_home_screen.xml`
Path: `app/src/main/res/layout/layout_home_screen.xml`
- Replace vertical branding stack with the horizontal `ConstraintLayout` header container.

### [MODIFY] `WebBrowserActivity.kt`
Path: `app/src/main/java/fulguris/activity/WebBrowserActivity.kt`
- Bind settings and profile click listeners to launch settings and open bookmarks respectively.
