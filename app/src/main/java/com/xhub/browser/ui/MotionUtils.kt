package com.xhub.browser.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Central gate for all home-screen motion. Every animation in [HomeMotionController]
 * calls [animationsEnabled] before running; if false, states snap to their end value.
 *
 * Honors:
 *  - Developer "Animator duration scale" == 0 (animations disabled)
 *  - Explore-by-touch / audible-feedback accessibility services
 *  - Low-RAM devices (for heavy effects only — see [heavyEffectsEnabled])
 *
 * No motion code should run animations without first checking this gate.
 */
object MotionUtils {

    /**
     * True when ANY animation may run. Cheap effects (entrance fade, ripple, streak pulse)
     * check this. Returns false if the user has disabled animations at the OS level or has
     * an audible-feedback accessibility service enabled (explore-by-touch etc.).
     */
    fun animationsEnabled(context: Context): Boolean {
        // OS-level animation kill-switch: "Animator duration scale" == 0 in Developer Options.
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (e: Exception) {
            1f
        }
        if (scale == 0f) return false

        // Honor audible-feedback accessibility services (e.g. TalkBack explore-by-touch),
        // where motion can be disorienting.
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return true
        if (!am.isEnabled) return true
        val touchExploreOn = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_AUDIBLE
        ).isNotEmpty()
        return !touchExploreOn
    }

    /**
     * True only when heavy effects (parallax, toolbar-collapse tracking) may run.
     * Excludes low-RAM devices, where continuous per-frame translation can cause jank.
     * Cheap effects should check [animationsEnabled]; only parallax/collapse check this.
     */
    fun heavyEffectsEnabled(context: Context): Boolean {
        if (!animationsEnabled(context)) return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        return !activityManager.isLowRamDevice
    }
}
