package com.xhub.browser.settings.preferences.delegates

import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.xhub.browser.app
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A [Long] delegate that is backed by [SharedPreferences].
 */
private class LongPreferenceDelegate(
    private val name: String,
    private val defaultValue: Long,
    private val preferences: SharedPreferences
) : ReadWriteProperty<Any, Long> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Long =
        preferences.getLong(name, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) {
        preferences.edit().putLong(name, value).apply()
    }
}

/**
 * Creates a [Long] from [SharedPreferences] with the provided arguments.
 */
fun SharedPreferences.longPreference(
    name: String,
    defaultValue: Long = 0L
): ReadWriteProperty<Any, Long> = LongPreferenceDelegate(name, defaultValue, this)

/**
 * Creates a [Long] from [SharedPreferences] with the provided arguments.
 */
fun SharedPreferences.longPreference(
    @StringRes stringRes: Int,
    defaultValue: Long = 0L
): ReadWriteProperty<Any, Long> = LongPreferenceDelegate(app.resources.getString(stringRes), defaultValue, this)
