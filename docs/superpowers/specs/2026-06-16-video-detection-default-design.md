# Video Detection Default and Toggle Fix Spec

Goal: Ensure that the video detection feature is enabled by default and the settings toggle is fully clickable and functional.

## Context & Problem
1. **Unclickable Toggle**: In `app/src/main/res/xml/preference_general.xml`, the video detection SwitchPreference (and other tab-related SwitchPreferences) uses the `app:` namespace for core attributes (`app:key`, `app:title`, `app:defaultValue`). On some layouts/system configurations, this custom/library namespace fallback causes issues with binding the click listener and key tracking, rendering the preference unclickable.
2. **Enabled by Default**: The preference is already set to default to `true` in `booleans.xml`, but the XML layout attributes need to be standard (`android:`) to ensure this default value is correctly loaded and parsed during inflation.

## Proposed Changes

### 1. `app/src/main/res/xml/preference_general.xml`
Convert all custom namespace attributes (`app:key`, `app:title`, `app:defaultValue`) to standard Android namespace attributes (`android:key`, `android:title`, `android:defaultValue`) for:
- `pref_key_video_detection_enabled`
- `pref_key_search_in_new_tab`
- `pref_key_url_in_new_tab`
- `pref_key_homepage_in_new_tab`
- `pref_key_bookmark_in_new_tab`

## Verification Plan

### Automated Build & Tests
- Compile the debug APK using:
  ```powershell
  taskkill /F /IM java.exe
  timeout /t 3
  .\gradlew.bat assembleSlionsFullDownloadDebug
  ```
- Run unit tests:
  ```powershell
  .\gradlew.bat testSlionsFullDownloadDebugUnitTest
  ```

### Manual Verification
- Deploy to device/emulator.
- Go to Settings -> General.
- Verify that **Enable video detection** is visible.
- Verify that it is clickable and toggles correctly.
- Verify that it defaults to ON (checked) on a fresh install or clean cache.
