package com.xhub.browser.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity

@AndroidEntryPoint
class SettingsMenuBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val browser = activity as? WebBrowserActivity

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }

        fun openSettingsFragment(fragmentClass: String) {
            browser?.let {
                val intent = android.content.Intent(it, com.xhub.browser.activity.SettingsActivity::class.java)
                intent.putExtra(com.xhub.browser.activity.FRAGMENT_CLASS_NAME, fragmentClass)
                it.startActivity(intent)
            }
            dismiss()
        }

        setupItem(
            view,
            R.id.menuAppearance,
            R.drawable.ic_palette_outline,
            "#ff007a",
            "#33ff007a",
            "Appearance",
            "Language, theme, configurations, menus, toolbars, tabs and panels"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.DisplaySettingsFragment")
        }

        setupItem(
            view,
            R.id.menuBrowser,
            R.drawable.ic_web,
            "#2196F3",
            "#332196F3",
            "Browser",
            "Homepage, search engine, system and tabs management"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.GeneralSettingsFragment")
        }

        setupItem(
            view,
            R.id.menuPrivacy,
            R.drawable.ic_shield_person_outline,
            "#4CAF50",
            "#334CAF50",
            "Privacy",
            "Storage, telemetry and permissions"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.PrivacySettingsFragment")
        }

        setupItem(
            view,
            R.id.menuDomains,
            R.drawable.ic_domain,
            "#00BCD4",
            "#3300BCD4",
            "Domains",
            "Manage site settings"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.DomainsSettingsFragment")
        }

        setupItem(
            view,
            R.id.menuAdBlock,
            R.drawable.ic_block,
            "#F44336",
            "#33F44336",
            "Ad blocker",
            "Manage ad blocker filters"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.AdBlockSettingsFragment")
        }

        setupItem(
            view,
            R.id.menuExtensions,
            R.drawable.ic_extension_outline,
            "#9C27B0",
            "#339C27B0",
            "Extensions",
            "Manage extension scripts"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.ExtensionsSettingsFragment")
        }

        setupItem(
            view,
            R.id.menuBackup,
            R.drawable.ic_backup_outline,
            "#607D8B",
            "#33607D8B",
            "Backup",
            "Export and import bookmarks and sessions"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.BackupSettingsFragment")
        }

        setupItem(
            view,
            R.id.menuContribute,
            R.drawable.ic_giftcard,
            "#FF9800",
            "#33FF9800",
            "Contribute",
            "Help Fulguris grow and succeed"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.SponsorshipSettingsFragment")
        }

        setupItem(
            view,
            R.id.menuAbout,
            R.drawable.ic_info,
            "#E0E0E0",
            "#33E0E0E0",
            "About",
            "Version, contact, and legal details"
        ) {
            openSettingsFragment("com.xhub.browser.settings.fragment.AboutSettingsFragment")
        }
    }

    private fun setupItem(
        parent: View,
        id: Int,
        iconRes: Int,
        iconColorHex: String,
        containerBgColorHex: String,
        title: String,
        summary: String,
        onClick: () -> Unit
    ) {
        val itemLayout = parent.findViewById<View>(id)
        val icon = itemLayout.findViewById<ImageView>(R.id.menuIcon)
        val iconContainer = itemLayout.findViewById<View>(R.id.icon_container)
        val textView = itemLayout.findViewById<TextView>(R.id.menuText)
        val summaryView = itemLayout.findViewById<TextView>(R.id.menuSummary)

        icon?.setImageResource(iconRes)
        try {
            icon?.imageTintList = ColorStateList.valueOf(Color.parseColor(iconColorHex))
            iconContainer?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(containerBgColorHex))
        } catch (e: Exception) {
            // fallback if color parsing fails
        }
        textView?.text = title
        summaryView?.text = summary
        itemLayout.setOnClickListener { onClick() }
    }
}
