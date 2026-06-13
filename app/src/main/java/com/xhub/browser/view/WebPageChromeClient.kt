package com.xhub.browser.view

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.parseAsHtml
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.EntryPointAccessors
import com.xhub.browser.R
import com.xhub.browser.browser.WebBrowser
import com.xhub.browser.constant.Schemes
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.dialog.BrowserDialog
import com.xhub.browser.dialog.DialogItem
import com.xhub.browser.extensions.launch
import com.xhub.browser.extensions.originToDomain
import com.xhub.browser.favicon.FaviconModel
import com.xhub.browser.permissions.PermissionsManager
import com.xhub.browser.permissions.PermissionsResultAction
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.view.webrtc.WebRtcPermissionsModel
import com.xhub.browser.view.webrtc.WebRtcPermissionsView
import io.reactivex.Scheduler
import timber.log.Timber

/**
 * We have one instance of this per [WebView].
 */
class WebPageChromeClient(
    private val activity: Activity,
    private val webPageTab: WebPageTab
) : WebChromeClient(),
    WebRtcPermissionsView {

    private val geoLocationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private val webBrowser: WebBrowser = activity as WebBrowser

    companion object {
        // Console message prefix for meta tag updates from JavaScript
        private const val META_TAG_PREFIX = "${Schemes.Fulguris}: "
        private const val META_THEME_COLOR_PREFIX = "${META_TAG_PREFIX}meta-theme-color: "
        private const val META_COLOR_SCHEME_PREFIX = "${META_TAG_PREFIX}meta-color-scheme: "
    }


    private val hiltEntryPoint = EntryPointAccessors.fromApplication(activity.applicationContext, HiltEntryPoint::class.java)
    val faviconModel: FaviconModel = hiltEntryPoint.faviconModel
    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val webRtcPermissionsModel: WebRtcPermissionsModel = hiltEntryPoint.webRtcPermissionsModel
    val diskScheduler: Scheduler = hiltEntryPoint.diskScheduler()
    private val themeColorJs = hiltEntryPoint.themeColorJs


    override fun onProgressChanged(view: WebView, newProgress: Int) {
        Timber.v("onProgressChanged: $newProgress")

        webBrowser.onProgressChanged(webPageTab, newProgress)

        // We don't need to run that when color mode is disabled
        if (userPreferences.colorModeEnabled) {
            if (newProgress > 10 && webPageTab.shouldFetchMetaTags)
            {
                webPageTab.shouldFetchMetaTags = false

                // Extract meta theme-color and setup observer for changes
                // Results are parsed from onConsoleMessage
                Timber.i("evaluateJavascript: theme color extraction and observer setup")
                view.evaluateJavascript(themeColorJs.provideJs(), null)
            }
        }
    }

    /**
     * Called once the favicon is ready
     */
    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
        Timber.d("onReceivedIcon")
        webPageTab.titleInfo.setFavicon(icon)
        webBrowser.onTabChangedIcon(webPageTab)
        cacheFavicon(view.url, icon)
    }

    /**
     * Naive caching of the favicon according to the domain name of the URL
     *
     * @param icon the icon to cache
     */
    private fun cacheFavicon(url: String?, icon: Bitmap?) {
        if (icon == null || url == null) {
            return
        }

        faviconModel.cacheFaviconForUrl(icon, url)
            .subscribeOn(diskScheduler)
            .subscribe()
    }

    /**
     * From [WebChromeClient.onReceivedTitle]
     * Not called when going through page history on YouTube between entries with the same title.
     */
    override fun onReceivedTitle(view: WebView?, title: String?) {
        Timber.i("onReceivedTitle: $title")

        // First update web page property
        if (title?.isNotEmpty() == true) {
            webPageTab.titleInfo.setTitle(title)
        } else {
            webPageTab.titleInfo.setTitle(activity.getString(R.string.untitled))
        }

        // Then notify the browser
        webBrowser.onTabChangedTitle(webPageTab)
        if (view != null && view.url != null) {
            webBrowser.updateHistory(title, view.url as String)
        }
    }

    /**
     * This is some sort of alternate favicon. F-Droid and Wikipedia have one for instance.
     * BBC has lots of them.
     * Possibly higher resolution than your typical favicon?
     */
    override fun onReceivedTouchIconUrl(view: WebView?, url: String?, precomposed: Boolean) {
        Timber.d("onReceivedTouchIconUrl: $url")
        super.onReceivedTouchIconUrl(view, url, precomposed)
    }

    /**
     *
     */
    override fun onRequestFocus(view: WebView?) {
        Timber.d("onRequestFocus")
        super.onRequestFocus(view)
    }

    /**
     *
     */
    private fun showJsBottomSheet(
        title: String?,
        message: String?,
        defaultValue: String? = null,
        isPrompt: Boolean = false,
        isConfirm: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_js_bottom_sheet, null)
        dialog.setContentView(view)

        val titleView = view.findViewById<android.widget.TextView>(R.id.dialog_title)
        val messageView = view.findViewById<android.widget.TextView>(R.id.dialog_message)
        val inputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialog_input_layout)
        val inputView = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialog_input)
        val btnPositive = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_positive)
        val btnNegative = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_negative)

        titleView.text = title ?: activity.getString(R.string.title_js_dialog)
        messageView.text = message

        if (isPrompt) {
            inputLayout.visibility = View.VISIBLE
            inputView.setText(defaultValue)
            inputView.requestFocus()
        }

        if (isConfirm || isPrompt) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = activity.getString(R.string.action_cancel)
            btnNegative.setOnClickListener {
                onResult(false, null)
                dialog.dismiss()
            }
        }

        btnPositive.text = activity.getString(R.string.action_ok)
        btnPositive.setOnClickListener {
            onResult(true, inputView.text?.toString())
            dialog.dismiss()
        }

        dialog.setOnCancelListener {
            onResult(false, null)
        }

        dialog.show()
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        Timber.d("onJsAlert")
        showJsBottomSheet(
            title = url?.originToDomain(),
            message = message,
            onResult = { _, _ -> result?.confirm() }
        )
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        Timber.d("onJsConfirm")
        showJsBottomSheet(
            title = url?.originToDomain(),
            message = message,
            isConfirm = true,
            onResult = { confirmed, _ ->
                if (confirmed) result?.confirm() else result?.cancel()
            }
        )
        return true
    }

    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        Timber.d("onJsPrompt")
        showJsBottomSheet(
            title = url?.originToDomain(),
            message = message,
            defaultValue = defaultValue,
            isPrompt = true,
            onResult = { confirmed, input ->
                if (confirmed) result?.confirm(input) else result?.cancel()
            }
        )
        return true
    }

    override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        Timber.d("onJsBeforeUnload")
        showJsBottomSheet(
            title = activity.getString(R.string.title_js_before_unload),
            message = message ?: activity.getString(R.string.message_js_before_unload),
            isConfirm = true,
            onResult = { confirmed, _ ->
                if (confirmed) result?.confirm() else result?.cancel()
            }
        )
        return true
    }

    /**
     *
     */
    @Deprecated("Deprecated in Java")
    override fun onJsTimeout(): Boolean {
        // Should never get there
        Timber.d("onJsTimeout")
        return super.onJsTimeout()
    }

    /**
     * From [WebRtcPermissionsView]
     */
    override fun requestPermissions(permissions: Set<String>, onGrant: (Boolean) -> Unit) {
        val missingPermissions = permissions
            // Filter out the permissions that we don't have
            .filter { !PermissionsManager.getInstance().hasPermission(activity, it) }

        if (missingPermissions.isEmpty()) {
            // We got all permissions already, notify caller then
            onGrant(true)
        } else {
            // Ask user for the missing permissions
            PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(
                activity,
                missingPermissions.toTypedArray(),
                object : PermissionsResultAction() {
                    override fun onGranted() = onGrant(true)

                    override fun onDenied(permission: String?) = onGrant(false)
                }
            )
        }
    }

    /**
     * From [WebRtcPermissionsView]
     */
    override fun requestResources(source: String,
                                  resources: Array<String>,
                                  onGrant: (Boolean) -> Unit) {
        // Ask user to grant resource access
        activity.runOnUiThread {
            val resourcesString = resources.joinToString(separator = "\n")
            BrowserDialog.showPositiveNegativeDialog(
                aContext = activity,
                title = R.string.title_permission_request,
                message = R.string.message_permission_request,
                messageArguments = arrayOf(source, resourcesString),
                positiveButton = DialogItem(title = R.string.action_allow) { onGrant(true) },
                negativeButton = DialogItem(title = R.string.action_dont_allow) { onGrant(false) },
                onCancel = { onGrant(false) }
            )
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPermissionRequest(request: PermissionRequest) {
        Timber.d("onPermissionRequest")
        if (userPreferences.webRtcEnabled) {
            webRtcPermissionsModel.requestPermission(request, this)
        } else {
            //TODO: display warning message as snackbar I guess
            request.deny()
        }
    }

    /**
     * From [WebChromeClient.onGeolocationPermissionsShowPrompt]
     *
     * Called when a domain location permission is requested.
     */
    override fun onGeolocationPermissionsShowPrompt(origin: String,
                                                    callback: GeolocationPermissions.Callback) {
        Timber.d("onGeolocationPermissionsShowPrompt: $origin")

        // Strip URL scheme, port, and trailing slash
        val domain = origin.originToDomain()
        val displayDomain = if (domain.length > 50) {
            "${domain.subSequence(0, 50)}..."
        } else {
            domain
        }

        // Inflate layout with checkbox
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_with_checkbox, null)
        val checkboxView = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxDontAskAgain)

        // Show dialog first
        MaterialAlertDialogBuilder(activity).apply {
            setIcon(R.drawable.ic_location)
            setTitle(activity.getString(R.string.dialog_title_grant_location_access))
            setMessage(activity.getString(R.string.dialog_message_grant_location_access, displayDomain).parseAsHtml())
            setView(dialogView)
            setCancelable(true)
            setPositiveButton(activity.getString(R.string.action_yes)) { _, _ ->
                val remember = checkboxView.isChecked
                // User accepted in dialog, now request Android permissions if needed
                PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(
                    activity,
                    geoLocationPermissions,
                    object : PermissionsResultAction() {
                        override fun onGranted() {
                            callback.invoke(origin, true, remember)
                        }

                        override fun onDenied(permission: String) {
                            callback.invoke(origin, false, false)
                        }
                    }
                )
            }
            setNegativeButton(activity.getString(R.string.action_no)) { _, _ ->
                val remember = checkboxView.isChecked
                callback.invoke(origin, false, remember)
            }
            setOnCancelListener {
                callback.invoke(origin, false, false)
            }
        }.launch()
    }


    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        Timber.d("onCreateWindow")
        // TODO: redo that
        webBrowser.onCreateWindow(resultMsg)
        //TODO: surely that can't be right,
        return true
        //return false
    }

    override fun onCloseWindow(window: WebView) {
        Timber.d("onCloseWindow")
        webBrowser.onCloseWindow(webPageTab)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun openFileChooser(uploadMsg: ValueCallback<Uri>) = webBrowser.openFileChooser(uploadMsg)

    @Suppress("unused", "UNUSED_PARAMETER")
    fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String) =
        webBrowser.openFileChooser(uploadMsg)

    @Suppress("unused", "UNUSED_PARAMETER")
    fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) =
        webBrowser.openFileChooser(uploadMsg)

    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                                   fileChooserParams: FileChooserParams): Boolean {
        Timber.d("onShowFileChooser - acceptTypes: ${fileChooserParams.acceptTypes.contentToString()}")

        // Default file chooser for file inputs
        webBrowser.showFileChooser(filePathCallback)
        return true
    }


    /**
     * Obtain an image that is displayed as a placeholder on a video until the video has initialized
     * and can begin loading.
     *
     * @return a Bitmap that can be used as a place holder for videos.
     */
    override fun getDefaultVideoPoster(): Bitmap? {
        Timber.d("getDefaultVideoPoster")
        // TODO: In theory we could even load site specific icons here or just tint that drawable using the site theme color
        val bitmap = AppCompatResources.getDrawable(activity, R.drawable.ic_filmstrip)?.toBitmap(1024,1024)
        if (bitmap==null) {
            Timber.d("Failed to load video poster")
        }
        return bitmap
    }

    /**
     * Inflate a view to send to a [WebPageTab] when it needs to display a video and has to
     * show a loading dialog. Inflates a progress view and returns it.
     *
     * @return A view that should be used to display the state
     * of a video's loading progress.
     */
    override fun getVideoLoadingProgressView(): View {
        // Not sure that's ever being used anymore
        Timber.d("getVideoLoadingProgressView")
        return LayoutInflater.from(activity).inflate(R.layout.video_loading_progress, null)
    }


    override fun onHideCustomView() {
        Timber.d("onHideCustomView")
        webBrowser.onHideCustomView()
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        Timber.d("onShowCustomView")
        webBrowser.onShowCustomView(view, callback)
    }


    @Deprecated("Deprecated in Java")
    override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) {
        Timber.d("onShowCustomView: $requestedOrientation")
        webBrowser.onShowCustomView(view, callback, requestedOrientation)
    }


    /**
     * Needed to display javascript console message in logcat.
     */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        //Timber.tag(tag).d("message")

        consoleMessage.apply {
            val tag = "JavaScript"
            val log = "${messageLevel()} - ${message()} -- from line ${lineNumber()} of ${sourceId()}"

            // Collect the console message object in WebPageTab
            webPageTab.addConsoleMessage(consoleMessage)

            // Check if this is a Fulguris meta tag notification from our MutationObserver
            val msg = message()
            if (messageLevel() == ConsoleMessage.MessageLevel.TIP
                && userPreferences.colorModeEnabled
                && msg.startsWith(META_TAG_PREFIX)) {
                when {
                    msg.startsWith(META_THEME_COLOR_PREFIX) -> {
                        // Extract theme-color value after the prefix
                        val colorValue = msg.substringAfter(META_THEME_COLOR_PREFIX).trim()
                        try {
                            // Color.parseColor handles hex (#RGB, #RRGGBB, #AARRGGBB), rgb(), rgba(), and named colors
                            val color = Color.parseColor(colorValue)
                            if (webPageTab.htmlMetaThemeColor != color) {
                                // Format as 8-digit hex with alpha (AARRGGBB)
                                val hexColor = String.format("#%08X", color)
                                Timber.i("New meta theme-color: '$colorValue' == $hexColor (ARGB: ${Color.alpha(color)}, ${Color.red(color)}, ${Color.green(color)}, ${Color.blue(color)})")
                                webPageTab.htmlMetaThemeColor = color
                                webBrowser.onTabChanged(webPageTab)
                            }
                        } catch (e: Exception) {
                            Timber.w("Could not parse theme color: $colorValue - ${e.message}")
                        }
                    }
                    msg.startsWith(META_COLOR_SCHEME_PREFIX) -> {
                        // Extract color-scheme value after the prefix
                        val schemeValue = msg.substringAfter(META_COLOR_SCHEME_PREFIX).trim()
                        Timber.i("New meta color-scheme: $schemeValue")
                        // TODO: Handle color-scheme changes (light, dark, light dark, etc.)
                        // This could be used to automatically switch between light/dark themes
                    }
                }
            }

            // Here is what we got on HONOR Magic V2:
            // - console.log: LOG
            // - console.info: LOG
            // - console.trace: LOG
            // - console.group: LOG
            // - console.error: ERROR
            // - console.assert: ERROR
            // - console.warn: WARNING
            // - console.debug: TIP
            // - console.timer: TIP

            when (messageLevel()) {
                ConsoleMessage.MessageLevel.DEBUG -> Timber.tag(tag).d(log)
                ConsoleMessage.MessageLevel.WARNING -> Timber.tag(tag).w(log)
                ConsoleMessage.MessageLevel.ERROR -> Timber.tag(tag).e(log)
                ConsoleMessage.MessageLevel.TIP -> Timber.tag(tag).i(log)
                ConsoleMessage.MessageLevel.LOG -> Timber.tag(tag).v(log)
                null -> Timber.tag(tag).d(log)
            }
        }
        return true
    }

}
