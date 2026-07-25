package com.xhub.browser.ui.message

import androidx.annotation.DrawableRes
import com.xhub.browser.R

/**
 * Visual tone for [XHubMessage] banners / themed toasts.
 */
enum class MessageStyle(
    @DrawableRes val iconRes: Int
) {
    Info(R.drawable.ic_info),
    Success(R.drawable.ic_check),
    Warning(R.drawable.ic_warning_outline),
    Error(R.drawable.ic_error_outline)
}

/**
 * Gravity for Activity-hosted messages relative to toolbar placement.
 */
object MessageGravity {
    /**
     * When toolbars sit at the bottom, show messages at the top so they are not covered.
     * Mirrors the existing snackbar convention in [com.xhub.browser.activity.WebBrowserActivity].
     */
    fun forToolbarsBottom(toolbarsBottom: Boolean): Int =
        if (toolbarsBottom) android.view.Gravity.TOP else android.view.Gravity.BOTTOM
}
