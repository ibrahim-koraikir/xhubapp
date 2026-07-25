# Smart Download Hub & Long-Press Hint — Implementation Plan

Goal: Upgrade the download progress card to a glassmorphic floating card with speed/ETA, and show a Toast hint on every shortcut tap until the user performs a long-press.
Architecture: Layout-only redesign for the card; no new service/bus changes. One new UserPreferences boolean and two touch-points in WebBrowserActivity for the hint.
Tech Stack: Android XML layouts, Material Components, Kotlin, SharedPreferences delegates.

---

## Task 1 — Add `shortcutLongPressDone` preference to UserPreferences

**File:** `app/src/main/java/com/xhub/browser/settings/preferences/UserPreferences.kt`

Find the last `booleanPreference` line in the file (currently `adsConsentAsked`) and add the new field directly after it.

**Add after the last booleanPreference field:**
```kotlin
/** True once the user has long-pressed a home shortcut — suppresses the tip Toast permanently. */
var shortcutLongPressDone by preferences.booleanPreference("pref_key_shortcut_longpress_done", false)
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL` (or no new errors).

---

## Task 2 — Add `shortcut_longpress_hint` string to strings.xml

**File:** `app/src/main/res/values/strings.xml`

Find the block of `shortcut_*` strings (e.g., near `shortcut_favorites_header`) and add:

```xml
<string name="shortcut_longpress_hint">💡 Tip: Press &amp; hold a shortcut to open in background or incognito</string>
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 3 — Wire hint Toast in `onHomeScreenShortcutClick` + suppress on `onHomeScreenShortcutLongClick`

**File:** `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`

### Change 3.1 — `onHomeScreenShortcutClick` (line ~6041)

Current:
```kotlin
    fun onHomeScreenShortcutClick(view: View) {
        val url = view.tag as? String ?: return
        if (url.isNotBlank()) {
            searchTheWeb(url)
        }
    }
```

New:
```kotlin
    fun onHomeScreenShortcutClick(view: View) {
        val url = view.tag as? String ?: return
        if (url.isNotBlank()) {
            // Show long-press tip on every tap until the user has performed a long-press.
            if (!userPreferences.shortcutLongPressDone) {
                Toast.makeText(this, R.string.shortcut_longpress_hint, Toast.LENGTH_SHORT).show()
            }
            searchTheWeb(url)
        }
    }
```

### Change 3.2 — `onHomeScreenShortcutLongClick` (line ~574)

The very first line of the method body should set the preference so future taps suppress the hint:

Current (first lines inside the method):
```kotlin
    fun onHomeScreenShortcutLongClick(view: View) {
        val url = view.tag as? String ?: return
        if (url.isBlank()) return
```

New:
```kotlin
    fun onHomeScreenShortcutLongClick(view: View) {
        // User has discovered long-press — suppress the hint Toast permanently.
        if (!userPreferences.shortcutLongPressDone) {
            userPreferences.shortcutLongPressDone = true
        }
        val url = view.tag as? String ?: return
        if (url.isBlank()) return
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 4 — Upgrade download card layout in `activity_main.xml`

**File:** `app/src/main/res/layout/activity_main.xml`

Replace the entire `<com.google.android.material.card.MaterialCardView android:id="@+id/downloadProgressCard" ...>` block (lines ~139–220) with the new glassmorphic design below. **All existing IDs are preserved** (`downloadProgressCard`, `tvDownloadProgressFilename`, `tvDownloadProgressPercent`, `downloadProgressIndicator`, `btnDownloadProgressCancel`, `btnDownloadProgressOpen`). One new ID is added: `tvDownloadSpeedEta`.

```xml
<!--
In-app download progress card — Glassmorphic floating design.
Driven by DownloadProgressBus. Visible even when POST_NOTIFICATIONS is denied.
All existing view IDs preserved + tvDownloadSpeedEta added for speed/ETA subtitle.
-->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/downloadProgressCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:layout_marginStart="16dp"
    android:layout_marginEnd="16dp"
    android:layout_marginBottom="12dp"
    android:visibility="gone"
    app:cardCornerRadius="20dp"
    app:cardElevation="10dp"
    app:cardBackgroundColor="?attr/appColorSheetGlass"
    app:strokeColor="?attr/appColorGlassStroke"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingTop="14dp"
        android:paddingBottom="14dp">

        <!-- Row 1: icon + filename + percent -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <!-- File type icon -->
            <ImageView
                android:layout_width="22dp"
                android:layout_height="22dp"
                android:src="@drawable/ic_file_download"
                android:importantForAccessibility="no"
                android:contentDescription="@null"
                app:tint="?attr/appColorAccentOrange"
                android:layout_marginEnd="10dp" />

            <!-- Filename -->
            <TextView
                android:id="@+id/tvDownloadProgressFilename"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textAppearance="@style/TextAppearance.App.BodyMedium"
                android:textStyle="bold"
                android:textColor="?attr/colorOnSurface"
                android:ellipsize="middle"
                android:singleLine="true"
                tools:text="my_video.mp4" />

            <!-- Percent / state label -->
            <TextView
                android:id="@+id/tvDownloadProgressPercent"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="10dp"
                android:textAppearance="@style/TextAppearance.App.LabelMedium"
                android:textColor="?attr/colorPrimary"
                tools:text="42%" />

        </LinearLayout>

        <!-- Row 2: Speed · ETA subtitle (hidden when unknown) -->
        <TextView
            android:id="@+id/tvDownloadSpeedEta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textAppearance="@style/TextAppearance.App.BodySmall"
            android:textColor="?attr/appColorSubtleForeground"
            android:visibility="gone"
            tools:text="3.2 MB/s • 12s remaining"
            tools:visibility="visible" />

        <!-- Row 3: Progress bar -->
        <com.google.android.material.progressindicator.LinearProgressIndicator
            android:id="@+id/downloadProgressIndicator"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            app:indicatorColor="?attr/colorPrimary"
            app:trackCornerRadius="6dp"
            app:trackThickness="5dp" />

        <!-- Row 4: Action buttons -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="end"
            android:layout_marginTop="4dp">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnDownloadProgressCancel"
                style="@style/Widget.MaterialComponents.Button.TextButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:minWidth="0dp"
                android:minHeight="0dp"
                android:textColor="?attr/appColorSubtleForeground"
                android:text="@string/action_cancel" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnDownloadProgressOpen"
                style="@style/Widget.MaterialComponents.Button.TextButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:minWidth="0dp"
                android:minHeight="0dp"
                android:visibility="gone"
                android:text="@string/action_open" />

        </LinearLayout>

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 5 — Populate speed/ETA in `renderDownloadProgress()` + add helper

**File:** `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`

### Change 5.1 — Add `formatSpeedEta()` helper (near `formatEta` / `downloadMimeType`)

Add this new private function anywhere in the download-card section of `WebBrowserActivity`:

```kotlin
/**
 * Format speed + ETA into a human-readable subtitle for the download progress card.
 * Returns null (hides the row) when both are unknown.
 * Examples: "3.2 MB/s • 12s remaining", "3.2 MB/s", "12s remaining"
 */
private fun formatSpeedEta(speedBytesPerSec: Long, etaSeconds: Long): String? {
    val parts = mutableListOf<String>()
    if (speedBytesPerSec >= 0) {
        parts.add(android.text.format.Formatter.formatShortFileSize(this, speedBytesPerSec) + "/s")
    }
    if (etaSeconds >= 0) {
        val mins = etaSeconds / 60
        val secs = etaSeconds % 60
        val etaStr = if (mins > 0) "${mins}m ${secs}s remaining" else "${secs}s remaining"
        parts.add(etaStr)
    }
    return if (parts.isEmpty()) null else parts.joinToString(" • ")
}
```

### Change 5.2 — Populate `tvDownloadSpeedEta` in `renderDownloadProgress()`

Inside `renderDownloadProgress()`, after the line that sets `filenameTv?.text = active.filename`,
add the following to resolve and update the new subtitle view:

```kotlin
val speedEtaTv = iBinding.root.findViewById<TextView>(R.id.tvDownloadSpeedEta)
```

Then inside the `DownloadProgress.State.RUNNING` branch, after setting `percentTv?.text`, add:

```kotlin
// Populate speed/ETA subtitle row
val speedEtaStr = formatSpeedEta(active.speedBytesPerSec, active.etaSeconds)
if (speedEtaTv != null) {
    if (speedEtaStr != null) {
        speedEtaTv.text = speedEtaStr
        speedEtaTv.isVisible = true
    } else {
        speedEtaTv.isVisible = false
    }
}
```

For all other states (COMPLETE, ERROR, CANCELLED, PAUSED), hide the subtitle:
```kotlin
speedEtaTv?.isVisible = false
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 6 — Final full build + unit tests

```powershell
taskkill /F /IM java.exe
timeout /t 3
.\gradlew.bat assembleXhubFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat testXhubFullDownloadDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` — all existing tests pass (no new layout IDs removed, no logic changed).

---

## Notes
- `formatSpeedEta()` is purely cosmetic and has no side-effects.
- The `shortcutLongPressDone` preference is internal (raw string key, never appears in Settings XML).
- The card's bottom margin is still adjusted programmatically in `renderDownloadProgress()` to clear
  the bottom toolbar and FAB — no change needed to that logic.
- `ic_file_download` is already in the drawable set (used by the FAB in `activity_main.xml`).
