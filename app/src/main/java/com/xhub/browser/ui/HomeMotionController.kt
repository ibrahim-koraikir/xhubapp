package com.xhub.browser.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

/**
 * Drives all home-screen motion. Attached once to the home overlay by WebBrowserActivity
 * after the RecyclerView adapter is set.
 *
 * Effects (all gated on [MotionUtils]):
 *  - Parallax: background ImageView translates at 0.5x scroll, clamped ±12dp each direction
 *    (net 24dp travel) so the starfield edges are never exposed.
 *  - Entrance: the first screenful of tiles fade+scale in with a 50ms stagger on first bind.
 *
 * Streak pulse is exposed via [pulseStreakChip]; the caller invokes it when the count changes.
 *
 * Performance discipline:
 *  - No new threads; no WebView hooks. Observes existing scroll/bind events only.
 *  - All effects are property/alpha/scale (GPU-composited), never on the input pipeline.
 *  - Heavy effects (parallax) are disabled on low-RAM devices via [MotionUtils.heavyEffectsEnabled].
 *  - Every effect snaps to its end state when the OS has animations disabled.
 */
class HomeMotionController(
    private val scrollView: NestedScrollView,
    private val backgroundView: View,
    private val recyclerView: RecyclerView,
    private val context: Context
) {
    private var entrancePlayed = false
    private val density = context.resources.displayMetrics.density

    /** Call exactly once after the RecyclerView has its adapter. Idempotent on entrance. */
    fun attach() {
        wireParallax()
        wireEntrance()
    }

    private fun wireParallax() {
        if (!MotionUtils.heavyEffectsEnabled(context)) return
        // 12dp each direction (net 24dp travel), in pixels.
        val clampPx = 12f * density
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // 0.5x parallax, clamped to protect the background image edges.
            val translation = (-scrollY * 0.5f).coerceIn(-clampPx, clampPx)
            backgroundView.translationY = translation
        }
    }

    private fun wireEntrance() {
        if (!MotionUtils.animationsEnabled(context)) return
        if (recyclerView.adapter == null) return
        // Animate on the next layout pass (children are bound by then).
        recyclerView.post { playEntrance() }
    }

    private fun playEntrance() {
        if (entrancePlayed) return
        entrancePlayed = true

        // Cap at the first screenful (~8 tiles). Headers/spacers/empties are skipped implicitly
        // because they're not among the first 8 children in a typical 4-column grid.
        val tiles = (0 until recyclerView.childCount)
            .mapNotNull { recyclerView.getChildAt(it) }
            .take(MAX_ENTRANCE_TILES)

        val startOffsetPx = ENTRANCE_SLIDE_DP * density
        tiles.forEachIndexed { index, child ->
            child.alpha = 0f
            child.scaleX = ENTRANCE_SCALE_START
            child.scaleY = ENTRANCE_SCALE_START
            child.translationY = startOffsetPx

            val startDelay = ENTRANCE_STAGGER_MS * index
            listOf(
                ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(child, View.SCALE_X, ENTRANCE_SCALE_START, 1f),
                ObjectAnimator.ofFloat(child, View.SCALE_Y, ENTRANCE_SCALE_START, 1f),
                ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, startOffsetPx, 0f)
            ).forEach { anim ->
                anim.startDelay = startDelay
                anim.duration = ENTRANCE_DURATION_MS
                anim.interpolator = DECELERATE
                anim.start()
            }
        }
    }

    /**
     * One-shot scale pulse on the streak chip. Call when the streak count increments.
     * No idle looping. Snaps (no-op) when animations are disabled.
     */
    fun pulseStreakChip(chip: View) {
        if (!MotionUtils.animationsEnabled(context)) return
        listOf(View.SCALE_X, View.SCALE_Y).forEach { property ->
            ObjectAnimator.ofFloat(chip, property, 1f, PULSE_PEAK, 1f).apply {
                duration = PULSE_DURATION_MS
                interpolator = DECELERATE
            }.start()
        }
    }

    private companion object {
        const val MAX_ENTRANCE_TILES = 8
        const val ENTRANCE_SCALE_START = 0.92f
        const val ENTRANCE_SLIDE_DP = 8f
        const val ENTRANCE_DURATION_MS = 180L
        const val ENTRANCE_STAGGER_MS = 50L
        const val PULSE_PEAK = 1.15f
        const val PULSE_DURATION_MS = 400L
        val DECELERATE = DecelerateInterpolator()
    }
}
