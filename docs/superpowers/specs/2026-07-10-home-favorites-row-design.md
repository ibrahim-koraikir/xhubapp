# Home-screen Favorites Row Design

Date: 2026-07-10
Status: Approved (user confirmed via ask_user)

## Goal
Add a manual, star-based Favorites row pinned at the very top of the home screen. Users star/unstar a site from the existing long-press context menu; starred sites appear in a dedicated "⭐ Favorites" group rendered before all other groups.

## User decisions (confirmed)
1. **Entry point / placement**: Star item lives in the existing long-press context menu. The Favorites group renders above all other groups on home.
2. **Persistence**: Favorites are self-contained — store the full `{name, url}` so a starred site survives even if it is later removed from its original group or dropped from the remote list.

## Architecture

Three data sources already merge in `ShortcutRepository` (remote ⊕ defaults ⊕ user overlay). Favorites are a **fourth, independent layer** — a persisted, ordered list of `ShortcutSite` keyed by normalized URL. They are NOT part of the overlay so:
- Remote pushes never touch favorites.
- A favorite keeps working even if its source site is removed.

The home grid (`buildDynamicShortcuts`) already flattens groups into `List<ShortcutItem>`. The Favorites row is simply a synthetic `ShortcutGroup("⭐ Favorites", favoriteSites)` prepended to the list before flattening — so it automatically inherits the existing Header/Tile rendering, the 12-cap + Show more behavior, and favicon loading. No new adapter view type is needed.

## Changes

### 1. `ShortcutRepository.kt` — favorites storage
- New pref key `KEY_FAVORITES` — JSON array of `{name, url}` (same shape as a group's sites).
- `favoriteSites(context): MutableList<ShortcutSite>` — parse the stored list (empty if none/corrupt).
- `isFavorite(context, url): Boolean` — membership by `urlKey`.
- `toggleFavorite(context, site: ShortcutSite): Boolean` — add if absent (append to end), remove if present (by `urlKey`); persist; bump `dataVersion`. Returns the new state (true = now favorited).
- Serialization reuses the same `{name,url}` JSON encoding used elsewhere.

### 2. `strings.xml` — new strings
- `shortcut_favorites_header` = "⭐ Favorites"
- `shortcut_add_favorite` = "Add to Favorites"
- `shortcut_remove_favorite` = "Remove from Favorites"
- `shortcut_favorited_toast` = "Added to Favorites"
- `shortcut_unfavorited_toast` = "Removed from Favorites"

### 3. `WebBrowserActivity.kt`
- **Context menu** (`onHomeScreenShortcutLongClick`): insert a dynamic favorite item right after Share. Label is `shortcut_add_favorite` or `shortcut_remove_favorite` depending on `ShortcutRepository.isFavorite(this, url)`. Selecting it calls a new `toggleFavorite(name, url)` helper.
- **`toggleFavorite(name, url)`**: off-main-thread `Single` → `ShortcutRepository.toggleFavorite`, then on main thread show the matching toast, set `shortcutsDataVersion = -1`, and `buildDynamicShortcuts()`. Uses `remoteShortcutsDisposables` (survives grid rebuilds, disposed in onDestroy) — same pattern as `removeShortcut`.
- **Grid assembly** (`buildDynamicShortcuts`): before iterating `groups`, load `favoriteSites`; if non-empty, prepend a synthetic `ShortcutGroup(getString(R.string.shortcut_favorites_header), favorites)`. This makes it the first group. It participates in the same cap/show-more/padding logic. The synthetic group name is used only for display + expandedGroups key; it is never persisted (favorites are their own store), and `removeShortcut` won't match it because its URLs are real site URLs handled by the favorites layer independently.

## Edge cases
- **Favoriting from within the Favorites row**: the tile's URL tag is a real site URL, so `isFavorite` returns true and the menu shows "Remove from Favorites" — toggling removes it. Correct.
- **Duplicate site across groups**: `urlKey` dedups; a URL is favorited once.
- **Corrupt stored favorites JSON**: `favoriteSites` returns empty (no crash, no row).
- **Show more on favorites**: if a user stars >12 sites, the Favorites group shows the 12-cap + Show more toggle like any other group. `expandedGroups` keys by the header string, so it works unchanged.

## Testing
- Typecheck: `.\gradlew.bat compileXhubFullDownloadDebugKotlin` → BUILD SUCCESSFUL.
- Code review of repository + activity changes.
- (Deferred) Unit tests for `toggleFavorite`/`favoriteSites`/`isFavorite` round-trip — can be a follow-up like the fetcher tests.
