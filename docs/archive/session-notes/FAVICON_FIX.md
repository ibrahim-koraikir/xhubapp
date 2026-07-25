# Home-Screen Shortcut Favicon Fix

This document records how the home-screen shortcut favicons were debugged and fixed. It captures both the **root cause** and the **debugging method**, so the same class of bug doesn't eat another afternoon.

**Status:** Fixed and verified on device. Favicons now render on the shortcut tiles.

---

## TL;DR — the actual bug

The favicon `ImageView` lived inside a `MaterialCardView`. Two things combined to hide it:

1. The `ImageView` used `FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)`, but **`MaterialCardView` measured that to `0×0`** in a RecyclerView holder. The bitmap was set successfully, the view was `VISIBLE`, but it had **no bounds to draw into** — so nothing painted.
2. The card's `foreground` was set to `bg_shortcut_tile_ripple`, whose content layer is an **opaque `#FF161616` rectangle**. With `cardElevation > 0`, `MaterialCardView` draws `foreground` **on top of all children**, which would have covered the favicon even if the bounds were right.

**Fix:** replace the `MaterialCardView` frame with a plain `FrameLayout` that carries the squircle look as a `background` drawable (drawn *behind* children), and give the favicon `ImageView` and letter `TextView` **explicit `tileSize × tileSize` dimensions** instead of `MATCH_PARENT`.

---

## Files changed

| File | Change |
|---|---|
| `app/src/main/java/com/xhub/browser/shortcuts/ShortcutTileAdapter.kt` | `MaterialCardView` → `FrameLayout`; children given explicit `tileSize` dims; `frame.background` swaps to white variant on favicon load |
| `app/src/main/res/drawable/bg_shortcut_tile_frame.xml` | **New** — dark squircle background (`home_tile_surface` + stroke + corners) |
| `app/src/main/res/drawable/bg_shortcut_tile_frame_white.xml` | **New** — white squircle background, applied when a favicon loads so transparent PNGs stay visible |

---

## How the bug was found (debugging method)

This was the key — the wrong fixes would never have worked. Every earlier attempt (privacy-toggle flip, `forceThirdParty`, cache-key change, `forceThirdParty` overload) compiled and the favicons still didn't show. The breakthrough came from **instrumenting the view and reading runtime state**, not from reading code.

### Step 1 — confirm the download path works
- Checked the disk cache on the device: **33 PNG files**, keyed by `host.hashCode()`.
- Computed the expected hashes for known shortcut hosts (`pimpbunny.com → -2144381123.png`) and confirmed they matched files present in `/data/data/<pkg>/cache/`.
- Conclusion: **favicons download and cache correctly.** The bug is in the *display* path, not the network path.

### Step 2 — confirm the bind/subscribe path works
- Added a temporary `android.util.Log.e("FAVDEBUG", …)` at `bind()` entry and inside the `onSuccess` consumer.
- Captured 78 log lines: `SUCCESS for https://… 48x48`, `32x32`, `128x128`, …
- Conclusion: **`realFaviconForUrl` returns valid bitmaps and `setImageBitmap` runs.** The bitmap reaches the view.

### Step 3 — log the view's runtime dimensions (this was the breakthrough)
Extended the `onSuccess` log to dump the view state:
```kotlin
android.util.Log.e("FAVDEBUG", "SUCCESS ${site.url} bmp=${bmp.width}x${bmp.height} " +
    "iv.vis=${faviconIv.visibility} iv.w=${faviconIv.width} iv.h=${faviconIv.height} " +
    "iv.drawable=${faviconIv.drawable != null} frame.w=${frame.width} initial.vis=${initial.visibility}")
```
Output:
```
SUCCESS https://pimpbunny.com bmp=48x48 iv.vis=0 iv.w=0 iv.h=0 iv.drawable=true frame.w=128 initial.vis=8
```
Reading that line:
- `iv.vis=0` → ImageView is `VISIBLE` ✓
- `iv.w=0 iv.h=0` → ImageView is **zero-sized** ✗  ← the bug
- `iv.drawable=true` → bitmap IS set ✓
- `frame.w=128` → the card frame is 128px wide ✓
- `initial.vis=8` → TextView is `GONE` ✓

The drawable was set on a VISIBLE view that had **no pixels to draw into**. That's why the letter (a `TextView`, which self-sizes to its text) showed, but the favicon (an `ImageView`, which needs bounds) didn't.

### Step 4 — the fix
- Give the favicon `ImageView` and letter `TextView` explicit `tileSize × tileSize` dimensions (not `MATCH_PARENT`).
- Replace the `MaterialCardView` with a `FrameLayout` so (a) children measure correctly and (b) the background drawable draws *behind* children instead of the card's opaque `foreground` drawing *over* them.
- Removed the temporary `FAVDEBUG` logging.

---

## Lessons

1. **Download working ≠ display working.** The whole first half of the session optimized the network/cache path — all of which was fine. The bug was in the view tree. Always verify *both* ends.
2. **Instrument runtime view state.** Static code reading couldn't distinguish "bitmap not set" from "bitmap set on a 0×0 view". Logging `view.width`/`view.height`/`view.drawable != null` immediately revealed the truth.
3. **`MATCH_PARENT` inside `MaterialCardView` is dangerous in recycled views.** The card measures its content area lazily; in a `RecyclerView` holder the children can report 0×0 at the moment an async callback fires. Prefer explicit dimensions for child views that receive async updates.
4. **A `foreground` drawable on an elevated `MaterialCardView` draws over children.** A ripple with a solid content layer will silently hide every child. Keep the look in `background`, not `foreground`, unless you specifically want overlay behaviour.

---

## How to rebuild & verify

```powershell
# From the project root
.\gradlew.bat assembleXhubFullDownloadDebug
```

APK output: `app\build\outputs\apk\xhubFullDownload\debug\XHub-v2.0.9-xhub-full-download-debug.apk`

Install (device must allow adb installs — MIUI users: enable "Install via USB" in Developer Options):

```powershell
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $ADB install -r -t "app\build\outputs\apk\xhubFullDownload\debug\XHub-v2.0.9-xhub-full-download-debug.apk"
```

Then open the app and go to the **home screen** (new tab / home button). The shortcut tiles should show actual website favicons.
