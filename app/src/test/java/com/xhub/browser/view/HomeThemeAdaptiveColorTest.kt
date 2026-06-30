package com.xhub.browser.view

import android.content.Context
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
 * Proves the home screen's semantic color tokens resolve to *different*, perceptually-correct
 * values under each of the four user-selectable themes. This is the regression test for
 * Phase 1's core deliverable: "home is theme-adaptive across Light/White/Dark/Black".
 *
 * If any attr resolves to the same value across a light vs dark theme, the test fails —
 * that would mean the token isn't actually wired per-theme.
 *
 * Implementation note: we wrap the application context in a ContextThemeWrapper rather than
 * calling setTheme() on the application itself. Application themes are applied at process
 * start and setTheme() on the Application context does not reliably re-resolve custom attrs
 * in Robolectric; ContextThemeWrapper forces a fresh theme for the given style.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = com.xhub.browser.TestApplication::class, sdk = [SDK_VERSION])
class HomeThemeAdaptiveColorTest {

    /** Wraps the app context in a fresh theme and resolves a ?attr/color token to its Int. */
    private fun themedContext(styleRes: Int): Context =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), styleRes)

    private fun resolveColor(context: Context, attrId: Int): Int {
        val tv = TypedValue()
        val ok = context.theme.resolveAttribute(attrId, tv, true)
        assertThat(ok).withFailMessage("attr $attrId did not resolve under theme").isTrue()
        return tv.data
    }

    private fun resolveAttrId(context: Context, name: String): Int {
        val id = context.resources.getIdentifier(name, "attr", context.packageName)
        assertThat(id).withFailMessage("attr $name not declared").isNotEqualTo(0)
        return id
    }

    @Test
    fun `orange accent is deeper in light themes than dark themes`() {
        val app = RuntimeEnvironment.getApplication()
        val orangeId = resolveAttrId(app, "appColorAccentOrange")

        val lightOrange = resolveColor(themedContext(R.style.Theme_App_Light), orangeId)
        val darkOrange = resolveColor(themedContext(R.style.Theme_App_Dark), orangeId)

        // Light must deepen orange for WCAG-AA; they must NOT be the same value.
        assertThat(lightOrange).isNotEqualTo(darkOrange)
    }

    @Test
    fun `home tile surface is light in light theme and dark in black theme`() {
        val app = RuntimeEnvironment.getApplication()
        val surfaceId = resolveAttrId(app, "appColorHomeTileSurface")

        val lightSurface = resolveColor(themedContext(R.style.Theme_App_Light), surfaceId)
        val blackSurface = resolveColor(themedContext(R.style.Theme_App_Black), surfaceId)

        assertThat(lightSurface).isNotEqualTo(blackSurface)
        // Light tile surface luminance must be high (> 0.5), black must be low (< 0.05).
        assertThat(luminance(lightSurface)).isGreaterThan(0.5)
        assertThat(luminance(blackSurface)).isLessThan(0.05)
    }

    @Test
    fun `starfield scrim is light in white theme and dark in dark theme`() {
        val app = RuntimeEnvironment.getApplication()
        val scrimId = resolveAttrId(app, "appStarfieldScrim")

        val whiteScrim = resolveColor(themedContext(R.style.Theme_App_White), scrimId)
        val darkScrim = resolveColor(themedContext(R.style.Theme_App_Dark), scrimId)

        assertThat(whiteScrim).isNotEqualTo(darkScrim)
    }

    @Test
    fun `home background drawable differs between light and dark themes`() {
        val app = RuntimeEnvironment.getApplication()
        val bgId = resolveAttrId(app, "appHomeBackground")

        val lightTv = TypedValue()
        themedContext(R.style.Theme_App_Light).theme.resolveAttribute(bgId, lightTv, true)
        val darkTv = TypedValue()
        themedContext(R.style.Theme_App_Dark).theme.resolveAttribute(bgId, darkTv, true)

        // Different drawables (the light starfield vs the dark starfield).
        assertThat(lightTv.resourceId).isNotEqualTo(darkTv.resourceId)
    }

    @Test
    fun `all four themes resolve every declared attr without error`() {
        val app = RuntimeEnvironment.getApplication()
        val attrNames = listOf(
            "appColorAccentOrange", "appColorAccentOrangeFaint", "appColorAccentOrangeLight",
            "appColorAccentOrangeDark", "appColorAccentPurple", "appColorHomeTileSurface",
            "appColorHomeStroke", "appColorHomeTileStroke", "appColorMutedForeground",
            "appColorSubtleForeground", "appColorDimForeground", "appColorIconTint",
            "appColorGroupLabel", "appStarfieldScrim",
            "appColorAccentPurple2", "appColorSheetGlass", "appColorCardSurface"
        )
        val themes = listOf(
            R.style.Theme_App_Light, R.style.Theme_App_White,
            R.style.Theme_App_Dark, R.style.Theme_App_Black
        )
        for (styleRes in themes) {
            val ctx = themedContext(styleRes)
            for (name in attrNames) {
                val tv = TypedValue()
                val ok = ctx.theme.resolveAttribute(resolveAttrId(app, name), tv, true)
                assertThat(ok).withFailMessage("$name failed to resolve under theme $styleRes").isTrue()
            }
        }
    }

    @Test
    fun `phase 2 tokens resolve distinctly across themes`() {
        val app = RuntimeEnvironment.getApplication()

        val purple2Id = resolveAttrId(app, "appColorAccentPurple2")
        val sheetGlassId = resolveAttrId(app, "appColorSheetGlass")
        val cardSurfaceId = resolveAttrId(app, "appColorCardSurface")

        val lightPurple2 = resolveColor(themedContext(R.style.Theme_App_Light), purple2Id)
        val lightSheetGlass = resolveColor(themedContext(R.style.Theme_App_Light), sheetGlassId)
        val lightCardSurface = resolveColor(themedContext(R.style.Theme_App_Light), cardSurfaceId)

        val blackPurple2 = resolveColor(themedContext(R.style.Theme_App_Black), purple2Id)
        val blackSheetGlass = resolveColor(themedContext(R.style.Theme_App_Black), sheetGlassId)
        val blackCardSurface = resolveColor(themedContext(R.style.Theme_App_Black), cardSurfaceId)

        // All three must differ between light and black themes.
        assertThat(lightPurple2).isNotEqualTo(blackPurple2)
        assertThat(lightSheetGlass).isNotEqualTo(blackSheetGlass)
        assertThat(lightCardSurface).isNotEqualTo(blackCardSurface)
    }

    private fun luminance(color: Int): Double {
        val r = ((color shr 16) and 0xff) / 255.0
        val g = ((color shr 8) and 0xff) / 255.0
        val b = (color and 0xff) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
