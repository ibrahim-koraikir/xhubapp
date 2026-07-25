# yt-dlp Integration Implementation Guide

## Status: Core Components Created ✅

The following files have been created:
- ✅ `YtDlpManager.kt` - Binary management
- ✅ `YtDlpDownloadService.kt` - Download service
- ⏳ Remaining integrations needed

## Next Steps Required

### 1. Obtain yt-dlp Binary

**Download the ARM64 binary:**
```bash
# Download from official yt-dlp releases
# https://github.com/yt-dlp/yt-dlp/releases

# For Android ARM64, you need the standalone binary
wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64

# Rename and place in assets
mv yt-dlp_linux_aarch64 app/src/main/assets/yt-dlp
```

**Important**: The binary is ~15-20MB. Consider these options:
1. Bundle in APK (increases size significantly)
2. Download on-demand (better for APK size, requires network)
3. Use multiple ABIs (arm64-v8a, armeabi-v7a)

### 2. Update strings.xml

Add these strings to `app/src/main/res/values/strings.xml`:

```xml
<!-- yt-dlp Video Download -->
<string name="video_adaptive_stream_message_ytdlp">This video uses adaptive streaming. Downloading via yt-dlp…</string>
<string name="video_download_started">Download started</string>
<string name="video_download_complete">Video downloaded successfully</string>
<string name="video_download_failed">Download failed: %1$s</string>
<string name="video_downloading">Downloading video…</string>
<string name="video_ytdlp_not_ready">Video downloader not ready. Please try again.</string>

<!-- Warning Dialog -->
<string name="warning_ytdlp_title">Video Download Notice</string>
<string name="warning_ytdlp_message">Downloading videos from some platforms may violate their Terms of Service. Only download videos you have the right to access.\n\nDo you want to continue?</string>
<string name="action_continue">Continue</string>
<string name="action_cancel">Cancel</string>
```

### 3. Update bottom_sheet_video_download.xml

Change the adaptive stream message color from error to informational:

```xml
<TextView
    android:id="@+id/tvAdaptiveStreamMessage"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:textColor="?attr/colorOnSurfaceVariant"
    <!-- CHANGED FROM: android:textColor="?attr/colorError" -->
    android:visibility="gone"
    ... />
```

### 4. Update WebPageTab.kt

Replace the adaptive stream handling section (around line 319):

```kotlin
if (isAdaptiveOnly && (qualities == null || qualities.all { classifyUrl(it.value) != "direct" })) {
    // ENABLE yt-dlp download for adaptive streams
    tvAdaptiveMessage.visibility = View.VISIBLE
    tvAdaptiveMessage.text = activity.getString(R.string.video_adaptive_stream_message_ytdlp)
    
    // ENABLE button instead of disabling
    btnDownload.isEnabled = true
    btnDownload.text = activity.getString(R.string.action_download)
    btnDownload.icon = ContextCompat.getDrawable(activity, R.drawable.ic_download_outline)
    
    containerQualityPicker.visibility = View.GONE
    
    // Override the button click listener for yt-dlp
    btnDownload.setOnClickListener {
        showYtDlpWarningAndDownload(videoUrl, "$pageTitle.$inferredExtension")
        dialog.dismiss()
        hideDownloadFab()
    }
} else {
    // Existing direct download logic
    tvAdaptiveMessage.visibility = View.GONE
    btnDownload.isEnabled = true
    btnDownload.text = activity.getString(R.string.action_download)
    btnDownload.icon = ContextCompat.getDrawable(activity, R.drawable.ic_download_outline)
    
    // ... rest of existing quality picker logic ...
}
```

Add these new methods to WebPageTab.kt:

```kotlin
private fun showYtDlpWarningAndDownload(url: String, filename: String) {
    // Show warning dialog first
    androidx.appcompat.app.AlertDialog.Builder(activity)
        .setTitle(R.string.warning_ytdlp_title)
        .setMessage(R.string.warning_ytdlp_message)
        .setPositiveButton(R.string.action_continue) { _, _ ->
            startYtDlpDownload(url, filename)
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

private fun startYtDlpDownload(url: String, filename: String) {
    // Use the helper method from YtDlpDownloadService
    YtDlpDownloadService.startDownload(
        context = activity,
        url = url,
        filename = filename,
        pageTitle = titleInfo.getTitle()
    )
    
    // Show feedback
    com.google.android.material.snackbar.Snackbar.make(
        activity.findViewById(android.R.id.content),
        R.string.video_download_started,
        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
    ).show()
}
```

### 5. Register Service in AndroidManifest.xml

Add the service declaration inside the `<application>` tag:

```xml
<application>
    ...
    
    <!-- yt-dlp Download Service -->
    <service
        android:name=".download.YtDlpDownloadService"
        android:exported="false"
        android:foregroundServiceType="dataSync"
        android:enabled="true" />
        
    ...
</application>
```

### 6. Add Permissions (if not already present)

These should already exist in a browser app:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

### 7. Handle Download Broadcasts (Optional Enhancement)

To show completion notifications in the browser, register a broadcast receiver:

```kotlin
// In WebBrowserActivity or appropriate place
private val ytDlpReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            YtDlpDownloadService.BROADCAST_DOWNLOAD_COMPLETE -> {
                val filePath = intent.getStringExtra(YtDlpDownloadService.EXTRA_FILE_PATH)
                // Show success message
                snackbar(R.string.video_download_complete)
            }
            YtDlpDownloadService.BROADCAST_DOWNLOAD_FAILED -> {
                val error = intent.getStringExtra(YtDlpDownloadService.EXTRA_ERROR)
                // Show error message
                snackbar(getString(R.string.video_download_failed, error ?: "Unknown"))
            }
        }
    }
}

// Register in onCreate
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val filter = IntentFilter().apply {
        addAction(YtDlpDownloadService.BROADCAST_DOWNLOAD_COMPLETE)
        addAction(YtDlpDownloadService.BROADCAST_DOWNLOAD_FAILED)
    }
    registerReceiver(ytDlpReceiver, filter, RECEIVER_NOT_EXPORTED)
}

// Unregister in onDestroy
override fun onDestroy() {
    super.onDestroy()
    unregisterReceiver(ytDlpReceiver)
}
```

## Testing Checklist

### Phase 1: Binary Setup
- [ ] Download yt-dlp ARM64 binary
- [ ] Place in `app/src/main/assets/yt-dlp`
- [ ] Build APK and verify binary is included
- [ ] Test binary extraction on first launch
- [ ] Verify binary has execute permissions

### Phase 2: Basic Download
- [ ] Find a test video with HLS/DASH stream
- [ ] Tap download button
- [ ] Verify warning dialog appears
- [ ] Accept warning and start download
- [ ] Check logcat for yt-dlp output
- [ ] Verify notification appears and updates
- [ ] Confirm file appears in Downloads folder
- [ ] Verify entry appears in Downloads list

### Phase 3: Error Handling
- [ ] Test with invalid URL
- [ ] Test with network disconnected
- [ ] Test with no storage space
- [ ] Test cancellation mid-download
- [ ] Verify error notifications appear

### Phase 4: Edge Cases
- [ ] Test with very long video titles
- [ ] Test with special characters in title
- [ ] Test multiple simultaneous downloads
- [ ] Test app closure during download (should continue)
- [ ] Test with playlist URL (should download single video)

## Known Limitations

1. **APK Size**: Binary adds ~15-20MB to APK
2. **Updates**: yt-dlp needs periodic updates as platforms change
3. **Battery**: Downloads can drain battery on long videos
4. **Storage**: No automatic cleanup of downloaded files
5. **Format**: Always downloads best quality (mp4 preferred)

## Future Enhancements

1. **On-demand binary download**: Download yt-dlp when first needed
2. **Format selection**: Let users choose quality/format
3. **Background sync**: Use WorkManager for better reliability
4. **Progress UI**: Show download progress in app
5. **Auto-update**: Check for and download new yt-dlp versions
6. **Settings**: Add preferences for download location, format, etc.

## Troubleshooting

### Binary not executing
```bash
# Test manually via adb
adb shell
cd /data/data/com.xhub.browser/files
ls -la yt-dlp
./yt-dlp --version
```

### Downloads failing
- Check logcat: `adb logcat | grep -i ytdlp`
- Verify network permission
- Verify storage permission (Android 10+)
- Try downloading manually with same URL

### Build issues
- Clean build: `./gradlew clean`
- Check asset is included in APK
- Verify manifest service declaration

## Security Considerations

1. **Binary integrity**: Consider verifying binary hash on extraction
2. **Storage permissions**: Request appropriately based on Android version
3. **URL validation**: Sanitize URLs before passing to yt-dlp
4. **Filename sanitization**: Already implemented in service
5. **User consent**: Warning dialog implemented

## Legal Disclaimer

⚠️ **Important**: Downloading videos from platforms like YouTube violates their Terms of Service. This feature should:
- Include clear warnings to users
- Be opt-in (disabled by default in settings)
- Include disclaimer about legal implications
- Be used only for content the user has rights to download

Consider adding a settings toggle to enable/disable this feature globally.

---

**Implementation Status**: Core components ready, integration pending
**Next Action**: Follow steps 1-7 above to complete integration
**Estimated Time**: 2-3 hours for full integration and testing
