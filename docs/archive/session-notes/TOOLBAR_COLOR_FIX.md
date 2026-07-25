# Toolbar Color Fix

## Issue
The toolbar color was changing when visiting different websites because the app was extracting theme colors from website favicons and HTML meta tags.

## Solution
Modified the `applyToolbarColor()` function in `WebBrowserActivity.kt` to always use a consistent dark color (#0b0b0b) regardless of website colors.

## Changes Made

### File: `app/src/main/java/fulguris/activity/WebBrowserActivity.kt`

**Function:** `applyToolbarColor(color: Int)`

**Key Changes:**

1. **Force Dark Background**
   ```kotlin
   // OLD: Dynamic color based on website
   val effectiveColor = if (ColorUtils.calculateLuminance(color) < 0.15) {
       Color.parseColor("#0A0A0A")
   } else {
       color
   }
   
   // NEW: Always use dark color
   val effectiveColor = Color.parseColor("#0b0b0b")
   ```

2. **Consistent Toolbar Background**
   ```kotlin
   // OLD: Transparent to show page background
   iBinding.toolbarInclude.toolbarLayout.setBackgroundColor(Color.TRANSPARENT)
   
   // NEW: Dark background
   iBinding.toolbarInclude.toolbarLayout.setBackgroundColor(effectiveColor)
   ```

3. **Fixed Progress Bar Background**
   ```kotlin
   // OLD: Dynamic color based on website
   DrawableUtils.mixColor(0.5f, effectiveColor, Color.WHITE).let { ... }
   
   // NEW: Consistent dark color
   iBinding.toolbarInclude.progressView.setBackgroundColor(Color.parseColor("#1a1a1a"))
   ```

## Result

The toolbar now maintains a consistent dark appearance:
- Background: #0b0b0b (very dark gray)
- Text/Icons: #f3f3f3 (light gray)
- Accent: #26C6DA (Comet Teal)
- Progress bar background: #1a1a1a (dark gray)

This matches the Banani design specifications and provides a consistent user experience across all websites.

## Testing

Build and test the app:
```bash
./REBUILD.bat
```

Visit different websites and verify:
- [ ] Toolbar stays dark (#0b0b0b)
- [ ] Icons remain white (#f3f3f3)
- [ ] Tab counter shows in teal (#26C6DA)
- [ ] Progress bar is visible on dark background
- [ ] No color changes when navigating between sites

## Technical Details

The app previously had a feature to extract colors from:
1. HTML meta theme-color tags
2. Website favicons (using Palette API)
3. Fallback to app theme color

This feature has been effectively disabled by forcing the toolbar to always use #0b0b0b, ensuring a consistent dark theme that matches the Comet design system.
