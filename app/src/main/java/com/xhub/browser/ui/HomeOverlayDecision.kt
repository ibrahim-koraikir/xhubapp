package com.xhub.browser.ui

import com.xhub.browser.utils.isBookmarkUri
import com.xhub.browser.utils.isBookmarkUrl
import com.xhub.browser.utils.isHomeUri
import com.xhub.browser.utils.isStartPageUrl

/**
 * Pure decision helpers for the home-screen overlay state machine in [com.xhub.browser.activity.WebBrowserActivity].
 *
 * Keeps transition rules (skip / duration / home URL detection) out of the activity so they stay
 * unit-testable without inflating the full browser chrome.
 */
object HomeOverlayDecision {

    const val TRANSITION_DURATION_MS = 300L

    /**
     * Whether the overlay is already animating toward (or sitting at) [isHome].
     * When true, [com.xhub.browser.activity.WebBrowserActivity.updateHomeScreenOverlay] should no-op.
     */
    fun shouldSkipTransition(targetState: Boolean?, isHome: Boolean): Boolean =
        targetState == isHome

    /**
     * Fade duration for a home overlay transition.
     * First paint ([wasShowing] null) snaps with no animation; later transitions animate.
     */
    fun animationDurationMs(wasShowing: Boolean?): Long =
        if (wasShowing == null) 0L else TRANSITION_DURATION_MS

    /**
     * True when [url] should show the native home overlay (home, start page, or bookmarks).
     * Matches the predicate historically inlined in WebBrowserActivity.
     */
    fun isHomeScreenUrl(url: String?): Boolean =
        url.isHomeUri() || url.isStartPageUrl() || url.isBookmarkUri() || url.isBookmarkUrl()
}
