package com.xhub.browser.activity

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.xhub.browser.R
import com.xhub.browser.ui.onboarding.OnboardingPages
import dagger.hilt.android.AndroidEntryPoint

/**
 * First-run product tour with Figma-style illustrated pages.
 * Shown from [SplashActivity] until onboarding is completed; reopened from Settings.
 */
@AndroidEntryPoint
class OnboardingActivity : ThemedActivity() {

    private data class Page(
        @StringRes val title: Int,
        @StringRes val description: Int,
        @DrawableRes val illustration: Int,
        /** Soft accent tint for page indicator (ARGB). */
        val accent: Int
    )

    private val pages = listOf(
        Page(
            R.string.intro_welcome_title,
            R.string.intro_welcome_description,
            R.drawable.ill_onboarding_welcome,
            0xFFFF007A.toInt()  // XHub brand pink
        ),
        Page(
            R.string.intro_title_tabs,
            R.string.intro_description_tabs,
            R.drawable.ill_onboarding_tabs,
            0xFFE91E8C.toInt()  // Deep rose — harmonious with brand
        ),
        Page(
            R.string.intro_title_downloads,
            R.string.intro_description_downloads,
            R.drawable.ill_onboarding_download,
            0xFFAD1457.toInt()  // Dark magenta — same family
        ),
        Page(
            R.string.intro_title_privacy,
            R.string.intro_description_privacy,
            R.drawable.ill_onboarding_privacy,
            0xFF880E4F.toInt()  // Deep berry — warm, brand-adjacent
        ),
        Page(
            R.string.intro_title_ready,
            R.string.intro_description_ready,
            R.drawable.ill_onboarding_ready,
            0xFFFF007A.toInt()  // XHub brand pink — bookend matches welcome
        )
    )

    private var index = 0

    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var iconView: ImageView
    private lateinit var illustrationCard: View
    private lateinit var indicator: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton

    private var fromSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        fromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)

        titleView = findViewById(R.id.onboardingTitle)
        descriptionView = findViewById(R.id.onboardingDescription)
        iconView = findViewById(R.id.onboardingIcon)
        illustrationCard = findViewById(R.id.illustrationCard)
        indicator = findViewById(R.id.pageIndicator)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        buildDots()
        bindPage(animate = false)

        require(pages.size == OnboardingPages.COUNT) {
            "Onboarding slide list must stay in sync with OnboardingPages.COUNT"
        }

        btnSkip.setOnClickListener { completeAndExit() }
        btnNext.setOnClickListener {
            if (OnboardingPages.isLastPage(index)) {
                completeAndExit()
            } else {
                index++
                bindPage(animate = true)
            }
        }
    }

    private fun buildDots() {
        indicator.removeAllViews()
        val height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
        repeat(pages.size) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(height, height).apply {
                marginStart = margin
                marginEnd = margin
            }
            dot.layoutParams = lp
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = height / 2f
                setColor(0x33FFFFFF)
            }
            indicator.addView(dot)
        }
    }

    private fun bindPage(animate: Boolean) {
        val page = pages[index]

        fun applyContent() {
            titleView.setText(page.title)
            descriptionView.setText(page.description)
            iconView.setImageDrawable(ContextCompat.getDrawable(this, page.illustration))

            btnNext.setText(
                if (OnboardingPages.isLastPage(index)) R.string.intro_action_done else R.string.intro_action_next
            )
            btnNext.backgroundTintList = android.content.res.ColorStateList.valueOf(page.accent)
            btnSkip.visibility = if (OnboardingPages.isLastPage(index)) View.INVISIBLE else View.VISIBLE

            val activeW = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, resources.displayMetrics).toInt()
            val inactiveW = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            val h = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            for (i in 0 until indicator.childCount) {
                val dot = indicator.getChildAt(i)
                val lp = dot.layoutParams as LinearLayout.LayoutParams
                lp.width = if (i == index) activeW else inactiveW
                lp.height = h
                dot.layoutParams = lp
                (dot.background as? GradientDrawable)?.setColor(
                    if (i == index) page.accent else 0x33FFFFFF
                )
            }
        }

        if (!animate) {
            applyContent()
            return
        }

        val fadeViews = listOf(illustrationCard, titleView, descriptionView)
        fadeViews.forEach { v ->
            v.animate().cancel()
            v.animate()
                .alpha(0f)
                .setDuration(120)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (v === illustrationCard) {
                        applyContent()
                    }
                    v.animate()
                        .alpha(1f)
                        .setDuration(180)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()
        }
    }

    private fun completeAndExit() {
        userPreferences.onboardingCompleted = true
        if (!fromSettings) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    companion object {
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }
}
