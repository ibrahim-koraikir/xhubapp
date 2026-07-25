package com.xhub.browser.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.doOnNextLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.xhub.browser.R

/**
 * Drives all home-screen motion. Attached once to the home overlay by WebBrowserActivity
 * after the RecyclerView adapter is set.
 *
 * Effects (all gated on [MotionUtils]):
 *  - Parallax: background View translates upward at 0.5x scroll, clamped to 12dp so the
 *    starfield edge is never exposed. The background has a matching -12dp top/bottom bleed
 *    (set in layout_home_screen.xml) to absorb the full translation range. The starfield
 *    translates at 0.7x the background for a 3-D depth cue.
 *  - Entrance: the first screenful of tiles fade+scale in with a 50ms stagger. The animation
 *    fires only when at least one real shortcut tile is attached — it is explicitly suppressed
 *    for [ShortcutItem.Empty] so the one-shot flag is not spent on the empty placeholder.
 *    After the first real shortcut list arrives, the host must call [onShortcutsUpdated] so
 *    the entrance can re-arm and fire on the next layout pass.
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

    private var waitingForLayout = false

    /** Call exactly once after the RecyclerView has its adapter. */
    fun attach() {
        wireParallax()
    }

    /** Call when the shortcuts list has been updated. Idempotent on entrance. */
    fun onShortcutsUpdated() {
        wireEntrance()
    }

    private fun wireParallax() {
        if (!MotionUtils.heavyEffectsEnabled(context)) return
        // 12dp upward clamp (px). The background view has a matching -12dp vertical bleed in
        // the layout so the translation never uncovers the window background beneath it.
        // Note: scrollY is always non-negative, so translation is always non-positive (upward).
        val clampPx = 12f * density
        val starfieldView = (scrollView.parent as? View)?.findViewById<View>(R.id.homeStarfield)
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // 0.5x parallax, clamped upward to protect the background edge.
            val translation = (-scrollY * 0.5f).coerceIn(-clampPx, 0f)
            backgroundView.translationY = translation
            // Stars translate at 0.7x of the background for a premium 3D depth effect.
            starfieldView?.translationY = translation * 0.7f
        }
    }

    private fun wireEntrance() {
        if (!MotionUtils.animationsEnabled(context)) return
        if (recyclerView.adapter == null) return
        if (entrancePlayed) return
        if (waitingForLayout) return

        val adapter = recyclerView.adapter as? com.xhub.browser.shortcuts.ShortcutTileAdapter
        if (adapter != null && adapter.itemCount == 1 && adapter.getItemViewType(0) == com.xhub.browser.shortcuts.ShortcutTileAdapter.VIEW_TYPE_EMPTY) {
            // Guard: do not wire or play entrance animation for empty state placeholder
            return
        }

        waitingForLayout = true
        // Wait until the next layout pass AND at least one child is attached. Using
        // doOnNextLayout instead of post() guarantees children have been measured and
        // attached by the time the block runs, preventing the entrance from latching
        // with zero children (which would set entrancePlayed = true prematurely).
        recyclerView.doOnNextLayout {
            waitingForLayout = false
            playEntrance()
        }
    }

    private fun playEntrance() {
        if (entrancePlayed) return

        // Guard: only play (and latch entrancePlayed) when at least one tile exists.
        if (recyclerView.childCount == 0) return

        val adapter = recyclerView.adapter as? com.xhub.browser.shortcuts.ShortcutTileAdapter
        if (adapter != null && adapter.itemCount == 1 && adapter.getItemViewType(0) == com.xhub.browser.shortcuts.ShortcutTileAdapter.VIEW_TYPE_EMPTY) {
            // Guard: double check we are not playing on empty state placeholder
            return
        }

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

    /** Reset the entrance animation state so it plays again on the next pass. */
    fun resetEntrance() {
        entrancePlayed = false
    }

    private companion object {
        const val MAX_ENTRANCE_TILES = 24
        const val ENTRANCE_SCALE_START = 0.92f
        const val ENTRANCE_SLIDE_DP = 8f
        const val ENTRANCE_DURATION_MS = 180L
        const val ENTRANCE_STAGGER_MS = 50L
        val DECELERATE = DecelerateInterpolator()
    }
}
