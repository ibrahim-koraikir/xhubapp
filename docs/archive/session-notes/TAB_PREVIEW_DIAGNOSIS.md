# Tab Preview Issue - Diagnosis

## Problem
Tab previews show blank/white instead of actual page content.

## Root Cause
`WebView.draw(canvas)` does NOT capture the actual web page content. It only draws the WebView's container/background, not the rendered HTML content.

## Why WebView.draw() Doesn't Work
WebView rendering happens in a separate process/layer. The `draw()` method only captures the View's own drawing, not the composited web content.

## Proper Solutions

### Option 1: Use PixelCopy API (Android 8.0+)
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(window, bitmap, { copyResult ->
        if (copyResult == PixelCopy.SUCCESS) {
            // Use bitmap
        }
    }, handler)
}
```

### Option 2: Disable Hardware Acceleration Temporarily
```kotlin
webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
// Capture with draw()
webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
```

### Option 3: Use capturePicture() (Deprecated but might work)
```kotlin
val picture = webView.capturePicture()
val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
val canvas = Canvas(bitmap)
picture.draw(canvas)
```

### Option 4: Remove Tab Previews Feature
Simply hide the preview ImageView and show only title/favicon.

## Recommendation
The app likely never had working tab previews. The preview ImageView was added to the layout but never properly implemented. We should either:

1. Implement proper WebView capture using PixelCopy (requires Android 8+)
2. Remove the preview feature entirely and just show title/favicon
3. Use a placeholder/icon instead of trying to capture content

## Current Status
- Layout has preview ImageView ✓
- Code attempts to capture with WebView.draw() ✗ (doesn't work)
- No existing working implementation found ✗
