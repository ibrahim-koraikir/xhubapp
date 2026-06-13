package com.xhub.browser.animation

import org.junit.Assert.assertNotEquals
import org.junit.Test
import com.xhub.browser.R

class AnimationResourceTest {
    @Test
    fun verifyPremiumFadesExist() {
        assertNotEquals(0, R.anim.premium_fade_in)
        assertNotEquals(0, R.anim.premium_fade_out)
    }
}
