package com.xhub.browser.adblock

import com.xhub.browser.database.adblock.UserRulesRepository
import androidx.core.net.toUri
import com.xhub.browser.adblock.AbpUserRules
import com.xhub.browser.adblock.UnifiedFilterResponse
import org.junit.Assert
import org.junit.Test

class AbpUserRulesTest {
    private val abpUserRules = AbpUserRules(NoUserRulesRepository())

    @Test
    fun block() {
        abpUserRules.allowPage("http://page.com/something".toUri(), add = true)
        // now page.com should be explicitly allowlisted
        Assert.assertTrue(abpUserRules.isAllowed("http://page.com/otherthing".toUri()))
        Assert.assertFalse(abpUserRules.isAllowed("http://page2.com/otherthing".toUri()))
        Assert.assertFalse(abpUserRules.isAllowed("http://test.page.com/otherthing".toUri()))
    }

}

private class NoUserRulesRepository:
    UserRulesRepository {
    override fun addRules(rules: List<UnifiedFilterResponse>) {}
    override fun removeAllRules() {}
    override fun removeRule(rule: UnifiedFilterResponse) {}
    override fun getAllRules() = listOf<UnifiedFilterResponse>()
}
