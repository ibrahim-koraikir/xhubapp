# Animation Overhaul Design
**Date:** 2026-05-24
**Topic:** Fixing clunky animations on New Tab and Intent/Video launches.

## Goal
Replace the existing jarring sliding and scaling animations (e.g., `slide_up_in`, `fade_out_scale`) with premium, subtle, fast crossfades. This will make opening new tabs, navigating activities, and launching external intents (like videos) feel extremely modern and buttery smooth.

## Approach 2: Custom Premium Crossfades

### 1. New Animation Resources
We will create two new animation XMLs in `app/src/main/res/anim/`:
* `premium_fade_in.xml` - A subtle `alpha` fade from `0.0` to `1.0` with a duration of `200ms` using `accelerate_decelerate_interpolator`.
* `premium_fade_out.xml` - A subtle `alpha` fade from `1.0` to `0.0` with a duration of `200ms` using `accelerate_decelerate_interpolator`.

### 2. Modifying Transitions in `WebBrowserActivity` and `ReadingActivity`
We will run a global replacement for the legacy transition calls:
**Old:** `overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)`
**Old:** `overridePendingTransition(R.anim.fade_in_scale, R.anim.slide_down_out)`
**Old:** `overridePendingTransition(R.anim.slide_in_from_right, R.anim.fade_out_scale)`

**New:** `overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out)`

This affects:
* `WebBrowserActivity.kt`
* `ReadingActivity.kt`
* `MainActivity.kt`

### 3. Reviewing Tab Click Transitions
* `WebBrowserActivity.tabClicked` uses a `postDelayed({ closePanels() }, 350)` hard-coded delay.
* We will ensure this delay plays nicely with the new 200ms fades, possibly tuning it slightly down if the tab drawer feels unresponsive.

## Spec Self-Review
- [x] No placeholders or "TODO" items.
- [x] Clear scope (only targets the specific animations that were reported as bad).
- [x] Correct file paths for modifications.
