# HomePageFactory Dead Code Cleanup

## Overview
Removed dead code from `HomePageFactory.kt` that performed 8 unused favicon-related string replacements on placeholders that no longer exist in `homepage.html`.

## Problem Statement
The `HomePageFactory.buildPage()` method was performing numerous string replacements for placeholders that were removed from the HTML template:
- 8 favicon replacements (`${netflixFavicon}`, `${imdbFavicon}`, `${letterboxdFavicon}`, `${rottenTomatoesFavicon}`, `${primeVideoFavicon}`, `${huluFavicon}`, `${appleTvFavicon}`, `${youtubeFavicon}`)
- 5 unused theme color replacements (`${searchBarColor}`, `${searchBarTextColor}`, `${borderColor}`, `${accent}`, `${search}`)

The code also included complete favicon downloading infrastructure that was no longer used.

## Changes Made

### 1. Removed Dead Methods

**Deleted `bitmapToBase64()`:**
```kotlin
// REMOVED: 6 lines
private fun bitmapToBase64(bitmap: Bitmap): String { ... }
```

**Deleted `downloadFaviconSingle()`:**
```kotlin
// REMOVED: 27 lines
private fun downloadFaviconSingle(domain: String): Single<String> { ... }
```

### 2. Removed FaviconData Class

**Deleted data class:**
```kotlin
// REMOVED: 11 lines
private data class FaviconData(
    val netflix: String,
    val imdb: String,
    val letterboxd: String,
    val rotten: String,
    val prime: String,
    val hulu: String,
    val apple: String,
    val youtube: String
)
```

### 3. Simplified buildPage() Method

**Before (44 lines):**
```kotlin
override fun buildPage(): Single<String> = Single
    .just(searchEngineProvider.provideSearchEngine())
    .flatMap { (iconUrl, queryUrl, _) ->
        App.setLocale()
        
        Timber.d("Building home page without favicon downloads")
        Single.just(FaviconData("", "", "", "", "", "", "", ""))
        .map { favicons ->
            parse(homePageReader.provideHtml()
                .replace("\${TITLE}", ...)
                .replace("\${backgroundColor}", ...)
                .replace("\${searchBarColor}", ...)        // DEAD
                .replace("\${searchBarTextColor}", ...)     // DEAD
                .replace("\${borderColor}", ...)            // DEAD
                .replace("\${accent}", ...)                 // DEAD
                .replace("\${search}", ...)                 // DEAD
                .replace("\${netflixFavicon}", ...)         // DEAD
                .replace("\${imdbFavicon}", ...)            // DEAD
                .replace("\${letterboxdFavicon}", ...)      // DEAD
                .replace("\${rottenTomatoesFavicon}", ...)  // DEAD
                .replace("\${primeVideoFavicon}", ...)      // DEAD
                .replace("\${huluFavicon}", ...)            // DEAD
                .replace("\${appleTvFavicon}", ...)         // DEAD
                .replace("\${youtubeFavicon}", ...)         // DEAD
            ) andBuild { ... }
        }
    }
    .map { content -> Pair(createHomePage(), content) }
    .doOnSuccess { ... }
    .map { (page, _) -> "$FILE$page" }
```

**After (21 lines - 52% reduction):**
```kotlin
override fun buildPage(): Single<String> = Single
    .just(searchEngineProvider.provideSearchEngine())
    .map { (iconUrl, queryUrl, _) ->
        App.setLocale()
        
        parse(homePageReader.provideHtml()
            .replace("\${TITLE}", application.getString(R.string.home))
            .replace("\${backgroundColor}", htmlColor(ThemeUtils.getSurfaceColor(App.currentContext())))
        ) andBuild {
            charset { UTF8 }
            body {
                when (userPreferences.searchChoice) {
                    0 -> id("image_url") { attr("src", userPreferences.imageUrlString) }
                    else -> id("image_url") { attr("src", iconUrl) }
                }
                tag("script") {
                    html(
                        html()
                            .replace("\${BASE_URL}", queryUrl)
                            .replace("&", "\\u0026")
                    )
                }
            }
        }
    }
    .map { content -> Pair(createHomePage(), content) }
    .doOnSuccess { (page, content) ->
        FileWriter(page, false).use { it.write(content) }
    }
    .map { (page, _) -> "$FILE$page" }
```

**Key improvements:**
- Changed from `.flatMap` to `.map` (no longer need async favicon downloads)
- Removed `Single.just(FaviconData(...))` wrapper
- Removed 13 dead string replacements
- Removed logging statement about favicon downloads

### 4. Cleaned Up Imports

**Removed unused imports:**
```kotlin
// REMOVED
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
```

**Retained necessary imports:**
```kotlin
import io.reactivex.Single
import java.io.File
import java.io.FileWriter
```

## Actual Placeholders Used

### In homepage.html template:
1. `${TITLE}` - Page title
2. `${backgroundColor}` - Body background color

### In script tag (processed by Jsoup):
3. `${BASE_URL}` - Search engine query URL

All other placeholders were dead code.

## Verification

### Build Test
```
.\gradlew.bat assembleXhubFullDownloadDebug

BUILD SUCCESSFUL in 1m 1s
74 actionable tasks: 1 executed, 73 up-to-date
```

✅ **Build passed successfully**

### Placeholder Verification
Searched `homepage.html` for all `${...}` patterns:
```bash
# Only 2 placeholders found in HTML template:
${TITLE}           # Line 7
${backgroundColor} # Line 11

# Script tag uses:
${BASE_URL}        # Replaced in Jsoup body.tag("script") block
```

## Impact Analysis

### Code Reduction
- **Total lines removed:** ~60 lines of dead code
- **Method complexity:** 52% reduction in `buildPage()` method
- **Import count:** 9 fewer unused imports

### Performance Impact
- **Startup:** Slightly faster home page generation (no favicon download logic)
- **Memory:** Reduced object allocation (no FaviconData instances)
- **Network:** No change (favicon downloads were already disabled)

### Maintainability
- **Clarity:** Method is now easier to understand
- **Debugging:** Fewer moving parts to trace
- **Testing:** Simpler code path to test

## Files Modified

1. `app/src/main/java/com/xhub/browser/html/homepage/HomePageFactory.kt`
   - Removed 3 methods (60+ lines)
   - Simplified `buildPage()` by 52%
   - Cleaned up 9 unused imports

## Files Verified

1. `app/src/main/html/homepage.html`
   - Confirmed only 2 placeholders in template
   - Confirmed `${BASE_URL}` used in script processing

## Related Documentation

- Original favicon download infrastructure was commented as "removed as we're using letter-based icons"
- This cleanup completes that removal by eliminating the dead code paths

## Status

✅ **COMPLETE** - Dead code removed, build verified, placeholders confirmed

## Testing Recommendations

Since the home page generation logic was simplified:

1. **Functional Testing:**
   - Launch app and verify home page displays correctly
   - Verify search bar appears with correct theme colors
   - Verify search functionality works (uses `${BASE_URL}`)

2. **Theme Testing:**
   - Test with light theme - verify background color
   - Test with dark theme - verify background color
   - Verify title displays correctly in all themes

3. **Search Engine Testing:**
   - Test with different search engines (Google, DuckDuckGo, etc.)
   - Verify search queries work correctly
   - Verify icon displays correctly based on `userPreferences.searchChoice`

All tests should show **identical behavior** to before the cleanup, as we only removed dead code that had no effect.
