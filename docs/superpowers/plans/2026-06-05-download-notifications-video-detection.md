# Download Notifications, List Visibility & Video Detection Implementation Plan
Goal: Fix silent download notifications, restore list titles, and make video/adaptive stream detection robust.
Architecture: Standardize notifications using system DownloadManager.Query in BroadcastReceiver. Revise injected JS for DOM mutations and media classification.
Tech Stack: Android DownloadManager, Kotlin, JavaScript, Hilt Dependency Injection.
---

## Task 1: Fix download list titles
Ensure that the guessed filename (instead of raw URL) is stored as the title when enqueuing requests in DownloadManager.

### Code Changes
Modify [DownloadHandler.java](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/download/DownloadHandler.java) to set the request title to `iFilename`.

In `onDownloadStartNoStream()` (~line 287), after:
```java
        request.setDescription(webAddress.getHost());
```
Add:
```java
        request.setTitle(iFilename);
```

In `onDownloadStartNoStreamWithFilename()` (~line 423), after:
```java
        request.setDescription(webAddress.getHost());
```
Add:
```java
        request.setTitle(iFilename);
```

### Verification
Command:
```powershell
.\gradlew.bat compileSlionsFullDownloadDebugJavaWithJavac
```
Expected output:
`BUILD SUCCESSFUL`

---

## Task 2: Request POST_NOTIFICATIONS permission on Android 13+
Prompt the user for permission to show notifications on app startup.

### Code Changes
Modify [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt).

Add imports:
```kotlin
import android.Manifest
import fulguris.permissions.PermissionsResultAction
```

In `initialize()` (~line 1029), after `createNotificationChannel()`, add:
```kotlin
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                object : PermissionsResultAction() {
                    override fun onGranted() {
                        Timber.d("POST_NOTIFICATIONS granted")
                    }
                    override fun onDenied(permission: String) {
                        Timber.d("POST_NOTIFICATIONS denied — download notifications suppressed")
                    }
                }
            )
        }
```

### Verification
Command:
```powershell
.\gradlew.bat compileSlionsFullDownloadDebugKotlin
```
Expected output:
`BUILD SUCCESSFUL`

---

## Task 3: Revive LightningDownloadListener's onReceive and remove Fetch2 listeners
Handle download completion notifications via BroadcastReceiver querying the system `DownloadManager` status.

### Code Changes
Modify [LightningDownloadListener.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/download/LightningDownloadListener.kt).

1. Remove Fetch2 imports:
```kotlin
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
```

2. Remove the entire `init` block (lines 56–123).

3. Rewrite `onReceive`:
```kotlin
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return

        val q = DownloadManager.Query().setFilterById(id)
        downloadManager.query(q)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val filename = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                ?.takeIf { it.isNotBlank() } ?: downloadHandler.iFilename

            val notifMgr = NotificationManagerCompat.from(mActivity)
            val channelId = (mActivity as WebBrowserActivity).CHANNEL_ID
            val builder = NotificationCompat.Builder(mActivity, channelId)
                .setSmallIcon(R.drawable.ic_download_outline)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (NotificationManagerCompat.from(mActivity).areNotificationsEnabled()) {
                        notifMgr.notify(id.toInt(), builder
                            .setContentTitle(mActivity.getString(R.string.download_complete))
                            .setContentText(filename).build())
                    }
                    mActivity.makeSnackbar(
                        mActivity.getString(R.string.download_complete),
                        KDuration,
                        if (mActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                    ).setAction(R.string.show) {
                        (mActivity as WebBrowserActivity).openDownloads()
                    }.show()
                }
                DownloadManager.STATUS_FAILED -> {
                    if (NotificationManagerCompat.from(mActivity).areNotificationsEnabled()) {
                        notifMgr.notify(id.toInt(), builder
                            .setContentTitle(mActivity.getString(R.string.download_failed))
                            .setContentText(filename).build())
                    }
                    mActivity.snackbar(
                        mActivity.getString(R.string.download_failed),
                        if (mActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                    )
                }
            }
        }
    }
```

### Verification
Command:
```powershell
.\gradlew.bat compileSlionsFullDownloadDebugKotlin
```
Expected output:
`BUILD SUCCESSFUL`

---

## Task 4: Enhance Video Detection JS Injection
Inject a robust, mutation-aware script for video scanning, anchors detection, and stream type classification.

### Code Changes
Modify [WebPageClient.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageClient.kt).

Replace `val videoScript = """ ... """.trimIndent()` (~lines 438-477) with:
```kotlin
        val videoScript = """
            (function() {
                if (window.__FulgurisVideoSnifferInstalled) return;
                window.__FulgurisVideoSnifferInstalled = true;

                function classifyUrl(url) {
                    if (!url) return 'unknown';
                    if (url.startsWith('blob:')) return 'blob';
                    if (url.indexOf('.m3u8') !== -1) return 'hls';
                    if (url.indexOf('.mpd') !== -1) return 'dash';
                    if (/^https?:\/\//i.test(url)) return 'direct';
                    return 'unknown';
                }

                function buildQualities(video) {
                    var qualities = {};
                    var sources = video.querySelectorAll('source');
                    for (var i = 0; i < sources.length; i++) {
                        var s = sources[i];
                        var sUrl = s.src || s.getAttribute('src') || '';
                        if (!sUrl) continue;
                        var label = s.getAttribute('label')
                            || s.getAttribute('title')
                            || s.getAttribute('data-res')
                            || s.getAttribute('res')
                            || s.getAttribute('size')
                            || (video.videoHeight > 0 ? video.videoHeight + 'p' : null)
                            || ('Source ' + (i + 1));
                        qualities[label] = sUrl;
                    }
                    // Also scan nearby <a> download links
                    var anchors = document.querySelectorAll('a[href]');
                    for (var j = 0; j < anchors.length; j++) {
                        var href = anchors[j].href || '';
                        if (/\.(mp4|webm|m4v|ogv|mkv)(\?|$)/i.test(href)) {
                            var aLabel = anchors[j].getAttribute('data-res')
                                || anchors[j].getAttribute('label')
                                || anchors[j].textContent.trim().substring(0, 30)
                                || 'Download ' + (j + 1);
                            qualities[aLabel] = href;
                        }
                    }
                    return qualities;
                }

                function reportVideo(video) {
                    var url = video.currentSrc || video.src || '';
                    if (!url) return;
                    var streamType = classifyUrl(url);
                    var qualities = buildQualities(video);
                    if (Object.keys(qualities).length === 0) {
                        qualities['Default'] = url;
                    }
                    var resolution = (video.videoHeight > 0) ? video.videoHeight + 'p' : '';
                    if (window.VideoSniffer) {
                        window.VideoSniffer.onVideoDetected(
                            url,
                            JSON.stringify(qualities),
                            resolution,
                            streamType
                        );
                    }
                }

                function scanAllVideos() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (v.__FulgurisAttached) continue;
                        v.__FulgurisAttached = true;
                        v.addEventListener('loadedmetadata', function() { reportVideo(this); });
                        v.addEventListener('playing', function() { reportVideo(this); });
                        // Report immediately if already has a source
                        if (v.readyState >= 1 && (v.currentSrc || v.src)) {
                            reportVideo(v);
                        }
                    }
                }

                // Initial scan
                scanAllVideos();

                // Watch for new video elements added dynamically (debounced)
                var debounceTimer = null;
                var observer = new MutationObserver(function() {
                    clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(scanAllVideos, 500);
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
            })();
        """.trimIndent()
```

### Verification
Command:
```powershell
.\gradlew.bat compileSlionsFullDownloadDebugKotlin
```
Expected output:
`BUILD SUCCESSFUL`

---

## Task 5: Update VideoJavascriptInterface to accept stream type and fallback legacy shim
Accept `streamType` and proxy to the tab. Keep the old method signature as a no-op fallback.

### Code Changes
Modify [VideoJavascriptInterface.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/js/VideoJavascriptInterface.kt).

Replace contents of the file:
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

    /** Legacy shim — new script calls onVideoDetected instead. */
    @JavascriptInterface
    fun onVideoPlaying(url: String, qualitiesJson: String?, resolution: String?) {
        // no-op: superseded by onVideoDetected
    }
}
```

### Verification
Command:
```powershell
.\gradlew.bat compileSlionsFullDownloadDebugKotlin
```
Expected output:
`BUILD SUCCESSFUL`

---

## Task 6: Update WebPageTab fields and video detection handler
Track the `detectedStreamType` and adjust picker logic.

### Code Changes
Modify [WebPageTab.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/view/WebPageTab.kt).

1. Add `detectedStreamType` field:
Under `detectedResolution` (~line 241), add:
```kotlin
    var detectedStreamType: String = "direct"
        private set
```

2. Reset it in `clearVideoDetectedState()`:
```kotlin
    private fun clearVideoDetectedState() {
        if (isVideoDetected) {
            isVideoDetected = false
            detectedVideoUrl = null
            detectedQualities = null
            detectedResolution = null
            detectedStreamType = "direct"
            activity.runOnUiThread { hideDownloadFab() }
        }
    }
```

3. Update signature and assignment in `onVideoDetected()` (~line 753):
```kotlin
    fun onVideoDetected(videoUrl: String, qualitiesJson: String?, resolution: String?, streamType: String = "direct") {
        isVideoDetected = true
        detectedVideoUrl = videoUrl
        detectedResolution = resolution?.takeIf { it.isNotBlank() }
        detectedStreamType = streamType

        if (qualitiesJson != null) {
```

4. Helper class classification function inside `WebPageTab` class:
```kotlin
    private fun classifyUrl(url: String): String {
        return when {
            url.startsWith("blob:") -> "blob"
            url.contains(".m3u8") -> "hls"
            url.contains(".mpd") -> "dash"
            else -> "direct"
        }
    }
```

5. Modify `showVideoDownloadSheet()` to display the warning and disable download for adaptive streams.
In `showVideoDownloadSheet()`:
- Change the qualities check to `>= 1`:
```kotlin
        val tvAdaptiveMessage = sheetView.findViewById<TextView>(R.id.tvAdaptiveStreamMessage)
        val btnDownload = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVideoDownload)
        val isAdaptiveOnly = detectedStreamType in listOf("blob", "hls", "dash")

        if (isAdaptiveOnly && (qualities == null || qualities.all { classifyUrl(it.value) != "direct" })) {
            tvAdaptiveMessage.visibility = View.VISIBLE
            tvAdaptiveMessage.text = activity.getString(R.string.video_adaptive_stream_message)
            btnDownload.isEnabled = false
            btnDownload.text = activity.getString(R.string.video_cannot_download)
            btnDownload.setIconResource(0) // Remove download icon if disabled
            containerQualityPicker.visibility = View.GONE
        } else {
            tvAdaptiveMessage.visibility = View.GONE
            btnDownload.isEnabled = true
            btnDownload.text = activity.getString(R.string.action_download)
            btnDownload.setIconResource(R.drawable.ic_download_outline)

            if (qualities != null && qualities.size >= 1) {
                containerQualityPicker.visibility = View.VISIBLE
                val qualityList = qualities.entries.toList()
                qualityList.forEachIndexed { index, entry ->
                    val rb = RadioButton(activity).apply {
                        id = index
                        text = entry.key
                        isChecked = index == 0
                        setTextColor(
                            activity.getColor(
                                com.google.android.material.R.color.m3_sys_color_dynamic_dark_on_surface
                            )
                        )
                    }
                    radioGroup.addView(rb)
                }
                selectedDownloadUrl = qualityList[0].value
                tvCurrentQuality.text = qualityList[0].key

                radioGroup.setOnCheckedChangeListener { _, checkedId ->
                    if (checkedId >= 0 && checkedId < qualityList.size) {
                        selectedDownloadUrl = qualityList[checkedId].value
                        tvCurrentQuality.text = qualityList[checkedId].key
                    }
                }
            }
        }
```

### Verification
Command:
```powershell
.\gradlew.bat compileSlionsFullDownloadDebugKotlin
```
Expected output:
`BUILD SUCCESSFUL`

---

## Task 7: Define resources in layouts and strings
Add new layout TextView and translation string resources.

### Layout Changes
Modify [bottom_sheet_video_download.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/bottom_sheet_video_download.xml).

Insert the text view below `containerQualityPicker` layout (~line 172):
```xml
    <TextView
        android:id="@+id/tvAdaptiveStreamMessage"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:paddingHorizontal="24dp"
        android:textSize="14sp"
        android:textColor="?attr/colorError"
        android:visibility="gone" />
```

### Strings Changes
Modify [strings.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/values/strings.xml).

Before `</resources>` at the end of the file:
```xml
    <string name="video_adaptive_stream_message">This video uses adaptive streaming (e.g. YouTube) and cannot be downloaded directly by the browser.</string>
    <string name="video_cannot_download">Cannot Download</string>
```

### Verification
Command:
```powershell
.\gradlew.bat assembleSlionsFullDownloadDebug
```
Expected output:
`BUILD SUCCESSFUL`
