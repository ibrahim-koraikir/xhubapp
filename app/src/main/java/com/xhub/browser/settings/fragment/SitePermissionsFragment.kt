/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package com.xhub.browser.settings.fragment

import com.xhub.browser.R
import com.xhub.browser.extensions.launch
import com.xhub.browser.extensions.snackbar
import android.app.Activity
import android.os.Bundle
import android.webkit.GeolocationPermissions
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Settings screen listing all sites that have been granted location (geolocation) access.
 * Users can view and revoke access on a per-site basis, or clear all at once.
 */
@AndroidEntryPoint
class SitePermissionsFragment : AbstractSettingsFragment() {

    private var grantedOriginCount = 0

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.site_permissions
    }

    override fun providePreferencesXmlResource() = R.xml.preferences_site_permissions

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        clickablePreference(
            preference = SETTINGS_CLEAR_ALL,
            onClick = {
                confirmClearAll()
                true
            }
        ).apply { isEnabled = false }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list whenever the user returns to this screen
        loadPermissions()
    }

    /**
     * Scans WebView's geolocation database and lists every origin with granted location access.
     */
    private fun loadPermissions() {
        val category = findPreference<PreferenceCategory>(SETTINGS_CATEGORY) ?: return
        category.removeAll()
        grantedOriginCount = 0

        GeolocationPermissions.getInstance().getOrigins { origins: Set<String>? ->
            val originList = origins?.toList() ?: emptyList()
            if (originList.isEmpty()) {
                addEmptyMessage(category)
                setClearAllEnabled()
                return@getOrigins
            }

            // getOrigins() includes denied origins, so check each one individually
            val grantedOrigins = mutableListOf<String>()
            var remaining = originList.size
            originList.forEach { origin ->
                GeolocationPermissions.getInstance().getAllowed(origin) { allowed ->
                    if (allowed == true) {
                        grantedOrigins.add(origin)
                    }
                    remaining--
                    if (remaining == 0) {
                        populateCategory(category, grantedOrigins)
                    }
                }
            }
        }
    }

    private fun populateCategory(category: PreferenceCategory, origins: List<String>) {
        grantedOriginCount = origins.size
        origins.sorted().forEach { origin ->
            category.addPreference(Preference(requireContext()).apply {
                title = displayName(origin)
                summary = getString(R.string.tap_to_revoke)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    revokePermission(origin)
                    true
                }
            })
        }
        if (origins.isEmpty()) {
            addEmptyMessage(category)
        }
        setClearAllEnabled()
    }

    /**
     * Shows a domain-friendly label for a geolocation origin
     * (e.g. "https://example.com" -> "example.com").
     */
    private fun displayName(origin: String): String {
        return try {
            java.net.URI(origin).host ?: origin.removePrefix("https://").removePrefix("http://")
        } catch (e: Exception) {
            origin
        }
    }

    private fun addEmptyMessage(category: PreferenceCategory) {
        category.addPreference(Preference(requireContext()).apply {
            title = getString(R.string.no_site_permissions)
            isEnabled = false
            isSelectable = false
            isIconSpaceReserved = false
        })
    }

    private fun setClearAllEnabled() {
        findPreference<Preference>(SETTINGS_CLEAR_ALL)?.isEnabled = grantedOriginCount > 0
    }

    /**
     * Revokes location access for a single origin and refreshes the list.
     */
    private fun revokePermission(origin: String) {
        GeolocationPermissions.getInstance().clear(origin)
        Timber.i("Revoked geolocation permission for: $origin")
        (activity as? Activity)?.snackbar(getString(R.string.permission_revoked, displayName(origin)))
        loadPermissions()
    }

    /**
     * Asks for confirmation before revoking location access for all sites.
     */
    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setCancelable(true)
            .setTitle(R.string.question_clear_all_site_permissions)
            .setIcon(R.drawable.ic_delete_outline)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_reset) { _, _ ->
                val count = grantedOriginCount
                GeolocationPermissions.getInstance().clearAll()
                Timber.i("Cleared geolocation permissions for $count origins")
                (activity as? Activity)?.snackbar(getString(R.string.all_permissions_cleared, count))
                loadPermissions()
            }
            .launch()
    }

    companion object {
        private const val SETTINGS_CLEAR_ALL = "clear_all_site_permissions"
        private const val SETTINGS_CATEGORY = "category_site_permissions"
    }
}
