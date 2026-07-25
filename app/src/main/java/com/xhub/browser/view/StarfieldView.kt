package com.xhub.browser.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.sin

/**
 * Animated starfield background.
 *
 * Renders 80 twinkling white star-dots (30 if system animations are disabled) using
 * Android Canvas + a Choreographer frame loop throttled to ~30 fps (every 2nd frame),
 * since the gentle organic-pulse effect does not need full 60 fps.
 *
 * Design mirrors the web implementation:
 *   - Each star has random x/y position, radius (0.5–2 dp), base alpha, and flicker speed
 *   - Brightness = baseAlpha × (0.5 + 0.5 × sin(t × speed + x))  → gentle organic pulse
 *   - Respects ANIMATOR_DURATION_SCALE == 0 (accessibility "reduce motion"):
 *     draws once, stops loop, uses 30 stars instead of 80
 *
 * Battery discipline:
 *   - The Choreographer loop is started only when the window is VISIBLE and stopped the
 *     moment it becomes invisible (app backgrounded, covered, etc.). This is achieved by
 *     overriding [onWindowVisibilityChanged] in addition to the existing [onVisibilityChanged]
 *     / [onAttachedToWindow] / [onDetachedFromWindow] hooks.
 *   - The reduce-motion preference is re-evaluated in [startAnimation] (not cached in a
 *     constructor val) so that system-setting changes made while the app runs are honored.
 *
 * Usage: drop into any layout behind content; set background on the parent instead of here.
 */
class StarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // ── Star data ─────────────────────────────────────────────────────────────

    private data class Star(
        val x: Float,
        val y: Float,
        val r: Float,     // radius px
        val a: Float,     // base alpha  0.2–1.0
        val speed: Float, // flicker frequency
    )

    private var stars: List<Star> = emptyList()

    // ── Paint ─────────────────────────────────────────────────────────────────

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    // ── Reduce-motion detection ───────────────────────────────────────────────
    // Re-evaluated each time startAnimation() is called instead of caching in a
    // constructor val, so changes to the developer setting are honored at runtime.

    private fun prefersReducedMotion(): Boolean = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (_: Exception) {
        false
    }

    // ── Animation loop (Choreographer) ────────────────────────────────────────
    // Throttled to every 2nd frame (~30 fps). The twinkle is a slow organic pulse;
    // 30 fps is indistinguishable from 60 fps for this effect while halving GPU wakes.

    private var animating = false
    private var frameCounter = 0

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback {
        if (animating) {
            // Skip every other frame → ~30 fps on a 60 Hz display
            if (frameCounter++ % 2 == 0) invalidate()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) buildStars(w, h)
    }

    private fun buildStars(w: Int, h: Int) {
        val reduced = prefersReducedMotion()
        val count = if (reduced) 30 else 80
        stars = List(count) {
            Star(
                x = (Math.random() * w).toFloat(),
                y = (Math.random() * h).toFloat(),
                r = (Math.random() * 1.5 + 0.5).toFloat(),
                a = (Math.random() * 0.8 + 0.2).toFloat(),
                speed = (Math.random() * 0.005 + 0.002).toFloat(),
            )
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val reduced = prefersReducedMotion()
        val t = SystemClock.uptimeMillis()
        for (star in stars) {
            val flicker = if (reduced) 1f
            else (0.5f + 0.5f * sin((t * star.speed + star.x).toDouble())).toFloat()
            paint.alpha = (star.a * flicker * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(star.x, star.y, star.r, paint)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Only start if the window is already visible (avoids spurious starts during
        // a transition where the window is attached but not yet shown).
        if (windowVisibility == VISIBLE) startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    /**
     * Halt/resume the frame loop based on window visibility.
     * This is the primary battery-saver hook: the loop stops the moment the app goes
     * to the background (windowVisibility != VISIBLE) so no frames are produced while
     * the user is not looking at the screen.
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startAnimation() else stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimation() else stopAnimation()
    }

    private fun startAnimation() {
        if (prefersReducedMotion()) {
            invalidate() // draw once, then stop
            return
        }
        if (!animating) {
            animating = true
            frameCounter = 0
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun stopAnimation() {
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }
}
