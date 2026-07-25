# Ad Blocker Default Settings

## Changes Made

Changed the default settings for the ad blocker in `app/src/main/java/fulguris/settings/preferences/UserPreferences.kt`:

### 1. Ad Blocker Enabled by Default
**Before:**
```kotlin
var adBlockEnabled by preferences.booleanPreference(BLOCK_ADS, false)
```

**After:**
```kotlin
var adBlockEnabled by preferences.booleanPreference(BLOCK_ADS, true)
```

### 2. Auto-Update Always On
**Before:**
```kotlin
var blockListAutoUpdate by preferences.enumPreference(R.string.pref_key_blocklist_auto_update, AbpUpdateMode.WIFI_ONLY)
```

**After:**
```kotlin
var blockListAutoUpdate by preferences.enumPreference(R.string.pref_key_blocklist_auto_update, AbpUpdateMode.ALWAYS)
```

### 3. Daily Update Frequency
**Before:**
```kotlin
var blockListAutoUpdateFrequency by preferences.intPreference(R.string.pref_key_blocklist_auto_update_frequency, 7)
```

**After:**
```kotlin
var blockListAutoUpdateFrequency by preferences.intPreference(R.string.pref_key_blocklist_auto_update_frequency, 1)
```

## Summary

The app now has:
- ✅ **Ad blocker enabled by default** - Users get ad blocking out of the box
- ✅ **Auto-update always on** - Block lists update automatically on any network (not just WiFi)
- ✅ **Daily updates** - Block lists update every day (changed from weekly)

## Note

These are default settings for **new installations only**. Existing users who have already configured these settings will keep their preferences. Users can still change these settings in:

**Settings → Ad block → Content control**
- Toggle ad blocker on/off
- Change auto-update mode (Always / WiFi only / None)
- Change update frequency (Daily / Weekly / Monthly)

## Build

Build the app with:
```bash
./gradlew assembleSlionsFullFdroidDebug
```
