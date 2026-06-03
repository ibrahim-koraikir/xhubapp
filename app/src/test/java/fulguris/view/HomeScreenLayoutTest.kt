package fulguris.view

import android.view.LayoutInflater
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import fulguris.R
import fulguris.SDK_VERSION
import fulguris.TestApplication
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
        
        // Background and overlay views should be removed (their resource IDs should either not exist or return null)
        val bgId = context.resources.getIdentifier("homeScreenBackground", "id", context.packageName)
        if (bgId != 0) {
            assertThat(view.findViewById<android.view.View>(bgId)).isNull()
        }
        val overlayId = context.resources.getIdentifier("homeScreenBackgroundOverlay", "id", context.packageName)
        if (overlayId != 0) {
            assertThat(view.findViewById<android.view.View>(overlayId)).isNull()
        }

        // Critical search card and shortcut container views must exist
        assertThat(view.findViewById<android.view.View>(R.id.homeSearchCard)).isNotNull()
        assertThat(view.findViewById<android.view.View>(R.id.shortcutsDynamicContainer)).isNotNull()
        assertThat(view.findViewById<TextView>(R.id.homeTitle)).isNotNull()
    }
}
