# Premium Starfield Background — Design Spec

Date: 2026-05-30
Skill Gates: android-m3-baseline, android-accessibility, android-rendering-perf

## Goal
Incorporate the user-uploaded starry space image as a full-screen premium background for the Fulguris Home Screen, dimmed with a high-contrast dark scrim overlay to maintain Material 3 readability and WCAG accessibility compliance.

---

## Technical Specifications

### 1. Resource Copying
- Source path: `C:\Users\w\.gemini\antigravity\brain\c2025dd7-271a-4118-92d6-5598aa26b212\media__1780142981228.jpg`
- Destination path: `c:\Users\w\Desktop\Fulguris-main\app\src\main\res\drawable\bg_home_starfield.jpg`

### 2. Layout Modificiation in `layout_home_screen.xml`
- Remove the root CoordinatorLayout background:
  `android:background="@color/home_background"`
- Add two new views at the very beginning of the root CoordinatorLayout (so they render in the background layer):
  1. An `ImageView` for `bg_home_starfield` with `scaleType="centerCrop"` and accessibility hidden.
  2. A semi-transparent overlay `View` with background `#66000000` (40% opacity black scrim) for maximum text contrast.

---

## Verification
- Compile: `.\gradlew.bat assembleSlionsFullDownloadDebug` → must be BUILD SUCCESSFUL
- Run unit tests: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest` → must be BUILD SUCCESSFUL
