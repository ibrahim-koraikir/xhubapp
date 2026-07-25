# Premium UI & Motion Enhancements — Design Spec

Date: 2026-07-11
Status: Approved

---

## 1. Staggered Entrance Animations for Speed Dial Tiles

### Goal
Provide a premium, staggered entrance animation when the speed dial shortcuts load or when the user navigates back to the home screen.

### Design & Behavior
- In `HomeMotionController.kt`, we will add `resetEntrance()` to clear `entrancePlayed`.
- We will invoke `homeMotionController?.resetEntrance()` when the home overlay becomes visible (`isHome` transitions to `true` in `updateHomeScreenOverlay()`).
- Increase the hard-coded `MAX_ENTRANCE_TILES` from `8` to `24` so that all visible tiles on larger tablet layouts are animated.
- The entrance animation will fade-in, scale from `0.92f` to `1.0f`, and slide up by `8dp` with a `50ms` stagger delay per item.

---

## 2. Swipe-to-Dismiss Download Card

### Goal
Allow the user to dismiss the in-app download progress card by swiping left or right, utilizing standard Android `SwipeDismissBehavior`.

### Design & Behavior
- In `renderDownloadProgress()`, we will programmatically attach a `SwipeDismissBehavior` to the card's `LayoutParams` if it hasn't been attached yet.
- Allow swiping in any horizontal direction (`SWIPE_DIRECTION_ANY`).
- On dismiss listener:
  1. Store the URL in `downloadCardDismissedUrl: String? = null` inside `WebBrowserActivity`.
  2. Remove the download progress from `DownloadProgressBus`.
  3. Call `hideDownloadProgressCard()`.
  4. Reset the card view's animation properties (`alpha = 1f`, `translationX = 0f`, `translationY = 0f`) so it displays correctly when a new download starts.
- Inside `renderDownloadProgress()`, if `active.url == downloadCardDismissedUrl`, we will return early and keep the card hidden.

---

## 3. Tab Count Badge Pulse Animation

### Goal
Provide visual feedback when a tab is successfully opened in the background.

### Design & Behavior
- In `TabCountView.kt`, add a `pulse()` function that runs a springy scale animation (scale up to `1.25f` over `120ms`, then animate back to `1.0f` over `150ms` using standard decelerate interpolators).
- In `WebBrowserActivity.kt`, in both places where background tabs are opened:
  1. `openShortcutInBackground(url)`
  2. `onPageLongClick` link options (`LightningDialogBuilder.NewTab.BACKGROUND`)
  If the `tabsManager.newTab` call returns a non-null Tab, trigger `iBindingToolbarContent.tabsButton.pulse()`.

---

## 4. Long-Press Haptic Feedback

### Goal
Provide solid haptic feedback when the shortcut context bottom sheet opens.

### Design & Behavior
- In `onHomeScreenShortcutLongClick(view: View)`, set `view.isHapticFeedbackEnabled = true` and call `view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` to trigger a tactile click.

---

## 5. Download Complete Glow & Checkmark Animation

### Goal
Provide an elegant, satisfying "success" feedback when a download completes.

### Design & Behavior
- Add an ID to the file icon inside `activity_main.xml`: `android:id="@+id/ivDownloadProgressIcon"`.
- When download is `RUNNING` or other states: Ensure the icon is `@drawable/ic_file_download` with the orange tint.
- When download transitions to `COMPLETE`:
  1. Animate the icon: scale down to `0f` over `150ms`, swap the icon to `@drawable/ic_check` (tinted with `colorPrimary`), then scale back up to `1f` with an overshoot bounce interpolator.
  2. Animate the border: use a `ValueAnimator` to fade the card's `strokeColor` from its current glass color to a bright `colorPrimary` glow and back over `800ms`.
