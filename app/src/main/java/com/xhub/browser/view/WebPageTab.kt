/*
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright 2014 A.C.R. Development
 */

package com.xhub.browser.view

import com.xhub.browser.Capabilities
import com.xhub.browser.R
import com.xhub.browser.activity.ThemedActivity
import com.xhub.browser.browser.TabModel
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.browser.WebBrowser
import com.xhub.browser.browser.tabs.TabThumbnailCache
import com.xhub.browser.utils.isHomeUri
import com.xhub.browser.utils.isStartPageUrl
import com.xhub.browser.utils.isBookmarkUri
import com.xhub.browser.utils.isBookmarkUrl
import com.xhub.browser.utils.VideoValidationHelper
import com.xhub.browser.dialog.LightningDialogBuilder
import com.xhub.browser.download.DownloadFormat
import com.xhub.browser.download.LightningDownloadListener
import com.xhub.browser.extensions.*
import com.xhub.browser.isSupported
import com.xhub.browser.network.NetworkConnectivityModel
import com.xhub.browser.settings.fragment.DisplaySettingsFragment.Companion.MIN_BROWSER_TEXT_SIZE
import com.xhub.browser.settings.preferences.DomainPreferences
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.settings.preferences.userAgent
import com.xhub.browser.settings.preferences.webViewEngineVersionDesktop
import com.xhub.browser.settings.preferences.setReducedClientHints
import com.xhub.browser.ssl.SslState
import com.xhub.browser.js.VideoJavascriptInterface
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONObject
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View.OnScrollChangeListener
import android.view.View.OnTouchListener
import android.view.PixelCopy
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebSettings.LOAD_DEFAULT
import android.webkit.WebSettings.LOAD_NO_CACHE
import android.webkit.WebSettings.LayoutAlgorithm
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.collection.ArrayMap
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.EntryPointAccessors
import com.xhub.browser.constant.Hosts
import com.xhub.browser.constant.Schemes
import com.xhub.browser.constant.Uris
import com.xhub.browser.constant.WINDOWS_DESKTOP_USER_AGENT_PREFIX
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.di.configPrefs
import com.xhub.browser.enums.LayerType
import com.xhub.browser.extensions.canScrollVertically
import com.xhub.browser.extensions.dp
import com.xhub.browser.extensions.isDarkTheme
import com.xhub.browser.extensions.makeSnackbar
import com.xhub.browser.extensions.px
import com.xhub.browser.extensions.removeFromParent
import com.xhub.browser.extensions.setIcon
import com.xhub.browser.utils.ThemeUtils
import com.xhub.browser.utils.isBookmarkUrl
import com.xhub.browser.utils.isHistoryUrl
import com.xhub.browser.utils.isSpecialUrl
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * [WebPageTab] acts as a tab for the browser, handling WebView creation and handling logic, as
 * well as properly initialing it. All interactions with the WebView should be made through this
 * class.
 */
class WebPageTab(
    private val activity: Activity,
    tabInitializer: TabInitializer,
    val isIncognito: Boolean,
    // TODO: Could we remove those?
    private val homePageInitializer: HomePageInitializer,
    private val incognitoPageInitializer: IncognitoPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer,
    private val historyPageInitializer: HistoryPageInitializer
): WebView.FindListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * A persistent logical tab key (unique ID) used for thumbnail caching and session restoration.
     * Persisted across sessions via TabModel to ensure stable thumbnail cache keys.
     *
     * For new tabs we generate a random positive Int instead of [View.generateViewId()] because
     * generateViewId() resets its counter on every process restart, which causes id collisions
     * between new tabs and disk thumbnails left by closed tabs from a previous session.
     * A random Int in [1, Int.MAX_VALUE) has a collision probability of ~1 in 2 billion per tab
     * and is persisted with the tab state bundle, giving us stable keys across restarts.
     */
    val id = if (tabInitializer is FreezableBundleInitializer && tabInitializer.tabModel.tabId != -1) {
        tabInitializer.tabModel.tabId
    } else {
        kotlin.random.Random.nextInt(1, Int.MAX_VALUE)
    }

    /**
     * Getter for the [WebPageHeader] of the current [WebPageTab] instance.
     */
    val titleInfo: WebPageHeader

    /**
     * Meta theme-color content value as extracted from page HTML
     */
    var htmlMetaThemeColor: Int = KHtmlMetaThemeColorInvalid

    /**
     * Flag to indicate if we should fetch HTML meta theme-color and color-scheme.
     * Set to false after first extraction attempt.
     */
    var shouldFetchMetaTags = true

    /**
     * Optional callback to execute after the next page finishes loading or is cancelled.
     * Will be executed once and then cleared automatically.
     */
    internal var onLoadCompleteCallback: (() -> Unit)? = null

    @Volatile
    private var captureSequence = 0

    /**
     * Timestamp of the last successful preview capture to prevent spamming.
     */
    private var lastCaptureTime = 0L

    /**
     * Runnable reference for the pending delayed capture task.
     * Used to cancel the delayed task before it executes.
     */
    @Volatile
    private var captureRunnable: Runnable? = null


    /**
     * Wrapper class to store ConsoleMessage with timestamp since webkit.ConsoleMessage doesn't expose timestamp
     */
    data class ConsoleMessage(
        val consoleMessage: android.webkit.ConsoleMessage,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Track all console messages for the current page
    private val consoleMessages = mutableListOf<ConsoleMessage>()

    /**
     * Get all console messages for the current page
     */
    fun getConsoleMessages(): List<ConsoleMessage> = synchronized(consoleMessages) { consoleMessages.toList() }

    /**
     * Clear tracked console messages
     */
    fun clearConsoleMessages() {
        synchronized(consoleMessages) {
            consoleMessages.clear()
        }
    }

    /**
     * Add a console message to the collection with timestamp
     */
    fun addConsoleMessage(consoleMessage: android.webkit.ConsoleMessage) {
        synchronized(consoleMessages) {
            consoleMessages.add(ConsoleMessage(consoleMessage))
        }
    }

    /**
     * A tab initializer that should be run when the view is first attached.
     * Notably contains a bundle to be load in our webView.
     */
    private var latentTabInitializer: FreezableBundleInitializer? = null

    /**
     * Gets the current WebView instance of the tab.
     *
     * @return the WebView instance of the tab, which can be null.
     */
    var webView: WebViewEx? = null
        private set

    /**
     * The WebPageClient instance for this tab.
     * Provides access to page loading events and request tracking.
     */
    lateinit var webPageClient: WebPageClient
        private set

    /**
     * The URL we tried to load
     */
    private var iTargetUrl: Uri = Uri.parse("")

    /**
     * Public getter and setter for the target URL that we are attempting to load
     */
    var targetUrl: Uri
        get() = iTargetUrl
        set(value) {
            // Only clear video detection if we're navigating to a different URL
            if (iTargetUrl != value) {
                clearVideoDetectedState()
            }
            iTargetUrl = value
        }

    var isShowingDirectAd = false

    /**
     * Resets the direct-ad state flag when the user navigates away from ad content.
     * Call this from every navigation entry point ([goBack], [goForward],
     * [loadUrl] without the ad flag, etc.) so that the ad-blocker bypass is
     * re-enabled as soon as the user leaves the ad page.
     *
     * Centralising the reset here means new navigation methods added later
     * don't accidentally leave the bypass active on normal content.
     */
    fun resetDirectAdState() {
        isShowingDirectAd = false
    }

    var isVideoDetected = false
        private set
    var detectedVideoUrl: String? = null
        private set
    var detectedQualities: Map<String, String>? = null
        private set
    var detectedResolution: String? = null
        private set
    var detectedStreamType: String = "direct"
        private set

    fun clearVideoDetectedState() {
        if (isVideoDetected) {
            isVideoDetected = false
            detectedVideoUrl = null
            detectedQualities = null
            detectedResolution = null
            detectedStreamType = "direct"
            activity.runOnUiThread { hideDownloadFab() }
        }
    }

    private fun showDownloadFab() {
        // Only show download FAB if video detection is enabled in settings
        if (!userPreferences.videoDetectionEnabled) {
            return
        }
        
        val fab = activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabDownloadVideo)
        fab?.let {
            it.setOnClickListener { _ ->
                showVideoDownloadSheet()
            }
            // Only play the entrance animation when the FAB is actually appearing.
            // onVideoDetected() can fire repeatedly for the same video (loadedmetadata,
            // playing, canplay, MutationObserver, the periodic fallback scan, ...), so
            // re-running the animation every tick would make it flicker/pulse. Guard on
            // the current visibility so we animate the transition GONE/INVISIBLE -> VISIBLE once.
            if (it.visibility != View.VISIBLE) {
                // Cancel any in-flight animation (e.g. a hide fade-out that hasn't finished).
                it.animate().cancel()
                it.visibility = View.VISIBLE
                it.alpha = 0f
                it.scaleX = FAB_ENTRANCE_START_SCALE
                it.scaleY = FAB_ENTRANCE_START_SCALE
                it.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(FAB_ENTRANCE_DURATION_MS)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .setListener(null)
                    .start()
            }
        }
    }

    /**
     * Injects the video sniffer JavaScript into the current WebView.
     * Safe to call multiple times — the script guards itself with window._vdInit.
     * Should be called from onPageFinished and onProgressChanged(100) to survive
     * redirect/cache scenarios where onPageFinished may be skipped.
     */
    fun injectVideoSniffer() {
        if (!userPreferences.videoDetectionEnabled) return
        val view = webView ?: return

        val videoScript = """
            (function() {
                if (window._vdInit) return;
                window._vdInit = true;

                var anchorQualities = null;

                // Narrow, path-scoped patterns for known embedded players. We deliberately match
                // the embed PATH (not just the host) so a real <video> whose src happens to live on
                // one of these hosts is never misclassified as an embed.
                var EMBED_PATTERNS = [
                    /youtube\.com\/embed\//i,
                    /youtube-nocookie\.com\/embed\//i,
                    /player\.vimeo\.com\/video\//i,
                    /dailymotion\.com\/embed\/video\//i,
                    /player\.twitch\.tv\//i,
                    /clips\.twitch\.tv\/embed/i,
                    /facebook\.com\/plugins\/video\.php/i,
                    /facebook\.com\/video\/embed/i,
                    /streamable\.com\/[oe]\//i,
                    /rumble\.com\/embed\//i
                ];

                function isEmbedUrl(url) {
                    if (!url) return false;
                    for (var i = 0; i < EMBED_PATTERNS.length; i++) {
                        if (EMBED_PATTERNS[i].test(url)) return true;
                    }
                    return false;
                }

                function classifyUrl(url) {
                    if (!url) return 'unknown';
                    if (url.startsWith('blob:')) return 'blob';
                    if (isEmbedUrl(url)) return 'embed';
                    if (url.indexOf('.m3u8') !== -1) return 'hls';
                    if (url.indexOf('.mpd') !== -1) return 'dash';
                    if (/^https?:\/\//i.test(url)) return 'direct';
                    return 'unknown';
                }

                function scanAnchorsOnce() {
                    if (anchorQualities !== null) return anchorQualities;
                    anchorQualities = {};
                    var anchors = document.querySelectorAll('a[href]');
                    for (var j = 0; j < anchors.length; j++) {
                        var href = anchors[j].href || '';
                        if (/\.(mp4|webm|m4v|ogv|mkv)(\?|${'$'})/i.test(href)) {
                            var aLabel = anchors[j].getAttribute('data-res')
                                || anchors[j].getAttribute('label')
                                || anchors[j].textContent.trim().substring(0, 30)
                                || 'Download ' + (j + 1);
                            anchorQualities[aLabel] = href;
                        }
                    }
                    return anchorQualities;
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
                    var anchorLinks = scanAnchorsOnce();
                    for (var key in anchorLinks) {
                        if (anchorLinks.hasOwnProperty(key)) {
                            qualities[key] = anchorLinks[key];
                        }
                    }
                    return qualities;
                }

                function reportVideo(video) {
                    var url = video.currentSrc || video.src || '';
                    if (!url) return;
                    video._vdLast = url;
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
                    // A real video was found — stop the periodic fallback scan to avoid
                    // further CPU/battery use. The MutationObserver stays active to
                    // catch source swaps on the same page.
                    if (window._vdIntervalId) {
                        clearInterval(window._vdIntervalId);
                        window._vdIntervalId = null;
                    }
                }

                // Report an embedded player (cross-origin iframe we cannot see into, e.g.
                // YouTube/Vimeo/Dailymotion). The download URL is the embed src itself, which
                // yt-dlp can resolve directly. streamType 'embed' routes the download to yt-dlp.
                function reportEmbed(src) {
                    if (!src) return;
                    var qualities = {};
                    qualities['Default'] = src;
                    if (window.VideoSniffer) {
                        window.VideoSniffer.onVideoDetected(
                            src,
                            JSON.stringify(qualities),
                            '',
                            'embed'
                        );
                    }
                    // Note: we do NOT clear the periodic scan here. An embed is a fallback; if a
                    // real <video> shows up later (e.g. a same-origin player finishes loading)
                    // it can still supersede this detection with proper quality options.
                }

                // Collect all <video> elements from the top document and any SAME-ORIGIN iframes
                // (recursively, depth-capped). Cross-origin iframe access throws a SecurityError
                // which we swallow; those are handled separately via reportEmbed().
                function collectVideos(doc, depth, out) {
                    if (!doc || depth > 3) return;
                    var vids = doc.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) {
                        out.push(vids[i]);
                    }
                    var frames = doc.querySelectorAll('iframe');
                    for (var j = 0; j < frames.length; j++) {
                        var childDoc = null;
                        try {
                            childDoc = frames[j].contentDocument
                                || (frames[j].contentWindow && frames[j].contentWindow.document);
                        } catch (e) {
                            childDoc = null; // cross-origin — inaccessible, handled by embed scan
                        }
                        if (childDoc) {
                            collectVideos(childDoc, depth + 1, out);
                        }
                    }
                }

                // Find the first cross-origin embedded-player iframe on the page.
                function findEmbedIframe() {
                    var frames = document.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                        var src = frames[i].src || frames[i].getAttribute('src') || '';
                        if (isEmbedUrl(src)) return src;
                    }
                    return '';
                }

                function scanAllVideos() {
                    // 1) Real <video> elements always win (top document + same-origin iframes).
                    //    We still wire listeners on every <video> (even srcless placeholders) so a
                    //    late src assignment is caught, but we only treat the page as "has a real
                    //    video" — and thus suppress the embed fallback — when at least one element
                    //    actually has a usable src. Otherwise a srcless placeholder <video> (common
                    //    on embed/hybrid pages) would hide the YouTube/Vimeo download FAB.
                    var videos = [];
                    collectVideos(document, 0, videos);
                    var hasUsableVideo = false;
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v._vdSet) {
                            v._vdSet = true;
                            v.addEventListener('loadedmetadata', function() { reportVideo(this); });
                            v.addEventListener('playing', function() { reportVideo(this); });
                            v.addEventListener('play', function() { reportVideo(this); });
                            v.addEventListener('loadeddata', function() { reportVideo(this); });
                            v.addEventListener('canplay', function() { reportVideo(this); });
                            if (v.readyState >= 1 && (v.currentSrc || v.src)) {
                                reportVideo(v);
                            }
                        }
                        var cur = v.currentSrc || v.src;
                        if (cur) {
                            hasUsableVideo = true;
                            if (cur !== v._vdLast) {
                                reportVideo(v);
                            }
                        }
                    }
                    // A usable real video takes priority over embed detection.
                    if (hasUsableVideo) return;

                    // 2) No accessible real <video> with a source — fall back to detecting a known
                    //    embedded player by its cross-origin iframe src (YouTube/Vimeo/Dailymotion).
                    var embedSrc = findEmbedIframe();
                    if (embedSrc && embedSrc !== window._vdLastEmbed) {
                        window._vdLastEmbed = embedSrc;
                        reportEmbed(embedSrc);
                    }
                }

                // SPA route change support
                var navHandler = function() {
                    anchorQualities = null;
                    setTimeout(scanAllVideos, 300);
                };
                window._vdNav = navHandler;
                window.addEventListener('popstate', navHandler);
                window.addEventListener('hashchange', navHandler);

                scanAllVideos();

                // Watch for new/changed video elements — includes src attribute changes.
                // Observe document.body rather than document.documentElement: the head is
                // never going to contain <video> elements and observing it only generates
                // useless mutation callbacks (extra CPU/battery on every DOM change).
                var debounceTimer = null;
                var observer = new MutationObserver(function() {
                    clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(scanAllVideos, 500);
                });
                var observeTarget = document.body || document.documentElement;
                observer.observe(observeTarget, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['src']
                });
                window._vdObs = observer;

                // Periodic fallback scan — catches lazy-loaded or preload=none players.
                // Bounded to a finite number of iterations (10 scans = ~20s from page load)
                // so the interval does not run forever on every open tab, draining CPU/battery.
                // The interval is also cleared as soon as a video is detected (see reportVideo).
                var _vdRemainingScans = 10;
                var _vdIntervalId = setInterval(function() {
                    scanAllVideos();
                    _vdRemainingScans--;
                    if (_vdRemainingScans <= 0) {
                        clearInterval(_vdIntervalId);
                    }
                }, 2000);
                window._vdIntervalId = _vdIntervalId;
            })();
        """.trimIndent()
        view.evaluateJavascript(videoScript, null)
    }


    private fun showVideoDownloadSheet() {
        val videoUrl = detectedVideoUrl ?: return
        val qualities = detectedQualities

        val dialog = BottomSheetDialog(activity)
        val sheetView = LayoutInflater.from(activity).inflate(
            R.layout.bottom_sheet_video_download, null
        )
        dialog.setContentView(sheetView)

        // --- Populate host / URL as prominent security identifier ---
        // The originating host is the primary identity indicator for the user,
        // helping them verify the source of the video before downloading.
        val host = try {
            android.net.Uri.parse(videoUrl).host ?: videoUrl
        } catch (e: Exception) { videoUrl }
        sheetView.findViewById<TextView>(R.id.tvVideoHost).text = host

        // --- Infer file extension from URL, fallback to .mp4 ---
        val inferredExtension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(videoUrl)
            .takeIf { it.isNotBlank() } ?: "mp4"

        // --- Populate filename from page title ---
        val pageTitle = titleInfo.getTitle()
            .ifBlank { host }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") // sanitize illegal chars
        sheetView.findViewById<TextView>(R.id.tvVideoFilename).text = "$pageTitle.$inferredExtension"

        // --- Quality badge ---
        val tvCurrentQuality = sheetView.findViewById<TextView>(R.id.tvCurrentQuality)
        val resolution = detectedResolution
        tvCurrentQuality.text = when {
            !resolution.isNullOrBlank() -> resolution
            else -> activity.getString(R.string.video_quality_auto)
        }

        // --- Quality picker (multiple sources only) ---
        val containerQualityPicker = sheetView.findViewById<android.view.View>(R.id.containerQualityPicker)
        val radioGroup = sheetView.findViewById<RadioGroup>(R.id.radioGroupQualities)
        var selectedDownloadUrl = videoUrl

        // yt-dlp format picker (only used/visible for adaptive/embed streams).
        val containerFormatPicker = sheetView.findViewById<android.view.View>(R.id.containerFormatPicker)
        val radioGroupFormats = sheetView.findViewById<RadioGroup>(R.id.radioGroupFormats)
        var selectedFormat = DownloadFormat.DEFAULT

        val colorValue = android.util.TypedValue()
        sheetView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, colorValue, true)
        val textColor = colorValue.data

        val tvAdaptiveMessage = sheetView.findViewById<TextView>(R.id.tvAdaptiveStreamMessage)
        val btnDownload = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVideoDownload)
        val isAdaptiveOnly = detectedStreamType in listOf("blob", "hls", "dash", "embed")

        if (isAdaptiveOnly && (qualities == null || qualities.all { classifyUrl(it.value) != "direct" })) {
            // ENABLE yt-dlp download for adaptive streams
            tvAdaptiveMessage.visibility = View.VISIBLE
            tvAdaptiveMessage.text = activity.getString(R.string.video_adaptive_stream_message_ytdlp)
            
            // ENABLE button instead of disabling (yt-dlp will handle this)
            btnDownload.isEnabled = true
            btnDownload.text = activity.getString(R.string.action_download)
            btnDownload.icon = ContextCompat.getDrawable(activity, R.drawable.ic_download_outline)
            
            containerQualityPicker.visibility = View.GONE

            // Offer a yt-dlp format/quality picker (Best / 1080p / 720p / 480p / Audio-only).
            containerFormatPicker.visibility = View.VISIBLE
            radioGroupFormats.removeAllViews()
            DownloadFormat.values().forEachIndexed { index, fmt ->
                val rb = RadioButton(activity).apply {
                    id = View.generateViewId()
                    tag = fmt
                    text = activity.getString(labelResFor(fmt))
                    isChecked = index == 0
                    setTextColor(textColor)
                }
                radioGroupFormats.addView(rb)
            }
            selectedFormat = DownloadFormat.DEFAULT
            radioGroupFormats.setOnCheckedChangeListener { group, checkedId ->
                val checked = group.findViewById<RadioButton>(checkedId)
                (checked?.tag as? DownloadFormat)?.let { selectedFormat = it }
            }
        } else {
            tvAdaptiveMessage.visibility = View.GONE
            containerFormatPicker.visibility = View.GONE
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
                        selectedDownloadUrl = entry.value
                    }
                }

                radioGroup.setOnCheckedChangeListener { group, checkedId ->
                    val checkedRadioButton = group.findViewById<RadioButton>(checkedId)
                    @Suppress("UNCHECKED_CAST")
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

        // --- Buttons ---
        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVideoCancel)
            .setOnClickListener { dialog.dismiss() }

        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVideoDownload)
            .setOnClickListener {
                // Route based on the SELECTED URL classification, not page-level flag
                // This allows users to pick direct MP4s even when adaptive streams are also available
                val selectedUrlType = classifyUrl(selectedDownloadUrl)
                
                when (selectedUrlType) {
                    "embed" -> {
                        // Embedded player (YouTube/Vimeo/Dailymotion) detected via a cross-origin
                        // iframe src. yt-dlp resolves the embed URL directly, so pass it as-is
                        // (NOT webView.url, which would be the wrapping host page).
                        showYtDlpWarningAndDownload(selectedDownloadUrl, "$pageTitle.$inferredExtension", selectedFormat)
                    }
                    "blob", "hls", "dash" -> {
                        // Adaptive stream - use yt-dlp
                        // For blob streams, yt-dlp needs the page URL, not the blob URL
                        // For hls/dash, pass the actual manifest URL
                        val urlForDownload = if (selectedUrlType == "blob") {
                            webView?.url
                        } else {
                            selectedDownloadUrl
                        }
                        
                        // Guard against null/blank page URL
                        if (urlForDownload.isNullOrBlank()) {
                            com.google.android.material.snackbar.Snackbar.make(
                                activity.findViewById<android.view.View>(android.R.id.content),
                                R.string.invalid_url,
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            showYtDlpWarningAndDownload(urlForDownload, "$pageTitle.$inferredExtension", selectedFormat)
                        }
                    }
                    "direct" -> {
                        // Direct URL - use standard download handler
                        startDownload(selectedDownloadUrl)
                    }
                    else -> {
                        // Unknown type - try standard download as fallback
                        startDownload(selectedDownloadUrl)
                    }
                }
                dialog.dismiss()
                hideDownloadFab()
            }

        dialog.show()
    }

    private fun showYtDlpWarningAndDownload(url: String, filename: String, format: DownloadFormat) {
        // Show warning dialog first
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.warning_ytdlp_title)
            .setMessage(R.string.warning_ytdlp_message)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                startYtDlpDownload(url, filename, format)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * Map a [DownloadFormat] to its user-facing label string resource. Kept in the UI layer so the
     * [DownloadFormat] enum itself stays free of Android (`R`) dependencies and remains unit-testable.
     */
    @androidx.annotation.StringRes
    private fun labelResFor(format: DownloadFormat): Int = when (format) {
        DownloadFormat.BEST -> R.string.video_format_best
        DownloadFormat.P1080 -> R.string.video_format_1080p
        DownloadFormat.P720 -> R.string.video_format_720p
        DownloadFormat.P480 -> R.string.video_format_480p
        DownloadFormat.AUDIO_MP3 -> R.string.video_format_audio_mp3
    }

    private fun startYtDlpDownload(url: String, filename: String, format: DownloadFormat) {
        // Use the helper method from YtDlpDownloadService
        com.xhub.browser.download.YtDlpDownloadService.startDownload(
            context = activity,
            url = url,
            filename = filename,
            pageTitle = titleInfo.getTitle(),
            format = format
        )
        
        // Show feedback
        com.google.android.material.snackbar.Snackbar.make(
            activity.findViewById(android.R.id.content),
            R.string.video_download_started,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }

    private fun classifyUrl(url: String): String {
        return when {
            url.startsWith("blob:") -> "blob"
            isEmbedUrl(url) -> "embed"
            url.contains(".m3u8") -> "hls"
            url.contains(".mpd") -> "dash"
            else -> "direct"
        }
    }

    /**
     * Narrow, path-scoped recognition of known embedded-player URLs (YouTube/Vimeo/Dailymotion).
     * Mirrors the JS EMBED_PATTERNS and acts as a Kotlin-side backstop so a selected embed URL
     * routes to yt-dlp rather than the standard (HTML-page) download handler. We match the embed
     * PATH, not just the host, so a real <video> src hosted on these domains is never caught.
     */
    private fun isEmbedUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com/embed/")
            || lower.contains("youtube-nocookie.com/embed/")
            || lower.contains("player.vimeo.com/video/")
            || lower.contains("dailymotion.com/embed/video/")
            || lower.contains("player.twitch.tv/")
            || lower.contains("clips.twitch.tv/embed")
            || lower.contains("facebook.com/plugins/video.php")
            || lower.contains("facebook.com/video/embed")
            || lower.contains("streamable.com/o/")
            || lower.contains("streamable.com/e/")
            || lower.contains("rumble.com/embed/")
    }

    private fun startDownload(url: String) {
        val downloadHandler = hiltEntryPoint.downloadHandler
        
        // Infer file extension and mimetype from URL, fallback to .mp4 and video/*
        val inferredExtension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(url)
            .takeIf { it.isNotBlank() } ?: "mp4"
        val mimeTypeMap = android.webkit.MimeTypeMap.getSingleton()
        val inferredMimeType = mimeTypeMap.getMimeTypeFromExtension(inferredExtension) ?: "video/*"
        
        // Use page title as filename hint to avoid "unknown"
        val rawTitle = titleInfo.getTitle().ifBlank {
            try { android.net.Uri.parse(url).host ?: "video" } catch (e: Exception) { "video" }
        }
        val safeTitle = rawTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val contentDisposition = "attachment; filename=\"$safeTitle.$inferredExtension\""
        
        downloadHandler.onDownloadStart(
            activity,
            userPreferences,
            url,
            userAgent,
            contentDisposition,
            inferredMimeType,
            activity.getString(R.string.unknown_file_size),
            webView?.url // Pass current page URL as referer
        )
        hideDownloadFab()
    }

    private fun hideDownloadFab() {
        val fab = activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabDownloadVideo)
        fab?.let {
            // Nothing to do if it's already hidden — also avoids animating from a hidden state.
            if (it.visibility != View.VISIBLE) {
                it.visibility = View.GONE
                return
            }
            it.animate().cancel()
            it.animate()
                .alpha(0f)
                .scaleX(FAB_ENTRANCE_START_SCALE)
                .scaleY(FAB_ENTRANCE_START_SCALE)
                .setDuration(FAB_EXIT_DURATION_MS)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        it.visibility = View.GONE
                        // Reset transient animation properties so a future show() starts clean
                        // even if its own entrance animation is skipped for any reason.
                        it.alpha = 1f
                        it.scaleX = 1f
                        it.scaleY = 1f
                    }
                })
                .start()
        }
    }

    private val webBrowser: WebBrowser
    private lateinit var gestureDetector: GestureDetector
    private val paint = Paint()

    /**
     * Sets whether this tab was the result of a new intent sent to the browser.
     * That's notably used to decide if we close our activity when closing this tab thus going back to the app which opened it.
     */
    val isNewTab: Boolean get() = iIntent!=null
    //val fromSelf: Boolean get() = iIntent?.getStringExtra("PACKAGE") == activity.packageName;

    var iIntent: Intent? = null

    /**
     * This method sets the tab as the foreground tab or a background tab.
     */
    var isForeground: Boolean = false
        set(aIsForeground) {
            field = aIsForeground
            if (isForeground) {
                // When frozen tab goes foreground we need to load its bundle in webView
                latentTabInitializer?.apply {
		            // Lazy creation of our WebView
                    createWebView()
                    // Load bundle in WebView
                    initializeContent(this)
                    // Discard tab initializer since we just consumed it
                    latentTabInitializer = null
                }
                if (isVideoDetected) {
                    showDownloadFab()
                }
                // Clear preview when tab becomes active to avoid showing stale content
                invalidatePreview()
            } else {
                // A tab sent to the background is not so new anymore
                iIntent = null
                activity.runOnUiThread { hideDownloadFab() }
            }
            webBrowser.onTabChanged(this)
        }
    /**
     * Gets whether or not the page rendering is inverted or not. The main purpose of this is to
     * indicate that JavaScript should be run at the end of a page load to invert only the images
     * back to their non-inverted states.
     *
     * @return true if the page is in inverted mode, false otherwise.
     */
    var invertPage = false
        private set

    /**
     * True if desktop mode is enabled for this tab.
     */
    var desktopMode = false
        set(aDesktopMode) {
            field = aDesktopMode
            // Set our user agent accordingly
            if (aDesktopMode) {
                webView?.settings?.userAgentString = WINDOWS_DESKTOP_USER_AGENT_PREFIX + webViewEngineVersionDesktop(activity.application)
            } else {
                setUserAgentForPreference(userPreferences)
            }
        }

    /**
     *
     */
    var darkMode = false
        set(aDarkMode) {
            field = aDarkMode
            applyDarkMode();
        }

    /**
     * Enable user to override domain settings dark mode preference at the tab level.
     * TODO: should we persist that guy?
     * Maybe not as we could see this as a temporary option
     */
    var darkModeBypassDomainSettings = false

    /**
     *
     */
    var desktopModeBypassDomainSettings = false

    /**
     * Get our find in page search query.
     *
     * @return The find in page search query or an empty string.
     */
    var searchQuery: String = ""
        set(aSearchQuery) {
            field = aSearchQuery
            //find(searchQuery)
        }

    /**
     * Define if this tab has an active find in page search.
     */
    var searchActive = false

    /**
     *
     */
    private val webViewHandler = WebViewHandler(this)

    /**
     * This method gets the additional headers that should be added with each request the browser
     * makes.
     *
     * @return a non null Map of Strings with the additional request headers.
     */
    internal val requestHeaders = ArrayMap<String, String>()

    private val maxFling: Float

    private val hiltEntryPoint = EntryPointAccessors.fromApplication(activity.applicationContext, HiltEntryPoint::class.java)

    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val dialogBuilder: LightningDialogBuilder = hiltEntryPoint.dialogBuilder
    val databaseScheduler: Scheduler = hiltEntryPoint.databaseScheduler()
    val mainScheduler: Scheduler = hiltEntryPoint.mainScheduler()
    val networkConnectivityModel: NetworkConnectivityModel = hiltEntryPoint.networkConnectivityModel
    val defaultDomainSettings = DomainPreferences(activity)

    private val networkDisposable: Disposable

    /**
     * Will decide to enable hardware acceleration and WebGL or not
     */
    private var layerType = LayerType.Hardware

    /**
     * This method determines whether the current tab is visible or not.
     *
     * @return true if the WebView is non-null and visible, false otherwise.
     */
    val isShown: Boolean
        get() = webView?.isShown == true

    /**
     * Gets the current progress of the WebView.
     *
     * @return returns a number between 0 and 100 with the current progress of the WebView. If the
     * WebView is null, then the progress returned will be 100.
     */
    val progress: Int
        get() = webView?.progress ?: 100

    /**
     * Tells if a web page is currently loading.
     */
    val isLoading
        get() = progress != 100

    /**
     * Get the current user agent used by the WebView.
     *
     * @return retuns the current user agent of the WebView instance, or an empty string if the
     * WebView is null.
     */
    private val userAgent: String
        get() = webView?.settings?.userAgentString ?: ""

    /**
     * Gets the favicon currently in use by the page. If the current page does not have a favicon,
     * it returns a default icon.
     *
     * @return a non-null Bitmap with the current favicon.
     */
    val favicon: Bitmap
        get() = titleInfo.getFavicon()

    /**
     * Get the current title of the page, retrieved from the title object.
     *
     * @return the title of the page, or an empty string if there is no title.
     */
    val title: String
        get() = titleInfo.getTitle()

    /**
     * Get the current [SslCertificate] if there is any associated with the current page.
     */
    val sslCertificate: SslCertificate?
        get() = webView?.certificate

    /**
     * Get the current URL of the WebView, or an empty string if the WebView is null or the URL is
     * null.
     *
     * @return the current URL or an empty string.
     */
    val url: String
        get() {
            //TODO: One day find a way to write this expression without !! and without duplicating iTargetUrl.toString(), Kotlin is so weird
            return if (webView == null || webView!!.url.isNullOrBlank() || webView!!.url.isSpecialUrl()) {
                iTargetUrl.toString()
            } else  {
                webView!!.url as String
            }
        }

    /**
     * Used to check if our URL really changed
     */
    var lastUrl: String = ""

    /**
     * Return true if this tab is frozen, meaning it was not yet loaded from its bundle
     */
    val isFrozen : Boolean
        get() = latentTabInitializer?.tabModel?.webView != null


    /**
     * We had forgotten to unregisterReceiver our download listener thus leaking them all whenever we switched between sessions.
     * It turns out android as a hardcoded limit of 1000 [BroadcastReceiver] per application.
     * So after a while switching between sessions with many tabs we would get an exception saying:
     * "Too many receivers, total of 1000, registered for pid"
     * See: https://stackoverflow.com/q/58179733/3969362
     * TODO: Do we really need one of those per tab/WebView?
     */
    private var iDownloadListener: LightningDownloadListener? = null

    /**
     * Constructor
     */
    init {
        //activity.injector.inject(this)
        webBrowser = activity as WebBrowser
        titleInfo = WebPageHeader(activity)
        maxFling = ViewConfiguration.get(activity).scaledMaximumFlingVelocity.toFloat()
	
        // Mark our URL
        iTargetUrl = Uri.parse(tabInitializer.url())

        if (tabInitializer !is FreezableBundleInitializer) {
            // Create our WebView now
            //TODO: it looks like our special URLs don't get frozen for some reason
            createWebView()
            initializeContent(tabInitializer)
            desktopMode = defaultDomainSettings.desktopMode
            darkMode = defaultDomainSettings.darkMode
        } else {
            // Our WebView will only be created whenever our tab goes to the foreground
            latentTabInitializer = tabInitializer
            titleInfo.setTitle(tabInitializer.tabModel.title)
            tabInitializer.tabModel.favicon.let {titleInfo.setFavicon(it)}
            desktopMode = tabInitializer.tabModel.desktopMode
            darkMode = tabInitializer.tabModel.darkMode
            searchQuery = tabInitializer.tabModel.searchQuery
            searchActive = tabInitializer.tabModel.searchActive
        }

        networkDisposable = networkConnectivityModel.connectivity()
            .observeOn(mainScheduler)
            .subscribe(::setNetworkAvailable)
    }

    /**
     *
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            activity.getString(R.string.pref_key_scrollbar_size) -> {
                webView?.scrollBarSize = userPreferences.scrollbarSize.px.toInt()
                webView?.postInvalidate()
            }

            activity.getString(R.string.pref_key_scrollbar_fading) -> {
                webView?.isScrollbarFadingEnabled = userPreferences.scrollbarFading
                webView?.postInvalidate()
            }

            activity.getString(R.string.pref_key_scrollbar_delay_before_fade) ->
                webView?.scrollBarDefaultDelayBeforeFade = userPreferences.scrollbarDelayBeforeFade.toInt()

            activity.getString(R.string.pref_key_scrollbar_fade_duration) ->
                webView?.scrollBarFadeDuration = userPreferences.scrollbarFadeDuration.toInt()

            activity.getString(R.string.pref_key_location) -> {
                // Handle location permission changes from default domain settings
                if (!isIncognito) {
                    webView?.settings?.setGeolocationEnabled(defaultDomainSettings.locationEnabled)
                }
            }

            activity.getString(R.string.pref_key_video_detection_enabled) -> {
                // Add or remove the VideoSniffer JS interface on the live WebView so that
                // toggling the preference takes effect without requiring a tab restart.
                if (userPreferences.videoDetectionEnabled) {
                    webView?.addJavascriptInterface(VideoJavascriptInterface(this), "VideoSniffer")
                    Timber.d("VideoSniffer JS interface registered (preference enabled)")
                } else {
                    webView?.removeJavascriptInterface("VideoSniffer")
                    Timber.d("VideoSniffer JS interface removed (preference disabled)")
                }
            }

            // TODO: Handle other settings, or we could just call initializePreferences()
        }
    }

    /**
     * Create our WebView.
     */
    private fun createWebView() {

        userPreferences.preferences.registerOnSharedPreferenceChangeListener(this)
        defaultDomainSettings.preferences.registerOnSharedPreferenceChangeListener(this)

        webPageClient = WebPageClient(activity, this)
        // Inflate our WebView as loading it from XML layout is needed to be able to set scrollbars color
        webView = activity.layoutInflater.inflate(R.layout.webview, null) as WebViewEx
        webView?.apply {
            proxy = this@WebPageTab
            Timber.d("WebView scrollbar defaults: ${scrollBarSize.toFloat().dp}, $scrollBarDefaultDelayBeforeFade, $scrollBarFadeDuration")
            scrollBarSize = userPreferences.scrollbarSize.px.toInt()
            isScrollbarFadingEnabled = userPreferences.scrollbarFading
            scrollBarDefaultDelayBeforeFade = userPreferences.scrollbarDelayBeforeFade.toInt()
            scrollBarFadeDuration = userPreferences.scrollbarFadeDuration.toInt()

            setFindListener(this@WebPageTab)
            //id = this@[WebPageTab].id
            gestureDetector = GestureDetector(activity, CustomGestureListener(this))

            isFocusableInTouchMode = true
            isFocusable = true
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                isAnimationCacheEnabled = false
                isAlwaysDrawnWithCacheEnabled = false
            }

            // Some web sites are broken if the background color is not white, thanks bbc.com and bbc.com/news for not defining background color.
            // However whatever we set here should be irrelevant as this is being taken care of in [BrowserActivity.changeToolbarBackground]
            // Though strictly speaking in a perfect world where web sites always define their background color themselves this should be our theme background color.
            setBackgroundColor(ThemeUtils.getBackgroundColor(activity))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            }

            isSaveEnabled = true
            setNetworkAvailable(true)
            webChromeClient = WebPageChromeClient(activity, this@WebPageTab)
            webViewClient = webPageClient

            // Only register VideoSniffer JS interface if user has enabled video detection
            if (userPreferences.videoDetectionEnabled) {
                addJavascriptInterface(VideoJavascriptInterface(this@WebPageTab), "VideoSniffer")
            }

            createDownloadListener()

            // For older devices show Tool Bar On Page Top won't work after fling to top.
            // Who cares? I mean those devices are probably from 2014 or older.
            val tl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) TouchListener().also { setOnScrollChangeListener(it) } else TouchListenerLollipop()
            setOnTouchListener(tl)

            initializeSettings()
        }

        initializePreferences()

        // If search was active enable it again
        if (searchActive) {
            find(searchQuery)
        }
    }

    /**
     *
     */
    private fun createDownloadListener() {
        // We want to receive download complete notifications
        iDownloadListener = LightningDownloadListener(activity)
        webView?.setDownloadListener(iDownloadListener.also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // We need to export it otherwise we don't get download ready notifications
                activity.registerReceiver(it, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                activity.registerReceiver(it, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        })
    }

    /**
     *
     */
    private fun destroyDownloadListener() {
        if (iDownloadListener!=null) {
            webView?.setDownloadListener(null)
            activity.unregisterReceiver(iDownloadListener)
            iDownloadListener = null
        }
    }


    fun onVideoDetected(videoUrl: String, qualitiesJson: String?, resolution: String?, streamType: String = "direct") {
        if (!VideoValidationHelper.isAcceptableMediaUrl(videoUrl)) {
            Timber.w("Received invalid video URL: $videoUrl")
            return
        }

        val sanitizedResolution = resolution?.trim()?.take(20)
        val sanitizedQualities = VideoValidationHelper.parseQualitiesJson(qualitiesJson)

        activity.runOnUiThread {
            // Perform all field assignments on the UI thread to ensure consistent state
            isVideoDetected = true
            detectedVideoUrl = videoUrl
            detectedResolution = sanitizedResolution?.takeIf { it.isNotBlank() }
            detectedStreamType = streamType.trim().take(20)
            detectedQualities = sanitizedQualities
            
            if (!isShown) return@runOnUiThread
            if (isForeground) {
                showDownloadFab()
            }
        }
    }

    /**
     * SSL state of the loaded page, or [SslState.None] if the WebView client has not been created
     * yet (frozen/session-restored tabs defer [createWebView] until they become foreground).
     */
    fun currentSslState(): SslState =
        if (::webPageClient.isInitialized) webPageClient.sslState else SslState.None

    /**
     * This method loads the homepage for the browser. Either it loads the URL stored as the
     * homepage, or loads the startpage or bookmark page if either of those are set as the homepage.
     */
    fun loadHomePage() {
        if (isIncognito) {
            iTargetUrl = Uri.parse(Uris.FulgurisIncognito)
            initializeContent(incognitoPageInitializer)
        } else {
            iTargetUrl = Uri.parse(Uris.FulgurisHome)
            initializeContent(homePageInitializer)
        }
    }

    /**
     * This function loads the bookmark page via the [BookmarkPageInitializer].
     */
    fun loadBookmarkPage() {
        iTargetUrl = Uri.parse(Uris.FulgurisBookmarks)
        initializeContent(bookmarkPageInitializer)
    }

    /**
     *
     */
    fun loadHistoryPage() {
        iTargetUrl = Uri.parse(Uris.FulgurisHistory)
        initializeContent(historyPageInitializer)
    }


    /**
     * Basically activate our tab initializer which typically loads something in our WebView.
     * [ResultMessageInitializer] being a notable exception as it will only send a message to something to load target URL at a later stage.
     */
    private fun initializeContent(tabInitializer: TabInitializer) {
        webView?.let { tabInitializer.initialize(it, requestHeaders) }
    }


    /**
     * Initialize the preference driven settings of the WebView. This method must be called whenever
     * the preferences are changed within SharedPreferences.
     * Apparently called whenever the app is sent to the foreground.
     */
    @SuppressLint("NewApi", "SetJavaScriptEnabled")
    fun initializePreferences() {
        val settings = webView?.settings ?: return
        // Frozen tabs have no WebView yet; fully-created tabs always initialize webPageClient
        // in createWebView(). Guard so a race mid-create cannot throw UninitializedPropertyAccessException.
        if (::webPageClient.isInitialized) {
            webPageClient.updatePreferences()
        }


        val modifiesHeaders = userPreferences.doNotTrackEnabled
            || userPreferences.saveDataEnabled
            || userPreferences.removeIdentifyingHeadersEnabled

        if (userPreferences.doNotTrackEnabled) {
            requestHeaders[HEADER_DNT] = "1"
        } else {
            requestHeaders.remove(HEADER_DNT)
        }

        if (userPreferences.saveDataEnabled) {
            requestHeaders[HEADER_SAVEDATA] = "on"
        } else {
            requestHeaders.remove(HEADER_SAVEDATA)
        }

        if (userPreferences.removeIdentifyingHeadersEnabled) {
            requestHeaders[HEADER_REQUESTED_WITH] = ""
            requestHeaders[HEADER_WAP_PROFILE] = ""
        } else {
            requestHeaders.remove(HEADER_REQUESTED_WITH)
            requestHeaders.remove(HEADER_WAP_PROFILE)
        }

        settings.defaultTextEncodingName = userPreferences.textEncoding
        layerType = userPreferences.layerType
        setColorMode(userPreferences.renderingMode)

        if (!isIncognito) {
            settings.setGeolocationEnabled(defaultDomainSettings.locationEnabled)
        } else {
            settings.setGeolocationEnabled(false)
        }

        // Since this runs when the activity resumes we need to also take desktop mode into account
        // Set the user agent properly taking desktop mode into account
        desktopMode = desktopMode
        // Don't just do the following as that's not taking desktop mode into account
        //setUserAgentForPreference(userPreferences)

        settings.saveFormData = userPreferences.savePasswordsEnabled && !isIncognito

        if (defaultDomainSettings.javaScriptEnabled) {
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
        } else {
            settings.javaScriptEnabled = false
            settings.javaScriptCanOpenWindowsAutomatically = false
        }

        if (userPreferences.textReflowEnabled) {
            settings.layoutAlgorithm = LayoutAlgorithm.NARROW_COLUMNS
            try {
                settings.layoutAlgorithm = LayoutAlgorithm.TEXT_AUTOSIZING
            } catch (e: Exception) {
                // This shouldn't be necessary, but there are a number
                // of KitKat devices that crash trying to set this
                Timber.e(e,"Problem setting LayoutAlgorithm to TEXT_AUTOSIZING")
            }
        } else {
            settings.layoutAlgorithm = LayoutAlgorithm.NORMAL
        }

        settings.blockNetworkImage = !userPreferences.loadImages
        // Modifying headers causes SEGFAULTS, so disallow multi window if headers are enabled.
        // We always set multiple windows to true here so that onCreateWindow is always invoked;
        // this allows us to intercept popup/new-window requests and route them back to the same tab
        // when popups/new-windows are disabled, preventing blank screens on target="_blank" links.
        settings.setSupportMultipleWindows(!modifiesHeaders)

        settings.loadWithOverviewMode = userPreferences.overviewModeEnabled

        settings.textZoom = userPreferences.browserTextSize +  MIN_BROWSER_TEXT_SIZE

        // Apply default settings for third-party cookies
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, defaultDomainSettings.thirdPartyCookies)


        applyDarkMode();
    }

    /**
     * Apply dark mode as needed.
     * We try to go dark when using app dark theme or when page is forced to dark mode.
     *
     * To test that you can load:
     * https://septatrix.github.io/prefers-color-scheme-test/
     *
     * See also:
     * https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
     */
    private fun applyDarkMode() {
        val settings = webView?.settings ?: return

        // We needed to add this for force dark mode to work when targeting SDK>=33
        // See: https://developer.android.com/about/versions/13/behavior-changes-13#webview-color-theme
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            // For forced dark mode to work on website that do not provide a dark theme we need to enable this
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkMode)
        }

        // If forced dark mode is supported
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) &&
            // and we are in dark theme or forced dark mode
            ((activity as ThemedActivity).isDarkTheme() || darkMode)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                if (darkMode) {
                    // User requested forced dark mode from menu, we need to enable user agent dark mode then.
                    WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        // Looks like that flag it's not working and will just do user agent dark mode even if page supports dark web theme.
                        // That means that when using app light theme you can't get dark web theme, you will just get user agent dark theme.
                        // No big deal though, just use app dark theme if you want proper web dark theme.
                        WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                    )
                } else {
                    // We are in app dark theme but this page does not forces to dark mode
                    // Just request dark web theme then.
                    // That's actually the only way to dark web theme rather than user agent darkening, see above comment.
                    WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                    )
                }
            }

            // We are either in app dark theme or forced dark mode, just request dark theme without actually forcing it.
            // Yes I know that flag's name is misleading to say the least.
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
        } else {
            // We are neither app dark theme or force dark mode or force dark mode is not supported.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                // We are in app light theme and force dark mode is disabled therefore:
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
            } else {
                // WebView force dark mode is not supported.
                if (darkMode) {
                    // Fallback to our special rendering mode then if user requests dark mode
                    // TODO: Have a setting option to make this the default behaviour?
                    setColorMode(RenderingMode.INVERTED_GRAYSCALE)
                } else {
                    setColorMode(userPreferences.renderingMode)
                }
            }
        }
    }

    /**
     * Initialize the settings of the WebView that are intrinsic to Lightning and cannot be altered
     * by the user. Distinguish between Incognito and Regular tabs here.
     */
    @SuppressLint("NewApi")
    private fun WebView.initializeSettings() {
        settings.apply {
            // That needs to be false for WebRTC to work at all, don't ask me why
            mediaPlaybackRequiresUserGesture = false

            if (API >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = if (defaultDomainSettings.allowMixedContent && !isIncognito) {
                    // User explicitly allowed mixed content for this domain
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                } else {
                    // Strict blocking: Never allow insecure subresources on HTTPS pages
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
            }

            if (!isIncognito || Capabilities.FULL_INCOGNITO.isSupported) {
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            } else {
                domStorageEnabled = false
                // TODO: Is this really needed for incognito mode?
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Disable file and content access for security (prevents cross-app file/JavaScript attacks)
            // File URLs can still be loaded internally if needed, but not from external intents
            allowContentAccess = false
            allowFileAccess = false
            // Needed to prevent CTRL+TAB to scroll back to top of the page
            // See: https://github.com/Slion/Fulguris/issues/82
            setNeedInitialFocus(false)


            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                getPathObservable("geolocation")
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe { file ->
                        @Suppress("DEPRECATION")
                        setGeolocationDatabasePath(file.path)
                    }
            }
        }

    }

    private fun getPathObservable(subFolder: String) = Single.fromCallable {
        activity.getDir(subFolder, 0)
    }

    /**
     * This method is used to toggle the user agent between desktop and the current preference of
     * the user.
     */
    fun toggleDesktopUserAgent(aBypass: Boolean = true) {
        // Toggle desktop mode
        desktopMode = !desktopMode
        desktopModeBypassDomainSettings = aBypass
    }

    /**
     *
     */
    fun toggleDarkMode(aBypass: Boolean = true) {
        // Toggle dark mode
        darkMode = !darkMode
        darkModeBypassDomainSettings = aBypass
    }


    /**
     * This method sets the user agent of the current tab based on the user's preference
     */
    private fun setUserAgentForPreference(userPreferences: UserPreferences) {
        webView?.settings?.let { settings ->
            settings.userAgentString = userPreferences.userAgent(activity.application)
            settings.setReducedClientHints()
        }
    }

    /**
     * Save the state of this tab Web View and return it as a [Bundle].
     * We get that state bundle either directly from our Web View,
     * or from our frozen tab initializer if ever our Web View was never loaded.
     */
    private fun webViewState(): Bundle {
        // Use frozen bundle from latent tab initializer if available
        latentTabInitializer?.tabModel?.webView?.let { return it }

        // Otherwise save current WebView state
        return Bundle(ClassLoader.getSystemClassLoader()).also { webView?.saveState(it) }
    }

    /**
     *
     */
    fun getModel() = TabModel(url, title, desktopMode, darkMode, favicon, searchQuery, searchActive, webViewState(), id)

    /**
     * Save the state of this tab and return it as a [Bundle].
     */
    fun saveState(): Bundle {
         return getModel().toBundle()
    }
    /**
     * Pause the current WebView instance.
     */
    fun onPause() {
        webView?.onPause()
        Timber.d("WebView onPause: ${webView?.id}")
    }

    /**
     * Resume the current WebView instance.
     */
    fun onResume() {
        webView?.onResume()
        Timber.d("WebView onResume: ${webView?.id}")
    }

    /**
     * Notify the WebView to stop the current load.
     * Executes callback if present since this is an explicit user action.
     */
    fun stopLoading() {
        webView?.stopLoading()

        // SL: I don't think we need this here as onPageFinished is called when we stop loading
        // Execute callback since load was explicitly stopped
        // This ensures proper cleanup (e.g., restoring cache mode after reload)
        //onLoadCompleteCallback?.invoke()
        //onLoadCompleteCallback = null
    }
    
    /**
     * Layer type notably determines if we use hardware acceleration and WebGL
     */
    private fun setLayerType() {
        Timber.d("$ihs : setLayerType: $layerType")
        webView?.setLayerType(layerType.value, paint)
    }


    /**
     * Sets the current rendering color of the WebView instance
     * of the current [WebPageTab]. The for modes are normal
     * rendering, inverted rendering, grayscale rendering,
     * and inverted grayscale rendering
     *
     * @param mode the integer mode to set as the rendering mode.
     * see the numbers in documentation above for the
     * values this method accepts.
     */
    private fun setColorMode(mode: RenderingMode) {
        invertPage = false
        when (mode) {
            RenderingMode.NORMAL -> {
                paint.colorFilter = null
                // setSoftwareRendering(); // Some devices get segfaults
                // in the WebView with Hardware Acceleration enabled,
                // the only fix is to disable hardware rendering
                //setNormalRendering()
                // SL: enabled that and the performance gain is very noticeable on  F(x)tec Pro1
                // Notably on: https://www.bbc.com/worklife
                setLayerType()
            }
            RenderingMode.INVERTED -> {
                val filterInvert = ColorMatrixColorFilter(
                    negativeColorArray
                )
                paint.colorFilter = filterInvert
                setLayerType()

                invertPage = true
            }
            RenderingMode.GRAYSCALE -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val filterGray = ColorMatrixColorFilter(cm)
                paint.colorFilter = filterGray
                setLayerType()
            }
            RenderingMode.INVERTED_GRAYSCALE -> {
                val matrix = ColorMatrix()
                matrix.set(negativeColorArray)
                val matrixGray = ColorMatrix()
                matrixGray.setSaturation(0f)
                val concat = ColorMatrix()
                concat.setConcat(matrix, matrixGray)
                val filterInvertGray = ColorMatrixColorFilter(concat)
                paint.colorFilter = filterInvertGray
                setLayerType()

                invertPage = true
            }

            RenderingMode.INCREASE_CONTRAST -> {
                val increaseHighContrast = ColorMatrixColorFilter(increaseContrastColorArray)
                paint.colorFilter = increaseHighContrast
                setLayerType()
            }
        }

    }

    /**
     * Pauses the JavaScript timers of the
     * WebView instance, which will trigger a
     * pause for all WebViews in the app.
     */
    fun pauseTimers() {
        webView?.pauseTimers()
        Timber.d("Pausing JS timers")
    }

    /**
     * Resumes the JavaScript timers of the
     * WebView instance, which will trigger a
     * resume for all WebViews in the app.
     */
    fun resumeTimers() {
        webView?.resumeTimers()
        Timber.d("Resuming JS timers")
    }

    /**
     * Requests focus down on the WebView instance
     * if the view does not already have focus.
     */
    fun requestFocus() {
        if (webView?.hasFocus() == false) {
            webView?.requestFocus()
        }
    }

    /**
     * Sets the visibility of the WebView to either
     * View.GONE, View.VISIBLE, or View.INVISIBLE.
     * other values passed in will have no effect.
     *
     * @param visible the visibility to set on the WebView.
     */
    fun setVisibility(visible: Int) {
        webView?.visibility = visible
    }

    /**
     * Tells the WebView to reload the current page.
     * Forces a fresh fetch from the server bypassing cache.
     * Cache mode is temporarily changed and will be restored after page finishes loading
     * or if the load is cancelled.
     * If the proxy settings are not ready then the
     * this method will not have an affect as the
     * proxy must start before the load occurs.
     */
    fun reload(aForce: Boolean = false) {
        webView?.let { wv ->

            if (!aForce) {
                loadUrl(url)
            } else {
                // Store original cache mode
                val originalCacheMode = wv.settings.cacheMode

                // Temporarily disable cache to force fresh reload
                wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE

                // Handle the case where we display error page for instance
                // Pass callback to restore cache mode after load completes OR is cancelled
                loadUrl(url) {
                    wv.settings.cacheMode = originalCacheMode
                }
            }
        }
    }

    /**
     * Finds all the instances of the text passed to this
     * method and highlights the instances of that text
     * in the WebView.
     *
     * @param text the text to search for.
     */
    @SuppressLint("NewApi")
    fun find(text: String) {
        resetFind()
        searchQuery = text
        searchActive = true
        // Kick off our search
        webView?.findAllAsync(text)
    }

    fun findNext() {
        webView?.findNext(true)
    }

    fun findPrevious() {
        webView?.findNext(false)
    }

    fun clearFind() {
        webView?.clearMatches()
        searchActive = false
        resetFind()
    }

    // Used to implement find in page
    private var iActiveMatchOrdinal: Int = -1
    private var iNumberOfMatches: Int = -1
    private var iSnackbar: Snackbar? = null

    /**
     *
     */
    private fun resetFind() {
        iActiveMatchOrdinal = -1
        iNumberOfMatches = -1
    }

    /**
     * That's where find in page results are being reported by our WebView.
     */
    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {

        // If our page is still loading or if our find in page search is not complete
        if (isLoading || !isDoneCounting) {
            // Just don't report intermediary results
            return
        }
        // Only display message if something was changed
        if (iActiveMatchOrdinal != activeMatchOrdinal || iNumberOfMatches != numberOfMatches) {

            // Remember what we last reported
            iActiveMatchOrdinal = activeMatchOrdinal
            iNumberOfMatches = numberOfMatches

            // Empty search query just dismisses any results previously displayed
            // Notably useful when doing backspace on the search field until no characters are left
            if (searchQuery.isEmpty()) {
                // Hide last snackbar to avoid having outdated stats lingering
                iSnackbar?.dismiss()
            }
            // Check if our search is reporting any match
            else if (iNumberOfMatches==0) {
                // Find in page did not find any match, tell our user about it
                iSnackbar = activity.makeSnackbar(
                        activity.getString(R.string.no_match_found),
                        Snackbar.LENGTH_SHORT, if (activity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                        .setAction(R.string.button_dismiss) {
                            iSnackbar?.dismiss()
                        }

                iSnackbar?.show()
            } else {
                // Show our user how many matches we have and which one is currently focused
                val currentMatch = iActiveMatchOrdinal + 1
                iSnackbar = activity.makeSnackbar(
                        activity.getString(R.string.match_x_of_n,currentMatch,iNumberOfMatches) ,
                        Snackbar.LENGTH_SHORT, if (activity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                        .setAction(R.string.button_dismiss) {
                            iSnackbar?.dismiss()
                        }

                iSnackbar?.show()
            }
        }
    }

    /**
     * Notify the tab to shutdown and destroy
     * its WebView instance and to remove the reference
     * to it. After this method is called, the current
     * instance of the [WebPageTab] is useless as
     * the WebView cannot be recreated using the public
     * api.
     */
    fun destroy() {
        destroyWebView()
        networkDisposable.dispose()
    }

    /**
     * Destroy our WebView after we unregister from all various handlers and listener as needed
     */
    private fun destroyWebView() {
        userPreferences.preferences.unregisterOnSharedPreferenceChangeListener(this)
        defaultDomainSettings.preferences.unregisterOnSharedPreferenceChangeListener(this)
        destroyDownloadListener()
        // Cancel any in-flight background work owned by the client (e.g. userscript downloads)
        // so it cannot leak this Activity after the tab is gone.
        //
        // Guard against the lateinit not yet being assigned: if a tab is swipe-closed before
        // createWebView() finished (webPageClient is assigned there), touching it would throw
        // UninitializedPropertyAccessException and crash the app. See crash logs.
        if (::webPageClient.isInitialized) {
            webPageClient.destroy()
        }
        // Cancel any pending capture tasks before destroying WebView
        cancelPendingCapture()
        // Purge this tab's thumbnail from memory and disk so closed tabs never leave orphan files
        evictThumbnail()
        // Tear down the video sniffer's MutationObserver and bounded interval so they do not
        // keep firing JS callbacks on a tab that is going away.
        webView?.evaluateJavascript(
            """
            (function() {
                try {
                    if (window._vdObs && typeof window._vdObs.disconnect === 'function') {
                        window._vdObs.disconnect();
                        window._vdObs = null;
                    }
                    if (window._vdIntervalId) {
                        clearInterval(window._vdIntervalId);
                        window._vdIntervalId = null;
                    }
                } catch (e) { /* ignore — WebView is tearing down */ }
            })();
            """.trimIndent(),
            null
        )
        // No need to do anything for the touch listeners they are owned by the WebView anyway
        webView?.autoDestruction()
        webView = null
    }

    /**
     * Tell the WebView to navigate backwards
     * in its history to the previous page.
     */
    fun goBack() {
        resetDirectAdState()
        webView?.goBack()
    }

    /**
     * Tell the WebView to navigate forwards
     * in its history to the next page.
     */
    fun goForward() {
        resetDirectAdState()
        webView?.goForward()
    }

    /**
     * Navigate forward or backward by the specified number of steps in the history.
     * Positive steps go forward, negative steps go backward.
     *
     * @param steps Number of steps to navigate (negative for back, positive for forward)
     */
    fun goBackOrForward(steps: Int) {
        resetDirectAdState()
        webView?.goBackOrForward(steps)
    }

    /**
     * Notifies the [WebView] whether the network is available or not.
     */
    private fun setNetworkAvailable(isAvailable: Boolean) {
        webView?.setNetworkAvailable(isAvailable)
    }

    /**
     * Handles a long click on the page and delegates the URL to the
     * proper dialog if it is not null, otherwise, it tries to get the
     * URL using HitTestResult.
     *
     * @param url the url that should have been obtained from the WebView touch node
     * thingy, if it is null, this method tries to deal with it and find
     * a workaround.
     * @param text Text from the target Anchor
     * @param src Source from the target Image
     */
    private fun longClickPage(url: String?, text: String?, src: String?) {
        val result = webView?.hitTestResult
        val currentUrl = webView?.url
        val newUrl = result?.extra

        if (currentUrl != null && currentUrl.isSpecialUrl()) {
            if (currentUrl.isHistoryUrl()) {
                if (url != null) {
                    dialogBuilder.showLongPressedHistoryLinkDialog(activity, webBrowser, url)
                } else if (newUrl != null) {
                    dialogBuilder.showLongPressedHistoryLinkDialog(activity, webBrowser, newUrl)
                }
            } else if (currentUrl.isBookmarkUrl()) {
                if (url != null) {
                    dialogBuilder.showLongPressedDialogForBookmarkUrl(activity, webBrowser, url)
                } else if (newUrl != null) {
                    dialogBuilder.showLongPressedDialogForBookmarkUrl(activity, webBrowser, newUrl)
                }
            }
        } else {

            // See: https://developer.android.com/reference/android/webkit/WebView#getHitTestResult()
            result?.extra?.let { extraUrl ->
                if (result.type == WebView.HitTestResult.IMAGE_TYPE) {
                    dialogBuilder.showLongPressLinkImageDialog(
                        activity, webBrowser, "", extraUrl, text, userAgent,
                        showLinkTab = false,
                        showImageTab = true
                    )
                } else if (result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    dialogBuilder.showLongPressLinkImageDialog(
                        activity, webBrowser, url ?: "", extraUrl, text, userAgent,
                        showLinkTab = true,
                        showImageTab = true
                    )
                } else if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                    dialogBuilder.showLongPressLinkImageDialog(
                        activity, webBrowser, extraUrl, "", text, userAgent,
                        showLinkTab = true,
                        showImageTab = false
                    )
                }
                // TODO: UNKNOWN_TYPE for JavaScript URLs do we really want to?
                // TODO: Handle other types such as phone, geo and email
            }
        }
    }

    /**
     * Determines whether or not the WebView can go
     * backward or if it as the end of its history.
     *
     * @return true if the WebView can go back, false otherwise.
     */
    fun canGoBack(): Boolean = webView?.canGoBack() == true

    /**
     * Determine whether or not the WebView can go
     * forward or if it is at the front of its history.
     *
     * @return true if it can go forward, false otherwise.
     */
    fun canGoForward(): Boolean = webView?.canGoForward() == true

    /**
     * Loads the URL in the WebView. If the proxy settings
     * are still initializing, then the URL will not load
     * as it is necessary to have the settings initialized
     * before a load occurs.
     *
     * SL: Funny enough this is hardly ever used only when opening new tba from intent apparently
     *
     * @param aUrl the non-null URL to attempt to load in
     * the WebView.
     * @param onLoadComplete optional callback to execute after the page finishes loading or is cancelled.
     * Will be executed once and then cleared automatically.
     */
    fun loadUrl(aUrl: String, isAd: Boolean = false, onLoadComplete: (() -> Unit)? = null) {
        if (!isAd) {
            resetDirectAdState()
        }

        iTargetUrl = Uri.parse(aUrl)

        // Store the callback if provided
        onLoadCompleteCallback = onLoadComplete

        if (iTargetUrl.scheme == Schemes.Fulguris || iTargetUrl.scheme == Schemes.About) {
            //TODO: support more of our custom URLs?
            if (iTargetUrl.host == Hosts.Home) {
                loadHomePage()
            } else if (iTargetUrl.host == Hosts.Bookmarks) {
                loadBookmarkPage()
            } else if (iTargetUrl.host == Hosts.History) {
                loadHistoryPage()
            }
        } else {
            webView?.loadUrl(aUrl, requestHeaders)
        }
    }

    /**
     * Check relevant user preferences and configuration before showing the tool bar if needed
     */
    fun showToolBarOnScrollUpIfNeeded() {
        webBrowser.showActionBar()
    }

    /**
     * Check relevant user preferences and configuration before showing the tool bar if needed
     */
    fun showToolBarOnPageTopIfNeeded() {
        webBrowser.showActionBar()
    }


    /**
     * Our render process crashed, we must destroy our WebView.
     */
    fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {

        // Defensive
        if (view!=webView) {
            Timber.w("onRenderProcessGone: Not our WebView")
            // Still don't want to crash the app
            return true
        }

        // Refreeze our tab before destroying it's WebView
        // Tested that against Bookmarks page and it worked fine too
        latentTabInitializer = FreezableBundleInitializer(getModel())
        val vg = webView?.removeFromParent()
        destroyWebView()

        vg?.let {
            // That should run if the current tab lost its render process
            // TODO: Proper dialog with bug report link?
            // TODO: Firebase report?
            // Would be nice to have ACRA: https://github.com/ACRA/acra
            // Show user a message if this is our current tab
            iSnackbar = activity.makeSnackbar(
                activity.getString(R.string.message_render_process_crashed),
                5000, if (activity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                /*.setAction(R.string.button_dismiss) {
                    iSnackbar?.dismiss()
                }*/.setIcon(R.drawable.ic_warn)

            iSnackbar?.show()

            // TODO: Another broken workflow, just refactor our tab manager and presenter
            // We could not get presenter injection to work so we just use the one from our activity
            (activity as? WebBrowserActivity)?.apply {
                // Trigger the recreation of our tab
                tabsManager.tabChanged(tabsManager.indexOfTab(this@WebPageTab),false,false)
            }
        }

        // Needed I guess in case the current tab was using another render process
        webBrowser.onTabChanged(this)

        // We don't want to crash the app
        return true
    }


    /**
     * The OnTouchListener used by the WebView so we can
     * get scroll events and show/hide the action bar when
     * the page is scrolled up/down.
     */
    private open inner class TouchListenerLollipop : OnTouchListener {

        internal var location: Float = 0f
        protected var touchingScreen: Boolean = false
        internal var y: Float = 0f
        internal var action: Int = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View?, arg1: MotionEvent): Boolean {

            if (view == null) return false

            if (!view.hasFocus()) {
                view.requestFocus()
            }

            action = arg1.action
            y = arg1.y
            // Handle tool bar visibility when doing slow scrolling
            if (action == MotionEvent.ACTION_DOWN) {
                location = y
                touchingScreen=true
            }
            // Only show or hide tool bar when the user stop touching the screen otherwise that looks ugly
            else if (action == MotionEvent.ACTION_UP) {
                val distance = y - location
                touchingScreen=false
                if (view.scrollY < SCROLL_DOWN_THRESHOLD
                        // Touch input won't show tool bar again if no vertical scroll
                        // It can still be accessed using the back button
                        && view.canScrollVertically()) {
                    showToolBarOnPageTopIfNeeded()
                } else if (distance < -SCROLL_UP_THRESHOLD) {
                    // Aggressive hiding of tool bar
                    webBrowser.hideActionBar()
                }
                location = 0f
            }

            // Handle tool bar visibility upon fling gesture
            gestureDetector.onTouchEvent(arg1)

            return false
        }
    }

    /**
     * Improved touch listener for devices above API 21 Lollipop
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private inner class TouchListener: TouchListenerLollipop(), OnScrollChangeListener {

        override fun onScrollChange(view: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {

            view?.apply {
                if (canScrollVertically()) {
                    // Handle the case after fling all the way to the top of the web page
                    // Are we near the top of our web page and is user finger not on the screen
                    if (scrollY < SCROLL_DOWN_THRESHOLD && !touchingScreen) {
                        showToolBarOnPageTopIfNeeded()
                    }
                }
            }
        }
    }

    /**
     * The SimpleOnGestureListener used by the [TouchListener]
     * in order to delegate show/hide events to the action bar when
     * the user flings the page. Also handles long press events so
     * that we can capture them accurately.
     */
    private inner class CustomGestureListener(private val view: View) : SimpleOnGestureListener() {

        /**
         * Without this, onLongPress is not called when user is zooming using
         * two fingers, but is when using only one.
         *
         *
         * The required behaviour is to not trigger this when the user is
         * zooming, it shouldn't matter how much fingers the user's using.
         */
        private var canTriggerLongPress = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {

            if (e1==null) {
                return false
            }

            val power = (velocityY * 100 / maxFling).toInt()
            if (power < -10) {
                webBrowser.hideActionBar()
            } else if (power > 15
                    // Touch input won't show tool bar again if no top level vertical scroll
                    // It can still be accessed using the back button
                    && view.canScrollVertically()) {
                showToolBarOnScrollUpIfNeeded()
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onLongPress(e: MotionEvent) {
            if (canTriggerLongPress) {
                val msg = webViewHandler.obtainMessage()
                if (msg != null) {
                    msg.target = webViewHandler
                    webView?.requestFocusNodeHref(msg)
                }
            }
        }

        /**
         * Is called when the user is swiping after the doubletap, which in our
         * case means that he is zooming.
         */
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            canTriggerLongPress = false
            return false
        }

        /**
         * Is called when something is starting being pressed, always before
         * onLongPress.
         */
        override fun onShowPress(e: MotionEvent) {
            canTriggerLongPress = true
        }

        /**
         *
         */
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            webBrowser.onSingleTapUp(this@WebPageTab)
            return false
        }
    }

    /**
     * A Handler used to get the URL from a long click
     * event on the WebView. It does not hold a hard
     * reference to the WebView and therefore will not
     * leak it if the WebView is garbage collected.
     */
    private class WebViewHandler(view: WebPageTab) : Handler() {

        private val reference: WeakReference<WebPageTab> = WeakReference(view)

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            // Fetch message data: url, text, image source
            // See: https://developer.android.com/reference/android/webkit/WebView#requestFocusNodeHref(android.os.Message)
            val url = msg.data.getString("url")
            val title = msg.data.getString("title")
            val src = msg.data.getString("src")
            //
            reference.get()?.longClickPage(url,title,src)
        }
    }

    /**
     * Captures a preview/thumbnail bitmap of the current WebView content.
     * Returns cached preview if available, or captures a new one.
     */
    fun getPreviewBitmap(): Bitmap? {
        // Check centralized cache first
        val preview = TabThumbnailCache.get(id, persistable = !isIncognito)
        Timber.v("getPreviewBitmap for tab=$id: ${if (preview != null) "found ${preview.width}x${preview.height}" else "not found"}")
        return preview
    }

    /**
     * Schedules a deferred preview capture after page load completes.
     * Prevents capturing while a page is still rendering.
     * Debounces successive requests and posts on the webViewHandler thread.
     * Only safe to call when this tab IS the foreground tab and no view-swap is in progress.
     * Delegates to [capturePreviewAsync] for hardware-accurate output via PixelCopy.
     */
    fun scheduleDeferredPreviewCapture() {
        cancelPendingCapture()
        val currentSequence = captureSequence
        val runnable = Runnable {
            if (currentSequence == captureSequence) {
                capturePreviewAsync()
            }
        }
        captureRunnable = runnable
        webViewHandler.postDelayed(runnable, CAPTURE_DELAY_MS)
    }

    /**
     * Asynchronous capture using PixelCopy (API 26+) with canvas-draw fallback.
     * Must only be called when the view is the *current* foreground tab and no tab-swap
     * is happening, i.e. from [scheduleDeferredPreviewCapture].
     */
    private fun capturePreviewAsync() {
        // Debounce successive captures (max once per second)
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < 1000L) {
            Timber.d("Debouncing async capture for tab=$id; last capture was ${now - lastCaptureTime}ms ago")
            return
        }

        val isHome = url.isHomeUri() || url.isStartPageUrl() || url.isBookmarkUri() || url.isBookmarkUrl()
        val viewToCapture: View? = if (isHome) {
            try {
                val browserActivity = activity as? WebBrowserActivity
                val overlay = browserActivity?.iBinding?.homeScreenOverlay
                if (overlay != null && overlay.visibility == View.VISIBLE) {
                    overlay
                } else {
                    webView
                }
            } catch (e: Exception) {
                webView
            }
        } else {
            webView
        }

        val view = viewToCapture ?: return
        
        // Cancel any pending capture task
        cancelPendingCapture()
        
        val currentSequence = captureSequence
        
        try {
            // Ignore views that are tiny or not fully laid out yet
            if (view.width < 100 || view.height < 100) {
                Timber.w("View has invalid dimensions (${view.width}x${view.height}), cannot capture preview")
                return
            }

            // Use fixed-pixel dimensions (not dp-scaled) so memory is predictable on every screen density.
            val targetWidth  = TabThumbnailCache.TARGET_WIDTH_PX
            val targetHeight = TabThumbnailCache.TARGET_HEIGHT_PX
            val scale = targetHeight.toFloat() / view.height.toFloat()

            Timber.d("Capturing preview async (seq=$currentSequence, tab=$id): View=${view.width}x${view.height}, Target=${targetWidth}x${targetHeight}, Scale=$scale")

            val browserActivity = activity as? WebBrowserActivity
            val window = browserActivity?.window

            if (Build.VERSION.SDK_INT >= 26 && window != null && view.isAttachedToWindow) {
                val location = IntArray(2)
                view.getLocationInWindow(location)
                val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
                
                if (rect.width() > 0 && rect.height() > 0 && rect.left >= 0 && rect.top >= 0) {
                    val destBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    try {
                        PixelCopy.request(window, rect, destBitmap, { result ->
                            if (currentSequence == captureSequence) {
                                if (result == PixelCopy.SUCCESS && !isBlankBitmap(destBitmap)) {
                                    TabThumbnailCache.put(id, destBitmap, persistable = !isIncognito)
                                    lastCaptureTime = System.currentTimeMillis()
                                    notifyTabChanged()
                                } else {
                                    Timber.w("PixelCopy failed ($result) or blank; falling back to canvas draw")
                                    captureWithDrawingOptimized(view, targetWidth, targetHeight, scale, currentSequence)
                                }
                            }
                        }, webViewHandler)
                        return
                    } catch (e: Exception) {
                        Timber.e(e, "PixelCopy request failed; using fallback canvas draw")
                    }
                }
            }

            // Fallback for API < 26 or if window isn't ready / attached
            captureWithDrawingOptimized(view, targetWidth, targetHeight, scale, currentSequence)
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture preview async")
        }
    }

    /**
     * Synchronous canvas-based capture. Always used for the tab-switch path
     * (called from [TabsManager] before the view is swapped) so the outgoing tab is
     * guaranteed to still own the window surface when we draw.
     *
     * Also called from [openTabs] before the drawer slides in.
     */
    fun capturePreviewSync() {
        val isHome = url.isHomeUri() || url.isStartPageUrl() || url.isBookmarkUri() || url.isBookmarkUrl()
        val viewToCapture: View? = if (isHome) {
            try {
                val browserActivity = activity as? WebBrowserActivity
                val overlay = browserActivity?.iBinding?.homeScreenOverlay
                if (overlay != null && overlay.visibility == View.VISIBLE) overlay else webView
            } catch (e: Exception) { webView }
        } else {
            webView
        }
        val view = viewToCapture ?: return
        cancelPendingCapture()
        val currentSequence = captureSequence
        if (view.width < 100 || view.height < 100) {
            Timber.w("View has invalid dimensions (${view.width}x${view.height}), cannot capture preview")
            return
        }
        val targetWidth  = TabThumbnailCache.TARGET_WIDTH_PX
        val targetHeight = TabThumbnailCache.TARGET_HEIGHT_PX
        val scale = targetHeight.toFloat() / view.height.toFloat()
        captureWithDrawingOptimized(view, targetWidth, targetHeight, scale, currentSequence)
    }

    /**
     * Helper to verify if a bitmap is entirely a solid color (e.g. solid white/black/transparent).
     * Helps us reject blank, un-rendered frames.
     */
    private fun isBlankBitmap(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val firstPixel = bitmap.getPixel(0, 0)
        val checkPoints = arrayOf(
            Pair(0, 0), Pair(w - 1, 0), Pair(0, h - 1), Pair(w - 1, h - 1),
            Pair(w / 2, h / 2), Pair(w / 4, h / 4), Pair(3 * w / 4, 3 * h / 4)  // fixed: was 3*w/4
        )
        for (pt in checkPoints) {
            if (bitmap.getPixel(pt.first, pt.second) != firstPixel) {
                return false
            }
        }
        return true
    }

    /**
     * Signals the activity that this tab's preview/state was updated,
     * which updates its specific position in the adapter.
     */
    private fun notifyTabChanged() {
        val browserActivity = activity as? WebBrowserActivity ?: return
        val index = browserActivity.tabsManager.indexOfTab(this)
        if (index >= 0) {
            browserActivity.runOnUiThread {
                browserActivity.notifyTabViewChanged(index)
            }
        }
    }

    /**
     * Cancel any pending preview capture operations.
     * Called when navigation starts to prevent stale captures.
     */
    fun cancelPendingCapture() {
        captureSequence++
        captureRunnable?.let {
            webViewHandler.removeCallbacks(it)
            captureRunnable = null
        }
        Timber.d("Cancelled pending capture (seq=$captureSequence)")
    }

    /**
     * Capture the View preview efficiently using a direct Canvas draw pass.
     */
    private fun captureWithDrawingOptimized(view: View, targetWidth: Int, targetHeight: Int, scale: Float, expectedSequence: Int) {
        try {
            if (expectedSequence != captureSequence) return

            Timber.d("Capturing tab preview via canvas draw (tab=$id, target=${targetWidth}x${targetHeight})")

            val scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(scaled)

            canvas.drawColor(Color.WHITE)

            canvas.save()
            canvas.scale(scale, scale)
            view.draw(canvas)
            canvas.restore()

            if (expectedSequence == captureSequence) {
                // Reject solid-colour frames (blank WebView not yet rendered)
                if (isBlankBitmap(scaled)) {
                    Timber.w("Canvas draw produced a blank frame for tab=$id; discarding")
                    return
                }
                TabThumbnailCache.put(id, scaled, persistable = !isIncognito)
                lastCaptureTime = System.currentTimeMillis()
                notifyTabChanged()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture preview for tab=$id")
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "OOM while capturing preview for tab=$id — skipping")
        }
    }

    /**
     * Invalidate the cached preview so it will be regenerated on next request
     */
    fun invalidatePreview() {
        TabThumbnailCache.remove(id)
    }

    /**
     * Permanently remove this tab's cached thumbnail (memory + disk).
     * Called from [destroy] so closing a tab never leaves orphaned files.
     */
    private fun evictThumbnail() {
        TabThumbnailCache.remove(id)
    }

    companion object {

        public const val KHtmlMetaThemeColorInvalid: Int = Color.TRANSPARENT

        const val HEADER_REQUESTED_WITH = "X-Requested-With"
        const val HEADER_WAP_PROFILE = "X-Wap-Profile"
        private const val HEADER_DNT = "DNT"
        private const val HEADER_SAVEDATA = "Save-Data"

        /**
         * Delay in ms after [WebPageClient.onPageFinished] before we snapshot the WebView.
         * onPageFinished fires when the HTML is parsed, but images/fonts/JS may still be painting.
         * 800ms gives enough time for the WebView to produce a visually complete frame.
         */
        private const val CAPTURE_DELAY_MS = 800L

        /** Entrance/exit animation tuning for the video-download FAB. */
        private const val FAB_ENTRANCE_DURATION_MS = 220L
        private const val FAB_EXIT_DURATION_MS = 150L
        private const val FAB_ENTRANCE_START_SCALE = 0.85f

        private val API = Build.VERSION.SDK_INT
        private val SCROLL_UP_THRESHOLD = com.xhub.browser.utils.Utils.dpToPx(10f)
        private val SCROLL_DOWN_THRESHOLD = com.xhub.browser.utils.Utils.dpToPx(30f)

        private val negativeColorArray = floatArrayOf(
            -1.0f, 0f, 0f, 0f, 255f, // red
            0f, -1.0f, 0f, 0f, 255f, // green
            0f, 0f, -1.0f, 0f, 255f, // blue
            0f, 0f, 0f, 1.0f, 0f // alpha
        )
        private val increaseContrastColorArray = floatArrayOf(
            2.0f, 0f, 0f, 0f, -160f, // red
            0f, 2.0f, 0f, 0f, -160f, // green
            0f, 0f, 2.0f, 0f, -160f, // blue
            0f, 0f, 0f, 1.0f, 0f // alpha
        )
    }
}
