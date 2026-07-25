package com.xhub.browser.ui.onboarding

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Guards the onboarding slide count from the design (welcome, tabs, downloads, privacy, ready).
 */
class OnboardingPagesTest {

    @Test
    fun `product onboarding has exactly five slides`() {
        assertThat(OnboardingPages.COUNT).isEqualTo(5)
    }

    @Test
    fun `last slide index is four`() {
        assertThat(OnboardingPages.lastIndex).isEqualTo(4)
    }

    @Test
    fun `isLastPage only on final slide`() {
        assertThat(OnboardingPages.isLastPage(0)).isFalse()
        assertThat(OnboardingPages.isLastPage(3)).isFalse()
        assertThat(OnboardingPages.isLastPage(4)).isTrue()
    }
}
