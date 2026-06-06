# Spec: Pre-Release Review Improvements

## Section 1: Ad System Removal (Comment 1)
* **Goal**: Completely remove the ad-injection/monetization system end-to-end to prevent the app from being flagged as adware/PUA.
* **Wiring Verification**:
  * The injected `@Inject lateinit var adManager` in [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt) has been confirmed as a declaration-only property; removing it is safe.
  * Deleting `AdManager.kt` cleans up the `ConnectivityManager` network listener automatically, solving a potential context leak.
* **Proposed Changes**:
  * **[DELETE]** [AdManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/ads/AdManager.kt)
  * **[DELETE]** `AD_INTEGRATION.md` and `AD_IMPLEMENTATION_SUMMARY.md` from the repo root.
  * **[MODIFY]** [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt)
    * Remove `import fulguris.ads.AdManager` and the declaration `@Inject lateinit var adManager: fulguris.ads.AdManager`.
  * **[MODIFY]** [AbpBlockerManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/adblock/AbpBlockerManager.kt)
    * Remove `import fulguris.ads.AdManager`.
    * Remove the `private val adManager: AdManager` constructor parameter and update the `@Inject` constructor declaration.
    * In `shouldBlock()`, change the always-allow check to remove `|| adManager.isAdUrl(it)`.
  * **[MODIFY]** [WebPageClient.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageClient.kt)
    * Remove `val adManager: fulguris.ads.AdManager = hiltEntryPoint.adManager`.
    * In `shouldOverrideUrlLoading()`, delete the ad tab injection block and drop the stale comments:
      ```kotlin
      if (request.isForMainFrame && !request.isRedirect && webPageTab.isForeground) {
          if (adManager.trackAction()) { ... }
      }
      ```
  * **[MODIFY]** [EntryPoint.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/di/EntryPoint.kt)
    * Remove `import fulguris.ads.AdManager` and `val adManager: AdManager`.

---

## Section 2: Network Security Config Hardening (Comment 2)
* **Goal**: Harden secure network connections in release builds (removing `<certificates src="user" />` trust globally) while keeping user-CA trust in debug builds for local proxying (Charles/Fiddler/mitmproxy). Keep cleartext globally permitted to support legacy HTTP web content.
* **Proposed Changes**:
  * **[MODIFY]** [network_security_config.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/xml/network_security_config.xml) (Release config)
    * Set `<base-config cleartextTrafficPermitted="true">`.
    * Keep only `<certificates src="system" />` inside `<trust-anchors>`.
  * **[NEW]** `app/src/debug/res/xml/network_security_config.xml` (Debug overlay config)
    * Create config with `<base-config cleartextTrafficPermitted="true">`.
    * Under `<trust-anchors>`, declare both `<certificates src="system" />` and `<certificates src="user" />`.

---

## Section 3: Video Quality Picker Theming & Sanitization (Comment 3)
* **Goal**: Support light/dark theme color adaptability for RadioButtons, implement dynamic generated View IDs mapped via tags, and consistently render sanitized quality labels on both the selection items and the status badge.
* **Proposed Changes**:
  * **[MODIFY]** [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt) inside `showVideoDownloadSheet()`:
    * Resolve `?attr/colorOnSurface` from the sheet context:
      ```kotlin
      val colorValue = android.util.TypedValue()
      sheetView.context.theme.resolveAttribute(R.attr.colorOnSurface, colorValue, true)
      val textColor = colorValue.data
      ```
    * For each quality, calculate a sanitized label:
      ```kotlin
      val sanitizedLabel = entry.key
          .replace(Regex("<[^>]*>"), "")
          .trim()
          .take(50)
      ```
    * Programmatically set RadioButtons:
      ```kotlin
      val generatedId = View.generateViewId()
      val rb = RadioButton(activity).apply {
          id = generatedId
          tag = entry
          text = sanitizedLabel
          isChecked = index == 0
          setTextColor(textColor)
      }
      radioGroup.addView(rb)
      ```
    * Apply the `sanitizedLabel` consistently to `tvCurrentQuality.text` upon initial layout and inside `radioGroup.setOnCheckedChangeListener` by casting the checked radio button tag back to the quality entry.

---

## Section 4: Fetch2 Download Library Clean Up (Comment 4)
* **Goal**: Completely remove Fetch2 dependency artifacts and unused provider bindings.
* **Proposed Changes**:
  * **[MODIFY]** [app/build.gradle](file:///c:/Users/w/Desktop/Fulguris-main/app/build.gradle)
    * Remove Fetch2 dependencies: `com.github.tonyofrancis.Fetch:fetch2:3.4.1` and `:fetch2okhttp`.
  * **[MODIFY]** [AppModule.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/di/AppModule.kt)
    * Delete `providesFetch()` provider.
    * Remove imports of `com.tonyodev.fetch2.*` and reference to `com.tonyodev.fetch2okhttp.OkHttpDownloader`.
  * **[MODIFY]** [EntryPoint.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/di/EntryPoint.kt)
    * Remove `import com.tonyodev.fetch2.Fetch` and `val fetch: Fetch`.
  * **[MODIFY]** [LightningDownloadListener.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/download/LightningDownloadListener.kt)
    * Remove `val fetch` property and the unused `getFileName(id: Long)` helper.

---

## Section 5: TabThumbnailCache Concurrency Fix (Comment 5)
* **Goal**: Prevent redundant decodes using check-and-register under a single lock keyed by `tabId`. Prevent dropped callbacks by removing the in-flight marker *before* executing the list notifications. Support testability.
* **Proposed Changes**:
  * **[MODIFY]** [TabThumbnailCache.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabThumbnailCache.kt)
    * Declare `@Volatile var customCacheDir: File? = null`.
    * Update `diskDir()` to check `customCacheDir` first.
    * Atomic check and register:
      ```kotlin
      var startDiskLoad = false
      synchronized(pendingCallbacks) {
          val existingList = pendingCallbacks[tabId]
          if (existingList != null) {
              if (onLoaded != null) {
                  synchronized(existingList) {
                      existingList.add(onLoaded)
                  }
              }
          } else {
              val list = mutableListOf<(Bitmap?) -> Unit>()
              if (onLoaded != null) {
                  list.add(onLoaded)
              }
              pendingCallbacks[tabId] = list
              startDiskLoad = true
          }
      }
      if (!startDiskLoad) return null
      ```
    * File-not-found/error handling early exits:
      Ensure registered callbacks are notified with `null` when a file does not exist or fails to resolve:
      ```kotlin
      val list = synchronized(pendingCallbacks) { pendingCallbacks.remove(tabId) }
      list?.let {
          synchronized(it) {
              for (cb in it) cb(null)
          }
      }
      ```
    * In-flight Completion notification:
      First remove the marker from the map, then notify all callbacks.
      ```kotlin
      val list = synchronized(pendingCallbacks) { pendingCallbacks.remove(tabId) }
      list?.let {
          synchronized(it) {
              for (cb in it) cb(bitmap)
          }
      }
      ```
  * **[MODIFY]** [TabThumbnailCacheTest.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/test/java/fulguris/browser/tabs/TabThumbnailCacheTest.kt)
    * Add unit tests:
      1. `concurrent get triggers only one disk read and notifies all callbacks`
      2. `file not found notifies all callbacks with null and cleans up map`
      3. `customCacheDir configuration overrides default path successfully`

---

## Section 6: VideoSniffer JS Bridge Input Validation (Comment 6)
* **Goal**: Validate URLs in bridge callbacks, capping lengths and entries. Differentiate acceptable media source URLs (HLS, DASH, blob) from actual direct download URLs (strict HTTP(S) without streaming segments). Remove the dead legacy shim.
* **Proposed Changes**:
  * **[MODIFY]** [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt)
    * Define two URL validation helpers:
      ```kotlin
      private fun isAcceptableMediaUrl(url: String): Boolean {
          if (url.length > 4096) return false
          val lower = url.lowercase()
          return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("blob:")
      }

      private fun isDownloadableHttpUrl(url: String): Boolean {
          if (url.length > 4096) return false
          val lower = url.lowercase()
          return (lower.startsWith("http://") || lower.startsWith("https://")) &&
                 !lower.contains(".m3u8") &&
                 !lower.contains(".mpd")
      }
      ```
    * In `onVideoDetected()`:
      * Validate the top-level `videoUrl` using `isAcceptableMediaUrl()`.
      * Restrict maximum length of the resolution string to 20 characters.
      * Restrict parsed qualities count to 20.
      * Validate every quality option URL using `isDownloadableHttpUrl()`. Skip invalid entries.
  * **[MODIFY]** [VideoJavascriptInterface.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/js/VideoJavascriptInterface.kt)
    * Remove the legacy `onVideoPlaying()` shim.

---

## Section 7: Repo Hygiene & Cleanup (Comment 7)
* **Goal**: Clean up transient log and compilation files from the root of the project without touching actual tracked assets like `gradlew.bat`. Configure anchored `.gitignore` patterns.
* **Proposed Changes**:
  * **[DELETE]** Specific root-anchored files only:
    * `/build_*.log`, `/build_*.txt`
    * `/kapt_*.txt`, `/kapt_*.log`
    * `/fulguris_logs.txt`, `/logcat.txt`
    * `/*.diff`, `/diff.txt`, `/realdiff.txt`
    * `/process_res_log.txt`, `/merge_res_log.txt`
    * `/build.log`, `/compile_error.log`
  * **[MODIFY]** [.gitignore](file:///c:/Users/w/Desktop/Fulguris-main/.gitignore)
    * Add specific root-anchored patterns with a leading slash `/`:
      ```
      # Development and temporary log files (anchored to root only)
      /build_*.log
      /build_*.txt
      /kapt_*.txt
      /kapt_*.log
      /logcat.txt
      /fulguris_logs.txt
      /*.diff
      /process_res_log.txt
      /merge_res_log.txt
      /build.log
      /compile_error.log
      ```
    * Fix typos: `n# Android Studio` → `# Android Studio` and `t # Fastlane` → `# Fastlane`.
