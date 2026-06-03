# Design Spec: OLED Minimalist Home Screen Redesign

## Problem Description
The previous home screen design utilized a vibrant starfield theme with glassmorphic cards, gradients, and multiple layered overlays. The user found this design unsatisfactory and requested a cleaner, high-contrast **OLED Minimalist** layout.

## Proposed UI Design (Approach 2 - OLED Minimalist)
The goal is to transition the home screen to a pure black (`#000000`) aesthetic with high legibility, clean thin outlines, and monochromatic circular shortcut icons.

### Key Changes by Section:
1. **Background**: Remove the starfield background image and gradient overlay. Set the home screen root coordinator layout background to pure black (`#000000`).
2. **Header/Branding**: Center the logo badge with a solid dark grey (`#1A1A1A`) background and a thin grey border. Style the branding text `xHub` to be modern monochromatic white. Style greeting sub-text to `#888888` (dark grey).
3. **Search Bar**: Restyle the home search card to be a clean, thin-outlined container with a `#333333` border and solid deep black background (`#0D0D0D`). Use flat light-grey text and icons (`#888888`).
4. **Shortcuts Section**: Use a flat layout with simple dividers rather than an elevated translucent card. Restyle the circular shortcut icon backgrounds to a subtle solid dark grey (`#161616`) with high-contrast white/grey glyphs/icons.

---

## Proposed Changes

### [Component: UI Resources / Layouts]

#### [MODIFY] [layout_home_screen.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/layout_home_screen.xml)
- Set the root `CoordinatorLayout` background to `#000000`.
- Remove the `ImageView` (`homeScreenBackground`) and overlay `View` (`homeScreenBackgroundOverlay`).
- Remove the top header gradient overlay `View`.
- Restyle logo container `FrameLayout` background to a new solid minimalist background (`bg_home_logo_minimalist`) instead of `bg_home_logo_gradient`.
- Update profile and settings button ring drawables to a clean solid grey outline (`bg_home_profile_ring_minimalist`).
- Restyle the shortcuts section container to remove the card background.

#### [NEW] [bg_home_logo_minimalist.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_logo_minimalist.xml)
- Simple solid background `#1A1A1A` with a thin `#333333` outline.

#### [NEW] [bg_home_profile_ring_minimalist.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_profile_ring_minimalist.xml)
- Clean, thin solid outline in `#333333`.

#### [MODIFY] [bg_home_search_card.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_search_card.xml)
- Set solid background to `#0D0D0D` and stroke color to `#333333`.

#### [MODIFY] [bg_home_shortcut_card.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_shortcut_card.xml)
- Transparent background (remove solid/stroke borders) to keep the shortcut grid flat.

---

## Verification Plan

### Automated Tests
- Build debug APK: `.\gradlew.bat assembleSlionsFullDownloadDebug`
- Run local unit tests: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`

### Manual Verification
- Launch the app and verify the home screen is pure black with a clean, minimalist header, thin-bordered search bar, and flat shortcut icons.
