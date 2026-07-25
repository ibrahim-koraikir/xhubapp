# XHub Launcher Icon Update Guide

## Current Status: MANUAL ACTION REQUIRED ⚠️

The launcher icons still display the old Fulguris branding. This guide explains how to generate new XHub-branded launcher icons.

---

## Background

### Current Icon Structure

The app uses two icon systems:

1. **Adaptive Icons (Android 8.0+)** - XML-based, uses vector drawables
   - Location: `app/src/main/res/mipmap-anydpi-v26/`
   - Files: `ic_launcher.xml`, `ic_launcher_round.xml`
   - References: `@drawable/ic_launcher_foreground`, `@drawable/ic_launcher_monochrome`

2. **Legacy Raster Icons (Android 7.x and below)** - WebP bitmap files
   - Locations: `mipmap-mdpi/`, `mipmap-hdpi/`, `mipmap-xhdpi/`, `mipmap-xxhdpi/`, `mipmap-xxxhdpi/`
   - Files in each folder:
     - `ic_launcher.webp`
     - `ic_launcher_round.webp`
     - `ic_launcher_incognito.webp`
     - `ic_launcher_incognito_round.webp`

### What Needs to Change

The **legacy raster icons** (.webp files) still show the old Fulguris logo and need to be regenerated with XHub branding.

The **adaptive icon XML files** already point to the correct vector drawables, but those drawables may also need to be updated if they contain Fulguris branding.

---

## Step-by-Step Icon Update Process

### Option A: Using Android Studio Image Asset Studio (Recommended)

This is the standard Android way to generate launcher icons at all required densities.

#### 1. Check Current Vector Drawables

First, verify what the current foreground icons look like:

```
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/ic_launcher_monochrome.xml
```

If these still show Fulguris branding, you'll need to:
1. Design a new XHub logo as a vector drawable
2. Replace these files with your new XHub logo

#### 2. Open Image Asset Studio

1. In Android Studio, right-click on `app/src/main/res/`
2. Select **New → Image Asset**

#### 3. Generate Main Launcher Icons

**Configure Icon Type: Launcher Icons (Adaptive and Legacy)**

- **Foreground Layer:**
  - Source Asset Type: `Image` or `Clip Art` or `Text`
  - Path: Select your XHub logo SVG/PNG or use built-in clip art
  - Scaling: Adjust to fit properly within safe zone
  - Color: Choose XHub brand color

- **Background Layer:**
  - Source Asset Type: `Color`
  - Color: `@color/ic_launcher_background` (or choose XHub brand color)

- **Options:**
  - Icon Name: `ic_launcher`
  - ✅ Generate Legacy Icon: **Yes** (this generates the .webp files)
  - ✅ Generate Round Icon: **Yes**
  - Shape: Circle (or your preference)

- **Click Next → Finish**

This will generate:
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` (adaptive icons)
- `mipmap-*/ic_launcher.webp` for all densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- `mipmap-*/ic_launcher_round.webp` for all densities

#### 4. Generate Incognito Launcher Icons

Repeat the process for incognito mode icons:

- **Configure Icon Type: Launcher Icons (Adaptive and Legacy)**
- **Foreground Layer:** Use XHub logo with incognito visual treatment (e.g., darker, with mask icon)
- **Icon Name:** `ic_launcher_incognito`
- ✅ Generate Legacy Icon: **Yes**
- ✅ Generate Round Icon: **Yes**

This will generate:
- `mipmap-*/ic_launcher_incognito.webp` for all densities
- `mipmap-*/ic_launcher_incognito_round.webp` for all densities

---

### Option B: Manual Generation (Advanced)

If you can't use Android Studio, you can generate icons manually:

#### Tools Required:
- Vector graphics editor (Inkscape, Adobe Illustrator, Figma)
- Image editor (GIMP, Photoshop)
- Or online tool: https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html

#### Icon Sizes Needed:

| Density | Size (px) | Folder |
|---------|-----------|--------|
| mdpi    | 48x48     | mipmap-mdpi |
| hdpi    | 72x72     | mipmap-hdpi |
| xhdpi   | 96x96     | mipmap-xhdpi |
| xxhdpi  | 144x144   | mipmap-xxhdpi |
| xxxhdpi | 192x192   | mipmap-xxxhdpi |

#### Process:

1. **Design the XHub icon** at 512x512px or as SVG
2. **Export at each required size** in WebP format
3. **Name the files correctly:**
   - `ic_launcher.webp`
   - `ic_launcher_round.webp`
   - `ic_launcher_incognito.webp`
   - `ic_launcher_incognito_round.webp`
4. **Place in corresponding mipmap folders**

#### Converting PNG to WebP:

```powershell
# If you have PNG files, convert to WebP using cwebp tool
cwebp -q 90 ic_launcher.png -o ic_launcher.webp
```

Or use Android Studio: Right-click PNG → Convert to WebP

---

## Files to Replace

### Standard Launcher Icons:

```
app/src/main/res/mipmap-mdpi/ic_launcher.webp
app/src/main/res/mipmap-mdpi/ic_launcher_round.webp
app/src/main/res/mipmap-hdpi/ic_launcher.webp
app/src/main/res/mipmap-hdpi/ic_launcher_round.webp
app/src/main/res/mipmap-xhdpi/ic_launcher.webp
app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp
app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp
app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp
app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
```

### Incognito Launcher Icons:

```
app/src/main/res/mipmap-mdpi/ic_launcher_incognito.webp
app/src/main/res/mipmap-mdpi/ic_launcher_incognito_round.webp
app/src/main/res/mipmap-hdpi/ic_launcher_incognito.webp
app/src/main/res/mipmap-hdpi/ic_launcher_incognito_round.webp
app/src/main/res/mipmap-xhdpi/ic_launcher_incognito.webp
app/src/main/res/mipmap-xhdpi/ic_launcher_incognito_round.webp
app/src/main/res/mipmap-xxhdpi/ic_launcher_incognito.webp
app/src/main/res/mipmap-xxhdpi/ic_launcher_incognito_round.webp
app/src/main/res/mipmap-xxxhdpi/ic_launcher_incognito.webp
app/src/main/res/mipmap-xxxhdpi/ic_launcher_incognito_round.webp
```

**Total: 20 WebP files to replace** (10 standard + 10 incognito)

---

## Adaptive Icon Vector Drawables (May Also Need Update)

Check if these files contain Fulguris branding:

```
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/ic_launcher_monochrome.xml
```

If they do, replace the `<path>` data with your XHub logo vector path.

---

## Design Guidelines

### Android Launcher Icon Specifications

1. **Safe Zone:** Keep important content within inner 66% of icon
2. **Adaptive Icon:** Design foreground assuming background may be any shape (circle, square, squircle, etc.)
3. **Contrast:** Ensure icon is visible on both light and dark launchers
4. **Consistency:** Maintain visual consistency between standard and incognito icons

### XHub Branding Considerations

- Use XHub's brand colors
- Maintain recognizable logo shape
- Ensure legibility at small sizes (48x48px)
- Incognito icon should have clear visual distinction (darker, masked, etc.)

---

## Testing

### After Generating Icons:

1. **Clean and Rebuild:**
   ```powershell
   .\gradlew.bat clean assembleXhubFullDownloadDebug
   ```

2. **Install on Test Devices:**
   ```powershell
   adb install -r app\build\outputs\apk\xhubFullDownload\debug\XHub-*.apk
   ```

3. **Verify on Different Android Versions:**
   - ✅ **Android 7.x or below:** Check legacy raster icons display correctly
   - ✅ **Android 8.0+:** Check adaptive icons display correctly
   - ✅ **Different launcher apps:** Nova, Pixel Launcher, Samsung, etc.
   - ✅ **Light and dark themes:** Ensure icon is visible in both

4. **Check All Icon Variants:**
   - Standard launcher icon (home screen)
   - Round launcher icon (if launcher supports it)
   - Incognito launcher icon
   - Incognito round launcher icon
   - App switcher/recent apps icon
   - Settings app list icon

---

## Verification Checklist

Before considering this task complete:

- [ ] **Design:** XHub logo designed and finalized
- [ ] **Vector Drawables:** `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` updated (if needed)
- [ ] **Legacy Icons Generated:** All 20 .webp files created at correct sizes
- [ ] **Files Replaced:** All old Fulguris .webp icons replaced with XHub icons
- [ ] **Build Successful:** App builds without errors
- [ ] **Android 7.x Tested:** Legacy icons display correctly on older Android
- [ ] **Android 8.0+ Tested:** Adaptive icons display correctly
- [ ] **Incognito Tested:** Incognito icons display correctly and are distinguishable
- [ ] **Multiple Launchers Tested:** Icons look good on different launcher apps
- [ ] **Light/Dark Themes Tested:** Icons visible in both themes

---

## Troubleshooting

### Icon Not Updating After Installation

**Problem:** Old Fulguris icon still shows after installing new APK

**Solutions:**
1. **Uninstall old version first:**
   ```powershell
   adb uninstall com.xhub.browser.full.download
   adb install app\build\outputs\apk\xhubFullDownload\debug\XHub-*.apk
   ```

2. **Clear launcher cache:**
   - Android Settings → Apps → Launcher → Storage → Clear Cache

3. **Restart device**

### Icon Looks Blurry or Pixelated

**Problem:** Icons not sharp at certain sizes

**Solution:** Regenerate at correct sizes for each density bucket. Don't scale/resize a single icon.

### Adaptive Icon Gets Cropped

**Problem:** Important parts of logo are cut off

**Solution:** Reduce foreground layer scale or ensure logo fits within safe zone (66% of canvas)

### Icons Don't Match

**Problem:** Standard and incognito icons look too different

**Solution:** Use same base logo, apply subtle incognito treatment (darker tone, small mask overlay)

---

## Quick Reference: Density Sizes

```
mdpi    = 48x48   (1x baseline)
hdpi    = 72x72   (1.5x)
xhdpi   = 96x96   (2x)
xxhdpi  = 144x144 (3x)
xxxhdpi = 192x192 (4x)
```

For adaptive icons, Android Studio typically uses a 108x108dp canvas with 72x72dp safe zone.

---

## Related Files

- `app/src/main/AndroidManifest.xml` - Specifies `android:icon` and `android:roundIcon`
- `app/src/main/res/values/colors.xml` - May define `ic_launcher_background` color
- `REBRAND_SESSION_SUMMARY.md` - Main rebranding progress tracker

---

## Why This Can't Be Automated

Icon generation requires:
1. **Design decisions:** Logo design, colors, style
2. **Android Studio GUI:** Image Asset Studio is a visual tool
3. **Manual verification:** Visual inspection on real devices
4. **Artistic judgment:** Ensuring icons look good at all sizes

**This task requires manual execution by a human with access to:**
- Android Studio IDE
- XHub logo design files
- Physical or emulated Android devices for testing

---

## Next Steps

1. **Open Android Studio** and load the Fulguris-main project
2. **Follow Option A instructions** above to use Image Asset Studio
3. **Test on Android 7.x device/emulator** to verify legacy icons
4. **Update this checklist** when complete

---

**Status:** ⚠️ **AWAITING MANUAL COMPLETION**

**Estimated Time:** 30-60 minutes (including design and testing)

**Last Updated:** 2026-06-11
