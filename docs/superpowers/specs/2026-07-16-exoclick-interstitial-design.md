# ExoClick Interstitial Ad Integration

## Goal

Add ExoClick mobile fullpage interstitial ads as an overlay in the `download` flavor, alongside the existing Adsterra direct-link ads (which remain untouched).

## Files

```
app/src/main/java/com/xhub/browser/ads/
├── AdConfigRepository.kt       (existing — unchanged)
├── DirectLinkAdManager.kt      (existing — unchanged)
├── InterstitialAdManager.kt    (NEW)
└── InterstitialAdConfig.kt     (NEW)
```

## `InterstitialAdConfig`

Simple data class holding ad configuration:

```kotlin
data class InterstitialAdConfig(
    val zoneId: String = "5952204",
    val closeButtonDelayMs: Long = 5000L,  // close button appears after 5s
    val autoDismissMs: Long = 15_000L,     // auto-dismiss after N seconds
    val adProviderUrl: String = "https://a.pemsrv.com/ad-provider.js"
)
```

`autoDismissMs` should be a configurable constant; 15s is recommended default.

## `InterstitialAdManager`

### Constructor

```kotlin
class InterstitialAdManager(
    private val activity: AppCompatActivity,
    private val rootView: CoordinatorLayout,
    private val config: InterstitialAdConfig = InterstitialAdConfig()
)
```

### State

- `overlayView: FrameLayout?` — the full-screen overlay container
- `adWebView: WebView?` — loads the ExoClick HTML
- `closeButton: ImageButton?` — close button, initially GONE
- `isShowing: Boolean`

### Public API

- `fun showAfterDelay(delayMs: Long)` — posts `show()` to handler after delay
- `fun show()` — creates overlay, starts timers
- `fun dismiss()` — removes overlay, destroys WebView, cancels timers
- `fun onPause()` — pauses the WebView
- `fun onResume()` — resumes the WebView
- `fun onDestroy()` — clears all handlers, dismisses
- `fun onBackPressed(): Boolean` — dismisses if showing, returns true

### `show()` Flow

1. Create `FrameLayout` with `MATCH_PARENT` × `MATCH_PARENT`, background `#000000`
2. Create `WebView` inside it (`MATCH_PARENT` × `MATCH_PARENT`)
   - `javaScriptEnabled = true`
   - `layoutAlgorithm = NARROW_COLUMNS`
   - `loadWithOverviewMode = true`, `useWideViewPort = true`
   - zoom disabled
3. Create `ImageButton` (close) inside a top-right-aligned container
   - White close icon on semi-transparent dark circle background
   - `visibility = GONE` initially
   - `setOnClickListener { dismiss() }`
4. Load ad HTML via `loadDataWithBaseURL("https://exoclick.com", html, "text/html", "UTF-8", null)`
5. `rootView.addView(overlayView)`
6. Handler posts at `closeButtonDelayMs` → `closeButton.visibility = VISIBLE`
7. Handler posts at `autoDismissMs` → `dismiss()`

### HTML Template

Built at show-time using `config.zoneId` and `config.adProviderUrl`:

```kotlin
private fun buildAdHtml(): String = """
<html>
<body style="margin:0;overflow:hidden;background:#000;width:100vw;height:100vh;">
<script async src="${config.adProviderUrl}"></script>
<ins class="eas6a97888e33" data-zoneid="${config.zoneId}"></ins>
<script>(AdProvider=window.AdProvider||[]).push({"serve":{}});</script>
</body>
</html>
""".trimIndent()
```

### Back Button

`onBackPressed()` returns `true` and calls `dismiss()` if the overlay is showing. The activity must call through:

```kotlin
override fun onBackPressed() {
    if (interstitialAdManager?.onBackPressed() == true) return
    super.onBackPressed()
}
```

## Integration in WebBrowserActivity

### Field

```kotlin
private var interstitialAdManager: InterstitialAdManager? = null
```

### Initialization (alongside existing ad block, ~line 494)

```kotlin
if (BuildConfig.ADS_ENABLED) {
    // ... existing Adsterra DirectLinkAdManager setup ...

    interstitialAdManager = InterstitialAdManager(
        activity = this,
        rootView = iBinding.coordinatorLayout,
        config = InterstitialAdConfig()
    )
    tabsManager.doOnceAfterInitialization {
        interstitialAdManager?.showAfterDelay(2_000L)
    }
}
```

### Lifecycle hooks

```kotlin
override fun onPause() {
    super.onPause()
    interstitialAdManager?.onPause()
}

override fun onResume() {
    super.onResume()
    interstitialAdManager?.onResume()
}

override fun onDestroy() {
    interstitialAdManager?.onDestroy()
    super.onDestroy()
}
```

### Back press

```kotlin
override fun onBackPressed() {
    if (interstitialAdManager?.onBackPressed() == true) return
    super.onBackPressed()
}
```

## Testing

**File**: `app/src/test/java/com/xhub/browser/ads/InterstitialAdManagerTest.kt`

Robolectric + Mockito test class following `DirectLinkAdManagerTest` patterns:

- `show() creates WebView and adds overlay to rootView`
- `closeButton is GONE before delay, VISIBLE after`
- `dismiss() removes overlay and destroys WebView`
- `onBackPressed() returns true and dismisses when showing`
- `onBackPressed() returns false when not showing`
- `showAfterDelay posts delayed show()`
- `onDestroy() cancels pending handlers`
- `double show() is idempotent`
- `dismiss() is safe to call multiple times`

## Flavor Gating

Gated by `BuildConfig.ADS_ENABLED` — only active in `download` flavor (`ADS_ENABLED=true`). No change to `fdroid` or `playstore` flavors.

## What Does NOT Change

- Direct-link Adsterra ads (untouched)
- `AdConfigRepository` (untouched)
- `DirectLinkAdManager` (untouched)
- `isShowingDirectAd` flag (untouched)
- `WebPageClient` adblocker bypass (untouched)
- Layout XMLs (no changes needed — overlay is constructed programmatically)
- Existing tests (all pass)
