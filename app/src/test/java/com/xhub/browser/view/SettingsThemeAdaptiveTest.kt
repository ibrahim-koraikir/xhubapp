package com.xhub.browser.view

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import com.xhub.browser.R
import com.xhub.browser.SDK_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Proves the settings screen is theme-adaptive: inflates the key settings layouts under
 * each theme and asserts they inflate without error (no unresolved ?attr refs).
 * Also verifies the category color resources exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = com.xhub.browser.TestApplication::class, sdk = [SDK_VERSION])
class SettingsThemeAdaptiveTest {

    @Test
    fun `settings menu item inflates under all four themes`() {
        val themes = listOf(
            R.style.Theme_App_Light, R.style.Theme_App_White,
            R.style.Theme_App_Dark, R.style.Theme_App_Black
        )
        for (styleRes in themes) {
            val ctx = ContextThemeWrapper(RuntimeEnvironment.getApplication(), styleRes)
            val inflater = LayoutInflater.from(ctx)
            val view = inflater.inflate(R.layout.item_settings_menu, null, false)
            assertThat(view).isNotNull()
        }
    }

    @Test
    fun `category color resources exist`() {
        val app = RuntimeEnvironment.getApplication()
        val cats = listOf(
            "app_cat_appearance", "app_cat_browser", "app_cat_privacy", "app_cat_domains",
            "app_cat_adblock", "app_cat_extensions", "app_cat_backup",
            "app_cat_contribute", "app_cat_about"
        )
        for (name in cats) {
            val id = app.resources.getIdentifier(name, "color", app.packageName)
            assertThat(id).withFailMessage("category color $name not declared").isNotEqualTo(0)
        }
    }
}
