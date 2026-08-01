package com.xhub.browser.browser

import com.xhub.browser.Entitlement
import com.xhub.browser.R
import acr.browser.lightning.browser.sessions.Session
import com.xhub.browser.extensions.snackbar
import com.xhub.browser.search.SearchEngineProvider
import com.xhub.browser.settings.NewTabPosition
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.ssl.SslState
import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import com.xhub.browser.Component
import com.xhub.browser.utils.isBookmarkUrl
import com.xhub.browser.utils.isHistoryUrl
import com.xhub.browser.utils.isIncognitoPageUrl
import com.xhub.browser.utils.isSpecialUrl
import com.xhub.browser.utils.isStartPageUrl
import com.xhub.browser.view.BookmarkPageInitializer
import com.xhub.browser.view.FreezableBundleInitializer
import com.xhub.browser.view.HistoryPageInitializer
import com.xhub.browser.view.HomePageInitializer
import com.xhub.browser.view.IncognitoPageInitializer
import com.xhub.browser.view.NoOpInitializer
import com.xhub.browser.view.TabInitializer
import com.xhub.browser.view.WebPageTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A manager singleton that holds all the [WebPageTab] and tracks the current tab. It handles
 * creation, deletion, restoration, state saving, and switching of tabs and sessions.
 */
//@HiltViewModel
@Singleton
class TabsManager @Inject constructor(
    private val application: Application,
    private val searchEngineProvider: SearchEngineProvider,
    private val homePageInitializer: HomePageInitializer,
    private val incognitoPageInitializer: IncognitoPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer,
    private val historyPageInitializer: HistoryPageInitializer,
    private val noOpPageInitializer: NoOpInitializer,
    private val userPreferences: UserPreferences,
    private val sessionsManager: SessionsManager
): Component() {

    private val tabList = arrayListOf<WebPageTab>()
    private val tabMap = mutableMapOf<Int, WebPageTab>()
    var iRecentTabs = mutableSetOf<WebPageTab>()
    // This is just used when loading and saving sessions.
    // TODO: Ideally it should not be a data member.
    val savedRecentTabsIndices = mutableSetOf<Int>()

    // Mutex to serialize session save operations
    private val saveMutex = Mutex()
    
    // Track the current save job to allow cancellation of superseded saves
    private var currentSaveJob: Job? = null
    
    // Counter to track save requests for coalescing
    @Volatile
    private var pendingSaveRequests = 0


    /**
     * Return the current [WebPageTab] or null if no current tab has been set.
     *
     * @return a [WebPageTab] or null if there is no current tab.
     */
    var currentTab: WebPageTab? = null
        private set

    private var tabNumberListeners = emptySet<(Int) -> Unit>()

    var isInitialized = false
    private var postInitializationWorkList = mutableListOf<InitializationListener>()

    // Set to true while shutdown() destroys tabs, so tab-count notifications fired during a
    // session switch don't write into the session that just became current (the old session's
    // count is persisted by saveState() before the switch, and the new session's count is
    // populated by the newTab() calls that follow).
    private var tabCountUpdatesSuspended = false

    init {

        addTabNumberChangedListener {
            // Update current session tab count
            if (!tabCountUpdatesSuspended) {
                updateCurrentSessionTabCount(it)
            }
        }
    }

    private fun updateCurrentSessionTabCount(count: Int) {
        val session = sessionsManager.sessions().filter { s -> s.name == sessionsManager.currentSessionName() }
        if (session.isNotEmpty()) {
            session[0].tabCount = count
        }
    }

    /*
    override fun onCleared() {
        super.onCleared()
        shutdown()
        app.tabsManager = null;
    }
    */

    /**
     * From [DefaultLifecycleObserver.onStop]
     *
     * This is called once our activity is not visible anymore.
     * That's where we should save our data according to the docs.
     * https://developer.android.com/guide/components/activities/activity-lifecycle#onstop
     * Saving data can't wait for onDestroy as there is no guarantee onDestroy will ever be called.
     * In fact even when user closes our Task from recent Task list our activity is just terminated without getting any notifications.
     */
    override fun onStop(owner: LifecycleOwner) {
        // Once we go background make sure the current tab is not new anymore
        currentTab?.iIntent = null
        saveIfNeeded()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        //shutdown()
    }



    /**
     * Adds a listener to be notified when the number of tabs changes.
     */
    fun addTabNumberChangedListener(listener: ((Int) -> Unit)) {
        tabNumberListeners += listener
    }

    /**
     * Removes a previously registered tab-number-changed listener.
     *
     * This must be called by any Activity-scoped listener in its onDestroy() to avoid leaking the
     * Activity: [TabsManager] is an application-scoped singleton, so a lambda capturing an Activity
     * would otherwise be retained for the whole process lifetime. Pass the exact same reference that
     * was given to [addTabNumberChangedListener].
     */
    fun removeTabNumberChangedListener(listener: ((Int) -> Unit)) {
        tabNumberListeners -= listener
    }

    /**
     * Cancels any pending work that was scheduled to run after initialization.
     */
    fun cancelPendingWork() {
        postInitializationWorkList.clear()
    }


    /**
     * Executes the [runnable] once after the next time this manager has been initialized.
     */
    fun doOnceAfterInitialization(runnable: () -> Unit) {
        if (isInitialized) {
            runnable()
        } else {
            postInitializationWorkList.add(object :
                InitializationListener {
                override fun onInitializationComplete() {
                    runnable()
                    postInitializationWorkList.remove(this)
                }
            })
        }
    }

    /**
     * Executes the [runnable] every time after this manager has been initialized.
     */
    fun doAfterInitialization(runnable: () -> Unit) {
        if (isInitialized) {
            runnable()
        } else {
            postInitializationWorkList.add(object :
                InitializationListener {
                override fun onInitializationComplete() {
                    runnable()
                }
            })
        }
    }

    /**
     *
     */
    private fun finishInitialization() {

        try {
            if (allTabs.size == savedRecentTabsIndices.size) { // Defensive
                // Populate our recent tab list from our persisted indices
                iRecentTabs.clear()
                // Looks like we can somehow persist -1 as a tab index
                // TODO: That should never be the case. We ought to find out what's causing this.
                savedRecentTabsIndices.forEach { iRecentTabs.add(allTabs.elementAt(it))}
            } else {
                // Defensive, if we have missing tabs in our recent tab list just reset it
                resetRecentTabsList()
            }
        }
        catch (ex: Exception) {
            Timber.d("Failed to load recent tab list")
            resetRecentTabsList()
        }

        isInitialized = true

        // Reconcile disk cache with all live tab IDs across all saved sessions asynchronously.
        // Snapshot all data on the main thread; the background thread must not touch SessionsManager.
        val liveIds = allTabs.map { it.id }.toSet()
        val currentSession = sessionsManager.currentSessionName()
        // Exclude the active session — its IDs are already fully represented in liveIds.
        val otherSessionsSnapshot = sessionsManager.sessions()
            .filter { it.name != currentSession }
            .map { session -> Pair(session.name, sessionsManager.fileNameFromSessionName(session.name)) }
        com.xhub.browser.browser.tabs.TabThumbnailCache.reconcileAsync(liveIds, otherSessionsSnapshot, currentSession, application)

        // Iterate through our collection while allowing item to be removed and avoid ConcurrentModificationException
        // To do that we need to make a copy of our list
        val listCopy = postInitializationWorkList.toList()
        for (listener in listCopy) {
            listener.onInitializationComplete()
        }
    }

    /**
     *
     */
    private fun resetRecentTabsList()
    {
        Timber.d("resetRecentTabsList")
        // Reset recent tabs list to arbitrary order
        iRecentTabs.clear()
        iRecentTabs.addAll(allTabs)

        // Put back current tab on top
        currentTab?.let {
            iRecentTabs.apply {
                remove(it)
                add(it)
            }
        }
    }

    /**
     * Initialize the state of the [TabsManager] based on previous state of the browser.
     * This method now only performs main-thread work; disk I/O is done beforehand.
     *
     * @param activity The activity context
     * @param tabInitializers Pre-loaded tab initializers from background thread
     */
    private fun initializeTabs(activity: Activity, tabInitializers: MutableList<TabInitializer>) : MutableList<WebPageTab> {
        Timber.d("initializeTabs")

        shutdown()

        val list = mutableListOf<WebPageTab>()

        if (iWebBrowser.isIncognito()) {
            list.add(newTab(activity, incognitoPageInitializer, NewTabPosition.END_OF_TAB_LIST))
        }
        else {
            tabInitializers.forEach { initializer ->
                try {
                    list.add(newTab(activity, initializer, NewTabPosition.END_OF_TAB_LIST))
                } catch (ex: Throwable) {
                    Timber.e(ex, "Failed to create tab for ${initializer.url()}")
                    // That's a corrupted session file, can happen when importing garbage.
                    activity.snackbar(R.string.error_session_file_corrupted)
                }
            }

            // Make sure we have one tab
            if (list.isEmpty()) {
                list.add(newTab(activity, homePageInitializer, NewTabPosition.END_OF_TAB_LIST))
            }
        }

        Timber.d("initializeTabs: created ${list.size} tabs")
        finishInitialization()

        return list
    }


    /**
     * Load tabs from the given file with automatic corruption recovery:
     * main session → backup session → binary URL scrape.
     */
    private fun loadSession(aFilename: String): MutableList<TabInitializer> {
        Timber.d("loadSession: $aFilename")

        // Step 1: main session file
        var bundle = com.xhub.browser.utils.FileUtils.readBundleFromStorage(application, aFilename)
        var recoveredFrom = aFilename

        // Step 2: backup written during save (BACKUP_SESSION_<name>)
        if (bundle == null) {
            val backupFilename = backupFilenameForSession(aFilename)
            if (backupFilename != null &&
                com.xhub.browser.utils.FileUtils.fileExists(application, backupFilename)
            ) {
                Timber.w("Main session missing/corrupt, trying backup: $backupFilename")
                bundle = com.xhub.browser.utils.FileUtils.readBundleFromStorage(application, backupFilename)
                if (bundle != null) {
                    recoveredFrom = backupFilename
                    Timber.i("Restored session from backup: $backupFilename")
                    // Promote backup to main so next launch uses a healthy primary file
                    com.xhub.browser.utils.FileUtils.renameBundleInStorage(
                        application,
                        backupFilename,
                        aFilename
                    )
                }
            }
        }

        // Defensive. should have happened in the shutdown already
        savedRecentTabsIndices.clear()
        // Read saved current tab index if any
        bundle?.let {
            try {
                it.getIntArray(RECENT_TAB_INDICES)?.toList()?.let { indices ->
                    savedRecentTabsIndices.addAll(indices)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to read recent tab indices")
            }
        }

        val list = mutableListOf<TabInitializer>()
        var tabModels = readSavedStateFromDisk(bundle)

        // Step 3: binary recovery from main, then backup if needed
        if (tabModels.isEmpty()) {
            Timber.w("Normal loading failed, attempting binary recovery")
            var recoveredTabs =
                com.xhub.browser.utils.SessionRecovery.recoverTabsFromSession(application, aFilename)
            if (recoveredTabs.isEmpty()) {
                val backupFilename = backupFilenameForSession(aFilename)
                if (backupFilename != null) {
                    recoveredTabs = com.xhub.browser.utils.SessionRecovery.recoverTabsFromSession(
                        application,
                        backupFilename
                    )
                }
            }
            if (recoveredTabs.isNotEmpty()) {
                Timber.i("Binary recovery found ${recoveredTabs.size} tabs from $recoveredFrom")
                tabModels = recoveredTabs.map { RecoveredTabModel(it.url, it.title) }.toMutableList()
            }
        }

        tabModels.forEach {
            list.add(
                if (it.url.isSpecialUrl()) tabInitializerForSpecialUrl(it.url)
                else FreezableBundleInitializer(it)
            )
        }

        // Make sure we have at least one tab
        if (list.isEmpty()) {
            list.add(homePageInitializer)
        }

        Timber.d("loadSession: ${list.size} tabs loaded")
        return list
    }

    /**
     * Maps a session file name to its save-time backup name.
     * - `SESSION_<name>` → `BACKUP_SESSION_<name>`
     * - legacy default `SAVED_TABS.parcel` → `BACKUP_SESSION_SAVED_TABS.parcel`
     */
    private fun backupFilenameForSession(aFilename: String): String? {
        return when {
            aFilename.startsWith(com.xhub.browser.browser.SessionsManager.FILENAME_SESSION_PREFIX) -> {
                FILENAME_BACKUP_PREFIX +
                    aFilename.removePrefix(com.xhub.browser.browser.SessionsManager.FILENAME_SESSION_PREFIX)
            }
            aFilename == FILENAME_SESSION_DEFAULT -> FILENAME_BACKUP_PREFIX + aFilename
            else -> FILENAME_BACKUP_PREFIX + aFilename
        }
    }

    /**
     * Create a recovery session
     */
    private fun loadRecoverySession(): MutableList<TabInitializer>
    {
        // Defensive. should have happened in the shutdown already
        savedRecentTabsIndices.clear()
        val list = mutableListOf<TabInitializer>()

        // Make sure we have at least one tab
        if (list.isEmpty()) {
            list.add(noOpPageInitializer)
        }
        return list
    }



    /**
     * Returns tab initializers for each previously opened tab as saved on disk.
     */
    private fun restorePreviousTabs(): MutableList<TabInitializer>
    {
        //throw Exception("Hi There!")
        // Check if we have a current session
        val currentSessionName = sessionsManager.currentSessionName()

        if (currentSessionName.isBlank()) {
            // No current session name meaning first load with version support
            // Add our default session
            val newSessionName = application.getString(R.string.session_default)
            val sessions = sessionsManager.sessions()
            sessions.add(Session(newSessionName))
            sessionsManager.setCurrentSession(newSessionName)
            // Than load legacy session file to make sure tabs from earlier version are preserved
            return loadSession(FILENAME_SESSION_DEFAULT)
            // TODO: delete legacy session file at some point
        } else {
            // Load current session then
            return loadSession(sessionsManager.fileNameFromSessionName(currentSessionName))
        }
    }

    /**
     * Safely restore previous tabs.
     */
    private fun tryRestorePreviousTabs(activity: Activity): MutableList<TabInitializer>
    {
        return try {
            restorePreviousTabs()
        } catch (ex: Throwable) {
            // TODO: report this using firebase or local crash logs
            Timber.e(ex,"restorePreviousTabs failed")
            activity.snackbar(R.string.error_recovery_session)
            createRecoverySession()
        }
    }


    /**
     * Called whenever we fail to load a session properly.
     * The idea is that it should enable the app to start even when it's pointing to a corrupted session.
     */
    private fun createRecoverySession(): MutableList<TabInitializer>
    {
        // Force reload of sessions from disk to recover any orphaned session files
        sessionsManager.reloadSessions()

        // Add our recovery session using timestamp
        val recoverySessionName = application.getString(R.string.session_recovery) + "-" + Date().time
        val sessions = sessionsManager.sessions()
        sessions.add(Session(recoverySessionName,1, true))
        sessionsManager.setCurrentSession(recoverySessionName)

        return loadRecoverySession()
    }




    /**
     * Provide a tab initializer for the given special URL
     */
    fun tabInitializerForSpecialUrl(url: String): TabInitializer {
        return when {
            url.isBookmarkUrl() -> bookmarkPageInitializer
            url.isStartPageUrl() -> homePageInitializer
            url.isIncognitoPageUrl() -> incognitoPageInitializer
            url.isHistoryUrl() -> historyPageInitializer
            else -> homePageInitializer
        }
    }

    /**
     * Method used to resume all the tabs in the browser. This is necessary because we cannot pause
     * the WebView when the application is open currently due to a bug in the WebView, where calling
     * onResume doesn't consistently resume it.
     */
    fun resumeAll() {
        currentTab?.resumeTimers()
        for (tab in tabList) {
            // tab.onResume() // Commented out to prevent the WebView blank screen bug
            tab.initializePreferences()
        }
    }

    /**
     * Method used to pause all the tabs in the browser. This is necessary because we cannot pause
     * the WebView when the application is open currently due to a bug in the WebView, where calling
     * onResume doesn't consistently resume it.
     */
    fun pauseAll() {
        currentTab?.pauseTimers()
        // tabList.forEach(WebPageTab::onPause) // Commented out to prevent the WebView blank screen bug
    }

    /**
     * Return the tab at the given position in tabs list, or null if position is not in tabs list
     * range.
     *
     * @param position the index in tabs list
     * @return the corespondent [WebPageTab], or null if the index is invalid
     */
    fun getTabAtPosition(position: Int): WebPageTab? =
        if (position < 0 || position >= tabList.size) {
            null
        } else {
            tabList[position]
        }

    /**
     * Return the tab with the given ID.
     *
     * @param id the unique ID of the tab.
     * @return the [WebPageTab] with the given ID, or null if not found.
     */
    fun getTabById(id: Int): WebPageTab? = tabMap[id]

    val allTabs: List<WebPageTab>
        get() = tabList

    /**
     * Shutdown the manager. This destroys all tabs and clears the references to those tabs.
     * Current tab is also released for garbage collection.
     */
    fun shutdown() {
        Timber.d("shutdown")
        tabCountUpdatesSuspended = true
        try {
            // Deleting from the top of the array should be more efficient
            repeat(tabList.size) { doDeleteTab(tabList.size-1) }
        } finally {
            tabCountUpdatesSuspended = false
        }
        savedRecentTabsIndices.clear()
        isInitialized = false
        currentTab = null
    }

    /**
     * The current number of tabs in the manager.
     *
     * @return the number of tabs in the list.
     */
    fun size(): Int = tabList.size

    /**
     * The index of the last tab in the manager.
     *
     * @return the last tab in the list or -1 if there are no tabs.
     */
    fun last(): Int = tabList.size - 1


    /**
     * The last tab in the tab manager.
     *
     * @return the last tab, or null if there are no tabs.
     */
    fun lastTab(): WebPageTab? = tabList.lastOrNull()

    /**
     * Create and return a new tab. The tab is automatically added to the tabs list.
     *
     * @param activity the activity needed to create the tab.
     * @param tabInitializer the initializer to run on the tab after it's been created.
     * @return a valid initialized tab.
     */
    fun newTab(
        activity: Activity,
        tabInitializer: TabInitializer,
        newTabPosition: NewTabPosition
    ): WebPageTab {
        Timber.i("New tab")
        val tab = WebPageTab(
                activity,
                tabInitializer,
    iWebBrowser.isIncognito(),
                homePageInitializer,
                incognitoPageInitializer,
                bookmarkPageInitializer,
                historyPageInitializer
        )

        // Add our new tab at the specified position
        when(newTabPosition){
            NewTabPosition.BEFORE_CURRENT_TAB -> tabList.add(indexOfCurrentTab(), tab)
            NewTabPosition.AFTER_CURRENT_TAB -> tabList.add(indexOfCurrentTab() + 1, tab)
            NewTabPosition.START_OF_TAB_LIST -> tabList.add(0, tab)
            NewTabPosition.END_OF_TAB_LIST -> tabList.add(tab)
        }
        tabMap[tab.id] = tab

        tabNumberListeners.forEach { it(size()) }
        return tab
    }

    /**
     * Removes a tab from the list and destroys the tab. If the tab removed is the current tab, the
     * reference to the current tab will be nullified.
     *
     * @param position The position of the tab to remove.
     */
    private fun removeTab(position: Int) {
        if (position >= tabList.size) {
            return
        }

        val tab = tabList.removeAt(position)
        tabMap.remove(tab.id)
        iRecentTabs.remove(tab)
        if (currentTab == tab) {
            currentTab = null
        }
        if (currentTabFromPresenter == tab) {
            currentTabFromPresenter = null
        }
        // Eagerly remove the thumbnail for this tab so its disk file is deleted immediately
        // rather than waiting for the next startup reconciliation.
        com.xhub.browser.browser.tabs.TabThumbnailCache.remove(tab.id)
        tab.destroy()
    }

    /**
     * Deletes a tab from the manager. If the tab being deleted is the current tab, this method will
     * switch the current tab to a new valid tab.
     *
     * @param aIndex the position of the tab to delete.
     * @return returns true if the current tab was deleted, false otherwise.
     */
    private fun doDeleteTab(aIndex: Int, aShutdown: Boolean = false): Boolean {
        Timber.i("doDeleteTab: $aIndex")
        val currentTab = currentTab
        val current = positionOf(currentTab)

        if (current == aIndex) {
            when {
                size() == 1 -> this.currentTab = null
                // Switch to previous tab, but not during shutdown
                !aShutdown -> switchToTab(indexOfTab(iRecentTabs.elementAt(iRecentTabs.size - 2)))
            }
        }

        removeTab(aIndex)
        // Skip notifications during shutdown, except the last one
        // tabNumberListeners is used by UI to update tab count display and incognito notification
        if (!aShutdown || size()==0) {
            tabNumberListeners.forEach { it(size()) }
        }
        return current == aIndex
    }

    /**
     * Return the position of the given tab.
     *
     * @param tab the tab to look for.
     * @return the position of the tab or -1 if the tab is not in the list.
     */
    fun positionOf(tab: WebPageTab?): Int = tabList.indexOf(tab)


    /**
     * Save our states if needed.
     */
    private fun saveIfNeeded() {
        if (iWebBrowser.isIncognito()) {
            // We don't persist anything when browsing incognito
            return
        }

        if (userPreferences.restoreTabsOnStartup) {
            saveState()
        }
        else {
            com.xhub.browser.utils.FileUtils.deleteBundleInStorage(application, FILENAME_SESSION_DEFAULT)
        }
    }

    /**
     * Saves the state of the current WebViews, to a bundle which is then stored in persistent
     * storage and can be unparceled.
     */
    fun saveState() {
        Timber.d("saveState")

        // Fix bug where all tabs would get lost
        // See: https://github.com/Slion/Fulguris/issues/193
        if (!isInitialized) {
            Timber.d("saveState - Don't do that")
            return
        }

        // Save sessions info
        sessionsManager.saveSessions()
        // Save our session
        saveCurrentSession(sessionsManager.currentSessionName())
    }

    /**
     * Save current session including WebView tab states and recent tab list in the specified file.
     * This method is now serialized to prevent race conditions from overlapping saves.
     * Uses a dedicated save worker and implements save coalescing for rapid successive calls.
     */
    private fun saveCurrentSession(aName: String) {
        Timber.d("saveCurrentSession - $aName, ${tabList.size} tabs")
        
        // Increment pending save counter for coalescing
        pendingSaveRequests++
        
        // Cancel any existing save job - it will be superseded by this one
        currentSaveJob?.cancel()
        
        // Capture current state on the calling thread
        val outState = Bundle(ClassLoader.getSystemClassLoader())
        tabList
            .withIndex()
            .forEach { (index, tab) ->
                    // Index padding with zero to make sure they are restored in the correct order
                    // That gives us proper sorting up to 99999 tabs which should be more than enough :)
                    outState.putBundle(TAB_KEY_PREFIX + String.format("%05d", index), tab.saveState())
                }

        //Now save our recent tabs
        // Create an array of tab indices from our recent tab list to be persisted
        savedRecentTabsIndices.clear()
        iRecentTabs.forEach { savedRecentTabsIndices.add(indexOfTab(it))}
        outState.putIntArray(RECENT_TAB_INDICES, savedRecentTabsIndices.toIntArray())

        // Launch serialized save operation on dedicated worker
        currentSaveJob = iScopeThreadPool.launch {
            // Decrement counter - we're processing this save
            pendingSaveRequests--
            
            // Acquire mutex to serialize saves - only one save can run at a time
            saveMutex.withLock {
                try {
                    // Check if more saves were requested while we were waiting
                    if (pendingSaveRequests > 0) {
                        Timber.d("Coalescing save - newer save request pending")
                        return@withLock
                    }
                    
                    Timber.d("Starting serialized session save for: $aName")
                    
                    val temp = FILENAME_TEMP_PREFIX + aName
                    val backup = FILENAME_BACKUP_PREFIX + aName
                    val session = sessionsManager.fileNameFromSessionName(aName)

                    // Step 1: Write to temporary file
                    com.xhub.browser.utils.FileUtils.writeBundleToStorage(application, outState, temp)
                    
                    // Verify temp file was created successfully
                    if (!com.xhub.browser.utils.FileUtils.fileExists(application, temp)) {
                        Timber.e("Failed to create temporary session file: $temp")
                        return@withLock
                    }
                    
                    // Step 2: If session file exists, rename it to backup
                    // Keep the previous backup until new session is fully committed
                    if (com.xhub.browser.utils.FileUtils.fileExists(application, session)) {
                        // Delete old backup if it exists
                        if (com.xhub.browser.utils.FileUtils.fileExists(application, backup)) {
                            if (!com.xhub.browser.utils.FileUtils.deleteBundleInStorageVerified(application, backup)) {
                                Timber.e("Failed to delete old backup: $backup")
                                // Continue anyway - we can overwrite it
                            }
                        }
                        
                        // Rename current session to backup
                        if (!com.xhub.browser.utils.FileUtils.renameBundleInStorage(application, session, backup)) {
                            Timber.e("Failed to rename session to backup: $session -> $backup")
                            // This is critical - don't proceed if we can't backup the current session
                            // Delete the temp file to clean up
                            com.xhub.browser.utils.FileUtils.deleteBundleInStorageVerified(application, temp)
                            return@withLock
                        }
                        
                        // Verify backup was created
                        if (!com.xhub.browser.utils.FileUtils.fileExists(application, backup)) {
                            Timber.e("Backup file not found after rename: $backup")
                            // Try to restore from temp
                            com.xhub.browser.utils.FileUtils.renameBundleInStorage(application, temp, session)
                            return@withLock
                        }
                    }
                    
                    // Step 3: Rename temporary file to actual session file
                    if (!com.xhub.browser.utils.FileUtils.renameBundleInStorage(application, temp, session)) {
                        Timber.e("Failed to rename temp to session: $temp -> $session")
                        
                        // Critical failure - try to restore from backup if it exists
                        if (com.xhub.browser.utils.FileUtils.fileExists(application, backup)) {
                            Timber.w("Attempting to restore from backup: $backup")
                            com.xhub.browser.utils.FileUtils.renameBundleInStorage(application, backup, session)
                        }
                        return@withLock
                    }
                    
                    // Verify session file was created successfully
                    if (!com.xhub.browser.utils.FileUtils.fileExists(application, session)) {
                        Timber.e("Session file not found after rename: $session")
                        
                        // Try to restore from backup
                        if (com.xhub.browser.utils.FileUtils.fileExists(application, backup)) {
                            Timber.w("Attempting to restore from backup: $backup")
                            com.xhub.browser.utils.FileUtils.renameBundleInStorage(application, backup, session)
                        }
                        return@withLock
                    }
                    
                    // Step 4: Only now delete the backup - new session is fully committed
                    if (com.xhub.browser.utils.FileUtils.fileExists(application, backup)) {
                        if (!com.xhub.browser.utils.FileUtils.deleteBundleInStorageVerified(application, backup)) {
                            Timber.w("Failed to delete backup after successful save: $backup")
                            // Not critical - backup can remain
                        }
                    }
                    
                    Timber.d("Successfully saved session: $session")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Exception during session save for: $aName")
                    // Don't rethrow - we want the app to continue even if save fails
                }
            }
        }
    }



    /**
     *
     */
    private fun readSavedStateFromDisk(aBundle: Bundle?): MutableList<TabModel> {
        if (aBundle == null) {
            return mutableListOf()
        }

        // Accessing keySet() can throw BadParcelableException if bundle is corrupted
        val tabKeys = try {
            aBundle.keySet().filter { it.startsWith(TAB_KEY_PREFIX) }
        } catch (e: Exception) {
            Timber.e(e, "Bundle keySet() failed - data corrupted or incompatible")
            return mutableListOf()
        }

        val list = mutableListOf<TabModel>()
        tabKeys.forEach { bundleKey ->
            try {
                aBundle.getBundle(bundleKey)?.let { tabBundle ->
                    val tabModel = TabModelFromBundle(tabBundle)
                    // Only add tabs that have a valid URL
                    if (tabModel.url.isNotEmpty()) {
                        list.add(tabModel)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to restore tab '$bundleKey'")
            }
        }

        return list
    }


    /**
     * Returns the index of the current tab.
     *
     * @return Return the index of the current tab, or -1 if the current tab is null.
     */
    fun indexOfCurrentTab(): Int = tabList.indexOf(currentTab)

    /**
     * Returns the index of the tab.
     *
     * @return Return the index of the tab, or -1 if the tab isn't in the list.
     */
    fun indexOfTab(tab: WebPageTab): Int = tabList.indexOf(tab)

    /**
     * Returns the [WebPageTab] with the provided hash, or null if there is no tab with the hash.
     *
     * @param hashCode the hashcode.
     * @return the tab with an identical hash, or null.
     */
    fun getTabForHashCode(hashCode: Int): WebPageTab? =
        tabList.firstOrNull { webPageTab -> webPageTab.webView?.let { it.hashCode() == hashCode } == true }

    /**
     * Switch from the current tab to the one at the given [aPosition].
     *
     * @param aPosition Index of the tab we want to switch to.
     * @exception IndexOutOfBoundsException if the provided index is out of range.
     * @return The selected tab we just switched to.
     */
    fun switchToTab(aPosition: Int): WebPageTab? {
        Timber.i("switch to tab: $aPosition")

        // Be defensive here
        if (aPosition < 0 || aPosition >= tabList.size) {
            Timber.w("Tab out of range: $aPosition / ${tabList.size}")
            return null
        }

        return tabList[aPosition].also {
                currentTab = it
                // Put that tab at the top of our recent tab list
                iRecentTabs.apply{
                    remove(it)
                    add(it)
                    }
            //logger.log(TAG, "Recent indices: $recentTabsIndices")
            }
        }

    /**
     * Was needed instead of simple runnable to be able to implement run once after init function
     */
    interface InitializationListener {
        fun onInitializationComplete()
    }

    ///////////////////
    // From here we have the former browser presenter stuff
    ///////////////////
    private var currentTabFromPresenter: WebPageTab? = null
    lateinit var iWebBrowser: WebBrowser
    lateinit var closedTabs: RecentTabsModel

    /**
     * Switch to the session with the given name
     */
    fun switchToSession(aSessionName: String) {
        // Don't do anything if given session name is already the current one or if such session does not exists
        if (!isInitialized
            || sessionsManager.currentSessionName() == aSessionName
            || sessionsManager.sessions().none { s -> s.name == aSessionName }
        ) {
            return
        }

        // Save current states
        saveState()
        //
        isInitialized = false
        // Change current session
        sessionsManager.setCurrentSession(aSessionName)
        // Save it again to preserve new current session name
        sessionsManager.saveSessions()
        // Then reload our tabs
        setupTabs()

        // TODO: Using toast should really be avoided as they pileup
        // TODO: Doing this here is also wrong as we do not know yet if our session was loaded correctly
        // TODO: Give some user feedback yes but please do it properly
        //app.apply {
        //    toast(getString(R.string.session_switched,aSessionName))
        //}
    }


    /**
     * Initializes our tab manager.
     * Called when the activity is created and when switching sessions.
     */
    fun setupTabs() {
        Timber.d("setupTabs")
        val activity = iWebBrowser as Activity
        
        // Load tab initializers
        val tabInitializers = if (iWebBrowser.isIncognito()) {
            mutableListOf<TabInitializer>()
        } else {
            tryRestorePreviousTabs(activity)
        }
        
        // Create tabs and update UI
        val tabs = initializeTabs(activity, tabInitializers)
        // At this point we always have at least one tab in the tab manager
        iWebBrowser.notifyTabViewInitialized()
        iWebBrowser.updateTabNumber(size())
        // Switch to persisted current tab
        tabChanged(if (savedRecentTabsIndices.isNotEmpty()) savedRecentTabsIndices.last() else positionOf(tabs.last()),false, false)
    }

    /**
     * Called when the foreground is changing.
     *
     * [aTab] The tab we are switching to.
     * [aWasTabAdded] True if [aTab] was just created.
     * [aGoingBack] Tells in which direction we are going, this can help determine what kind of tab animation will be used.
     */
    private fun onTabChanged(aTab: WebPageTab, aWasTabAdded: Boolean, aPreviousTabClosed: Boolean, aGoingBack: Boolean) {
        Timber.d("onTabChanged")

        currentTabFromPresenter?.let {
            // Capture the preview before this tab goes into the background
            it.capturePreviewSync()
            it.isForeground = false
        }

        // Must come first so that frozen tabs are unfrozen
        // This will create frozen tab WebView, before that WebView is not available
        aTab.isForeground = true

        aTab.resumeTimers()

        iWebBrowser.setTabView(aTab.webView!!,aWasTabAdded,aPreviousTabClosed, aGoingBack)
        val index = indexOfTab(aTab)
        if (index >= 0) {
            iWebBrowser.notifyTabViewChanged(indexOfTab(aTab))
        }

        // Must come late as it needs a webview
        iWebBrowser.updateSslState(aTab.currentSslState() ?: SslState.None)

        currentTabFromPresenter = aTab

        // Freeze excess background tabs to keep RAM/CPU usage ultra low when 5+ tabs are open
        trimBackgroundTabsMemory()
    }

    /**
     * Keep memory usage low when multiple tabs are open (5+ tabs).
     * Keeps the active foreground tab + top 3 most recently used background tabs alive in RAM.
     * Older background tabs are frozen into lightweight state bundles (reclaiming 100% of their WebView RAM),
     * so opening 10–20 tabs uses virtually no extra CPU/RAM.
     * Tabs with unsaved form data or still loading are skipped by [WebPageTab.freeze].
     */
    private fun trimBackgroundTabsMemory() {
        if (!userPreferences.enableTabFreezing) return
        if (tabList.size <= 4) return
        val activeTab = currentTab ?: return

        // Keep active tab + top 3 most recent background tabs in RAM
        val keepAlive = iRecentTabs.toList().takeLast(4).toSet()

        var frozenCount = 0
        var skippedCount = 0
        tabList.forEach { tab ->
            if (tab != activeTab && tab !in keepAlive) {
                val wasFrozen = tab.isFrozen
                tab.freeze()
                if (!wasFrozen && tab.isFrozen) {
                    frozenCount++
                } else if (!tab.isFrozen) {
                    skippedCount++
                }
            }
        }
        if (frozenCount > 0 || skippedCount > 0) {
            Timber.d("Memory trim: froze $frozenCount tabs, skipped $skippedCount")
        }
    }

    /**
     * Closes all tabs but the current tab.
     */
    fun closeAllOtherTabs() {
        Timber.d("closeAllOtherTabs")
        while (last() != indexOfCurrentTab()) {
            deleteTab(last())
        }

        while (0 != indexOfCurrentTab()) {
            deleteTab(0)
        }
    }

    /**
     * SL: That's not quite working for some reason.
     * Close all tabs
     */
    fun closeAllTabs() {
        // That should never be the case though
        if (allTabs.count()==0) return

        while (allTabs.count() > 1) {
            deleteTab(last())
        }

        //deleteTab(last())
    }

    /**
     * Deletes the tab at the specified position.
     *
     * @param position the position at which to delete the tab.
     */
    fun deleteTab(position: Int) {
        Timber.d("deleteTab - position=$position")
        val tabToDelete = getTabAtPosition(position) ?: return

        Timber.v("deleteTab - Tab to delete: url=${tabToDelete.url}, isNewTab=${tabToDelete.isNewTab}")

        closedTabs.add(tabToDelete.saveState())

        val isShown = tabToDelete.isShown
        val intent = tabToDelete.iIntent
        val shouldClose = isShown && tabToDelete.isNewTab
        val beforeTab = currentTab

        Timber.v("deleteTab - isShown=$isShown, shouldClose=$shouldClose, beforeTab=${beforeTab?.url}")

        val currentDeleted = doDeleteTab(position)
        if (currentDeleted) {
            tabChanged(indexOfCurrentTab(), isShown, false)
        }

        val afterTab = currentTab
        iWebBrowser.notifyTabViewRemoved(position)

        Timber.v("deleteTab - currentDeleted=$currentDeleted, afterTab=${afterTab?.url}")

        if (afterTab == null) {
            Timber.d("deleteTab - No tabs left, closing browser")
            iWebBrowser.closeBrowser()
            return
        } else if (afterTab !== beforeTab) {
            iWebBrowser.notifyTabViewChanged(indexOfCurrentTab())
        }

        if (shouldClose) {
            Timber.d("deleteTab - Closing activity due to shouldClose=true")
            // Defensive: We must have an intent if going through here but using let saves us from using !!
            intent?.let { iWebBrowser.closeActivity(it) }
        }

        iWebBrowser.updateTabNumber(size())

        Timber.v("deleteTab - END")
    }


    /**
     * Recover last closed tab.
     */
    fun recoverClosedTab(show: Boolean = true) {
        closedTabs.popLast()?.let { bundle ->
            TabModelFromBundle(bundle).let {
                if (it.url.isSpecialUrl()) {
                    // That's a special URL
                    newTab(tabInitializerForSpecialUrl(it.url), show)
                } else {
                    // That's an actual WebView bundle
                    newTab(FreezableBundleInitializer(it), show)
                }
            }
            iWebBrowser.showSnackbar(R.string.reopening_recent_tab)
        }
    }

    /**
     * Recover all closed tabs
     */
    fun recoverAllClosedTabs() {
        while (closedTabs.bundleStack.count()>0) {
            recoverClosedTab(false)
        }
    }

    /**
     * Loads a URL in the current tab.
     *
     * @param url the URL to load, must not be null.
     */
    fun loadUrlInCurrentView(url: String) {
        currentTab?.loadUrl(url)
    }

    /**
     * Notifies the presenter that we wish to switch to a different tab at the specified position.
     * If the position is not in the model, this method will do nothing.
     *
     * [position] the position of the tab to switch to.
     * [aPreviousTabClosed] Tells if the previous tab was closed, this can help determine what kind of tab animation will be used.
     * [aGoingBack] Tells in which direction we are going, this can help determine what kind of tab animation will be used.
     */
    fun tabChanged(position: Int, aPreviousTabClosed: Boolean, aGoingBack: Boolean) {
        if (position < 0 || position >= size()) {
            Timber.d("tabChanged invalid position: $position")
            return
        }

        Timber.d("tabChanged: $position")
        switchToTab(position)?.let { onTabChanged(it,false, aPreviousTabClosed, aGoingBack) }
    }




    /**
     * Open a new tab with the specified URL. You can choose to show the tab or load it in the
     * background.
     *
     * This method enforces the max tab count entitlement based on the user's sponsorship level.
     * If the maximum number of tabs has been reached, the user will be notified via
     * [IWebBrowser.onMaxTabReached] and no new tab will be created.
     *
     * @param tabInitializer the tab initializer to run after the tab as been created.
     * @param show whether or not to switch to this tab after opening it.
     * @return The newly created tab instance, or null if the max tab count has been reached
     *         and tab creation was rejected.
     */
    fun newTab(tabInitializer: TabInitializer, show: Boolean): WebPageTab? {
        // Enforce max tab count limit according to sponsorship level
        if (size() >= Entitlement.maxTabCount(userPreferences.sponsorship)) {
            Timber.w("Max tab count reached: ${size()} >= ${Entitlement.maxTabCount(userPreferences.sponsorship)}")
            iWebBrowser.onMaxTabReached()
            // Return null to indicate tab creation was rejected
            return null
        }

        Timber.d("New tab, show: $show")

        val newTab = newTab(iWebBrowser as Activity, tabInitializer,userPreferences.newTabPosition)
        if (size() == 1) {
            newTab.resumeTimers()
        }

        iWebBrowser.notifyTabViewAdded()
        iWebBrowser.updateTabNumber(size())

        if (show) {
            switchToTab(indexOfTab(newTab))?.let { onTabChanged(it,true, false, false)}
        }
        else {
            // We still need to add it to our recent tabs
            // Adding at the beginning of a Set is doggy though
            val recentTabs = iRecentTabs.toSet()
            iRecentTabs.clear()
            iRecentTabs.add(newTab)
            iRecentTabs.addAll(recentTabs)
        }

        return newTab
    }

    /**
     * Register a preloaded tab. Adds the tab to the tabs list and optionally switches to it.
     */
    fun registerPreloadedTab(tab: WebPageTab, show: Boolean) {
        tabList.add(tab)
        tabMap[tab.id] = tab

        iWebBrowser.notifyTabViewAdded()
        iWebBrowser.updateTabNumber(size())

        if (show) {
            switchToTab(indexOfTab(tab))?.let { onTabChanged(it, true, false, false) }
        } else {
            val recentTabs = iRecentTabs.toSet()
            iRecentTabs.clear()
            iRecentTabs.add(tab)
            iRecentTabs.addAll(recentTabs)
        }
    }

    companion object {

        const val TAB_KEY_PREFIX = "TAB_"
        // Preserve this file name for compatibility
        private const val FILENAME_SESSION_DEFAULT = "SAVED_TABS.parcel"
        // Temp and backup prefixes for session saving
        const val FILENAME_TEMP_PREFIX = "TEMP_SESSION_"
        const val FILENAME_BACKUP_PREFIX = "BACKUP_SESSION_"

        private const val RECENT_TAB_INDICES = "RECENT_TAB_INDICES"

    }

}
