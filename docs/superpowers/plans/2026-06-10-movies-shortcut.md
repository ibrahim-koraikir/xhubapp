# Movies Shortcut Group Implementation Plan

**Goal:** Add the new "movies" default shortcut group containing 10 curated adult movie websites to ShortcutRepository.defaultGroups().
**Architecture:** Update the list in ShortcutRepository.defaultGroups() to include the new ShortcutGroup.
**Tech Stack:** Kotlin, JUnit, Gradle.

---

### Task 1: Add unit test and implement default movies shortcut group

**Files:**
- [NEW] [ShortcutRepositoryTest.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/test/java/fulguris/shortcuts/ShortcutRepositoryTest.kt)
- [MODIFY] [ShortcutRepository.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/shortcuts/ShortcutRepository.kt)

- [ ] **Step 1: Write the failing test**
  Create file `app/src/test/java/fulguris/shortcuts/ShortcutRepositoryTest.kt` with the following content:
  ```kotlin
  package fulguris.shortcuts

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
  ```

- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.shortcuts.ShortcutRepositoryTest"`
  Expected: FAIL (movies group should exist in default groups assertion error)

- [ ] **Step 3: Write minimal implementation**
  Modify `app/src/main/java/fulguris/shortcuts/ShortcutRepository.kt` to append the new group inside `defaultGroups()`:
  ```kotlin
          ShortcutGroup("movies", mutableListOf(
              ShortcutSite("AEBN", "https://m.aebn.net/?theaterId=80365&genreId=101&locale=en"),
              ShortcutSite("Pornwatch", "https://pornwatch.ws/"),
              ShortcutSite("Speedporn", "https://speedporn.net/the-perfect-sister-in-law/"),
              ShortcutSite("xtapes", "https://xtapes.me/the-fiery-maid/"),
              ShortcutSite("Pornkino", "https://pornkino.cc/"),
              ShortcutSite("WatchFreeXXX", "https://watchfreexxx.net/"),
              ShortcutSite("Mangoporn", "https://mangoporn.net/"),
              ShortcutSite("Pandamovies", "https://pandamovies.pw/"),
              ShortcutSite("Freeomovie", "https://freeomovie.info/"),
              ShortcutSite("Mangoporn Movies", "https://mangoporn.net/movies/40-year-old-size-queens-4/")
          ))
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.shortcuts.ShortcutRepositoryTest"`
  Expected: PASS

- [ ] **Step 5: Build and verify**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**
  Run commands:
  ```powershell
  git add app/src/test/java/fulguris/shortcuts/ShortcutRepositoryTest.kt
  git add app/src/main/java/fulguris/shortcuts/ShortcutRepository.kt
  git commit -m "feat: add movies shortcut group with unit tests"
  ```
