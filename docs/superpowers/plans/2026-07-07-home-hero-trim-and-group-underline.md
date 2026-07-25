# Home Hero Trim + Group Title Underline Implementation Plan
Goal: Shrink the home screen hero header (~35%) by tightening spacing, and add a short orange accent underline beneath each shortcut group title.
Architecture: Pure dimens/layout changes for the hero (no new views, no behavior change). Programmatic accent-line View added under each header TextView in ShortcutTileAdapter. O2 (real stat counts) is already implemented and called on home-show — no work needed.
Tech Stack: Android XML resources, Kotlin view construction (matches existing adapter style).

---

## Task 1 — Trim hero header spacing (H1)

**File:** `app/src/main/res/values/dimens_home.xml`

The hero header currently stacks 7 elements with generous gaps. Trim the paddings and inter-element margins only — keep all content (greeting pill, quote mark, quote text, divider, label, stat chips) and all `home_*` IDs that tests depend on.

### Change 1.1 — Outer hero padding (less top/bottom air)

Current:
```xml
<dimen name="home_hero_padding_top">40dp</dimen>
<dimen name="home_hero_padding_h">20dp</dimen>
<dimen name="home_hero_padding_bottom">28dp</dimen>
```
New:
```xml
<dimen name="home_hero_padding_top">28dp</dimen>
<dimen name="home_hero_padding_h">20dp</dimen>
<dimen name="home_hero_padding_bottom">16dp</dimen>
```

### Change 1.2 — Quote mark text size

Current:
```xml
<dimen name="home_quote_mark_size">60sp</dimen>
```
New:
```xml
<dimen name="home_quote_mark_size">44sp</dimen>
```

### Change 1.3 — Quote text size

Current:
```xml
<dimen name="home_quote_text_size">22sp</dimen>
```
New:
```xml
<dimen name="home_quote_text_size">19sp</dimen>
```

**File:** `app/src/main/res/layout/layout_home_screen.xml`

### Change 1.4 — Greeting pill top margin

Current (line 136):
```xml
android:layout_marginTop="22dp"
```
New:
```xml
android:layout_marginTop="14dp"
```

### Change 1.5 — Quote text top margin (was -12dp to pull up under the large quote mark; keep relative spacing but less aggressive)

Current (line 187):
```xml
android:layout_marginTop="-12dp"
```
New:
```xml
android:layout_marginTop="-8dp"
```

### Verify
```powershell
.\gradlew.bat assembleXhubFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`. No test should break — no view removed, no ID removed.

---

## Task 2 — Accent underline under group titles (U1)

**File:** `app/src/main/java/com/xhub/browser/shortcuts/ShortcutTileAdapter.kt`

Add a short orange accent line directly beneath each group header label, matching the existing `bg_home_quote_divider` look (transparent → orange → transparent).

### Change 2.1 — New drawable for the accent line

**File:** `app/src/main/res/drawable/bg_home_group_underline.xml` (NEW)

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Short accent underline beneath shortcut group titles (SOCIAL, NEWS, …).
     Mirrors bg_home_quote_divider: transparent → orange → transparent. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:type="linear"
        android:startColor="?attr/appColorAccentOrangeFaint"
        android:centerColor="?attr/appColorAccentOrange"
        android:endColor="?attr/appColorAccentOrangeFaint"
        android:angle="0" />
    <size android:width="28dp" android:height="2dp" />
</shape>
```

### Change 2.2 — New dimens

**File:** `app/src/main/res/values/dimens_home.xml`

Add:
```xml
<!-- ── Group title accent underline ─────────────────────────── -->
<dimen name="home_group_underline_width">28dp</dimen>
<dimen name="home_group_underline_height">2dp</dimen>
<dimen name="home_group_underline_margin_top">4dp</dimen>
```

### Change 2.3 — Build header as a vertical container (label + accent line)

**File:** `app/src/main/java/com/xhub/browser/shortcuts/ShortcutTileAdapter.kt`

Replace the `VIEW_TYPE_HEADER ->` branch in `onCreateViewHolder` (currently builds a bare `TextView`).

Current (around line 132):
```kotlin
            VIEW_TYPE_HEADER -> HeaderViewHolder(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = ctx.resources.getDimensionPixelSize(R.dimen.home_group_label_margin_bottom) }
                setTextColor(ContextCompat.getColor(ctx, R.color.home_group_label))
                textSize = ctx.resources.getDimension(R.dimen.home_group_label_size) / density
                letterSpacing = 0.12f
                setTypeface(null, Typeface.BOLD)
            })
```

New:
```kotlin
            VIEW_TYPE_HEADER -> HeaderViewHolder(buildGroupHeader(ctx))
```

Add the builder method to the class (next to `buildTile` / `buildEmptyState`):

```kotlin
    /**
     * Group header: bold uppercase label with a short orange accent underline beneath it.
     * The accent line mirrors bg_home_quote_divider so the visual language stays consistent
     * with the hero (Task 2 / U1).
     */
    private fun buildGroupHeader(ctx: android.content.Context): View {
        val labelBottomMargin = ctx.resources.getDimensionPixelSize(R.dimen.home_group_label_margin_bottom)
        val underlineWidth = ctx.resources.getDimensionPixelSize(R.dimen.home_group_underline_width)
        val underlineHeight = ctx.resources.getDimensionPixelSize(R.dimen.home_group_underline_height)
        val underlineTopMargin = ctx.resources.getDimensionPixelSize(R.dimen.home_group_underline_margin_top)
        val labelSizeSp = ctx.resources.getDimension(R.dimen.home_group_label_size) / density

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = labelBottomMargin }
        }

        val label = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.home_group_label))
            textSize = labelSizeSp
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
        }
        container.addView(label)

        val underline = View(ctx).apply {
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_home_group_underline)
            layoutParams = LinearLayout.LayoutParams(underlineWidth, underlineHeight).also {
                it.topMargin = underlineTopMargin
                it.gravity = Gravity.START
            }
        }
        container.addView(underline)

        return container
    }
```

### Change 2.4 — HeaderViewHolder must reach the TextView inside the new container

The existing `bind` code does `(holder as HeaderViewHolder).textView.text = item.name.uppercase()`.

Current `HeaderViewHolder`:
```kotlin
    private class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
```

The header is now a `LinearLayout` whose first child is the `TextView`. Update the holder to resolve the TextView from the container:

```kotlin
    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = (view as LinearLayout).getChildAt(0) as TextView
    }
```

`onCreateViewHolder` already returns the container to the holder, so `bind` continues to work unchanged:
```kotlin
            is ShortcutItem.Header -> {
                (holder as HeaderViewHolder).textView.text = item.name.uppercase()
            }
```

### Verify
```powershell
.\gradlew.bat assembleXhubFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat testXhubFullDownloadDebugUnitTest --tests "*HomeScreen*"
```
Expected: existing home-screen layout tests still pass (no view removed, no ID removed, no layout contract changed).

---

## Notes

- **O2 (real stat counts): already done.** `updateHomeStats()` at `WebBrowserActivity.kt:1983` reads `bookmarkManager.count()` and `downloadsModel.count()`, formats via `R.plurals.home_stat_saved_count` / `home_stat_downloads_count`, and is called at both home-show paths (lines 1747, 1805). No code change.
- **No tests written for this UI polish** because the home screen has no existing unit test covering these exact spacing values or the header's child structure; the existing `HomeScreenLayoutTest` only asserts presence of key IDs (`homeTitle`, etc.), which are all preserved. TDD does not apply to pure dimen/drawable visual tweaks; verification is a clean build + manual device check.
