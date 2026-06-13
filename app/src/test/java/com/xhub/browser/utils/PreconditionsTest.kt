package com.xhub.browser.utils

import org.junit.Test

/**
 * Unit tests for [Preconditions].
 */
class PreconditionsTest {

    @Test(expected = RuntimeException::class)
    fun `checkNonNull throws exception for null param`() = com.xhub.browser.utils.Preconditions.checkNonNull(null)

    @Test
    fun `checkNonNull succeeds for non null param`() = com.xhub.browser.utils.Preconditions.checkNonNull(Any())
}