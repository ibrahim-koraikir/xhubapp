package com.xhub.browser.extensions

import android.app.Activity
import android.view.View
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class DialogWindowTypeTest {

    private fun shownBottomSheetDialog(): BottomSheetDialog {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val dialog = BottomSheetDialog(activity)
        dialog.show()
        return dialog
    }

    @Test
    fun `dialog window is application attached`() {
        val dialog = shownBottomSheetDialog()

        dialog.showBelowSessionPopup(darkStatusIcons = true)

        assertEquals(
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
            dialog.window?.attributes?.type
        )
    }

    @Test
    fun `dark status icons are requested for light backgrounds`() {
        val dialog = shownBottomSheetDialog()

        dialog.showBelowSessionPopup(darkStatusIcons = true)

        val flags = dialog.window?.decorView?.systemUiVisibility ?: 0
        assertTrue(flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0)
    }

    @Test
    fun `light status icons are requested for dark backgrounds`() {
        val dialog = shownBottomSheetDialog()

        dialog.showBelowSessionPopup(darkStatusIcons = false)

        val flags = dialog.window?.decorView?.systemUiVisibility ?: 0
        assertEquals(0, flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
    }
}
