package com.xhub.browser.browser

/**
 * Pure policy for Chromium [android.webkit.WebChromeClient.onCreateWindow] return values.
 *
 * Returning true without a distinct transport WebView makes Chromium host the popup in the
 * parent and crash with "Parent WebView cannot host its own popup window".
 */
object PopupWindowPolicy {

    /**
     * When popups are enabled, accept only if a new tab was actually created.
     * [newTabCreated] is false when max-tab entitlement rejects the tab.
     */
    fun acceptPopupWhenEnabled(newTabCreated: Boolean): Boolean = newTabCreated

    /**
     * When popups are disabled, reject all popups to block third-party direct ads and popups.
     */
    fun acceptSameTabRedirect(transportAvailable: Boolean): Boolean = false
}
