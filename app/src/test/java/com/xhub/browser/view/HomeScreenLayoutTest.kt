package com.xhub.browser.view

import android.view.LayoutInflater
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.xhub.browser.R
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class HomeScreenLayoutTest {

    @Test
    fun `home screen layout inflates successfully and contains correct views`() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_App_Black)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_home_screen, null) as CoordinatorLayout

        assertThat(view).isNotNull()
        
        // Background and overlay views must exist (starfield theme)
        val bgId = context.resources.getIdentifier("homeScreenBackground", "id", context.packageName)
        assertThat(bgId).isNotEqualTo(0)
        assertThat(view.findViewById<android.view.View>(bgId)).isNotNull()

        val overlayId = context.resources.getIdentifier("homeScreenBackgroundOverlay", "id", context.packageName)
        assertThat(overlayId).isNotEqualTo(0)
        assertThat(view.findViewById<android.view.View>(overlayId)).isNotNull()

        // Critical shortcut container and title views must exist
        assertThat(view.findViewById<android.view.View>(R.id.shortcutsDynamicContainer)).isNotNull()
        assertThat(view.findViewById<TextView>(R.id.homeTitle)).isNotNull()
    }
}
