# Dynamic Ad Network Implementation Plan

**Goal:** Implement fast, background-fetched dynamic ad networks from GitHub and increase ad frequency to 6-10 actions.
**Architecture:** 
- `AdManager` fetches the JSON from `raw.githubusercontent.com` on a background thread.
- Ads are saved in `SharedPreferences` for zero-delay instant retrieval (`O(1)` time) on the main thread.
- `AbpBlockerManager` uses `AdManager.isAdUrl()` to whitelist the dynamic domains dynamically.

---

### Task 1: Update AdManager for Dynamic Fetching and New Frequency

**Files:**
- Modify: `app/src/main/java/fulguris/ads/AdManager.kt`

- [ ] **Step 1:** Add a `CoroutineScope` and `OkHttpClient` to fetch the JSON from `https://raw.githubusercontent.com/ibrahim-koraikir/AhmedHytworker-AdsConfig/main/ad_networks.json` during `init`.
- [ ] **Step 2:** Parse the JSON using standard `JSONObject`/`JSONArray` and save the list of URLs to `SharedPreferences` as a `StringSet`.
- [ ] **Step 3:** Change `getRandomThreshold()` to return `(6..10).random()`.
- [ ] **Step 4:** Update `getAdUrl()` to pick a random URL from the `StringSet` (or fallback to the 3 hardcoded URLs if the set is empty/failed).
- [ ] **Step 5:** Add `isAdUrl(url: String)` to check if a given URL belongs to any of the currently active ad domains.

### Task 2: Whitelist Dynamic Ads in AdBlocker

**Files:**
- Modify: `app/src/main/java/fulguris/adblock/AbpBlockerManager.kt`

- [ ] **Step 1:** Inject `AdManager` into `AbpBlockerManager`.
- [ ] **Step 2:** Update the `shouldBlock` fast-path check (around line 176) to use `adManager.isAdUrl(it)` instead of hardcoding `effectivegatecpm.com`.
- [ ] **Step 3:** Build the app and verify compilation: `.\gradlew.bat assembleSlionsFullDownloadDebug`.
