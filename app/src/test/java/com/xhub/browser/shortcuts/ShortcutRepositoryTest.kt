package com.xhub.browser.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ShortcutRepositoryTest {

    @Test
    fun testMoviesShortcutGroupExists() {
        val defaults = ShortcutRepository.defaultGroups()
        val moviesGroup = defaults.find { it.name == "movies" }
        assertNotNull("movies group should exist in default groups", moviesGroup)
        assertEquals("movies group should have 10 sites", 10, moviesGroup?.sites?.size)
        
        val expectedSites = listOf(
            "AEBN" to "https://m.aebn.net/?theaterId=80365&genreId=101&locale=en",
            "Pornwatch" to "https://pornwatch.ws/",
            "Speedporn" to "https://speedporn.net/the-perfect-sister-in-law/",
            "xtapes" to "https://xtapes.me/the-fiery-maid/",
            "Pornkino" to "https://pornkino.cc/",
            "WatchFreeXXX" to "https://watchfreexxx.net/",
            "Mangoporn" to "https://mangoporn.net/",
            "Pandamovies" to "https://pandamovies.pw/",
            "Freeomovie" to "https://freeomovie.info/",
            "Mangoporn Movies" to "https://mangoporn.net/movies/40-year-old-size-queens-4/"
        )

        expectedSites.forEachIndexed { index, (name, url) ->
            val site = moviesGroup?.sites?.get(index)
            assertEquals("Site $index name matches", name, site?.name)
            assertEquals("Site $index url matches", url, site?.url)
        }
    }
}
