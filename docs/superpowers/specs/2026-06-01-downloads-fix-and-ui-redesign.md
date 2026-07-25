# Downloads Fix & UI Redesign — Design Spec
Date: 2026-06-01

## Problem Statement

Two distinct issues with the downloads screen:

1. **Bug — Missing Fetch2 downloads**: `DownloadHandler.java` routes downloads with a
   known MIME type through Fetch2 (`fetch.enqueue()`). The `DownloadsFragment` only
   queries `DownloadManager`, so Fetch2-managed downloads are completely invisible.

2. **Bad UI**: The downloads screen uses `PreferenceFragmentCompat` (settings-style
   text list). No thumbnails, no media previews, no visual hierarchy.

## Approved Solution

### Part 1 — Bug Fix (DownloadHandler.java)

Remove the Fetch2 path for known-MIME downloads. The `DownloadManager.Request` is
already fully built before the Fetch2 branch — just call `downloadManager.enqueue(request)`
for the valid-MIME case too, exactly as is done for the null-MIME path.

**Files changed:**
- `app/src/main/java/fulguris/download/DownloadHandler.java`
  - `onDownloadStartNoStream()`: replace `fetch.enqueue()` block with `downloadManager.enqueue(request)`
  - `onDownloadStartNoStreamWithFilename()`: same change

### Part 2 — UI Redesign (DownloadsFragment + layout)

Replace `PreferenceFragmentCompat` with a standard `Fragment` + `RecyclerView`.

#### Layout: Full-width cards (WhatsApp-style list)

Each download item is a full-width glassmorphic dark card with:
- **Left side**: thumbnail area (120×90dp)
  - Videos → extracted frame from `MediaMetadataRetriever` + semi-transparent play icon overlay
  - Images → decoded thumbnail bitmap
  - Other files → coloured icon on dark background (PDF=red, APK=green, ZIP=amber, generic=primary)
- **Right side**: text block
  - Title (filename, single line, ellipsis middle)
  - Subtitle: file size + date/time  
  - Status pill: `✓ Complete` / `⏬ 47% • 2.1 MB/s` / `✗ Failed` / `⏸ Paused`
- **Neon accent left border** for in-progress downloads (matches Starfield theme)
- **Swipe-to-delete** gesture (shows red delete background with trash icon)
- **Click** → options dialog (same options as current: Open, Share, Delete, Copy link, etc.)
- **Long-press** → same options dialog

#### Empty state
Full-screen empty state illustration with message "No downloads yet" + "Downloads you
start will appear here" subtitle. Matches the Starfield dark palette.

#### Header
Thin summary bar at top: "N downloads · X MB total" — no heavy category header.

#### Action buttons
Floating action row at bottom of screen (above keyboard safe area):
- "Clean failed" (only visible when failed/orphaned exist)
- "Remove all"  
- "Delete all"

#### Theming
- Background: transparent (parent activity provides Starfield background)
- Cards: `#1A1A2E` with 8% white overlay (glassmorphic), 12dp corner radius
- Card border: 1dp `#FFFFFF14` (subtle)
- Active-download neon border: 2dp `colorPrimary` on left edge
- Typography: `Inter` / system default, filename in `colorOnSurface`, meta in `colorOnSurfaceVariant`
- Status pills: rounded chip, colour-coded (green=success, blue=progress, red=failed, amber=paused)

#### Performance
- Thumbnail extraction is async (coroutine), cached in `LruCache<String, Bitmap>` (10 MB)
- RecyclerView uses `DiffUtil` for efficient updates
- ContentObserver + BroadcastReceiver logic unchanged from current implementation

## Files Changed

| File | Change |
|------|--------|
| `DownloadHandler.java` | Remove Fetch2 path, use DownloadManager for all downloads |
| `DownloadsFragment.kt` | Full rewrite: Fragment → RecyclerView + adapter |
| `fragment_downloads.xml` | Replace FragmentContainerView with RecyclerView layout |
| `item_download_card.xml` | New: card layout for each download item |
| `item_download_empty.xml` | New: empty state layout |

## Verification Plan

1. Build: `.\gradlew.bat assembleSlionsFullDownloadDebug` → `BUILD SUCCESSFUL`
2. Unit tests: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest` → `BUILD SUCCESSFUL`
3. Manual: trigger a video download from a web page → appears in downloads list
4. Manual: thumbnail shows for video/image files
5. Manual: swipe-to-delete works
6. Manual: action buttons (clean/remove/delete) work correctly
