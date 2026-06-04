/*
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright 2014 A.C.R. Development
 */

package fulguris.view

import fulguris.Capabilities
import fulguris.R
import fulguris.activity.ThemedActivity
import fulguris.browser.TabModel
import fulguris.activity.WebBrowserActivity
import fulguris.browser.WebBrowser
import fulguris.browser.tabs.TabThumbnailCache
import fulguris.utils.isHomeUri
import fulguris.utils.isStartPageUrl
import fulguris.utils.isBookmarkUri
import fulguris.utils.isBookmarkUrl
import fulguris.dialog.LightningDialogBuilder
import fulguris.download.LightningDownloadListener
import fulguris.extensions.*
import fulguris.isSupported
import fulguris.network.NetworkConnectivityModel
import fulguris.settings.fragment.DisplaySettingsFragment.Companion.MIN_BROWSER_TEXT_SIZE
import fulguris.settings.preferences.DomainPreferences
import fulguris.settings.preferences.UserPreferences
import fulguris.settings.preferences.userAgent
import fulguris.settings.preferences.webViewEngineVersionDesktop
import fulguris.settings.preferences.setReducedClientHints
import fulguris.ssl.SslState
import fulguris.js.VideoJavascriptInterface
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.EntryPointAccessors
import fulguris.constant.Hosts
import fulguris.constant.Schemes
import fulguris.constant.Uris
import fulguris.constant.WINDOWS_DESKTOP_USER_AGENT_PREFIX
import fulguris.di.HiltEntryPoint
import fulguris.di.configPrefs
import fulguris.enums.LayerType
import fulguris.extensions.canScrollVertically
import fulguris.extensions.dp
import fulguris.extensions.isDarkTheme
import fulguris.extensions.makeSnackbar
import fulguris.extensions.px
import fulguris.extensions.removeFromParent
import fulguris.extensions.setIcon
import fulguris.utils.ThemeUtils
import fulguris.utils.isBookmarkUrl
import fulguris.utils.isDownloadsUrl
import fulguris.utils.isHistoryUrl
import fulguris.utils.isSpecialUrl
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
    private val downloadPageInitializer: DownloadPageInitializer,
    private val historyPageInitializer: HistoryPageInitializer
): WebView.FindListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * The unique ID of the view.
     * Persisted across sessions via TabModel to ensure stable thumbnail cache keys.
     */
    val id = if (tabInitializer is FreezableBundleInitializer && tabInitializer.tabModel.tabId != -1) {
        tabInitializer.tabModel.tabId
    } else {
        View.generateViewId()
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
    fun getConsoleMessages(): List<ConsoleMessage> = consoleMessages.toList()

    /**
     * Clear tracked console messages
     */
    fun clearConsoleMessages() {
        consoleMessages.clear()
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
            iTargetUrl = value
            clearVideoDetectedState()
        }

    var isVideoDetected = false
        private set
    var detectedVideoUrl: String? = null
        private set
    var detectedQualities: Map<String, String>? = null
        private set
    var detectedResolution: String? = null
        private set

    private fun clearVideoDetectedState() {
        if (isVideoDetected) {
            isVideoDetected = false
            detectedVideoUrl = null
            detectedQualities = null
            detectedResolution = null
            activity.runOnUiThread { hideDownloadFab() }
        }
    }

    private fun showDownloadFab() {
        val fab = activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabDownloadVideo)
        fab?.let {
            it.visibility = View.VISIBLE
            it.setOnClickListener { _ ->
                showVideoDownloadSheet()
            }
        }
    }

    private fun showVideoDownloadSheet() {
        val videoUrl = detectedVideoUrl ?: return
        val qualities = detectedQualities

        val dialog = BottomSheetDialog(activity)
        val sheetView = LayoutInflater.from(activity).inflate(
            R.layout.bottom_sheet_video_download, null
        )
        dialog.setContentView(sheetView)

        // --- Populate host / URL ---
        val host = try {
            android.net.Uri.parse(videoUrl).host ?: videoUrl
        } catch (e: Exception) { videoUrl }
        sheetView.findViewById<TextView>(R.id.tvVideoHost).text = host

        // --- Populate filename from page title ---
        val pageTitle = titleInfo.getTitle()
            .ifBlank { host }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") // sanitize illegal chars
        sheetView.findViewById<TextView>(R.id.tvVideoFilename).text = "$pageTitle.mp4"

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

        if (qualities != null && qualities.size > 1) {
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

        // --- Buttons ---
        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVideoCancel)
            .setOnClickListener { dialog.dismiss() }

        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVideoDownload)
            .setOnClickListener {
                startDownload(selectedDownloadUrl)
                dialog.dismiss()
                hideDownloadFab()
            }

        dialog.show()
    }

    private fun startDownload(url: String) {
        val downloadHandler = hiltEntryPoint.downloadHandler
        // Use page title as filename hint to avoid "unknown"
        val rawTitle = titleInfo.getTitle().ifBlank {
            try { android.net.Uri.parse(url).host ?: "video" } catch (e: Exception) { "video" }
        }
        val safeTitle = rawTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val contentDisposition = "attachment; filename=\"$safeTitle.mp4\""
        
        downloadHandler.onDownloadStart(
            activity,
            userPreferences,
            url,
            userAgent,
            contentDisposition,
            "video/*",
            activity.getString(R.string.unknown_file_size),
            webView?.url // Pass current page URL as referer
        )
        hideDownloadFab()
    }

    private fun hideDownloadFab() {
        val fab = activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabDownloadVideo)
        fab?.visibility = View.GONE
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

            addJavascriptInterface(VideoJavascriptInterface(this@WebPageTab), "VideoSniffer")

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
        //See: https://console.firebase.google.com/project/fulguris-b1f69/crashlytics/app/android:net.slions.fulguris.full.playstore/issues/ea99c7ea0c57f66eae6e95532a16859d
        if (iDownloadListener!=null) {
            webView?.setDownloadListener(null)
            activity.unregisterReceiver(iDownloadListener)
            iDownloadListener = null
        }
    }


    fun onVideoDetected(videoUrl: String, qualitiesJson: String?, resolution: String?) {
        isVideoDetected = true
        detectedVideoUrl = videoUrl
        detectedResolution = resolution?.takeIf { it.isNotBlank() }

        if (qualitiesJson != null) {
            try {
                val json = JSONObject(qualitiesJson)
                val map = mutableMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.getString(key)
                }
                detectedQualities = map
            } catch (e: Exception) {
                Timber.e(e, "Error parsing video qualities")
            }
        }

        activity.runOnUiThread {
            if (!isShown) return@runOnUiThread
            if (isForeground) {
                showDownloadFab()
            }
        }
    }

    fun currentSslState(): SslState = webPageClient.sslState

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
     * This function loads the download page via the [DownloadPageInitializer].
     */
    fun loadDownloadsPage() {
        iTargetUrl = Uri.parse(Uris.FulgurisDownloads)
        initializeContent(downloadPageInitializer)
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

        webPageClient.updatePreferences()


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
        settings.setSupportMultipleWindows(userPreferences.popupsEnabled && !modifiesHeaders)

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
            allowContentAccess = true
            allowFileAccess = true
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
     * This method forces the layer type to hardware, which
     * enables hardware rendering on the WebView instance
     * of the current [WebPageTab].
     */
    private fun setHardwareRendering() {
        //webView?.setLayerType(View.LAYER_TYPE_SOFTWARE, paint)
        webView?.setLayerType(View.LAYER_TYPE_SOFTWARE, paint)
    }

    /**
     * This method sets the layer type to none, which
     * means that either the GPU and CPU can both compose
     * the layers when necessary.
     */
    private fun setNormalRendering() {
        webView?.setLayerType(View.LAYER_TYPE_NONE, paint)
    }

    /**
     * This method forces the layer type to software, which
     * disables hardware rendering on the WebView instance
     * of the current [WebPageTab] and makes the CPU render
     * the view.
     */
    fun setSoftwareRendering() {
        webView?.setLayerType(View.LAYER_TYPE_SOFTWARE, paint)
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
        // Cancel any pending capture tasks before destroying WebView
        cancelPendingCapture()
        // No need to do anything for the touch listeners they are owned by the WebView anyway
        webView?.autoDestruction()
        webView = null
    }

    /**
     * Tell the WebView to navigate backwards
     * in its history to the previous page.
     */
    fun goBack() {
        webView?.goBack()
    }

    /**
     * Tell the WebView to navigate forwards
     * in its history to the next page.
     */
    fun goForward() {
        webView?.goForward()
    }

    /**
     * Navigate forward or backward by the specified number of steps in the history.
     * Positive steps go forward, negative steps go backward.
     *
     * @param steps Number of steps to navigate (negative for back, positive for forward)
     */
    fun goBackOrForward(steps: Int) {
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
            } else if (currentUrl.isDownloadsUrl()) {
                if (url != null) {
                    dialogBuilder.showLongPressedDialogForDownloadUrl(activity, webBrowser, url)
                } else if (newUrl != null) {
                    dialogBuilder.showLongPressedDialogForDownloadUrl(activity, webBrowser, newUrl)
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
    fun loadUrl(aUrl: String, onLoadComplete: (() -> Unit)? = null) {

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
        val preview = TabThumbnailCache.get(id)
        Timber.v("getPreviewBitmap for tab=$id: ${if (preview != null) "found ${preview.width}x${preview.height}" else "not found"}")
        return preview
    }

    /**
     * Schedules a deferred preview capture.
     * Prevents capturing while a page is still rendering.
     * Debounces successive requests and posts on the webViewHandler thread.
     */
    fun scheduleDeferredPreviewCapture() {
        cancelPendingCapture()
        val currentSequence = captureSequence
        val runnable = Runnable {
            if (currentSequence == captureSequence) {
                capturePreviewSync()
            }
        }
        captureRunnable = runnable
        webViewHandler.postDelayed(runnable, CAPTURE_DELAY_MS)
    }

    /**
     * Capture preview asynchronously using modern PixelCopy when possible,
     * falling back to optimized Canvas drawing.
     */
    fun capturePreviewSync() {
        // Debounce successive captures (max once per second)
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < 1000L) {
            Timber.d("Debouncing capture request for tab=$id; last capture was ${now - lastCaptureTime}ms ago")
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

            Timber.d("Capturing preview (seq=$currentSequence, tab=$id): View=${view.width}x${view.height}, Target=${targetWidth}x${targetHeight}, Scale=$scale")

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
                                    TabThumbnailCache.put(id, destBitmap)
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
            Timber.e(e, "Failed to capture preview")
        }
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
            Pair(w / 2, h / 2), Pair(w / 4, h / 4), Pair(3 * w / 4, 3 * w / 4)
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
                TabThumbnailCache.put(id, scaled)
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

        private val API = Build.VERSION.SDK_INT
        private val SCROLL_UP_THRESHOLD = fulguris.utils.Utils.dpToPx(10f)
        private val SCROLL_DOWN_THRESHOLD = fulguris.utils.Utils.dpToPx(30f)

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
