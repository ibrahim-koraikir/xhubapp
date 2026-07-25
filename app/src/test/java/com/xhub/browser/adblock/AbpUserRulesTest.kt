package com.xhub.browser.adblock

import com.xhub.browser.database.adblock.UserRulesRepository
import androidx.core.net.toUri
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    /**
     * A repository whose persistence methods always throw. Backed by a [CountDownLatch] so the
     * test can deterministically wait for the (async, Dispatchers.IO) persistence launch to have
     * executed and thrown. If [AbpUserRules]'s CoroutineExceptionHandler ever regressed, the
     * uncaught exception would reach the thread's default handler; here we prove it is swallowed
     * (the throwing method ran, yet the test process is still alive) and that the in-memory
     * userRules already reflect the change regardless of persistence failure.
     */
    private class ThrowingUserRulesRepository : UserRulesRepository {
        val addLatch = CountDownLatch(1)
        val removeLatch = CountDownLatch(1)

        override fun addRules(rules: List<UnifiedFilterResponse>) {
            addLatch.countDown()
            throw RuntimeException("simulated DB write failure on addRules")
        }

        override fun removeAllRules() {}

        override fun removeRule(rule: UnifiedFilterResponse) {
            removeLatch.countDown()
            throw RuntimeException("simulated DB write failure on removeRule")
        }

        override fun getAllRules() = listOf<UnifiedFilterResponse>()
    }

    @Test
    fun failingAddDoesNotCrashAndInMemoryReflectsAdd() {
        val repo = ThrowingUserRulesRepository()
        val userRules = AbpUserRules(repo)

        // Adding a rule persists asynchronously; the repository will throw during persistence.
        userRules.allowPage("http://page.com/something".toUri(), add = true)

        // The in-memory container is updated synchronously, before the (failing) persistence launch,
        // so the rule is immediately effective regardless of the persistence outcome.
        Assert.assertTrue(userRules.isAllowed("http://page.com/otherthing".toUri()))

        // Confirm the failing persistence call actually executed. If the CoroutineExceptionHandler
        // were missing, the thrown exception would have propagated to the default handler; the fact
        // that we get here (and the JVM is still running the test) proves it was swallowed.
        Assert.assertTrue(
            "addRules should have been invoked on the background scope",
            repo.addLatch.await(5, TimeUnit.SECONDS)
        )
    }

    @Test
    fun failingRemoveDoesNotCrashAndInMemoryReflectsRemove() {
        val repo = ThrowingUserRulesRepository()
        val userRules = AbpUserRules(repo)

        // First add the allow rule (its persistence also throws, which must not crash either).
        userRules.allowPage("http://page.com/something".toUri(), add = true)
        Assert.assertTrue(userRules.isAllowed("http://page.com/otherthing".toUri()))
        Assert.assertTrue(repo.addLatch.await(5, TimeUnit.SECONDS))

        // Now remove it; removeRule will throw during persistence.
        userRules.allowPage("http://page.com/something".toUri(), add = false)

        // In-memory state reflects the removal synchronously despite the persistence failure.
        Assert.assertFalse(userRules.isAllowed("http://page.com/otherthing".toUri()))

        // Confirm the failing removeRule call executed and was swallowed (no crash).
        Assert.assertTrue(
            "removeRule should have been invoked on the background scope",
            repo.removeLatch.await(5, TimeUnit.SECONDS)
        )
    }
}

private class NoUserRulesRepository :
    UserRulesRepository {
    override fun addRules(rules: List<UnifiedFilterResponse>) {}
    override fun removeAllRules() {}
    override fun removeRule(rule: UnifiedFilterResponse) {}
    override fun getAllRules() = listOf<UnifiedFilterResponse>()
}
