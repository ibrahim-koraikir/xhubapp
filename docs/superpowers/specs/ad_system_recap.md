# XHub Direct-Link Ad System: Issues & Solutions

This document recaps the engineering journey of the **XHub Direct-Link Ad system**, mapping out the initial issues (blank screens, slow loads, unwanted tabs) and explaining how the final preloaded same-tab architecture resolved them.

---

## 1. The Initial Problems

When we started, three main issues broke the user experience and stopped monetization:

### Issue A: The "Blank Screen" Bug (Blocker Interference)
* **Symptom:** When an ad was triggered, the WebView would load a white/blank screen and hang indefinitely.
* **Root Cause:** XHub includes a built-in AdBlock engine (using ABP/EasyList rules). Adsterra's redirect domains (e.g., `effectivecpmnetwork.com`) are blacklisted on EasyList. When the browser tried to load the ad, `WebPageClient.shouldInterceptRequest` evaluated the redirect URL against the blocker, blocked it, and returned an empty dummy resource. The redirect chain was killed on the first hop.

### Issue B: "Takes a Long Time to Load" (Redirect Chains)
* **Symptom:** Even if the ad blocker was turned off, ads took 4–10 seconds of loading spinners to appear.
* **Root Cause:** Direct-link ad URLs are not static pages; they are redirect chains. The browser has to perform 3 to 5 separate HTTP `302` redirects (passing through click-trackers and CPM networks) before landing on the final ad offer. Each hop is a network round-trip.

### Issue C: "Opens in a New Tab"
* **Symptom:** Ads opened a new tab in the browser, showing up in the tab list/switcher.
* **Root Cause:** The early ad delivery code used `openAdTab(url, true)`, which created a brand new visible tab in the browser's foreground, cluttering the user's workspace.

---

## 2. Technical Architecture of the Fix

We designed a dual-mechanism system combining **Adblocker Bypass Tracking** and a **Detached Preloading Queue**.

```
[ User browses normally ]
          │
          ├─► Every tap is counted (threshold = 4-7 taps)
          ├─► preloadNextAd() called early
          │        │
          │        ▼
          │   Creates a detached WebPageTab (not in TabsManager's visible list)
          │   with `isShowingDirectAd = true`
          │        │
          │        ▼
          │   WebView resolves redirect chain silently in background:
          │   Adsterra (blocked list) ──► Tracker ──► Final Ad (resolved!)
          │
          ▼
[ Threshold Hits! ]
          │
          ├─► Reads resolved URL from background WebView (`bgTab.webView.url`)
          ├─► Destroys background tab (releasing resources)
          ├─► Calls `loadInCurrentTab(resolvedUrl)` on active tab
          │   with `isShowingDirectAd = true`
          │
          ▼
[ Instant Same-Tab Ad Display (0ms latency, blocker bypassed) ]
```

---

## 3. Detailed Component Breakdown

### A. Adblocker Bypass Logic
We introduced a flag to conditionally suspend ad-blocking *only* for the active direct-link ad lifecycle:

1. **`WebPageTab.isShowingDirectAd`**:
   A state flag tracking if the tab is currently loading an authenticated app ad.
   * *Reset Rules:* Set to `false` automatically whenever the user navigates to normal content (`loadUrl` without the ad flag, `goBack()`, `goForward()`, or loading special/history/bookmark pages).
2. **`WebPageClient.shouldInterceptRequest`**:
   Checks the state of the tab:
   ```kotlin
   if (webPageTab.isShowingDirectAd) {
       // Bypass adblock completely so redirect chains resolve successfully
       return null 
   }
   ```

### B. Same-Tab Preloading
To make ads load instantly without opening new tabs:

1. **Preload Phase (`DirectLinkAdManager.preloadNextAd`)**:
   We instantiate a standard `WebPageTab` in the background but **do not** register it with the `TabsManager`'s tab list:
   ```kotlin
   createPreloadedTab = { url ->
       WebPageTab(this, UrlInitializer(url), isIncognito = false, ...).apply {
           isShowingDirectAd = true // bypass blocker for background redirects
       }
   }
   ```
   Because it is never added to the tab list, it is completely invisible to the user (doesn't show in the tab switcher or tab count). It silently follows all redirect hops in the background.

2. **Trigger Phase (`onUserGestureNavigation`)**:
   When the user taps 4–7 times, we query the preloaded tab:
   ```kotlin
   val resolvedUrl = bgTab.webView?.url // e.g., "https://final-ad-offer.com"
   bgTab.destroy() // destroy the temporary background tab
   loadInCurrentTab(resolvedUrl) // load the pre-resolved URL in the current tab
   ```
   This replaces the redirect latency with a preloaded, clean URL swap in the active window.

---

## 4. Key Verification Metrics
* **Tab Switch Count:** 0 (Ad loads within the existing tab layout).
* **Loading Speed:** Immediate (Redirect hops already resolved prior to user trigger).
* **Ad Blocker Status:** Normal operations resume as soon as the user navigates away from the ad.
* **Leak Protection:** Background tab resources are explicitly freed via `bgTab.destroy()` in `onActivityDestroy()`.
