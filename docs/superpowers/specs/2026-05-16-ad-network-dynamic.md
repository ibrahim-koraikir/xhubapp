# Dynamic Ad Network Fetching

## Goal
Support multiple ad networks defined in a remote GitHub repository (`ad_networks.json`), while increasing the ad frequency threshold from 2-3 to 6-10 actions. The implementation must be extremely fast, non-blocking, and seamlessly bypass the built-in ad blocker.

## Architecture & Proposed Changes
**To make it lightning fast, we will use a "Background Sync & Cache" pattern.**

### 1. AdManager Updates
- **Threshold Change:** Update `getRandomThreshold()` to return `(6..10).random()`.
- **Background Fetch:** When `AdManager` initializes, it will launch a background coroutine to download `https://raw.githubusercontent.com/ibrahim-koraikir/AhmedHytworker-AdsConfig/main/ad_networks.json` using `OkHttpClient`.
- **Caching:** The downloaded JSON will be parsed and saved directly into `SharedPreferences`.
- **Fallback / Default:** If the device has no internet or hasn't downloaded the list yet, `AdManager` will use a hardcoded default list containing the 3 URLs you currently have.
- **Fast Retrieval:** When `getAdUrl()` is called, it simply picks a random URL from the list stored in `SharedPreferences` (or the default list). It takes 0 milliseconds because the network call was already done in the background.

### 2. AdBlocker Exemption (AbpBlockerManager)
- Currently, the blocker hardcodes `"effectivegatecpm.com"`.
- We will update `AbpBlockerManager.kt` to inject `AdManager` (or ask it) and check `adManager.isAdUrl(requestUrl)`.
- If a URL belongs to ANY of our active ad networks, the blocker will automatically allow it.

## Trade-offs & Security
- We must handle JSON parsing exceptions safely so a malformed file on GitHub doesn't crash the app.
- We will use `Moshi` or `JSONObject` (built-in) to parse the JSON easily.

## Next Steps
1. User approves this design.
2. Generate the exact implementation plan.
3. Test locally using a unit test or manual build to verify the JSON fetch.
