package com.xhub.browser.ui.onboarding

/**
 * Pure metadata for product onboarding slides (keeps count/index rules unit-testable).
 */
object OnboardingPages {
    const val COUNT = 5
    val lastIndex: Int get() = COUNT - 1
    fun isLastPage(index: Int): Boolean = index >= lastIndex
}
