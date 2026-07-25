package com.xhub.browser.ui.message

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.xhub.browser.R
import com.xhub.browser.extensions.KDuration
import com.xhub.browser.extensions.setIcon

/**
 * Unified high-contrast feedback banners.
 *
 * Uses Material [Snackbar] text APIs (not a custom child view) so the message is always
 * readable: light text on a dark charcoal pill, with a style-tinted icon.
 */
object XHubMessage {

    /** Dark charcoal banner — works over light and dark browser chrome. */
    private const val BANNER_BG = 0xF01C1C1E.toInt()
    private const val BANNER_TEXT = 0xFFFFFFFF.toInt()
    private const val BANNER_TEXT_SECONDARY = 0xFFE8E8ED.toInt()

    fun show(
        activity: Activity,
        text: CharSequence,
        style: MessageStyle = MessageStyle.Info,
        durationMs: Int = KDuration,
        gravity: Int = Gravity.BOTTOM,
        actionLabel: CharSequence? = null,
        action: (() -> Unit)? = null
    ) {
        val anchor = activity.findViewById<View>(R.id.web_view_frame)
            ?: activity.findViewById(android.R.id.content)
            ?: return

        val snackbar = Snackbar.make(anchor, text, durationMs.coerceAtLeast(Snackbar.LENGTH_SHORT))
        snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE
        snackbar.setBackgroundTint(BANNER_BG)
        snackbar.setTextColor(BANNER_TEXT)
        snackbar.setActionTextColor(accentFor(style))

        val icon = ContextCompat.getDrawable(activity, style.iconRes)?.mutate()
        if (icon != null) {
            DrawableCompat.setTint(icon, accentFor(style))
            snackbar.setIcon(icon)
        }

        if (actionLabel != null && action != null) {
            snackbar.setAction(actionLabel) { action.invoke() }
        }

        if (anchor.id == R.id.web_view_frame) {
            val params = snackbar.view.layoutParams as? CoordinatorLayout.LayoutParams
            if (params != null) {
                params.gravity = gravity
                val margin = dp(activity, 12f)
                params.setMargins(margin, margin, margin, margin)
                snackbar.view.layoutParams = params
            }
        }

        snackbar.view.elevation = dp(activity, 8f).toFloat()
        snackbar.show()
    }

    fun show(
        activity: Activity,
        @StringRes textRes: Int,
        style: MessageStyle = MessageStyle.Info,
        durationMs: Int = KDuration,
        gravity: Int = Gravity.BOTTOM,
        actionLabel: CharSequence? = null,
        action: (() -> Unit)? = null
    ) {
        show(activity, activity.getText(textRes), style, durationMs, gravity, actionLabel, action)
    }

    fun showToast(
        context: Context,
        text: CharSequence,
        style: MessageStyle = MessageStyle.Info
    ) {
        // Prefer plain Toast with system styling for reliability on all API levels when no Activity.
        // Prefix style emoji-free so text is always visible (no custom Toast view on API 30+).
        val prefix = when (style) {
            MessageStyle.Success -> "✓ "
            MessageStyle.Warning -> "! "
            MessageStyle.Error -> "✕ "
            MessageStyle.Info -> ""
        }
        Toast.makeText(context.applicationContext, prefix + text, Toast.LENGTH_SHORT).show()
    }

    fun showToast(context: Context, @StringRes textRes: Int, style: MessageStyle = MessageStyle.Info) {
        showToast(context, context.getText(textRes), style)
    }

    private fun accentFor(style: MessageStyle): Int = when (style) {
        MessageStyle.Success -> 0xFF34C759.toInt() // system green
        MessageStyle.Warning -> 0xFFFF9F0A.toInt() // amber
        MessageStyle.Error -> 0xFFFF453A.toInt()   // red
        MessageStyle.Info -> 0xFF0A84FF.toInt()    // blue
    }

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
}
