# yt-dlp Integration Plan for XHub Browser

## Overview
This document outlines the plan to integrate yt-dlp as a backend for downloading videos that cannot be handled by Android's standard DownloadManager (blob:, HLS .m3u8, DASH .mpd streams).

## ⚠️ Important Considerations

### Legal & Policy
- **YouTube ToS**: Downloading YouTube videos violates their Terms of Service
- **Copyright**: Users must have rights to download content
- **Liability**: App could be removed from stores or face legal issues
- **User Warning**: Clear disclaimer needed before enabling this feature

### Technical Challenges
- **APK Size**: yt-dlp binary is ~15-20MB (significant size increase)
- **Updates**: yt-dlp requires frequent updates as platforms change their APIs
- **Python Dependency**: Standalone binaries need to be maintained per architecture
- **Security**: Executing binaries requires careful permission and validation handling

### Alternatives to Consider
1. **Web-based approach**: Use existing web download services
2. **Library alternatives**: Consider lighter-weight solutions like youtube-dl-android
3. **Service integration**: Use external download services instead of bundling binaries

## Proposed Architecture

### 1. Binary Management (YtDlpManager.kt)

```kotlin
package com.xhub.browser.download

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class YtDlpManager(private val context: Context) {
    
    companion object {
        private const val BINARY_NAME = "yt-dlp"
        private const val ASSET_PATH = "yt-dlp"
        private const val VERSION_FILE = "yt-dlp.version"
        private const val CURRENT_VERSION = "2024.01.01" // Update with actual version
    }
    
    private val binaryFile: File
        get() = File(context.filesDir, BINARY_NAME)
    
    private val versionFile: File
        get() = File(context.filesDir, VERSION_FILE)
    
    /**
     * Ensures yt-dlp binary is extracted and executable
     * @return true if binary is ready, false otherwise
     */
    fun ensureBinaryReady(): Boolean {
        try {
            // Check if binary exists and is up-to-date
            if (binaryFile.exists() && isVersionCurrent()) {
                Timber.d("yt-dlp binary already exists and is current")
                return binaryFile.canExecute()
            }
            
            // Extract from assets
            Timber.i("Extracting yt-dlp binary from assets")
            extractBinaryFromAssets()
            
            // Make executable
            if (!binaryFile.setExecutable(true, false)) {
                Timber.e("Failed to make yt-dlp executable")
                return false
            }
            
            // Save version
            versionFile.writeText(CURRENT_VERSION)
            
            Timber.i("yt-dlp binary ready at: ${binaryFile.absolutePath}")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to prepare yt-dlp binary")
            return false
        }
    }
    
    private fun extractBinaryFromAssets() {
        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(binaryFile).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    private fun isVersionCurrent(): Boolean {
        if (!versionFile.exists()) return false
        val installedVersion = versionFile.readText().trim()
        return installedVersion == CURRENT_VERSION
    }
    
    fun getBinaryPath(): String? {
        return if (binaryFile.exists() && binaryFile.canExecute()) {
            binaryFile.absolutePath
        } else {
            null
        }
    }
    
    /**
     * Deletes the binary (for cleanup or forcing re-extraction)
     */
    fun deleteBinary() {
        if (binaryFile.exists()) {
            binaryFile.delete()
        }
        if (versionFile.exists()) {
            versionFile.delete()
        }
    }
}
```

### 2. Download Service (YtDlpDownloadService.kt)

```kotlin
package com.xhub.browser.download

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xhub.browser.R
import com.xhub.browser.database.downloads.DownloadEntry
import com.xhub.browser.database.downloads.DownloadsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

@AndroidEntryPoint
class YtDlpDownloadService : Service() {
    
    @Inject
    lateinit var downloadsRepository: DownloadsRepository
    
    @Inject
    lateinit var ytDlpManager: YtDlpManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val activeDownloads = mutableMapOf<String, Process>()
    
    companion object {
        const val ACTION_START_DOWNLOAD = "com.xhub.browser.ACTION_START_YTDLP_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.xhub.browser.ACTION_CANCEL_YTDLP_DOWNLOAD"
        
        const val EXTRA_URL = "extra_url"
        const val EXTRA_OUTPUT_DIR = "extra_output_dir"
        const val EXTRA_FILENAME = "extra_filename"
        
        const val BROADCAST_DOWNLOAD_COMPLETE = "com.xhub.browser.YTDLP_DOWNLOAD_COMPLETE"
        const val BROADCAST_DOWNLOAD_FAILED = "com.xhub.browser.YTDLP_DOWNLOAD_FAILED"
        
        private const val NOTIFICATION_ID = 1001
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val outputDir = intent.getStringExtra(EXTRA_OUTPUT_DIR)
                val filename = intent.getStringExtra(EXTRA_FILENAME)
                
                if (url != null && outputDir != null) {
                    startDownload(url, outputDir, filename)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) {
                    cancelDownload(url)
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun startDownload(url: String, outputDir: String, filename: String?) {
        serviceScope.launch {
            try {
                // Ensure binary is ready
                if (!ytDlpManager.ensureBinaryReady()) {
                    Timber.e("yt-dlp binary not ready")
                    broadcastFailure(url, "Failed to prepare yt-dlp")
                    return@launch
                }
                
                val binaryPath = ytDlpManager.getBinaryPath()
                if (binaryPath == null) {
                    broadcastFailure(url, "yt-dlp binary not found")
                    return@launch
                }
                
                // Build command
                val outputPath = if (filename != null) {
                    "$outputDir/$filename"
                } else {
                    "$outputDir/%(title)s.%(ext)s"
                }
                
                val command = listOf(
                    binaryPath,
                    "--no-playlist",
                    "--no-warnings",
                    "--newline",
                    "-o", outputPath,
                    url
                )
                
                Timber.i("Starting yt-dlp download: $url")
                Timber.d("Command: ${command.joinToString(" ")}")
                
                // Execute
                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectErrorStream(true)
                
                val process = processBuilder.start()
                activeDownloads[url] = process
                
                // Read output
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                var downloadedFile: String? = null
                
                while (reader.readLine().also { line = it } != null) {
                    Timber.d("yt-dlp: $line")
                    
                    // Parse output for file path
                    line?.let {
                        if (it.contains("[download] Destination:")) {
                            downloadedFile = it.substringAfter("[download] Destination:").trim()
                        }
                    }
                }
                
                val exitCode = process.waitFor()
                activeDownloads.remove(url)
                
                if (exitCode == 0) {
                    Timber.i("Download completed successfully: $url")
                    
                    // Save to repository
                    if (downloadedFile != null) {
                        val file = File(downloadedFile!!)
                        val entry = DownloadEntry(
                            url = url,
                            title = file.nameWithoutExtension,
                            contentSize = file.length().toString(),
                            file = file.absolutePath
                        )
                        downloadsRepository.addDownloadIfNotExists(entry)
                    }
                    
                    broadcastSuccess(url, downloadedFile)
                } else {
                    Timber.e("Download failed with exit code: $exitCode")
                    broadcastFailure(url, "Download failed (exit code: $exitCode)")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error during yt-dlp download")
                activeDownloads.remove(url)
                broadcastFailure(url, e.message ?: "Unknown error")
            } finally {
                checkAndStopService()
            }
        }
    }
    
    private fun cancelDownload(url: String) {
        activeDownloads[url]?.destroy()
        activeDownloads.remove(url)
        checkAndStopService()
    }
    
    private fun broadcastSuccess(url: String, filePath: String?) {
        val intent = Intent(BROADCAST_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_URL, url)
            putExtra("file_path", filePath)
        }
        sendBroadcast(intent)
    }
    
    private fun broadcastFailure(url: String, error: String) {
        val intent = Intent(BROADCAST_DOWNLOAD_FAILED).apply {
            putExtra(EXTRA_URL, url)
            putExtra("error", error)
        }
        sendBroadcast(intent)
    }
    
    private fun checkAndStopService() {
        if (activeDownloads.isEmpty()) {
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel all active downloads
        activeDownloads.values.forEach { it.destroy() }
        activeDownloads.clear()
    }
}
```

### 3. WebPageTab.kt Modifications

Location of changes in `showVideoDownloadSheet()`:

```kotlin
// Around line where adaptive streams are detected
when {
    detectedStreamType in listOf("blob", "hls", "dash") -> {
        // OLD CODE (disabled button):
        // binding.btnVideoDownload.isEnabled = false
        // binding.tvAdaptiveStreamMessage.visibility = View.VISIBLE
        // binding.tvAdaptiveStreamMessage.text = getString(R.string.error_adaptive_stream)
        
        // NEW CODE (enable yt-dlp download):
        binding.btnVideoDownload.isEnabled = true
        binding.tvAdaptiveStreamMessage.visibility = View.VISIBLE
        binding.tvAdaptiveStreamMessage.text = getString(R.string.info_adaptive_stream_ytdlp)
        
        binding.btnVideoDownload.setOnClickListener {
            startYtDlpDownload(videoUrl, videoTitle)
            bottomSheetDialog.dismiss()
        }
    }
}

private fun startYtDlpDownload(url: String, title: String?) {
    val downloadDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    ).absolutePath
    
    val intent = Intent(activity, YtDlpDownloadService::class.java).apply {
        action = YtDlpDownloadService.ACTION_START_DOWNLOAD
        putExtra(YtDlpDownloadService.EXTRA_URL, url)
        putExtra(YtDlpDownloadService.EXTRA_OUTPUT_DIR, downloadDir)
        title?.let { putExtra(YtDlpDownloadService.EXTRA_FILENAME, "$it.mp4") }
    }
    
    activity?.startService(intent)
    
    // Show feedback
    activity?.let {
        Snackbar.make(
            it.findViewById(android.R.id.content),
            R.string.download_started_ytdlp,
            Snackbar.LENGTH_LONG
        ).show()
    }
}
```

### 4. Strings Resource Updates

Add to `strings.xml`:

```xml
<!-- yt-dlp download strings -->
<string name="info_adaptive_stream_ytdlp">This video uses adaptive streaming. Downloading via yt-dlp…</string>
<string name="download_started_ytdlp">Download started with yt-dlp</string>
<string name="error_ytdlp_not_ready">Video downloader not ready. Please try again.</string>
<string name="error_ytdlp_failed">Download failed: %1$s</string>
<string name="ytdlp_download_complete">Video downloaded successfully</string>

<!-- Warning dialog -->
<string name="warning_ytdlp_title">Video Download Notice</string>
<string name="warning_ytdlp_message">Downloading videos from some platforms may violate their Terms of Service. Only download videos you have the right to access. Continue?</string>
```

### 5. Layout Updates

`bottom_sheet_video_download.xml`:

```xml
<!-- Change tvAdaptiveStreamMessage color from error to info -->
<TextView
    android:id="@+id/tvAdaptiveStreamMessage"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:textColor="?attr/colorOnSurfaceVariant"  <!-- Changed from colorError -->
    android:visibility="gone"
    ... />
```

### 6. AndroidManifest.xml Updates

```xml
<manifest>
    <!-- Permissions (already exists in browser) -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    
    <application>
        <!-- Register service -->
        <service
            android:name=".download.YtDlpDownloadService"
            android:exported="false" />
    </application>
</manifest>
```

## Implementation Steps

### Phase 1: Binary Setup
1. Download yt-dlp ARM64 binary from official releases
2. Test binary on Android device manually
3. Place in `app/src/main/assets/yt-dlp`
4. Update `.gitignore` if binary is too large (consider download-on-demand)

### Phase 2: Core Implementation
1. Implement `YtDlpManager.kt`
2. Implement `YtDlpDownloadService.kt`
3. Add Hilt injection modules if needed
4. Test binary extraction and execution

### Phase 3: UI Integration
1. Update `WebPageTab.kt` with yt-dlp routing
2. Update `bottom_sheet_video_download.xml` styling
3. Add strings to `strings.xml`
4. Update `AndroidManifest.xml`

### Phase 4: User Experience
1. Add user warning dialog on first use
2. Add progress notifications
3. Handle errors gracefully
4. Add settings toggle to enable/disable feature

### Phase 5: Testing
1. Test with YouTube videos
2. Test with Twitch streams
3. Test with HLS/DASH streams
4. Test cancellation
5. Test offline behavior
6. Test storage permissions

## Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Legal issues | High | Add clear warnings, make opt-in |
| APK size increase | Medium | Consider on-demand download of binary |
| Platform blocks | High | Regular yt-dlp updates needed |
| Security concerns | High | Validate binary hash, sandbox execution |
| Battery drain | Medium | Use WorkManager for background downloads |
| Storage issues | Medium | Check available space before download |

## Alternative Approaches

### 1. On-Demand Binary Download
Instead of bundling, download yt-dlp on first use:
- Pros: Smaller APK, always latest version
- Cons: Requires internet, trust issues, complexity

### 2. Use yt-dlp Web API
Use a web service wrapper:
- Pros: No binary management, always updated
- Cons: Privacy concerns, requires server, costs

### 3. Integration with External Apps
Share video URL to apps like NewPipe:
- Pros: No maintenance, no legal issues
- Cons: Requires other app installed

## Recommendation

**Before implementing**, consider:

1. **Start with a warning dialog** that explains legal implications
2. **Make it opt-in** via settings (disabled by default)
3. **Consider on-demand download** instead of bundling
4. **Add usage analytics** to see if feature is worth maintaining
5. **Prepare for platform pushback** (app store rejection risk)

## Next Steps

1. **Legal review**: Consult with legal team about ToS implications
2. **User research**: Survey users about interest in this feature
3. **Prototype**: Build minimal version to test feasibility
4. **Store policy review**: Check Google Play and F-Droid policies
5. **Decision**: Go/No-go based on above findings

---

**Status**: Planning phase - awaiting approval to proceed with implementation

**Created**: 2026-06-14  
**Author**: XHub Development Team
