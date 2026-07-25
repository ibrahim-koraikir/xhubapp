# Home Shortcuts: Long-press Menu + Haptics/Toast + Show-more Cap — Design

Date: 2026-07-10
Status: Approved (foundation phase; Favorites row deferred to a follow-up)

## Goal
Make the home-screen shortcut grid feel premium and more capable:
1. **Long-press context menu** on each tile with: Open in background tab, Open in new tab, Open in incognito, Copy link, Share, Edit, Remove.
2. **Haptic feedback** on long-press + a **toast** when a site is opened in the background.
3. **Per-group cap**: render at most **12** tiles per group; groups with more show a full-width **"Show more (N)" / "Show less"** toggle that expands/collapses the group inline. State is per-group and resets when leaving the home screen.

Favorites (⭐) is intentionally **out of scope** here (follow-up).

## Architecture (fits existing code)
- Tiles live in `ShortcutTileAdapter` (programmatic `LinearLayout` built in `buildTile`). Click routes through `onTileClick(itemView)` where `itemView.tag == site.url`.
- The grid is flattened in `WebBrowserActivity.buildDynamicShortcuts()`: per group it emits `Header` → `Tile`×N → `PlaceholderCell` padding → `Spacer`.
- `TabsManager.newTab(UrlInitializer(url), show)` — `show=false` = background, `true` = foreground.
- Incognito-to-URL: `startActivity(IncognitoActivity.createIntent(this, url.toUri()))` (already used elsewhere).
- Share: `ACTION_SEND` + `createChooser`. Copy: injected `clipboardManager`.

### 1. Long-press menu
- Add a **new adapter callback** `onTileLongClick: (View) -> Unit` alongside `onTileClick`.
- In `buildTile`, set `tile.setOnLongClickListener { performHapticFeedback(LONG_PRESS); onTileLongClick(it); true }` (haptic lives here so it fires regardless of what the host does).
- The host (`WebBrowserActivity`) implements `onHomeScreenShortcutLongClick(view)`: read `url = view.tag as String`, resolve the display name from the label `TextView`, and show a `MaterialAlertDialogBuilder(...).setItems(...)` context menu (or a `PopupMenu` anchored to the tile). We'll use `MaterialAlertDialogBuilder.setTitle(name).setItems(labels) { _, which -> ... }` — simplest, theme-consistent, no new menu XML.
- Actions:
  - **Open in background tab** → `tabsManager.newTab(UrlInitializer(url), show=false)`; on non-null result show toast `R.string.shortcut_opened_in_background`. (null = max-tab reached; `newTab` already surfaces that.)
  - **Open in new tab** → `tabsManager.newTab(UrlInitializer(url), show=true)`.
  - **Open in incognito** → `startActivity(IncognitoActivity.createIntent(this, url.toUri()))`.
  - **Copy link** → `clipboardManager.setPrimaryClip(ClipData.newPlainText(name, url))` + toast.
  - **Share** → `ACTION_SEND` chooser with the url.
  - **Edit** / **Remove** → launch `ManageShortcutsActivity` (Edit/Remove of a single tile isn't wired to a URL there today, so both simply open the manage screen for now; keeps scope tight and avoids new plumbing). *Decision:* Edit → open manage screen; Remove → confirm dialog then remove via `ShortcutRepository` overlay (see below).

  Refinement for **Remove**: to actually remove the tile we call `ShortcutRepository.loadGroups`, drop the matching site (by url) from whatever group holds it, and `ShortcutRepository.saveGroups(...)` — which bumps the data version so the grid rebuilds. This reuses the existing overlay/tombstone model (a removed base site becomes a tombstone; a removed user-added site just disappears). **Edit** stays as "open manage screen" (full editor) to avoid duplicating the add/edit dialog.

### 2. Haptics + toast
- Haptic: `view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` inside the tile's long-click listener (no permission needed).
- Toast: `Toast.makeText(this, R.string.shortcut_opened_in_background, Toast.LENGTH_SHORT)` after a successful background open.
- New string resources (translatable): `shortcut_opened_in_background`, `shortcut_link_copied`, and menu labels `shortcut_menu_open_background`, `shortcut_menu_open_new_tab`, `shortcut_menu_open_incognito`, `shortcut_menu_copy_link`, `shortcut_menu_share`, `shortcut_menu_edit`, `shortcut_menu_remove`, plus `shortcut_remove_confirm_title` / `shortcut_remove_confirm_message`.

### 3. Show-more cap (12 per group)
- Add a new `ShortcutItem` subtype: `data class ShowMore(val groupName: String, val hiddenCount: Int, val expanded: Boolean)` (full-span, `VIEW_TYPE_SHOWMORE`).
- Adapter: build its view (a full-width clickable `TextView`/button styled like a chip) and bind `"Show more (N)"` vs `"Show less"`; wire `onShowMoreClick(groupName)`.
- Host keeps `private val expandedGroups = mutableSetOf<String>()`. In `buildDynamicShortcuts`, per group: if `sites.size > CAP(12)` and group **not** expanded → emit first 12 tiles + `ShowMore(hidden = size-12, expanded=false)`; if expanded → emit all tiles + `ShowMore(hidden=0, expanded=true)`. Groups ≤12 → no button.
- `onShowMoreClick(groupName)` toggles membership in `expandedGroups`, forces `shortcutsDataVersion = -1`, calls `buildDynamicShortcuts()`.
- **Reset on leave**: clear `expandedGroups` when the overlay hides (in the Home→Web branch of `updateHomeScreenOverlay`).
- `SpanSizeLookup`: `VIEW_TYPE_SHOWMORE` spans full width (add to the `spanCount` branch). DiffUtil: `ShowMore` items are the same iff `groupName` matches; contents compare the data class.
- **PlaceholderCell padding** must be computed against the *visible* tile count for the group (12 or all), so the last visible row pads correctly before the ShowMore/Spacer.

## Files to change
- `app/src/main/java/com/xhub/browser/shortcuts/ShortcutTileAdapter.kt` — new callbacks (`onTileLongClick`, `onShowMoreClick`), `ShowMore` item + view type + view holder + span/diff handling, long-press+haptic in `buildTile`.
- `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt` — `onHomeScreenShortcutLongClick`, the context-menu dialog + actions (background/new/incognito/copy/share/edit/remove), `expandedGroups` state + `onShowMoreClick`, 12-cap logic in `buildDynamicShortcuts`, adapter wiring, reset on leave.
- `app/src/main/res/values/strings.xml` — new strings.
- (Tests) `app/src/test/java/com/xhub/browser/shortcuts/ShortcutShowMoreLogicTest.kt` — pure logic test for the cap/expand flattening if we extract it; otherwise a small helper.

## Non-goals / YAGNI
- No Favorites row here.
- No most-visited tracking, no per-tile favicon changes.
- Edit/Remove of a single tile from the menu: Remove is real (overlay); Edit opens the manage screen.
