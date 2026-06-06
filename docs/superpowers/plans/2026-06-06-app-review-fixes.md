# App Review Fixes Implementation Plan

Goal: Implement the pre-release review improvements including removing the ad injection system, hardening network security configuration, resolving video picker bugs, purging Fetch2, fixing TabThumbnailCache race conditions, validating JS bridge inputs, and tidying repo root.
Architecture: Clean separation of WebView JavascriptInterface video bridge inputs from Android's DownloadManager, Hilt dependency refactoring to strip AdManager/Fetch2, multi-thread atomic locks for TabThumbnailCache, and modular resource overlays for network security.
Tech Stack: Kotlin, Java, Android SDK, Android DownloadManager, XML, Dagger Hilt, JUnit/Robolectric.

---

## Task 1 — Remove Ad Injection System end-to-end
Remove AdManager.kt, related Hilt injections, bypass rules in blocker, and auto-open ad tabs.

### Files to delete:
- `app/src/main/java/fulguris/ads/AdManager.kt`
- `AD_INTEGRATION.md`
- `AD_IMPLEMENTATION_SUMMARY.md`

### [MODIFY] [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt)
Remove the injection of `adManager`:
```diff
-    @Inject lateinit var adManager: fulguris.ads.AdManager
```

### [MODIFY] [EntryPoint.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/di/EntryPoint.kt)
Remove `AdManager` from the entry point and clean up imports:
```diff
-import fulguris.ads.AdManager
...
-    val adManager: AdManager
```

### [MODIFY] [AbpBlockerManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/adblock/AbpBlockerManager.kt)
Remove constructor parameter for `adManager`, clean up imports, and remove always-allow condition check for ad URLs:
```diff
-import fulguris.ads.AdManager
...
 class AbpBlockerManager @Inject constructor(
     private val application: Application,
     abpListUpdater: AbpListUpdater,
     abpUserRules: AbpUserRules,
     val userPreferences: UserPreferences,
-    private val adManager: AdManager,
 ) : AdBlocker {
...
     override suspend fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
         // always allow special URLs, app scheme and cache dir (used for favicons)
         request.url.toString().let {
-            if (it.isSpecialUrl() || it.isAppScheme() || it.startsWith(cacheDir) || adManager.isAdUrl(it))
+            if (it.isSpecialUrl() || it.isAppScheme() || it.startsWith(cacheDir))
                 return null
         }
```

### [MODIFY] [WebPageClient.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageClient.kt)
Remove retrieval of `adManager` and remove the ad-triggering block inside `shouldOverrideUrlLoading`:
```diff
-    val adManager: fulguris.ads.AdManager = hiltEntryPoint.adManager
...
     override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
 
         Timber.i("$ihs : shouldOverrideUrlLoading - ${request.url}")
 
-        // Track user-initiated navigation (link/image/video clicks) for ad monetization
-        // Only trigger on main frame requests that the user actually navigated to
-        if (request.isForMainFrame && !request.isRedirect && webPageTab.isForeground) {
-            if (adManager.trackAction()) {
-                Timber.d("$ihs : Showing ad on URL load")
-                hiltEntryPoint.tabsManager.newTab(
-                    UrlInitializer(adManager.getAdUrl()),
-                    true
-                )
-            }
-        }
-
         val url = request.url.toString()
```

### Verification Command:
```powershell
taskkill /F /IM java.exe
.\gradlew.bat assembleSlionsFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`

---

## Task 2 — Network Security Config Hardening
Create a debug-specific config that trusts user CAs for proxy debugging, and remove user CA trust from the main config.

### [MODIFY] [network_security_config.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/xml/network_security_config.xml)
Replace file content with release-hardened base configuration:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Browser must load arbitrary HTTP web content in the WebView -->
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <!-- system only: protects first-party HTTPS and browsing from user-installed CA interception in release -->
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### [NEW] [network_security_config.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/debug/res/xml/network_security_config.xml)
Create debug config for proxy support:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Permit cleartext and trust both system and user CAs in debug builds for local proxy debugging -->
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### Verification Command:
```powershell
.\gradlew.bat assembleSlionsFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`

---

## Task 3 — Video Picker Theming, IDs & Sanitization
Use theme-resolved text color attribute `colorOnSurface`, generated View IDs with Tag mapping, and sanitize/truncate quality labels.

### [MODIFY] [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt)
Modify `showVideoDownloadSheet` to implement these changes:
```kotlin
        val containerQualityPicker = sheetView.findViewById<android.view.View>(R.id.containerQualityPicker)
        val radioGroup = sheetView.findViewById<RadioGroup>(R.id.radioGroupQualities)
        var selectedDownloadUrl = videoUrl

        val colorValue = android.util.TypedValue()
        sheetView.context.theme.resolveAttribute(R.attr.colorOnSurface, colorValue, true)
        val textColor = colorValue.data

        val tvAdaptiveMessage = sheetView.findViewById<TextView>(R.id.tvAdaptiveStreamMessage)
        val btnDownload = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVideoDownload)
        val isAdaptiveOnly = detectedStreamType in listOf("blob", "hls", "dash")

        if (isAdaptiveOnly && (qualities == null || qualities.all { classifyUrl(it.value) != "direct" })) {
            tvAdaptiveMessage.visibility = View.VISIBLE
            tvAdaptiveMessage.text = activity.getString(R.string.video_adaptive_stream_message)
            btnDownload.isEnabled = false
            btnDownload.text = activity.getString(R.string.video_cannot_download)
            btnDownload.icon = null
            containerQualityPicker.visibility = View.GONE
        } else {
            tvAdaptiveMessage.visibility = View.GONE
            btnDownload.isEnabled = true
            btnDownload.text = activity.getString(R.string.action_download)
            btnDownload.icon = ContextCompat.getDrawable(activity, R.drawable.ic_download_outline)

            if (qualities != null && qualities.isNotEmpty()) {
                containerQualityPicker.visibility = View.VISIBLE
                val qualityList = qualities.entries.toList()
                qualityList.forEachIndexed { index, entry ->
                    val sanitizedLabel = entry.key
                        .replace(Regex("<[^>]*>"), "")
                        .trim()
                        .take(50)

                    val generatedId = View.generateViewId()
                    val rb = RadioButton(activity).apply {
                        id = generatedId
                        tag = entry
                        text = sanitizedLabel
                        isChecked = index == 0
                        setTextColor(textColor)
                    }
                    radioGroup.addView(rb)

                    if (index == 0) {
                        tvCurrentQuality.text = sanitizedLabel
                    }
                }
                selectedDownloadUrl = qualityList[0].value

                radioGroup.setOnCheckedChangeListener { group, checkedId ->
                    val checkedRadioButton = group.findViewById<RadioButton>(checkedId)
                    val entry = checkedRadioButton?.tag as? Map.Entry<String, String>
                    if (entry != null) {
                        val sanitizedCheckedLabel = entry.key
                            .replace(Regex("<[^>]*>"), "")
                            .trim()
                            .take(50)
                        selectedDownloadUrl = entry.value
                        tvCurrentQuality.text = sanitizedCheckedLabel
                    }
                }
            }
        }
```

### Verification Command:
```powershell
.\gradlew.bat assembleSlionsFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`

---

## Task 4 — Fetch2 Download Library Purge
Remove unused Fetch2 library dependencies and all reference declarations.

### [MODIFY] [app/build.gradle](file:///c:/Users/w/Desktop/Fulguris-main/app/build.gradle)
Remove Fetch2 implementation dependencies:
```diff
-    // Fetch2 - Premium download manager with progress, pause/resume, parallel downloads
-    implementation 'com.github.tonyofrancis.Fetch:fetch2:3.4.1'
-    implementation 'com.github.tonyofrancis.Fetch:fetch2okhttp:3.4.1'
```

### [MODIFY] [AppModule.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/di/AppModule.kt)
Remove Fetch imports and the `providesFetch` method:
```diff
-import com.tonyodev.fetch2.Fetch
-import com.tonyodev.fetch2.FetchConfiguration
-import com.tonyodev.fetch2.HttpUrlConnectionDownloader
-import com.tonyodev.fetch2.NetworkType
...
-    @Singleton
-    @Provides
-    fun providesFetch(application: Application): Fetch {
-        val fetchConfiguration = FetchConfiguration.Builder(application)
-            .setDownloadConcurrentLimit(3)
-            .setHttpDownloader(com.tonyodev.fetch2okhttp.OkHttpDownloader())
-            .setNamespace("fulguris_downloads")
-            .setAutoRetryMaxAttempts(5)
-            .setGlobalNetworkType(NetworkType.ALL)
-            .enableLogging(true)
-            .build()
-        return Fetch.Impl.getInstance(fetchConfiguration)
-    }
```

### [MODIFY] [EntryPoint.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/di/EntryPoint.kt)
Remove Fetch import and field:
```diff
-import com.tonyodev.fetch2.Fetch
...
-    val fetch: Fetch
```

### [MODIFY] [LightningDownloadListener.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/download/LightningDownloadListener.kt)
Remove unused fields and functions:
```diff
-    val fetch = hiltEntryPoint.fetch
...
-    private fun getFileName(id: Long): String {
-        val q = DownloadManager.Query()
-        q.setFilterById(id)
-        val c = downloadManager.query(q)
-        var filename = ""
-        if (c.moveToFirst()) {
-            val status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))
-            if (status == DownloadManager.STATUS_SUCCESSFUL) {
-                val filePath = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
-                filename = filePath.substring(filePath.lastIndexOf('/') + 1, filePath.length)
-            } else if (status == DownloadManager.STATUS_FAILED) {
-                // That stupidly returns "placeholder" on F(x)tec Pro1
-                //filename = c.getString(c.getColumnIndex(DownloadManager.COLUMN_REASON));
-                filename = "Failed"
-            }
-        }
-        c.close()
-        return filename
-    }
```

### Verification Command:
```powershell
.\gradlew.bat assembleSlionsFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`

---

## Task 5 — TabThumbnailCache Concurrency & Tests
Add atomic check-and-load lock mapping, capture and remove callback lists before firing completion notifications, add `@Volatile var customCacheDir`, and write robust unit tests.

### [MODIFY] [TabThumbnailCache.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabThumbnailCache.kt)
Update custom cache directory property, `diskDir()`, `get()` load/registration concurrency, and error handling:
```kotlin
    @Volatile
    var customCacheDir: File? = null

    /**
     * Returns the cache directory used for disk persistence, or null if the app context
     * is not yet available (e.g. during unit tests).
     */
    private fun diskDir(): File? = customCacheDir ?: try {
        File(fulguris.app.cacheDir, "tab_thumbnails")
    } catch (_: Exception) {
        null
    }
```
And rewrite `get()` method to implement atomic lock & callback handling:
```kotlin
    fun get(tabId: Int, persistable: Boolean = true, onLoaded: ((Bitmap?) -> Unit)? = null): Bitmap? {
        val mem = cache.get(tabId)
        if (mem != null && !mem.isRecycled) return mem

        // Memory miss — try disk only for non-incognito tabs
        if (!persistable) return null

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

        if (!startDiskLoad) {
            return null
        }

        val file = diskDir()?.let { File(it, "$tabId.jpg") } ?: run {
            val list = synchronized(pendingCallbacks) { pendingCallbacks.remove(tabId) }
            list?.let {
                synchronized(it) {
                    for (cb in it) cb(null)
                }
            }
            return null
        }

        if (!file.exists()) {
            val list = synchronized(pendingCallbacks) { pendingCallbacks.remove(tabId) }
            list?.let {
                synchronized(it) {
                    for (cb in it) cb(null)
                }
            }
            return null
        }

        diskExecutor.execute {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                mainHandler.post {
                    if (bitmap != null) {
                        cache.put(tabId, bitmap)
                        if (versionMap[tabId] == null) versionMap[tabId] = 1
                        Timber.d("Loaded thumbnail for tab $tabId from disk asynchronously")
                    }
                    val list = synchronized(pendingCallbacks) { pendingCallbacks.remove(tabId) }
                    list?.let {
                        synchronized(it) {
                            for (cb in it) cb(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read thumbnail for tab $tabId from disk asynchronously")
                mainHandler.post {
                    val list = synchronized(pendingCallbacks) { pendingCallbacks.remove(tabId) }
                    list?.let {
                        synchronized(it) {
                            for (cb in it) cb(null)
                        }
                    }
                }
            }
        }

        return null
    }
```

### [MODIFY] [TabThumbnailCacheTest.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/test/java/fulguris/browser/tabs/TabThumbnailCacheTest.kt)
Add robust concurrent and edge-case unit tests:
```kotlin
    @Test
    fun `customCacheDir configuration overrides default path successfully`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            TabThumbnailCache.put(42, bitmap, true)
            // Allow disk executor to complete writing
            Thread.sleep(100)
            
            val writtenFile = java.io.File(tempDir, "42.jpg")
            assertThat(writtenFile.exists()).isTrue()
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }

    @Test
    fun `file not found notifies all callbacks with null and cleans up pending list`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            var callbackCalled = false
            var callbackResult: Bitmap? = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            
            TabThumbnailCache.get(99, true) { result ->
                callbackCalled = true
                callbackResult = result
            }
            
            assertThat(callbackCalled).isTrue()
            assertThat(callbackResult).isNull()
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }

    @Test
    fun `concurrent get triggers only one disk read and notifies all callbacks`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            // Put a valid bitmap on disk first
            val initialBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            val file = java.io.File(tempDir, "100.jpg")
            java.io.FileOutputStream(file).use { out ->
                initialBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            
            var cb1Called = false
            var cb2Called = false
            var cb1Result: Bitmap? = null
            var cb2Result: Bitmap? = null
            
            // Invoke get from main thread. First call starts disk executor.
            TabThumbnailCache.get(100, true) { result ->
                cb1Called = true
                cb1Result = result
            }
            
            // Second call appends callback to in-flight load list.
            TabThumbnailCache.get(100, true) { result ->
                cb2Called = true
                cb2Result = result
            }
            
            // Loop main thread looper messages so background post runs
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            
            assertThat(cb1Called).isTrue()
            assertThat(cb2Called).isTrue()
            assertThat(cb1Result).isNotNull
            assertThat(cb2Result).isNotNull
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }
```

### Verification Command:
```powershell
.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.browser.tabs.TabThumbnailCacheTest"
```
Expected: `BUILD SUCCESSFUL` (all unit tests passed)

---

## Task 6 — VideoSniffer JS Bridge Input Validation
Clean up the unused legacy `onVideoPlaying()` interface callback and validate bridge inputs with permissive (`isAcceptableMediaUrl()`) and strict (`isDownloadableHttpUrl()`) checks.

### [MODIFY] [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt)
Implement URL validation and safety constraints:
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
And update `onVideoDetected()`:
```kotlin
    fun onVideoDetected(videoUrl: String, qualitiesJson: String?, resolution: String?, streamType: String = "direct") {
        if (!isAcceptableMediaUrl(videoUrl)) {
            Timber.w("Received invalid video URL: $videoUrl")
            return
        }

        val sanitizedResolution = resolution?.trim()?.take(20)

        var sanitizedQualities: Map<String, String>? = null
        if (qualitiesJson != null && qualitiesJson.length < 50000) {
            try {
                val json = JSONObject(qualitiesJson)
                val map = mutableMapOf<String, String>()
                val keys = json.keys()
                var count = 0
                while (keys.hasNext() && count < 20) {
                    val key = keys.next().trim().take(50)
                    val value = json.getString(key)
                    if (isDownloadableHttpUrl(value)) {
                        map[key] = value
                        count++
                    }
                }
                sanitizedQualities = map
            } catch (e: Exception) {
                Timber.e(e, "Error parsing video qualities")
            }
        }

        isVideoDetected = true
        detectedVideoUrl = videoUrl
        detectedResolution = sanitizedResolution?.takeIf { it.isNotBlank() }
        detectedStreamType = streamType.trim().take(20)
        detectedQualities = sanitizedQualities

        activity.runOnUiThread {
            if (!isShown) return@runOnUiThread
            if (isForeground) {
                showDownloadFab()
            }
        }
    }
```

### [MODIFY] [VideoJavascriptInterface.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/js/VideoJavascriptInterface.kt)
Remove `onVideoPlaying()` legacy shim:
```kotlin
package fulguris.js

import android.webkit.JavascriptInterface
import fulguris.view.WebPageTab

class VideoJavascriptInterface(private val tab: WebPageTab) {

    /** Called by the new detection script (all stream types). */
    @JavascriptInterface
    fun onVideoDetected(url: String, qualitiesJson: String?, resolution: String?, streamType: String) {
        tab.onVideoDetected(url, qualitiesJson, resolution, streamType)
    }
}
```

### Verification Command:
```powershell
.\gradlew.bat assembleSlionsFullDownloadDebug
```
Expected: `BUILD SUCCESSFUL`

---

## Task 7 — Repo Hygiene & Cleanup
Delete explicit transient logs, diff files, and build outputs from the repository root, and anchor their patterns in `.gitignore`.

### Files to delete from root only:
- `/build.log`
- `/build_askbar.log`
- `/build_askbar3.log`
- `/build_askbar4.log`
- `/build_clean.txt`
- `/build_clean_tail.txt`
- `/build_crashfix.log`
- `/build_crashfix2.log`
- `/build_dark.log`
- `/build_downloads_ui.log`
- `/build_err.log`
- `/build_error.log`
- `/build_error.txt`
- `/build_errors.txt`
- `/build_fix.log`
- `/build_log.txt`
- `/build_log_2.txt`
- `/build_log_2_tail.txt`
- `/build_log_3.txt`
- `/build_log_3_tail.txt`
- `/build_output.txt`
- `/build_output_toolbar.txt`
- `/build_reports_errors.txt`
- `/build_ui.log`
- `/compile_error.log`
- `/diff.txt`
- `/fulguris_logs.txt`
- `/kapt_debug_errors.txt`
- `/kapt_errors.txt`
- `/kapt_info.txt`
- `/kapt_stacktrace.txt`
- `/kapt_stubs.txt`
- `/kapt_tail.txt`
- `/logcat.txt`
- `/merge_res_log.txt`
- `/process_res_log.txt`
- `/realdiff.txt`
- `/tab_history.diff`

### [MODIFY] [.gitignore](file:///c:/Users/w/Desktop/Fulguris-main/.gitignore)
Remove the typos (`n# Android Studio` → `# Android Studio` and `t # Fastlane` → `# Fastlane`), and append root-anchored patterns:
```diff
-n# Android Studio
+# Android Studio
...
-t # Fastlane
+# Fastlane
...
+# Development and temporary log files (anchored to root only)
+/build_*.log
+/build_*.txt
+/kapt_*.txt
+/kapt_*.log
+/logcat.txt
+/fulguris_logs.txt
+/*.diff
+/process_res_log.txt
+/merge_res_log.txt
+/build.log
+/compile_error.log
```

### Verification Command:
```powershell
git status --porcelain
```
Expected: Only modified files are shown, no untracked log or diff files in root.
