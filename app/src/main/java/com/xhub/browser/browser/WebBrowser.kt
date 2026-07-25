package com.xhub.browser.browser

import android.content.Intent
import com.xhub.browser.database.Bookmark
import com.xhub.browser.dialog.LightningDialogBuilder
import com.xhub.browser.ssl.SslState
import com.xhub.browser.view.WebPageTab
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.StringRes

/**
 * TODO: Find a proper name for that class
 */
interface WebBrowser {

    /**
     * Called when our current tab view needs to be changed.
     * Implementer typically will remove the currently bound tab view and hook the one provided here.
     *
     * [aView] is in fact a WebViewEx however this could change.
     * [aWasTabAdded] True if [aView] is a newly created tab.
     * [aPreviousTabClosed] True if the current foreground tab [aView] will replaced was closed.
     * [aGoingBack] True if we are going back rather than forward in our tab cycling.
     */
    fun setTabView(aView: View, aWasTabAdded: Boolean, aPreviousTabClosed: Boolean, aGoingBack: Boolean)


    fun updateTabNumber(number: Int)


    fun closeBrowser()

    /**
     * Only called when the current tab just opened from an external app through ACTION_VIEW intent is closed
     * TODO: Rename that to something more explicit
     */
    fun closeActivity(aIntent: Intent)

    fun showBlockedLocalFileDialog(onPositiveClick: () -> Unit)

    fun showSnackbar(@StringRes resource: Int)
    
    fun notifyTabViewRemoved(position: Int)

    fun notifyTabViewAdded()

    fun notifyTabViewChanged(position: Int)

    fun notifyTabViewInitialized()

    /**
     * Triggered whenever user reaches the maximum amount of tabs allowed by her license.
     * Should typically display a message warning the user about it.
     */
    fun onMaxTabReached()

    /**
     * Set the browser address bar text.
     */
    fun setAddressBarText(aText: String)

    /**
     * @return the current color of the UI as a color integer.
     */
    @ColorInt
    fun getUiColor(): Int

    /**
     * @return true if color mode is enabled, false otherwise.
     */
    fun isColorMode(): Boolean

    /**
     * @return the tab model which contains all the tabs presented to the user.
     */
    fun getTabModel(): TabsManager

    /**
     * Notifies the controller of a change in the favicon, indicating that the UI should adapt to
     * the color of the favicon.
     *
     * @param favicon the new favicon
     * @param color meta theme color as specified in HTML
     * @param tabBackground the background of the tab, only used when tabs are not displayed in the
     * drawer.
     */
    fun changeToolbarBackground(favicon: Bitmap?, color: Int, tabBackground: Drawable?)

    /**
     * Update the loading progress of the page.
     *
     * @param aProgress the loading progress of the page, an integer between 0 and 100.
     */
    fun onProgressChanged(aTab: WebPageTab, aProgress: Int)

    /**
     * Notify the controller that a [url] has been visited.
     *
     * @param title the optional title of the current page being viewed.
     * @param url the URL of the page being viewed.
     */
    fun updateHistory(title: String?, url: String)

    /**
     * Notify the controller that it should open the file chooser with the provided callback.
     */
    fun openFileChooser(uploadMsg: ValueCallback<Uri>)

    /**
     * Notify the controller that it should open the file chooser with the provided callback.
     */
    fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>)

    /**
     * Notify the controller that it should display the custom [view] in full-screen to the user.
     */
    fun onShowCustomView(
        view: View,
        callback: WebChromeClient.CustomViewCallback,
        requestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    )

    /**
     * Notify the controller that it should hide the custom view which was previously displayed in
     * full screen to the user.
     */
    fun onHideCustomView()

    /**
     * Called when a website wants to open a link in a new window.
     *
     * @param resultMsg the message to send to the new web view that is created.
     * @return true if a new tab (and thus a transport WebView) was actually created and the
     *         [resultMsg] was consumed; false if creation was rejected (e.g. the max tab count
     *         was reached). When this returns false the caller MUST decline the popup by
     *         returning false from [android.webkit.WebChromeClient.onCreateWindow], otherwise
     *         Chromium tries to host the popup in the parent WebView and crashes with
     *         "Parent WebView cannot host its own popup window".
     */
    fun onCreateWindow(resultMsg: Message): Boolean

    /**
     * Notify the browser that the website currently being displayed by the [tab] wants to be
     * closed.
     */
    fun onCloseWindow(tab: WebPageTab)

    /**
     * Hide the search bar from view via animation.
     */
    fun hideActionBar()

    /**
     * Show the search bar via animation.
     */
    fun showActionBar()

    /**
     * Show the close browser dialog for the tab at [position].
     */
    fun showCloseDialog(position: Int)

    /**
     * Notify the browser that the new tab button was clicked.
     */
    fun newTabButtonClicked()

    /**
     * Notify the browser that the new tab button was long pressed by the user.
     */
    fun newTabButtonLongClicked()

    /**
     * Notify the browser that the tab close button was clicked for the tab at [position].
     */
    fun tabCloseClicked(position: Int)

    /**
     * Notify the browser that the tab at [position] was selected by the user for display.
     */
    fun tabClicked(position: Int)

    /**
     * Notify the browser that the user pressed the bookmark button.
     */
    fun bookmarkButtonClicked()

    /**
     * Notify the browser that the user clicked on the bookmark [entry].
     */
    fun bookmarkItemClicked(entry: Bookmark.Entry)

    /**
     * Notify the UI that the [aTab] should be displayed.
     * This is called when a tab becomes the current tab.
     * It is also called when a tab is updated and it does not have to be the current tab.
     * The activity checks the tab manager for the current tab.
     */
    fun onTabChanged(aTab: WebPageTab)

    /**
     * Notify the UI that [aTab] started loading a page.
     */
    fun onPageStarted(aTab: WebPageTab)

    /**
     *
     */
    fun onTabChangedUrl(aTab: WebPageTab)

    /**
     *
     */
    fun onTabChangedIcon(aTab: WebPageTab)

    /**
     *
     */
    fun onTabChangedTitle(aTab: WebPageTab)

    /**
     * Notify the browser that the user pressed the back button.
     */
    fun onBackButtonPressed()

    /**
     * Notify the browser that the user pressed the forward button.
     */
    fun onForwardButtonPressed()

    /**
     * Notify the browser that the user pressed the home (not device home) button.
     */
    fun onHomeButtonPressed()

    /**
     * Notify the browser that the bookmarks list has changed.
     */
    fun handleBookmarksChange()

    /**
     * Notify the browser that the provided bookmark [bookmark] has changed.
     */
    fun handleBookmarkDeleted(bookmark: Bookmark)

    /**
     * Notify the browser that the history list has changed.
     */
    fun handleHistoryChange()

    /**
     * Notify the controller that a new tab action has originated from a dialog with the [url] and
     * the provided [newTabType].
     */
    fun handleNewTab(newTabType: LightningDialogBuilder.NewTab, url: String)

    /**
     *
     */
    fun executeAction(@IdRes id: Int): Boolean

    /**
     * TODO: Defined both in BrowserView and UIController
     * Sort out that mess.
     */
    fun updateSslState(sslState: SslState)

    /**
     *
     */
    fun onSingleTapUp(aTab: WebPageTab)

    /**
     * Determines if the current browser instance is in incognito mode or not.
     */
    fun isIncognito(): Boolean

    /**
     * Called by [com.xhub.browser.view.WebPageClient] whenever a user-gesture in-page link tap
     * triggers a main-frame navigation. The activity may open a direct-link ad as a **normal tab**.
     * @return true only if the original navigation must be cancelled (current direct-link ads
     * always return false so the user's link still loads).
     */
    fun onUserGestureNavigation(url: String): Boolean

    /**
     * Closes all open panels (tabs, bookmarks, etc.)
     *
     */
    fun closePanels()

}
