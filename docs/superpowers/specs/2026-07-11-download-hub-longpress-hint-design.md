# Smart Download Hub & Long-Press Hint — Design Spec

Date: 2026-07-11
Status: Approved

---

## Feature 1: Glassmorphic Download Progress Card

### Goal
Upgrade the existing `downloadProgressCard` in `activity_main.xml` from a plain `MaterialCardView`
into a premium floating glassmorphic card that shows:
- Filename (with file-type icon)
- Real-time speed and ETA as subtitle text
- Animated circular/linear progress indicator
- Cancel / Open (on COMPLETE) action buttons

### Architecture
The `DownloadProgressBus` (already a `StateFlow<Map<String, DownloadProgress>>`) and
`renderDownloadProgress()` in `WebBrowserActivity` are already wiring speed/ETA from
`YtDlpDownloadService` to the card. The data layer needs **zero changes**.

Only two things change:
1. **Layout** (`activity_main.xml`) — replace the card's inner content with a richer layout.
2. **`renderDownloadProgress()`** (`WebBrowserActivity.kt`) — populate the new speed/ETA `TextView`.

### Visual Design
- **Card style:** `app:cardBackgroundColor="?attr/appColorSheetGlass"` (near-black surface on dark,
  faint off-white on light). `app:cardElevation="10dp"`. `app:cardCornerRadius="20dp"`.
  `app:strokeColor="?attr/appColorGlassStroke"` (1dp semi-transparent border — glassmorphism edge).
- **Icon row:** A `24dp` file-type icon (`ic_video_file` or `ic_file_download`) on the far left,
  then filename `TextView` (bold, `?attr/colorOnSurface`, ellipsize middle), then spacer, then
  status text (`42%` or `✓ Done` or `✗ Failed`) tinted `?attr/colorPrimary`.
- **Subtitle row:** Speed + ETA label, e.g. `"3.2 MB/s • 12s remaining"`. If both unknown: hidden.
  Text style `TextAppearance.App.BodySmall`, color `?attr/appColorSubtleForeground`.
- **Progress bar:** `LinearProgressIndicator`, full-width, `indicatorColor="?attr/colorPrimary"`,
  `trackCornerRadius="6dp"`, `trackThickness="5dp"`.
- **Action buttons row:** `Cancel` (text button, shown while RUNNING/PAUSED) and `Open` (filled
  button, shown only on COMPLETE).

### Speed + ETA string helper
New private fun `formatSpeedEta(speedBytesPerSec: Long, etaSeconds: Long): String?` added to
`WebBrowserActivity`. Returns `null` when both are unknown (hides the subtitle row). Otherwise
produces `"3.2 MB/s"`, `"12s remaining"`, or `"3.2 MB/s • 12s remaining"`.  Uses
`Formatter.formatShortFileSize()` for speed (already imported) and the existing
`YtDlpDownloadService.formatEta()` pattern for ETA.

### Files changed
- `app/src/main/res/layout/activity_main.xml` — restyle the card's interior (no new IDs broken,
  just additional `TextView` for subtitle and icon `ImageView` added).
- `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt` — populate new
  `tvDownloadSpeedEta` view in `renderDownloadProgress()`, add `formatSpeedEta()` helper.

---

## Feature 2: Long-Press Shortcut Hint Toast

### Goal
Show a persistent Toast every time the user taps a home-screen shortcut tile:
> 💡 *"Tip: Press and hold a shortcut to open in background or incognito!"*

Stop showing it permanently the first time the user actually long-presses any tile
(i.e., `onHomeScreenShortcutLongClick` fires). Once they've seen the bottom sheet, they know.

### Architecture
- **Preference key:** `pref_key_shortcut_longpress_done` (string key stored as a boolean in
  `UserPreferences`).  Default: `false`.
- **Showing the hint:** In `onHomeScreenShortcutClick()`, read the preference. If `false`, call
  `Toast.makeText(this, R.string.shortcut_longpress_hint, Toast.LENGTH_SHORT).show()`.
- **Stopping the hint:** In `onHomeScreenShortcutLongClick()`, at the top of the method, set the
  preference to `true` (one write, then never shown again).
- **No animation, no new layouts** — plain `Toast` is intentional (lightweight, system-managed).

### New string
`shortcut_longpress_hint` = `"💡 Tip: Press & hold any shortcut to open in background or incognito"`

### New preference
In `UserPreferences.kt`:
```kotlin
/** True once the user has long-pressed a home shortcut — suppresses the tip Toast. */
var shortcutLongPressDone by preferences.booleanPreference(
    "pref_key_shortcut_longpress_done", false
)
```
(Uses raw string key — NOT a resource ID — so it is internal-only and never appears in the
 Settings UI.)

### Files changed
- `app/src/main/java/com/xhub/browser/settings/preferences/UserPreferences.kt` — new field.
- `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt` — read in `onHomeScreenShortcutClick`, write in `onHomeScreenShortcutLongClick`.
- `app/src/main/res/values/strings.xml` — new string `shortcut_longpress_hint`.

---

## Non-goals (YAGNI)
- No new Fragment, ViewModel, Activity, or Service.
- No changes to `DownloadProgressBus`, `YtDlpDownloadService`, or the notification system.
- No per-locale ETA formatting — simple `"Ns remaining"` is sufficient.
- No drag-to-dismiss on the card (too complex for the current `CoordinatorLayout` anchoring).
