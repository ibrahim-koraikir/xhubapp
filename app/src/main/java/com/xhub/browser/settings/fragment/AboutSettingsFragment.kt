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

import com.xhub.browser.utils.UpdateChecker

import com.xhub.browser.R
import com.xhub.browser.BuildConfig
import android.os.Bundle
import androidx.preference.Preference
import androidx.webkit.WebViewCompat
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import android.net.Uri
import android.os.Build
import timber.log.Timber
import com.xhub.browser.extensions.ihs

/**
 * About settings page.
 *
 * This fragment displays app version information, WebView details, and provides update checking
 * functionality for download variants.
 *
 * **Lifecycle and Update Checking:**
 * - Update checks are performed in `onResume()` rather than `onCreatePreferences()` to ensure
 *   they only run when the fragment is actually visible to the user
 * - During configuration changes (e.g., screen rotation), Android may create intermediate fragment
 *   instances that never reach the resumed state. By checking in `onResume()`, we avoid wasting
 *   network requests on these temporary fragments
 * - A `hasCheckedForUpdates` flag ensures each fragment instance only checks for updates once,
 *   even if it goes through multiple pause/resume cycles
 * - Lifecycle method overrides are declared in the order they are called by the Android framework
 *   (onAttach → onCreate → onCreatePreferences → onStart → onResume → onPause → onStop → onDestroyView → onDestroy → onDetach)
 *
 * **Why Multiple Fragments During Rotation:**
 * - This fragment is displayed within a `ResponsiveSettingsFragment` that uses `SlidingPaneLayout`
 *   for responsive master-detail UI (different layouts for portrait vs landscape)
 * - During configuration changes, the `SlidingPaneLayout` must recalculate which panes are visible
 *   based on the new screen dimensions and orientation
 * - Android's fragment state restoration creates fragments from the saved backstack, but the
 *   `SlidingPaneLayout` may temporarily create additional fragment instances while determining
 *   the correct layout configuration (single-pane vs dual-pane mode)
 * - These intermediate fragments are created (onAttach → onCreate → onCreatePreferences) but
 *   destroyed immediately (onDestroyView → onDestroy → onDetach) without ever reaching onStart/onResume
 * - Only the final fragment instance that matches the new layout configuration survives and
 *   reaches the resumed state where it becomes visible to the user
 * - Example rotation sequence: Fragment1(resumed) → destroyed, Fragment2(intermediate) → destroyed,
 *   Fragment3(final) → resumed
 *
 * **Network Request Management:**
 * - The Volley `RequestQueue` is lazily initialized and shared across the fragment's lifecycle
 * - Requests are tagged with the fragment instance (`request.tag = this`) to enable proper cancellation
 * - All pending requests are cancelled in `onStop()` when the fragment becomes invisible
 * - The API key is pre-fetched during request creation to avoid accessing the fragment context
 *   from Volley's background thread (which would crash if the fragment is detached)
 * - Response callbacks check `isAdded` before accessing context to handle race conditions where
 *   responses arrive after the fragment has been detached
 *
 * **Memory Leak Prevention:**
 * - Using `applicationContext` for RequestQueue creation prevents context leaks
 * - Instance-based request tagging ensures old fragments don't retain references through their requests
 * - Proper cancellation in `onStop()` releases callback references to the fragment
 */
class AboutSettingsFragment : AbstractSettingsFragment() {

    override fun providePreferencesXmlResource() = R.xml.preference_about

    // Use lazy initialization to ensure queue is created only once per fragment instance
    private val queue: RequestQueue by lazy {
        Volley.newRequestQueue(requireContext().applicationContext)
    }

    // Track whether we've already checked for updates in this fragment instance
    private var hasCheckedForUpdates = false

    override fun onAttach(context: android.content.Context) {
        Timber.d("$ihs: onAttach")
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("$ihs: onCreate")
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Timber.d("$ihs: onCreatePreferences")
        super.onCreatePreferences(savedInstanceState, rootKey)

        var webViewSummary = resources.getString(R.string.unknown)
        WebViewCompat.getCurrentWebViewPackage(requireContext())?.let {
            webViewSummary = "${it.packageName}\nv${it.versionName}"
        }

        clickablePreference(
            preference = SETTINGS_VERSION,
            summary = versionString
        )

        clickablePreference(
            preference = getString(R.string.pref_key_webview),
            summary = webViewSummary
        )

        clickablePreference(
            preference = getString(R.string.pref_key_introduction),
            summary = getString(R.string.pref_summary_introduction),
            onClick = {
                val intent = android.content.Intent(
                    requireContext(),
                    com.xhub.browser.activity.OnboardingActivity::class.java
                )
                intent.putExtra(
                    com.xhub.browser.activity.OnboardingActivity.EXTRA_FROM_SETTINGS,
                    true
                )
                startActivity(intent)
                true
            }
        )

        // Dynamically override preference intent URLs from RemoteAppConfig (with fallback to defaults)
        val context = requireContext()
        findPreference<Preference>(SETTINGS_VERSION)?.intent?.data = Uri.parse(com.xhub.browser.config.RemoteAppConfig.getHomePageUrl(context))
        findPreference<Preference>(getString(R.string.pref_title_terms_and_conditions))?.intent?.data = Uri.parse(com.xhub.browser.config.RemoteAppConfig.getTermsUrl(context))
        findPreference<Preference>(getString(R.string.pref_title_privacy_policy))?.intent?.data = Uri.parse(com.xhub.browser.config.RemoteAppConfig.getPrivacyPolicyUrl(context))
        findPreference<Preference>(getString(R.string.pref_title_discord))?.intent?.data = Uri.parse(com.xhub.browser.config.RemoteAppConfig.getDiscordUrl(context))

        // Add body to our email link to provide info about device and software
        findPreference<Preference>(getString(R.string.pref_key_contact_us))?.apply {
            val contactUrl = com.xhub.browser.config.RemoteAppConfig.getContactUsUrl(context)
            val uri = "$contactUrl?body=" + Uri.encode("\n\n\n----------------------------------------\n$versionString\n$androidInfo\n$webViewSummary\n$deviceInfo", Charsets.UTF_8.toString())
            intent?.data = Uri.parse(uri)
        }

    }

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_about
    }

    val versionString
        get() =  "${BuildConfig.APPLICATION_ID}\nv${BuildConfig.VERSION_NAME}"

    val androidInfo
        get() = "Android ${Build.VERSION.RELEASE} - API ${Build.VERSION.SDK_INT}"

    val deviceInfo
        get() = "Model ${Build.MODEL} - Brand ${Build.BRAND} - Manufacturer ${Build.MANUFACTURER}"


    override fun onStart() {
        Timber.d("$ihs: onStart")
        super.onStart()
    }

    override fun onResume() {
        Timber.d("$ihs: onResume")
        super.onResume()

        // Only check for updates once per fragment instance, and only when actually shown to user
        if (com.xhub.browser.Variant.isDownload() && !hasCheckedForUpdates) {
            Timber.d("$ihs: Starting update check from onResume")
            findPreference<Preference>(SETTINGS_VERSION)?.apply {title = getString(R.string.checking_for_updates)}
            checkForUpdates()
            hasCheckedForUpdates = true
        }
    }

    override fun onPause() {
        Timber.d("$ihs: onPause")
        super.onPause()
    }

    override fun onStop() {
        Timber.d("$ihs: onStop")
        super.onStop()
        // Cancel all pending requests when fragment is stopped
        queue.cancelAll(this)
    }

    override fun onDestroyView() {
        Timber.d("$ihs: onDestroyView")
        super.onDestroyView()
    }

    override fun onDestroy() {
        Timber.d("$ihs: onDestroy")
        super.onDestroy()
    }

    override fun onDetach() {
        Timber.d("$ihs: onDetach")
        super.onDetach()
    }

    /**
     * Check for app updates by querying our slions.net API
     * DISABLED: Update check infrastructure removed during XHub rebrand
     */
    private fun checkForUpdates() {
        val pref = findPreference<Preference>(SETTINGS_VERSION) ?: return
        UpdateChecker.checkForUpdates(requireContext(), object : UpdateChecker.Callback {
            override fun onUpdateAvailable(latestVersion: String, releaseUrl: String) {
                if (!isAdded) return
                pref.title = getString(R.string.update_available_title)
                pref.summary = getString(R.string.update_available_message, latestVersion)
                
                pref.setOnPreferenceClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(releaseUrl)).apply {
                        putExtra("PACKAGE", requireContext().packageName)
                    }
                    startActivity(intent)
                    true
                }
                UpdateChecker.showUpdateDialog(requireContext(), latestVersion, releaseUrl)
            }

            override fun onNoUpdate() {
                if (!isAdded) return
                pref.title = versionString
                pref.summary = getString(R.string.pref_summary_free_download)
            }

            override fun onError(errorMsg: String) {
                if (!isAdded) return
                pref.title = versionString
                pref.summary = getString(R.string.update_check_failed)
            }
        })
    }


    companion object {
        private const val SETTINGS_VERSION = "pref_version"
    }
}
