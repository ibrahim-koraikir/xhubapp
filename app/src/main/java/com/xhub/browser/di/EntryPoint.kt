package com.xhub.browser.di

import com.xhub.browser.adblock.AbpBlockerManager
import com.xhub.browser.adblock.AbpUserRules
import com.xhub.browser.adblock.NoOpAdBlocker
import com.xhub.browser.browser.TabsManager
import com.xhub.browser.database.bookmark.BookmarkRepository
import com.xhub.browser.database.downloads.DownloadsRepository
import com.xhub.browser.database.history.HistoryRepository
import com.xhub.browser.dialog.LightningDialogBuilder
import com.xhub.browser.download.DownloadHandler
import com.xhub.browser.favicon.FaviconModel
import com.xhub.browser.html.homepage.HomePageFactory
import com.xhub.browser.js.InvertPage
import com.xhub.browser.js.SetMetaViewport
import com.xhub.browser.js.TextReflow
import com.xhub.browser.js.ThemeColor
import com.xhub.browser.network.NetworkConnectivityModel
import com.xhub.browser.search.SearchEngineProvider
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.view.webrtc.WebRtcPermissionsModel
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.Scheduler

/**
 * Provide access to all our injectable classes.
 * Virtual fields can't resolve qualifiers for some reason.
 * Therefore we use functions where qualifiers are needed.
 *
 * Just add your class here if you need it.
 *
 * TODO: See if and how we can use the 'by' syntax to initialize usage of those.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltEntryPoint {

    val bookmarkRepository: BookmarkRepository
    val userPreferences: UserPreferences
    @UserPrefs
    fun userSharedPreferences(): SharedPreferences
    val historyRepository: HistoryRepository
    @DatabaseScheduler
    fun databaseScheduler(): Scheduler
    @NetworkScheduler
    fun networkScheduler(): Scheduler
    @DiskScheduler
    fun diskScheduler(): Scheduler
    @MainScheduler
    fun mainScheduler(): Scheduler
    val searchEngineProvider: SearchEngineProvider
    val textReflowJs: TextReflow
    val invertPageJs: InvertPage
    val setMetaViewport: SetMetaViewport
    val themeColorJs: ThemeColor
    val homePageFactory: HomePageFactory
    val abpBlockerManager: AbpBlockerManager
    val noopBlocker: NoOpAdBlocker
    val networkEngineManager: com.xhub.browser.network.NetworkEngineManager
    val userScriptManager: com.xhub.browser.userscript.UserScriptManager
    val dialogBuilder: LightningDialogBuilder
    val networkConnectivityModel: NetworkConnectivityModel
    val faviconModel: FaviconModel
    val webRtcPermissionsModel: WebRtcPermissionsModel
    val abpUserRules: AbpUserRules
    val downloadHandler: DownloadHandler
    val downloadManager: DownloadManager
    val downloadsRepository: DownloadsRepository
    var tabsManager: TabsManager
    var clipboardManager: ClipboardManager

}


