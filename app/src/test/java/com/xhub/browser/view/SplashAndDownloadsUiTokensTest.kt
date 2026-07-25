package com.xhub.browser.view

import android.graphics.Color
import com.xhub.browser.R
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression: cold-start splash must not use Android default green, and downloads sheet list
 * must open tall enough to be usable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class SplashAndDownloadsUiTokensTest {

    @Test
    fun `splash background is dark theme surface not android green`() {
        val app = RuntimeEnvironment.getApplication()
        val splashBg = app.getColor(R.color.md_theme_dark_background)
        // #0A0A0A
        assertThat(splashBg).isEqualTo(Color.parseColor("#0A0A0A"))
        // Must never regress to Android adaptive-icon green used by debug templates.
        assertThat(splashBg).isNotEqualTo(Color.parseColor("#3DDC84"))
    }

    @Test
    fun `launcher background is brand blue not android green`() {
        val app = RuntimeEnvironment.getApplication()
        val launcherBg = app.getColor(R.color.ic_launcher_background)
        assertThat(launcherBg).isEqualTo(Color.parseColor("#2563EB"))
        assertThat(launcherBg).isNotEqualTo(Color.parseColor("#3DDC84"))
    }

    @Test
    fun `downloads list min height is expanded for usable sheet`() {
        val app = RuntimeEnvironment.getApplication()
        val minPx = app.resources.getDimensionPixelSize(R.dimen.downloads_list_min_height)
        val maxPx = app.resources.getDimensionPixelSize(R.dimen.downloads_list_max_height)
        // 360dp min — well above the old 120dp strip that made the sheet feel tiny.
        val minDp = minPx / app.resources.displayMetrics.density
        val maxDp = maxPx / app.resources.displayMetrics.density
        assertThat(minDp).isGreaterThanOrEqualTo(320f)
        assertThat(maxDp).isGreaterThanOrEqualTo(minDp)
    }
}
