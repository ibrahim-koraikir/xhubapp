package com.xhub.browser.ui.message

import android.view.Gravity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class MessageGravityTest {

    @Test
    fun `toolbars at bottom use top gravity so banner is not covered`() {
        assertThat(MessageGravity.forToolbarsBottom(toolbarsBottom = true)).isEqualTo(Gravity.TOP)
    }

    @Test
    fun `toolbars at top use bottom gravity`() {
        assertThat(MessageGravity.forToolbarsBottom(toolbarsBottom = false)).isEqualTo(Gravity.BOTTOM)
    }
}
