# Movies Shortcut Group Design Spec

Add a new default shortcut group called "movies" containing curated adult movie websites to the home screen of the browser.

## Proposed Changes

### [ShortcutRepository.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/shortcuts/ShortcutRepository.kt)

Add the "movies" shortcut group to the list of defaults in `ShortcutRepository.defaultGroups()`.

The new group will contain the following entries:
- Name: "movies"
- Sites:
  - Name: "AEBN", URL: `https://m.aebn.net/?theaterId=80365&genreId=101&locale=en`
  - Name: "Pornwatch", URL: `https://pornwatch.ws/`
  - Name: "Speedporn", URL: `https://speedporn.net/the-perfect-sister-in-law/`
  - Name: "xtapes", URL: `https://xtapes.me/the-fiery-maid/`
  - Name: "Pornkino", URL: `https://pornkino.cc/`
  - Name: "WatchFreeXXX", URL: `https://watchfreexxx.net/`
  - Name: "Mangoporn", URL: `https://mangoporn.net/`
  - Name: "Pandamovies", URL: `https://pandamovies.pw/`
  - Name: "Freeomovie", URL: `https://freeomovie.info/`
  - Name: "Mangoporn Movies", URL: `https://mangoporn.net/movies/40-year-old-size-queens-4/`

## Verification Plan

### Automated Tests
Write a new unit test in a new/existing test file to verify that `defaultGroups()` returns the new "movies" group and that it contains all 10 expected sites with correct URLs.

Run test command:
```powershell
.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.shortcuts.ShortcutRepositoryTest"
```

### Manual Verification
N/A (covered by unit tests).
