package com.xhub.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Pure entitlement limits used by [com.xhub.browser.browser.TabsManager.newTab].
 * Play (TIN) is capped; sideload/F-Droid (BRONZE+) are effectively unlimited.
 */
class EntitlementTest {

    @Test
    fun `TIN sponsorship allows only 20 tabs`() {
        assertThat(Entitlement.maxTabCount(Sponsorship.TIN)).isEqualTo(20)
    }

    @Test
    fun `BRONZE and above allow high tab ceiling`() {
        val unlimited = listOf(
            Sponsorship.BRONZE,
            Sponsorship.SILVER,
            Sponsorship.GOLD,
            Sponsorship.PLATINUM,
            Sponsorship.DIAMOND
        )
        unlimited.forEach { level ->
            assertThat(Entitlement.maxTabCount(level))
                .describedAs("level=$level")
                .isGreaterThanOrEqualTo(10_000)
        }
    }

    @Test
    fun `TIN is the only low-cap sponsorship`() {
        val levels = Sponsorship.values()
        val lowCap = levels.filter { Entitlement.maxTabCount(it) < 100 }
        assertThat(lowCap).containsExactly(Sponsorship.TIN)
    }
}
