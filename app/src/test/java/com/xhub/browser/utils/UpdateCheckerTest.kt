package com.xhub.browser.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testIsNewerVersion() {
        // Newer versions
        assertTrue(UpdateChecker.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0", "2.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0.0", "v1.0.1"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.0", "v1.0.1"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0.0", "1.0.1"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.0.0", "1.0.0.1"))
        assertTrue(UpdateChecker.isNewerVersion("1.0", "1.0.1"))

        // Equal or older versions
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.0", "v1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("2.0", "1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.1", "v1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0.1", "1.0.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.1", "1.0"))
    }
}
