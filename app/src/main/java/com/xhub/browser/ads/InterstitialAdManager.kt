package com.xhub.browser.ads

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.app.Activity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import timber.log.Timber

class InterstitialAdManager(
    private val activity: Activity,
    private val rootView: CoordinatorLayout,
    private val config: InterstitialAdConfig = InterstitialAdConfig()
) {
    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: FrameLayout? = null
    private var adWebView: WebView? = null
    private var closeButton: ImageButton? = null
    private var isShowing = false

    private var showRunnable: Runnable? = null

    fun showAfterDelay(delayMs: Long) {
        if (isShowing) return
        showRunnable = Runnable { show() }
        handler.postDelayed(showRunnable!!, delayMs)
    }

    fun show() {
        if (isShowing) return
        isShowing = true

        val overlay = FrameLayout(activity).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val webView = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                loadWithOverviewMode = true
                useWideViewPort = true
                displayZoomControls = false
                builtInZoomControls = false
                setSupportZoom(false)
            }
            loadDataWithBaseURL(
                "https://exoclick.com",
                buildAdHtml(),
                "text/html",
                "UTF-8",
                null
            )
        }
        overlay.addView(webView)
        adWebView = webView

        val btn = ImageButton(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                120, 120
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 48
                rightMargin = 24
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel)
            visibility = View.GONE
            setOnClickListener { dismiss() }
        }
        overlay.addView(btn)
        closeButton = btn

        rootView.addView(overlay)
        overlayView = overlay

        // Schedule close button reveal
        handler.postDelayed({
            closeButton?.visibility = View.VISIBLE
        }, config.closeButtonDelayMs)

        // Schedule auto-dismiss
        handler.postDelayed({
            dismiss()
        }, config.autoDismissMs)

        Timber.i("ExoClick interstitial shown")
    }

    fun dismiss() {
        if (!isShowing) return
        isShowing = false

        cancelPendingCallbacks()
        overlayView?.let { rootView.removeView(it) }
        adWebView?.destroy()
        adWebView = null
        closeButton = null
        overlayView = null
        Timber.i("ExoClick interstitial dismissed")
    }

    fun onPause() {
        adWebView?.onPause()
    }

    fun onResume() {
        adWebView?.onResume()
    }

    fun onDestroy() {
        cancelPendingCallbacks()
        dismiss()
    }

    fun onBackPressed(): Boolean {
        if (isShowing) {
            dismiss()
            return true
        }
        return false
    }

    // ── internal ─────────────────────────────────────────────────

    private fun cancelPendingCallbacks() {
        showRunnable?.let { handler.removeCallbacks(it) }
        showRunnable = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun buildAdHtml(): String = """
        <html>
        <body style="margin:0;overflow:hidden;background:#000;width:100vw;height:100vh;">
        <script async src="${config.adProviderUrl}"></script>
        <ins class="eas6a97888e33" data-zoneid="${config.zoneId}"></ins>
        <script>(AdProvider=window.AdProvider||[]).push({"serve":{}});</script>
        </body>
        </html>
    """.trimIndent()
}
