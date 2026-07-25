package com.xhub.browser.ui

import com.xhub.browser.constant.FILE
import com.xhub.browser.constant.Uris
import com.xhub.browser.html.bookmark.BookmarkPageFactory
import com.xhub.browser.html.homepage.HomePageFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Pure decision logic for the home-screen overlay transition state machine.
 * Extracted so WebBrowserActivity does not re-encode the rules in multiple places.
 */
class HomeOverlayDecisionTest {

    @Test
    fun `shouldSkipTransition when target already matches desired state`() {
        assertThat(HomeOverlayDecision.shouldSkipTransition(targetState = true, isHome = true)).isTrue()
        assertThat(HomeOverlayDecision.shouldSkipTransition(targetState = false, isHome = false)).isTrue()
    }

    @Test
    fun `should not skip when state differs or target is unknown`() {
        assertThat(HomeOverlayDecision.shouldSkipTransition(targetState = true, isHome = false)).isFalse()
        assertThat(HomeOverlayDecision.shouldSkipTransition(targetState = false, isHome = true)).isFalse()
        assertThat(HomeOverlayDecision.shouldSkipTransition(targetState = null, isHome = true)).isFalse()
        assertThat(HomeOverlayDecision.shouldSkipTransition(targetState = null, isHome = false)).isFalse()
    }

    @Test
    fun `animationDuration is zero on first paint and 300ms on later transitions`() {
        assertThat(HomeOverlayDecision.animationDurationMs(wasShowing = null)).isEqualTo(0L)
        assertThat(HomeOverlayDecision.animationDurationMs(wasShowing = true)).isEqualTo(300L)
        assertThat(HomeOverlayDecision.animationDurationMs(wasShowing = false)).isEqualTo(300L)
    }

    @Test
    fun `isHomeScreenUrl matches home start and bookmark special pages`() {
        assertThat(HomeOverlayDecision.isHomeScreenUrl(Uris.FulgurisHome)).isTrue()
        assertThat(HomeOverlayDecision.isHomeScreenUrl(Uris.AboutHome)).isTrue()
        assertThat(HomeOverlayDecision.isHomeScreenUrl(Uris.FulgurisBookmarks)).isTrue()
        assertThat(HomeOverlayDecision.isHomeScreenUrl(Uris.AboutBookmarks)).isTrue()
        assertThat(HomeOverlayDecision.isHomeScreenUrl(FILE + "/data/user/0/app/files/" + HomePageFactory.FILENAME)).isTrue()
        assertThat(HomeOverlayDecision.isHomeScreenUrl(FILE + "/data/user/0/app/files/" + BookmarkPageFactory.FILENAME)).isTrue()
        assertThat(HomeOverlayDecision.isHomeScreenUrl("https://example.com")).isFalse()
        assertThat(HomeOverlayDecision.isHomeScreenUrl("")).isFalse()
        assertThat(HomeOverlayDecision.isHomeScreenUrl(null)).isFalse()
    }
}
