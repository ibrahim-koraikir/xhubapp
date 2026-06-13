package com.xhub.browser.adblock

import com.xhub.browser.settings.preferences.IntEnum

/**
 * An enum representing the browser's available rendering modes.
 */
enum class AbpUpdateMode(override val value: Int) :
    IntEnum {
    ALWAYS(0),
    NONE(1),
    WIFI_ONLY(2)
}
