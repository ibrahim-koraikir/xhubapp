package com.xhub.browser.shortcuts

import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the merge + diff logic that backs the remote-shortcuts / user-overlay model.
 * These verify that a user's edits survive remote list changes.
 *
 * Runs under Robolectric so the JSON (de)serialization paths (which use Android's org.json) work
 * on the JVM test classpath.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class ShortcutMergeTest {

    private fun base(): MutableList<ShortcutGroup> = mutableListOf(
        ShortcutGroup("Alpha", mutableListOf(
            ShortcutSite("A1", "https://a1.com/"),
            ShortcutSite("A2", "https://a2.com/")
        )),
        ShortcutGroup("Beta", mutableListOf(
            ShortcutSite("B1", "https://b1.com/")
        ))
    )

    private fun ShortcutGroup.urls() = sites.map { it.url }
    private fun List<ShortcutGroup>.group(name: String) = first { it.name == name }

    // ── merge with empty overlay is identity ─────────────────────────────
    @Test
    fun emptyOverlayReturnsBaseUnchanged() {
        val merged = ShortcutRepository.merge(base(), ShortcutRepository.Overlay())
        assertEquals(listOf("Alpha", "Beta"), merged.map { it.name })
        assertEquals(listOf("https://a1.com/", "https://a2.com/"), merged.group("Alpha").urls())
        assertEquals(listOf("https://b1.com/"), merged.group("Beta").urls())
    }

    // ── tombstone: user removed a site, and it stays removed ─────────────
    @Test
    fun removedSiteIsTombstonedEvenIfRemoteStillHasIt() {
        val edited = base()
        edited.group("Alpha").sites.removeAll { it.url == "https://a1.com/" }

        val overlay = ShortcutRepository.computeOverlay(base(), edited)
        assertTrue(overlay.removedSiteKeys.contains("https://a1.com"))

        // Re-merge onto the SAME base (simulating remote still listing a1) -> a1 stays gone.
        val merged = ShortcutRepository.merge(base(), overlay)
        assertEquals(listOf("https://a2.com/"), merged.group("Alpha").urls())
    }

    // ── user added a custom site into a curated group ───────────────────
    @Test
    fun addedSiteIsPreservedAndSurvivesRemote() {
        val edited = base()
        edited.group("Alpha").sites.add(ShortcutSite("Mine", "https://mine.com/"))

        val overlay = ShortcutRepository.computeOverlay(base(), edited)
        val merged = ShortcutRepository.merge(base(), overlay)
        assertTrue(merged.group("Alpha").urls().contains("https://mine.com/"))
    }

    // ── rename: custom name kept even though URL still comes from remote ─
    @Test
    fun renamedSiteKeepsCustomName() {
        val edited = base()
        val alpha = edited.group("Alpha")
        val idx = alpha.sites.indexOfFirst { it.url == "https://a1.com/" }
        alpha.sites[idx] = ShortcutSite("Custom Name", "https://a1.com/")

        val overlay = ShortcutRepository.computeOverlay(base(), edited)
        val merged = ShortcutRepository.merge(base(), overlay)
        assertEquals("Custom Name", merged.group("Alpha").sites.first { it.url == "https://a1.com/" }.name)
    }

    // ── group tombstone ─────────────────────────────────────────────────
    @Test
    fun removedGroupStaysRemoved() {
        val edited = base()
        edited.removeAll { it.name == "Beta" }

        val overlay = ShortcutRepository.computeOverlay(base(), edited)
        val merged = ShortcutRepository.merge(base(), overlay)
        assertNull(merged.find { it.name == "Beta" })
    }

    // ── user-created group ──────────────────────────────────────────────
    @Test
    fun addedGroupIsPreserved() {
        val edited = base()
        edited.add(ShortcutGroup("MyFaves", mutableListOf(ShortcutSite("F", "https://f.com/"))))

        val overlay = ShortcutRepository.computeOverlay(base(), edited)
        val merged = ShortcutRepository.merge(base(), overlay)
        val faves = merged.find { it.name == "MyFaves" }
        assertTrue(faves != null && faves.urls().contains("https://f.com/"))
    }

    // ── ordering: user's group order is preserved ───────────────────────
    @Test
    fun groupOrderIsPreserved() {
        val edited = base()
        // Move Beta before Alpha.
        val beta = edited.removeAt(edited.indexOfFirst { it.name == "Beta" })
        edited.add(0, beta)

        val overlay = ShortcutRepository.computeOverlay(base(), edited)
        val merged = ShortcutRepository.merge(base(), overlay)
        assertEquals(listOf("Beta", "Alpha"), merged.map { it.name })
    }

    // ── ordering: user's site order preserved; NEW remote sites append ──
    @Test
    fun newRemoteSitesAppendAfterUserOrderedSites() {
        // User reorders Alpha to [A2, A1].
        val edited = base()
        val alpha = edited.group("Alpha")
        alpha.sites.reverse() // [A2, A1]
        val overlay = ShortcutRepository.computeOverlay(base(), edited)

        // Now remote adds a brand new A3 to Alpha.
        val newRemote = mutableListOf(
            ShortcutGroup("Alpha", mutableListOf(
                ShortcutSite("A1", "https://a1.com/"),
                ShortcutSite("A2", "https://a2.com/"),
                ShortcutSite("A3", "https://a3.com/")
            )),
            ShortcutGroup("Beta", mutableListOf(ShortcutSite("B1", "https://b1.com/")))
        )

        val merged = ShortcutRepository.merge(newRemote, overlay)
        // User order kept for known items, new A3 appended at the end.
        assertEquals(
            listOf("https://a2.com/", "https://a1.com/", "https://a3.com/"),
            merged.group("Alpha").urls()
        )
    }

    // ── round-trip: merge(base, computeOverlay(base, edited)) == edited ─
    @Test
    fun overlayRoundTripReproducesEditedList() {
        val edited = base()
        edited.group("Alpha").sites.removeAll { it.url == "https://a1.com/" }
        edited.group("Alpha").sites.add(ShortcutSite("New", "https://new.com/"))
        edited.add(ShortcutGroup("Extra", mutableListOf(ShortcutSite("E", "https://e.com/"))))

        val overlay = ShortcutRepository.computeOverlay(base(), edited)
        val merged = ShortcutRepository.merge(base(), overlay)

        assertEquals(edited.map { it.name }, merged.map { it.name })
        edited.forEach { g ->
            assertEquals("Group ${g.name} urls", g.urls(), merged.group(g.name).urls())
        }
    }

    // ── URL identity is normalized (trailing slash / case) ──────────────
    @Test
    fun urlIdentityIgnoresTrailingSlashAndCase() {
        val edited = base()
        // Remove A1 using a slightly different spelling (no trailing slash, upper case host).
        edited.group("Alpha").sites.removeAll { it.url == "https://a1.com/" }
        val overlay = ShortcutRepository.computeOverlay(base(), edited)

        // Remote lists the same site with a trailing slash + different case.
        val newRemote = mutableListOf(
            ShortcutGroup("Alpha", mutableListOf(
                ShortcutSite("A1", "https://A1.COM"),
                ShortcutSite("A2", "https://a2.com/")
            )),
            ShortcutGroup("Beta", mutableListOf(ShortcutSite("B1", "https://b1.com/")))
        )
        val merged = ShortcutRepository.merge(newRemote, overlay)
        // The tombstone should still match despite the differing spelling.
        assertFalse(merged.group("Alpha").sites.any { it.url.equals("https://A1.COM", ignoreCase = true) })
    }

    // ── parse: invalid JSON returns null (validation guard) ─────────────
    @Test
    fun parseGroupsJsonRejectsGarbage() {
        assertNull(ShortcutRepository.parseGroupsJson("not json"))
        assertNull(ShortcutRepository.parseGroupsJson(""))
        assertNull(ShortcutRepository.parseGroupsJson(null))
    }

    @Test
    fun parseGroupsJsonAcceptsValidPayload() {
        val json = """[{"name":"G","sites":[{"name":"S","url":"https://s.com/"}]}]"""
        val parsed = ShortcutRepository.parseGroupsJson(json)
        assertEquals(1, parsed?.size)
        assertEquals("https://s.com/", parsed?.first()?.sites?.first()?.url)
    }

    // ── parse: the hosted { version, groups: [...] } object form is accepted ──
    // This is the exact shape of shortcuts.json served from jsDelivr. Before the fix,
    // parseGroupsJson only accepted a bare top-level array, so the remote fetch silently
    // rejected the real file and the app never picked up remote updates.
    @Test
    fun parseGroupsJsonAcceptsVersionedObjectForm() {
        val json = """{"version":3,"groups":[{"name":"G","sites":[{"name":"S","url":"https://s.com/"}]}]}"""
        val parsed = ShortcutRepository.parseGroupsJson(json)
        assertEquals(1, parsed?.size)
        assertEquals("G", parsed?.first()?.name)
        assertEquals("https://s.com/", parsed?.first()?.sites?.first()?.url)
    }

    // Object form with leading whitespace (real files often have it) is still detected.
    @Test
    fun parseGroupsJsonAcceptsObjectFormWithLeadingWhitespace() {
        val json = "\n  {\"version\":1,\"groups\":[{\"name\":\"G\",\"sites\":[]}]}"
        val parsed = ShortcutRepository.parseGroupsJson(json)
        assertEquals(1, parsed?.size)
        assertEquals("G", parsed?.first()?.name)
    }
}
