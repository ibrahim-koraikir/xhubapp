package com.xhub.browser.shortcuts

import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the fallback mirror chain selection logic in [RemoteShortcutsFetcher.selectFirstValidBody].
 * Robolectric is required because validation goes through [ShortcutRepository.parseGroupsJson],
 * which uses Android's org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class RemoteShortcutsFetcherTest {

    private val validJson = """
        { "version": 1, "groups": [ { "name": "G", "sites": [ { "name": "S", "url": "https://s.com/" } ] } ] }
    """.trimIndent()

    @Test
    fun `returns first mirror body when it is valid`() {
        val visited = mutableListOf<String>()
        val body = RemoteShortcutsFetcher.selectFirstValidBody(listOf("a", "b", "c")) { url ->
            visited.add(url)
            validJson
        }
        assertEquals(validJson, body)
        // Short-circuits: only the first URL is contacted.
        assertEquals(listOf("a"), visited)
    }

    @Test
    fun `falls through to next mirror when earlier ones fail or serve junk`() {
        val visited = mutableListOf<String>()
        val body = RemoteShortcutsFetcher.selectFirstValidBody(listOf("a", "b", "c")) { url ->
            visited.add(url)
            when (url) {
                "a" -> null            // network failure / HTTP error
                "b" -> "not json {["  // reachable but corrupt
                else -> validJson       // good mirror
            }
        }
        assertEquals(validJson, body)
        assertEquals(listOf("a", "b", "c"), visited)
    }

    @Test
    fun `returns null when every mirror fails`() {
        val visited = mutableListOf<String>()
        val body = RemoteShortcutsFetcher.selectFirstValidBody(listOf("a", "b", "c")) { url ->
            visited.add(url)
            null
        }
        assertNull(body)
        // All mirrors were attempted before giving up.
        assertEquals(listOf("a", "b", "c"), visited)
    }

    @Test
    fun `returns null when every mirror serves junk`() {
        val body = RemoteShortcutsFetcher.selectFirstValidBody(listOf("a", "b")) { "garbage" }
        assertNull(body)
    }

    @Test
    fun `does not contact mirrors after the first valid one`() {
        var cContacted = false
        RemoteShortcutsFetcher.selectFirstValidBody(listOf("a", "b", "c")) { url ->
            if (url == "c") cContacted = true
            if (url == "a") validJson else null
        }
        assertTrue("mirror after the first valid one should not be contacted", !cContacted)
    }
}
