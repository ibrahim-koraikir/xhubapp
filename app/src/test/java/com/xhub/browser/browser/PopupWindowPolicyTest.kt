package com.xhub.browser.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PopupWindowPolicyTest {

    @Test
    fun `popups enabled requires successful new tab`() {
        assertThat(PopupWindowPolicy.acceptPopupWhenEnabled(newTabCreated = true)).isTrue()
        assertThat(PopupWindowPolicy.acceptPopupWhenEnabled(newTabCreated = false)).isFalse()
    }

    @Test
    fun `popups disabled rejects all popup redirects`() {
        assertThat(PopupWindowPolicy.acceptSameTabRedirect(transportAvailable = true)).isFalse()
        assertThat(PopupWindowPolicy.acceptSameTabRedirect(transportAvailable = false)).isFalse()
    }
}
