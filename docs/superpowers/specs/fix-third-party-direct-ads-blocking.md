# Fix: Third-Party Direct-Link Ads & Popup Blocking

## Problem

Users visiting ad-heavy websites (streaming sites, file hosts, anime sites, etc.) reported that third-party **direct-link ads and popups were no longer being blocked** by XHub — they were opening directly inside the user's active browser tab.

---

## Root Cause

The bug was in `WebPageChromeClient.onCreateWindow()`.

When a website calls `window.open("ad-url")` or uses `<a target="_blank">`, Android's WebView fires `onCreateWindow()`. The browser setting **"Allow sites to open new windows"** (`popupsEnabled`) is `false` by default in XHub — meaning popups should be blocked.

However, the code that ran when `popupsEnabled == false` was doing the **opposite** of blocking:

```kotlin
// ❌ OLD BROKEN CODE — when popups were "disabled"
} else {
    val tempWebView = WebView(activity)
    tempWebView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(tempView: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.isNotBlank() && url != "about:blank") {
                view.loadUrl(url)  // ← hijacked the user's active tab with the site's ad URL!
            }
            view.post { tempView.destroy() }
            return true
        }
    }
    transport.webView = tempWebView
    resultMsg.sendToTarget()
    return true  // ← told Chromium the popup was accepted!
}
```

**What this meant in practice:**
- A website's ad script calls `window.open("https://some-ad-network.com/...")`
- Chromium fires `onCreateWindow()`
- The broken code captured that URL using a throwaway WebView
- Then called `view.loadUrl(adUrl)` on the **user's currently open tab**
- The user's tab navigated to the third-party ad — even with popups "blocked"!

This allowed **every ad-heavy site to bypass the popup blocker** and redirect the user's tab to their direct-link ads.

---

## The Fix

When `popupsEnabled` is `false`, `onCreateWindow()` must simply return `false`.

Returning `false` tells Chromium: *"This popup is not allowed."* Chromium cancels the `window.open()` request immediately — no new tab is created, no URL is loaded anywhere.

```kotlin
// ✅ NEW FIXED CODE — when popups are disabled
} else {
    // "Allow sites to open new windows" is OFF (Popups Blocked).
    // Return false so Chromium cancels the popup — blocking third-party popups/direct-link ads!
    Timber.i("onCreateWindow: popups disabled — declining third-party popup/direct ad")
    return PopupWindowPolicy.acceptSameTabRedirect(false)  // always returns false
}
```

---

## Files Changed

| File | Change |
|------|--------|
| [WebPageChromeClient.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/com/xhub/browser/view/WebPageChromeClient.kt) | Replaced the `tempWebView` hijack with `return false` when popups are disabled |
| [PopupWindowPolicy.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/com/xhub/browser/browser/PopupWindowPolicy.kt) | `acceptSameTabRedirect()` now always returns `false` |
| [PopupWindowPolicyTest.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/test/java/com/xhub/browser/browser/PopupWindowPolicyTest.kt) | Updated test to verify the always-reject policy |

---

## How the Two Ad Systems Work Independently

There are two separate ad delivery mechanisms in XHub. The fix does **not** affect the app's own monetization ads:

| Source | How it opens | Effect of fix |
|--------|-------------|---------------|
| **App's own ads** (`DirectLinkAdManager`) | Calls `loadUrl()` directly on the current tab — never goes through `onCreateWindow()` | ✅ Unaffected — still works |
| **Third-party website ads** (`window.open` / `target="_blank"`) | Goes through `onCreateWindow()` | ✅ Now correctly blocked |

### App's own ad flow (unchanged)

```
User taps 4–7 times
      │
      ▼
DirectLinkAdManager fires
      │
      ▼
loadInCurrentTab(resolvedUrl)
      │
      ▼
currentTab.loadUrl(url, isAd = true)   ← direct call, never touches onCreateWindow
      │
      ▼
Ad loads in current tab (isShowingDirectAd = true bypasses adblock for own ads only)
```

### Third-party site ad flow (now blocked)

```
Website calls window.open("ad-url")
      │
      ▼
Chromium fires onCreateWindow()
      │
      ▼
popupsEnabled == false → return false
      │
      ▼
Chromium cancels the popup ← BLOCKED
```

---

## Verification

- ✅ Build compiled successfully after fix: `.\gradlew.bat compileXhubFullDownloadDebugKotlin`
- ✅ Unit test passes: `PopupWindowPolicyTest` — `popups disabled rejects all popup redirects`
- ✅ Committed: `fix(popup): block all third-party site popups and direct-link ads`
