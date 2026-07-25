package com.xhub.browser.shortcuts

import android.content.Context
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for the manual "Favorites" layer in [ShortcutRepository]
 * ([ShortcutRepository.favoriteSites], [ShortcutRepository.isFavorite],
 * [ShortcutRepository.toggleFavorite], and the [ShortcutRepository.dataVersion] bump).
 *
 * Runs under Robolectric so the real SharedPreferences + Android org.json code paths execute on the
 * JVM test classpath. Each test starts from a clean prefs store (see [setUp]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class ShortcutFavoritesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Robolectric shares the app instance across tests in a class; clear our prefs so each test
        // starts from a known-empty favorites state.
        context.getSharedPreferences("home_shortcuts_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── default state: no favorites ─────────────────────────────────────
    @Test
    fun favoritesEmptyByDefault() {
        assertTrue(ShortcutRepository.favoriteSites(context).isEmpty())
        assertFalse(ShortcutRepository.isFavorite(context, "https://a.com/"))
    }

    // ── toggle adds a site (returns true) and it becomes favorited ──────
    @Test
    fun toggleAddsSiteAndReportsFavorited() {
        val site = ShortcutSite("A", "https://a.com/")
        val nowFavorited = ShortcutRepository.toggleFavorite(context, site)

        assertTrue("toggle should report the site as now favorited", nowFavorited)
        assertTrue(ShortcutRepository.isFavorite(context, "https://a.com/"))
        assertEquals(listOf("https://a.com/"), ShortcutRepository.favoriteSites(context).map { it.url })
        assertEquals("A", ShortcutRepository.favoriteSites(context).first().name)
    }

    // ── toggling the same site again removes it (returns false) ─────────
    @Test
    fun toggleTwiceRemovesSiteAndReportsUnfavorited() {
        val site = ShortcutSite("A", "https://a.com/")
        ShortcutRepository.toggleFavorite(context, site)
        val nowFavorited = ShortcutRepository.toggleFavorite(context, site)

        assertFalse("second toggle should report the site as un-favorited", nowFavorited)
        assertFalse(ShortcutRepository.isFavorite(context, "https://a.com/"))
        assertTrue(ShortcutRepository.favoriteSites(context).isEmpty())
    }

    // ── removal matches by NORMALIZED url (trailing slash / case) ───────
    @Test
    fun toggleRemovalMatchesNormalizedUrl() {
        ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://a.com/"))

        // Toggle again using a differently-spelled URL (no trailing slash, upper-case host).
        val nowFavorited = ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://A.COM"))

        assertFalse("normalized URL should match the existing favorite and remove it", nowFavorited)
        assertTrue(ShortcutRepository.favoriteSites(context).isEmpty())
    }

    // ── isFavorite is url-normalized too ────────────────────────────────
    @Test
    fun isFavoriteIgnoresTrailingSlashAndCase() {
        ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://a.com/"))

        assertTrue(ShortcutRepository.isFavorite(context, "https://a.com"))
        assertTrue(ShortcutRepository.isFavorite(context, "https://A.COM/"))
        assertTrue(ShortcutRepository.isFavorite(context, "  https://A.COM/  "))
        assertFalse(ShortcutRepository.isFavorite(context, "https://b.com/"))
    }

    // ── multiple favorites preserve insertion order ─────────────────────
    @Test
    fun favoritesPreserveInsertionOrder() {
        ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://a.com/"))
        ShortcutRepository.toggleFavorite(context, ShortcutSite("B", "https://b.com/"))
        ShortcutRepository.toggleFavorite(context, ShortcutSite("C", "https://c.com/"))

        assertEquals(
            listOf("https://a.com/", "https://b.com/", "https://c.com/"),
            ShortcutRepository.favoriteSites(context).map { it.url }
        )
    }

    // ── removing a middle favorite keeps the rest in order ──────────────
    @Test
    fun removingMiddleFavoriteKeepsRestOrdered() {
        val a = ShortcutSite("A", "https://a.com/")
        val b = ShortcutSite("B", "https://b.com/")
        val c = ShortcutSite("C", "https://c.com/")
        ShortcutRepository.toggleFavorite(context, a)
        ShortcutRepository.toggleFavorite(context, b)
        ShortcutRepository.toggleFavorite(context, c)

        ShortcutRepository.toggleFavorite(context, b) // remove the middle one

        assertEquals(
            listOf("https://a.com/", "https://c.com/"),
            ShortcutRepository.favoriteSites(context).map { it.url }
        )
    }

    // ── favorites are self-contained: full name+url is persisted ────────
    @Test
    fun favoriteStoresFullNameAndUrl() {
        ShortcutRepository.toggleFavorite(context, ShortcutSite("My Custom Name", "https://a.com/page?x=1"))

        val fav = ShortcutRepository.favoriteSites(context).single()
        assertEquals("My Custom Name", fav.name)
        assertEquals("https://a.com/page?x=1", fav.url)
    }

    // ── dataVersion is bumped on every toggle (so the home screen rebuilds) ──
    @Test
    fun toggleBumpsDataVersion() {
        val before = ShortcutRepository.dataVersion(context)

        ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://a.com/"))
        val afterAdd = ShortcutRepository.dataVersion(context)
        assertTrue("adding a favorite should bump dataVersion", afterAdd > before)

        ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://a.com/"))
        val afterRemove = ShortcutRepository.dataVersion(context)
        assertTrue("removing a favorite should bump dataVersion again", afterRemove > afterAdd)
    }

    @Test
    fun consecutiveTogglesProduceDistinctDataVersions() {
        val v0 = ShortcutRepository.dataVersion(context)
        ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://a.com/"))
        val v1 = ShortcutRepository.dataVersion(context)
        ShortcutRepository.toggleFavorite(context, ShortcutSite("B", "https://b.com/"))
        val v2 = ShortcutRepository.dataVersion(context)

        assertNotEquals(v0, v1)
        assertNotEquals(v1, v2)
    }

    // ── favorites persist across reads (backed by SharedPreferences) ────
    @Test
    fun favoritesPersistAcrossReads() {
        ShortcutRepository.toggleFavorite(context, ShortcutSite("A", "https://a.com/"))
        ShortcutRepository.toggleFavorite(context, ShortcutSite("B", "https://b.com/"))

        // A fresh read (favorites are deserialized from prefs each call) sees the same data.
        val reread = ShortcutRepository.favoriteSites(context)
        assertEquals(2, reread.size)
        assertEquals(listOf("A", "B"), reread.map { it.name })
        assertEquals(listOf("https://a.com/", "https://b.com/"), reread.map { it.url })
    }
}
