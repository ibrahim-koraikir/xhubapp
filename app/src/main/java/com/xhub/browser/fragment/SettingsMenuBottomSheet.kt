package com.xhub.browser.fragment

import android.content.res.ColorStateList
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
import com.xhub.browser.settings.fragment.*

/**
 * Settings menu bottom sheet.
 * 
 * IMPORTANT: Fragment class references use `::class.java.name` to ensure R8/ProGuard can trace them.
 * The XML file `preferences_root.xml` must be kept in sync manually with these class names.
 * ProGuard rules in `proguard-project.txt` protect the entire settings fragment package.
 */
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
            R.color.app_cat_appearance,
            R.color.app_cat_appearance_container,
            "Appearance",
            "Language, theme, configurations, menus, toolbars, tabs and panels"
        ) {
            openSettingsFragment(DisplaySettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuBrowser,
            R.drawable.ic_web,
            R.color.app_cat_browser,
            R.color.app_cat_browser_container,
            "Browser",
            "Homepage, search engine, system and tabs management"
        ) {
            openSettingsFragment(GeneralSettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuPrivacy,
            R.drawable.ic_shield_person_outline,
            R.color.app_cat_privacy,
            R.color.app_cat_privacy_container,
            "Privacy",
            "Storage, telemetry and permissions"
        ) {
            openSettingsFragment(PrivacySettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuDomains,
            R.drawable.ic_domain,
            R.color.app_cat_domains,
            R.color.app_cat_domains_container,
            "Domains",
            "Manage site settings"
        ) {
            openSettingsFragment(DomainsSettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuAdBlock,
            R.drawable.ic_block,
            R.color.app_cat_adblock,
            R.color.app_cat_adblock_container,
            "Ad blocker",
            "Manage ad blocker filters"
        ) {
            openSettingsFragment(AdBlockSettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuExtensions,
            R.drawable.ic_extension_outline,
            R.color.app_cat_extensions,
            R.color.app_cat_extensions_container,
            "Extensions",
            "Manage extension scripts"
        ) {
            openSettingsFragment(ExtensionsSettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuBackup,
            R.drawable.ic_backup_outline,
            R.color.app_cat_backup,
            R.color.app_cat_backup_container,
            "Backup",
            "Export and import bookmarks and sessions"
        ) {
            openSettingsFragment(BackupSettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuContribute,
            R.drawable.ic_giftcard,
            R.color.app_cat_contribute,
            R.color.app_cat_contribute_container,
            "Contribute",
            "Help Fulguris grow and succeed"
        ) {
            openSettingsFragment(SponsorshipSettingsFragment::class.java.name)
        }

        setupItem(
            view,
            R.id.menuAbout,
            R.drawable.ic_info,
            R.color.app_cat_about,
            R.color.app_cat_about_container,
            "About",
            "Version, contact, and legal details"
        ) {
            openSettingsFragment(AboutSettingsFragment::class.java.name)
        }
    }

    private fun setupItem(
        parent: View,
        id: Int,
        iconRes: Int,
        @androidx.annotation.ColorRes iconColorRes: Int,
        @androidx.annotation.ColorRes containerColorRes: Int,
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
        // Category colors are concrete @color resources (app_cat_*); ContextCompat.getColor
        // can't fail on a valid @ColorRes, so no try/catch is needed (replacing the old
        // Color.parseColor path that could throw on malformed hex).
        icon?.imageTintList = ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(parent.context, iconColorRes)
        )
        iconContainer?.backgroundTintList = ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(parent.context, containerColorRes)
        )
        textView?.text = title
        summaryView?.text = summary
        itemLayout.setOnClickListener { onClick() }
    }
}
