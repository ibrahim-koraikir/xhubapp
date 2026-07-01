package com.xhub.browser.view

import android.util.TypedValue
import android.view.ContextThemeWrapper
import com.xhub.browser.R
import com.xhub.browser.SDK_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Proves the tab-grid's new theme tokens resolve correctly under each theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = com.xhub.browser.TestApplication::class, sdk = [SDK_VERSION])
class TabsThemeAdaptiveTest {

    private fun resolveColor(styleRes: Int, attrName: String): Int {
        val app = RuntimeEnvironment.getApplication()
        val ctx = ContextThemeWrapper(app, styleRes)
        val attrId = app.resources.getIdentifier(attrName, "attr", app.packageName)
        assertThat(attrId).withFailMessage("attr $attrName not declared").isNotEqualTo(0)
        val tv = TypedValue()
        assertThat(ctx.theme.resolveAttribute(attrId, tv, true))
            .withFailMessage("$attrName did not resolve").isTrue()
        return tv.data
    }

    @Test
    fun `purple2 and sheet glass and card surface resolve distinctly per theme`() {
        val attrs = listOf("appColorAccentPurple2", "appColorSheetGlass", "appColorCardSurface")
        for (attr in attrs) {
            val light = resolveColor(R.style.Theme_App_Light, attr)
            val black = resolveColor(R.style.Theme_App_Black, attr)
            assertThat(light).withFailMessage("$attr same in light+black").isNotEqualTo(black)
        }
    }
}
