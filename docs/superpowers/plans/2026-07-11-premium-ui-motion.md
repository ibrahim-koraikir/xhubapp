# Premium UI & Motion Enhancements Implementation Plan

Goal: Implement speed dial entrance animations, swipe-to-dismiss download card, tab count pulse on background tab open, haptics on shortcut long-press, and a success checkmark + card glow pulse on download complete.
Architecture: Layout adjustments and UI controller/helper additions inside WebBrowserActivity, TabCountView, and HomeMotionController.
Tech Stack: Android View animators, SwipeDismissBehavior, ValueAnimator, haptic feedback APIs.

---

## Task 1 — Add Haptic Feedback on Shortcut Long-Press

**File:** `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`

Find `onHomeScreenShortcutLongClick(view: View)` (around line 574) and make sure haptic feedback is explicitly enabled and performed when the menu opens.

**Replace the top of `onHomeScreenShortcutLongClick`:**
```kotlin
    fun onHomeScreenShortcutLongClick(view: View) {
        // User has discovered long-press — suppress the hint Toast permanently.
        if (!userPreferences.shortcutLongPressDone) {
            userPreferences.shortcutLongPressDone = true
        }
        // Force haptic feedback on shortcut long-press
        view.isHapticFeedbackEnabled = true
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        val url = view.tag as? String ?: return
        if (url.isBlank()) return
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 2 — Implement Tab Count View Pulse Animation

**File:** `app/src/main/java/com/xhub/browser/icon/TabCountView.kt`

Add a `pulse()` function that scales the view up to 1.25x and back down with a decelerate interpolator.

**Add the method to `TabCountView` class:**
```kotlin
    /**
     * Perform a springy scale pulse animation to draw attention to background tab creation.
     */
    fun pulse() {
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        animate()
            .scaleX(1.25f)
            .scaleY(1.25f)
            .setDuration(120)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()
    }
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 3 — Trigger Pulse on Background Tab Creation

**File:** `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`

We will trigger the pulse animation only when a background tab is successfully opened:
1. In `openShortcutInBackground` (line ~667)
2. In `handleNewTab` under `BACKGROUND` case (line ~6003)

### Change 3.1 — `openShortcutInBackground`
```kotlin
    private fun openShortcutInBackground(url: String) {
        val tab = tabsManager.newTab(UrlInitializer(url), false)
        if (tab != null) {
            Toast.makeText(this, R.string.shortcut_opened_in_background, Toast.LENGTH_SHORT).show()
            iBindingToolbarContent.tabsButton.pulse()
        }
    }
```

### Change 3.2 — `handleNewTab`
```kotlin
    override fun handleNewTab(newTabType: LightningDialogBuilder.NewTab, url: String) {
        val urlInitializer = UrlInitializer(url)
        when (newTabType) {
            LightningDialogBuilder.NewTab.FOREGROUND -> tabsManager.newTab(urlInitializer, true)
            LightningDialogBuilder.NewTab.BACKGROUND -> {
                val tab = tabsManager.newTab(urlInitializer, false)
                if (tab != null) {
                    iBindingToolbarContent.tabsButton.pulse()
                }
            }
            LightningDialogBuilder.NewTab.INCOGNITO -> {
                closePanels()
                val intent = IncognitoActivity.createIntent(this, url.toUri())
                startActivity(intent)
                overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out)
            }
        }
    }
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 4 — Refine Home Screen Entrance Animation

**File:** `app/src/main/java/com/xhub/browser/ui/HomeMotionController.kt`

We need to:
1. Add `resetEntrance()` method so the animation can be run again on home return.
2. Increase the animation tile cap to `24` items to cover tablet grids.

**Replace the private companion object and add `resetEntrance`:**
```kotlin
    /** Reset the entrance animation state so it plays again on the next pass. */
    fun resetEntrance() {
        entrancePlayed = false
    }
```
And change:
```kotlin
    private companion object {
        const val MAX_ENTRANCE_TILES = 24
        const val ENTRANCE_SCALE_START = 0.92f
        const val ENTRANCE_SLIDE_DP = 8f
        const val ENTRANCE_DURATION_MS = 180L
        const val ENTRANCE_STAGGER_MS = 50L
        val DECELERATE = DecelerateInterpolator()
    }
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 5 — Reset Entrance Animation on Home Switch

**File:** `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`

Find `updateHomeScreenOverlay` (line ~2090). When `isHome` is true, call `homeMotionController?.resetEntrance()`.

**Inside `updateHomeScreenOverlay` under `if (isHome)` block:**
```kotlin
            buildDynamicShortcuts()
            // Refresh greeting and stats on every home-enter (time-sensitive, not version-gated).
            updateHomeGreeting()
            updateHomeStats()
            // Wire hero header actions (Edit, Settings, and the Saved/Downloads/Private stat chips).
            // Idempotent - safe to call on every home-enter. See wireHomeHeroActions().
            wireHomeHeroActions()
            homeMotionController?.resetEntrance()
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 6 — Add ID to Download Card Icon

**File:** `app/src/main/res/layout/activity_main.xml`

Find the `ImageView` inside `downloadProgressCard` (line ~170) and add `android:id="@+id/ivDownloadProgressIcon"`.

**Update the ImageView:**
```xml
                                        <ImageView
                                            android:id="@+id/ivDownloadProgressIcon"
                                            android:layout_width="22dp"
                                            android:layout_height="22dp"
                                            android:src="@drawable/ic_file_download"
                                            android:importantForAccessibility="no"
                                            android:contentDescription="@null"
                                            app:tint="?attr/appColorAccentOrange"
                                            android:layout_marginEnd="10dp" />
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 7 — Implement Swipe-to-Dismiss and Complete animations

**File:** `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`

### Change 7.1 — Add state variable
Add `downloadCardDismissedUrl` state variable below `shownDownloadUrl` (line ~2238):
```kotlin
    private var downloadCardDismissedUrl: String? = null
```

### Change 7.2 — Reset dismiss state on empty downloads
Find the top of `renderDownloadProgress` (line ~2275) and reset the dismissal state if the active map is empty:
```kotlin
        if (downloads.isEmpty()) {
            downloadCardDismissedUrl = null
            if (card.isVisible) hideDownloadProgressCard()
            return
        }
```

### Change 7.3 — Filter out dismissed card updates
Check if the active download is swiped/dismissed:
```kotlin
        // Prefer an active download; fall back to the most recently updated (terminal) one.
        val active = downloads.values.lastOrNull { it.state == DownloadProgress.State.RUNNING }
            ?: downloads.values.last()

        if (active.url == downloadCardDismissedUrl) {
            hideDownloadProgressCard()
            return
        }
```

### Change 7.4 — Attach SwipeDismissBehavior and Icon lookup
Add lookup for `ivDownloadProgressIcon` and attach behavior:
```kotlin
        val filenameTv = iBinding.root.findViewById<TextView>(R.id.tvDownloadProgressFilename)
        val percentTv = iBinding.root.findViewById<TextView>(R.id.tvDownloadProgressPercent)
        val speedEtaTv = iBinding.root.findViewById<TextView>(R.id.tvDownloadSpeedEta)
        val iconIv = iBinding.root.findViewById<ImageView>(R.id.ivDownloadProgressIcon)
...
```
And:
```kotlin
        // Keep the card clear of the bottom toolbar (when the "toolbars at bottom" option is on)
        // and the video-download FAB so they never overlap. The card lives inside web_view_frame
        // at bottom gravity; lift it by whichever obstruction is present (they're rarely both).
        (card.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { lp ->
            val baseMarginPx = 12.px
            val toolbarClearancePx = if (configPrefs.toolbarsBottom)
                baseMarginPx + resources.getDimensionPixelSize(R.dimen.toolbar_height_portrait) else 0
            // FAB sits bottom|end at 64dp margin, ~56dp tall; +8dp gap clears it fully.
            val fabClearancePx = if (iBinding.fabDownloadVideo.isVisible) 128.px else 0
            val desiredBottomMargin = maxOf(baseMarginPx, toolbarClearancePx, fabClearancePx)
            if (lp.bottomMargin != desiredBottomMargin) {
                lp.bottomMargin = desiredBottomMargin
                card.layoutParams = lp
            }

            // Attach SwipeDismissBehavior programmatically
            if (lp.behavior == null) {
                val swipe = com.google.android.material.behavior.SwipeDismissBehavior<View>()
                swipe.setSwipeDirection(com.google.android.material.behavior.SwipeDismissBehavior.SWIPE_DIRECTION_ANY)
                swipe.listener = object : com.google.android.material.behavior.SwipeDismissBehavior.OnDismissListener {
                    override fun onDismiss(view: View) {
                        val url = shownDownloadUrl
                        if (url != null) {
                            downloadCardDismissedUrl = url
                            DownloadProgressBus.remove(url)
                        }
                        hideDownloadProgressCard()
                        // Reset card layout positioning so it draws correctly next time
                        view.alpha = 1f
                        view.translationX = 0f
                        view.translationY = 0f
                    }
                    override fun onDragStateChanged(state: Int) {}
                }
                lp.behavior = swipe
            }
        }
```

### Change 7.5 — Configure icons and complete glow animations
Add support for checkmark pop and card glow on completion:

**RUNNING branch:**
```kotlin
            DownloadProgress.State.RUNNING -> {
                // Ensure default icon and color
                iconIv?.tag = null
                iconIv?.setImageResource(R.drawable.ic_file_download)
                val tvColor = android.util.TypedValue()
                theme.resolveAttribute(R.attr.appColorAccentOrange, tvColor, true)
                iconIv?.imageTintList = ColorStateList.valueOf(
                    if (tvColor.resourceId != 0) ContextCompat.getColor(this, tvColor.resourceId) else tvColor.data
                )
                openBtn?.isVisible = false
...
```

**COMPLETE branch:**
```kotlin
            DownloadProgress.State.COMPLETE -> {
                cancelBtn?.isVisible = false
                speedEtaTv?.isVisible = false
                indicator?.isIndeterminate = false
                indicator?.setProgressCompat(100, true)
                percentTv?.text = getString(R.string.video_download_complete)
                
                // Animate checkmark transition and card border pulse once
                if (iconIv?.tag != "complete") {
                    iconIv?.tag = "complete"
                    iconIv?.animate()?.scaleX(0f)?.scaleY(0f)?.setDuration(150)
                        ?.withEndAction {
                            iconIv.setImageResource(R.drawable.ic_check)
                            val tvColor = android.util.TypedValue()
                            theme.resolveAttribute(R.attr.colorPrimary, tvColor, true)
                            iconIv.imageTintList = ColorStateList.valueOf(tvColor.data)
                            iconIv.animate()?.scaleX(1f)?.scaleY(1f)
                                ?.setInterpolator(android.view.animation.OvershootInterpolator())
                                ?.setDuration(250)?.start()
                        }?.start()
                        
                    // Border stroke color flash animation (ValueAnimator)
                    val tvBorder = android.util.TypedValue()
                    theme.resolveAttribute(R.attr.appColorGlassStroke, tvBorder, true)
                    val colorFrom = if (tvBorder.resourceId != 0) ContextCompat.getColor(this, tvBorder.resourceId) else tvBorder.data
                    
                    val tvPrimary = android.util.TypedValue()
                    theme.resolveAttribute(R.attr.colorPrimary, tvPrimary, true)
                    val colorTo = tvPrimary.data
                    
                    android.animation.ValueAnimator.ofObject(
                        android.animation.ArgbEvaluator(),
                        colorFrom,
                        colorTo,
                        colorFrom
                    ).apply {
                        duration = 800
                        addUpdateListener { animator ->
                            card.strokeColorStateList = ColorStateList.valueOf(animator.animatedValue as Int)
                        }
                        start()
                    }
                }
                
                // Offer an Open action mirroring the success notification's tap behaviour.
...
```

**ERROR, CANCELLED, and PAUSED branches:**
Ensure `iconIv?.tag = null` is set in case downloads transition back or restart.
Also ensure icon is reset in PAUSED branch if it can be restarted:
```kotlin
            DownloadProgress.State.ERROR -> {
                iconIv?.tag = null
                ...
            DownloadProgress.State.CANCELLED -> {
                iconIv?.tag = null
                ...
            DownloadProgress.State.PAUSED -> {
                iconIv?.tag = null
                iconIv?.setImageResource(R.drawable.ic_file_download)
                val tvColor = android.util.TypedValue()
                theme.resolveAttribute(R.attr.appColorAccentOrange, tvColor, true)
                iconIv?.imageTintList = ColorStateList.valueOf(
                    if (tvColor.resourceId != 0) ContextCompat.getColor(this, tvColor.resourceId) else tvColor.data
                )
                ...
```

### Verify
```powershell
.\gradlew.bat compileXhubFullDownloadDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 8 — Final Verification and Clean Build

```powershell
taskkill /F /IM java.exe
timeout /t 3
.\gradlew.bat assembleXhubFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`.
