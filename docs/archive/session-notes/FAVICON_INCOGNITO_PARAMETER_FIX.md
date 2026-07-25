# Favicon Incognito Parameter Fix

## Problem

The incognito favicon exemption added in the previous task never activated because no caller passed the new `isIncognito` flag, which defaulted to `false`. This meant third-party favicon services (DuckDuckGo, Google) were still being contacted for incognito sessions when the user enabled the global preference.

## Root Cause

The `isIncognito` parameter was optional with a default value of `false` in:
- `faviconForUrl(url, title, aOnDark, isIncognito = false)`
- `realFaviconForUrl(url, aOnDark, isIncognito = false)`
- `downloadFaviconForHost(host, isIncognito = false)`

No existing callers were updated to pass the flag, so the incognito protection gate never activated.

## Solution

Made `isIncognito` a **required parameter** (removed defaults) so the compiler forces every caller to make an explicit decision. This prevents silent regressions where future callers forget to consider incognito state.

### Changes

#### 1. FaviconModel.kt — Removed defaults, made parameter required

```kotlin
// Before
fun faviconForUrl(url: String, title: String, aOnDark: Boolean, isIncognito: Boolean = false): Maybe<Bitmap>
fun realFaviconForUrl(url: String, aOnDark: Boolean, isIncognito: Boolean = false): Maybe<Bitmap>
private fun downloadFaviconForHost(host: String, isIncognito: Boolean = false): Bitmap?

// After
fun faviconForUrl(url: String, title: String, aOnDark: Boolean, isIncognito: Boolean): Maybe<Bitmap>
fun realFaviconForUrl(url: String, aOnDark: Boolean, isIncognito: Boolean): Maybe<Bitmap>
private fun downloadFaviconForHost(host: String, isIncognito: Boolean): Bitmap?
```

Updated KDoc to mark parameters as **REQUIRED**.

#### 2. SuggestionsAdapter.kt — Pass actual incognito state

```kotlin
// Before
faviconModel.realFaviconForUrl(webPage.url, useDark)

// After  
faviconModel.realFaviconForUrl(webPage.url, useDark, isIncognito)
```

The adapter already has an `isIncognito` field passed in constructor — now it's threaded through to the favicon model.

#### 3. WebBrowserActivity.kt — Pass false for home screen shortcuts

```kotlin
// Before
faviconModel.realFaviconForUrl(site.url, true)

// After
faviconModel.realFaviconForUrl(site.url, true, isIncognito = false)
```

Home screen shortcuts are persistent across sessions and not incognito-specific.

#### 4. BookmarksAdapter.kt — Pass false for bookmarks

```kotlin
// Before
faviconModel.faviconForUrl(url, viewModel.bookmark.title, context.isDarkTheme())

// After
faviconModel.faviconForUrl(url, viewModel.bookmark.title, context.isDarkTheme(), isIncognito = false)
```

Bookmarks are persistent storage, not incognito-specific.

#### 5. DomainsSettingsFragment.kt — Pass false for domain settings

```kotlin
// Before
faviconModel.faviconForUrl("http://$domain", "", (activity as? ThemedActivity)?.isDarkTheme() == true)

// After
faviconModel.faviconForUrl("http://$domain", "", (activity as? ThemedActivity)?.isDarkTheme() == true, isIncognito = false)
```

Domain settings UI is not session-specific.

#### 6. PageHistorySettingsFragment.kt — Pass false for page history

```kotlin
// Before
faviconModel.faviconForUrl(item.url, "", context?.isDarkTheme() == true)

// After
faviconModel.faviconForUrl(item.url, "", context?.isDarkTheme() == true, isIncognito = false)
```

Page history settings UI shows cached favicons, not session-specific.

## Verification

**Build:** `BUILD SUCCESSFUL in 4m 43s` ✅

Compiler enforced that all callers now explicitly pass `isIncognito`:
- **SuggestionsAdapter**: Threads actual `isIncognito` field from adapter
- **WebBrowserActivity**: Explicitly passes `false` for home screen shortcuts  
- **BookmarksAdapter**: Explicitly passes `false` for persistent bookmarks
- **DomainsSettingsFragment**: Explicitly passes `false` for settings UI
- **PageHistorySettingsFragment**: Explicitly passes `false` for history UI

## Behavior

### For SuggestionsAdapter (search suggestions)
- **Incognito session**: Third-party services skipped even when preference enabled ✅
- **Normal session**: Third-party services used if preference enabled

### For all other contexts
- Always pass `false` (not incognito-specific contexts)
- Third-party services used if preference enabled

## Privacy Protection

Two independent gates now protect incognito browsing:

1. **Preference gate**: User must enable `thirdPartyFaviconServicesEnabled`
2. **Incognito gate**: Even when enabled, incognito sessions are exempted

Both gates must pass for third-party services to be contacted. The incognito gate now **actually activates** because callers pass the correct state.

## Regression Prevention

Making `isIncognito` required (no default) means:
- Future callers **must** explicitly decide the incognito state
- Compiler enforces this at build time
- Cannot silently regress to always passing `false`
- Forces developers to think about privacy implications

---

**Status**: ✅ Complete and verified
**Build command**: `.\gradlew.bat assembleXhubFullDownloadDebug`
**Files modified**: 6
