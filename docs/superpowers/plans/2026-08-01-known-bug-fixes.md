# Known Browser Bug Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four documented user-visible bugs: stray session tab-count writes during session switch, special-URL tabs not frozen at restore, `xhub://` URLs not treated as special (latent History-restored-as-Home), and the bottom-sheet dialog below the session pop-up breaking status-bar icon color in light theme.

**Architecture:** All four bugs live in the View-based tab/session architecture: `TabsManager` (session/tab lifecycle + tab-count notifications), `WebPageTab`/`TabInitializer` (freeze/restore), `UrlUtils` (special URL classification), and `WebBrowserActivity`/`ActivityExtensions` (dialog windowing). Each fix is TDD: a Robolectric regression test is written first, verified RED, then the minimal fix, verified GREEN.

**Tech Stack:** Kotlin 2.2.10, Android Views, Hilt, JUnit 4 + Robolectric + Mockito (`mockStatic` for Hilt `EntryPointAccessors`, same pattern as `WebPageTabTest`).

## Global Constraints

- All unit tests use Robolectric: `@RunWith(RobolectricTestRunner::class)` + `@Config(application = TestApplication::class, sdk = [SDK_VERSION])` (`SDK_VERSION` comes from `app/src/test/java/com/xhub/browser/Constants.kt`).
- Windows shell: run Gradle via `cmd //c "gradlew.bat <task>"`. To run one test class append `--tests <fully.qualified.ClassName>` (single arg, no inner quotes).
- TDD: write the failing test first, run it to confirm it FAILS (RED), then implement, then confirm GREEN. Never implement before the RED is verified.
- Commit after each task with conventional commits; keep commits focused (one fix per commit).
- Do NOT re-add `DownloadPageFactory.FILENAME` to `isSpecialUrl()` — it was deliberately removed in commit f2da7a8 (2026-07-25).
- Do NOT change production code outside the exact lines listed per task. No unrelated refactors (YAGNI).
- Final gate before any success claim: full unit suite green + `assembleXhubFullDownloadDebug` succeeds (verification-before-completion).
- Baseline known-good: 233 unit tests passing, `assembleXhubFullDownloadDebug` builds, before any of this plan's changes.

---

### Task 1: Suppress stray session tab-count writes during session switch

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/browser/TabsManager.kt:91-104` (init listener block) and `TabsManager.kt:542-549` (`shutdown()`)
- Test: `app/src/test/java/com/xhub/browser/browser/TabsManagerTabCountTest.kt` (new)

**Interfaces:**
- Consumes: `TabsManager` constructor (unchanged), `TabsManager.iWebBrowser: WebBrowser` (public lateinit var, assigned at TabsManager.kt:446 by `WebBrowserActivity`), `TabsManager.newTab(Activity, TabInitializer, NewTabPosition)` (public, TabsManager.kt:580), `TabsManager.shutdown()` (public, TabsManager.kt:542), `SessionsManager.sessions()`, `SessionsManager.currentSessionName()`, `Session(name, tabCount, isCurrent)` (`acr.browser.lightning.browser.sessions.Session`).
- Produces: `private var tabCountUpdatesSuspended: Boolean` (new field), `private fun updateCurrentSessionTabCount(count: Int)` (new helper). `shutdown()` now suppresses tab-count session writes while destroying tabs.

**Context / root cause:** TabsManager.kt:93-103 registers an init listener that writes the tab count into the session whose name matches `sessionsManager.currentSessionName()`. During a session switch (`switchToSession`, TabsManager.kt:944) the current session name is changed to the NEW session BEFORE `setupTabs()` → `initializeTabs()` → `shutdown()` (TabsManager.kt:263-266) destroys the OLD session's tabs. The last delete notification during shutdown (`doDeleteTab` with `aShutdown=true`, fires when `size()==0`, TabsManager.kt:658) therefore writes `tabCount = 0` into the brand-new session. This was acknowledged by the author in the TODOs at TabsManager.kt:95-98. Note: newTab() also fires `tabNumberListeners` (TabsManager.kt:605) and must still update the current session — only *shutdown-driven* deletions are suppressed.

- [ ] **Step 1: Write the failing regression test**

Create `app/src/test/java/com/xhub/browser/browser/TabsManagerTabCountTest.kt`:

```kotlin
package com.xhub.browser.browser

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.AttributeSet
import acr.browser.lightning.browser.sessions.Session
import com.xhub.browser.App
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.enums.LayerType
import com.xhub.browser.search.SearchEngineProvider
import com.xhub.browser.settings.NewTabPosition
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.view.BookmarkPageInitializer
import com.xhub.browser.view.FreezableBundleInitializer
import com.xhub.browser.view.HistoryPageInitializer
import com.xhub.browser.view.HomePageInitializer
import com.xhub.browser.view.IncognitoPageInitializer
import com.xhub.browser.view.NoOpInitializer
import com.xhub.browser.view.RenderingMode
import dagger.hilt.android.EntryPointAccessors
import io.reactivex.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class TabsManagerTabCountTest {

    private lateinit var mockedStatic: MockedStatic<EntryPointAccessors>
    private lateinit var sessions: ArrayList<Session>
    private lateinit var tabsManager: TabsManager
    private lateinit var mockActivity: TestActivity
    private var currentSessionName = "session-a"

    @Before
    fun setUp() {
        val realActivity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Point the global `app` at a mock so preference/domain delegates work in tests
        val mockApp = mock(App::class.java)
        `when`(mockApp.resources).thenReturn(RuntimeEnvironment.getApplication().resources)
        `when`(mockApp.applicationContext).thenReturn(RuntimeEnvironment.getApplication())
        `when`(mockApp.applicationInfo).thenReturn(RuntimeEnvironment.getApplication().applicationInfo)
        `when`(mockApp.packageName).thenReturn(RuntimeEnvironment.getApplication().packageName)
        `when`(mockApp.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            RuntimeEnvironment.getApplication().getSharedPreferences(
                invocation.getArgument(0),
                invocation.getArgument(1)
            )
        }
        `when`(mockApp.getString(anyInt())).thenAnswer { invocation ->
            RuntimeEnvironment.getApplication().getString(invocation.getArgument(0))
        }
        val appKtClass = Class.forName("com.xhub.browser.AppKt")
        val field = appKtClass.getDeclaredField("app")
        field.isAccessible = true
        field.set(null, mockApp)

        // UserPreferences + HiltEntryPoint mocks (same pattern as WebPageTabTest)
        val userPreferences = mock(UserPreferences::class.java)
        val mockUserPrefs = mock(SharedPreferences::class.java)
        `when`(userPreferences.preferences).thenReturn(mockUserPrefs)
        `when`(userPreferences.renderingMode).thenReturn(RenderingMode.NORMAL)
        `when`(userPreferences.layerType).thenReturn(LayerType.Hardware)
        `when`(userPreferences.textEncoding).thenReturn("UTF-8")
        `when`(userPreferences.browserTextSize).thenReturn(0)
        `when`(userPreferences.loadImages).thenReturn(true)
        `when`(userPreferences.popupsEnabled).thenReturn(false)
        `when`(userPreferences.overviewModeEnabled).thenReturn(false)
        `when`(userPreferences.textReflowEnabled).thenReturn(false)
        `when`(userPreferences.scrollbarSize).thenReturn(10f)
        `when`(userPreferences.scrollbarFading).thenReturn(true)
        `when`(userPreferences.scrollbarDelayBeforeFade).thenReturn(300f)
        `when`(userPreferences.scrollbarFadeDuration).thenReturn(250f)
        `when`(userPreferences.videoDetectionEnabled).thenReturn(false)
        `when`(userPreferences.userAgentChoice).thenReturn("agent_default")
        `when`(userPreferences.userAgentString).thenReturn("")
        `when`(userPreferences.newTabPosition).thenReturn(NewTabPosition.END_OF_TAB_LIST)

        val entryPoint = mock(HiltEntryPoint::class.java)
        `when`(entryPoint.userPreferences).thenReturn(userPreferences)
        `when`(entryPoint.databaseScheduler()).thenReturn(Schedulers.trampoline())
        `when`(entryPoint.mainScheduler()).thenReturn(Schedulers.trampoline())

        mockedStatic = mockStatic(EntryPointAccessors::class.java)
        mockedStatic.`when`<Any> {
            EntryPointAccessors.fromApplication(
                RuntimeEnvironment.getApplication(),
                HiltEntryPoint::class.java
            )
        }.thenReturn(entryPoint)
        mockedStatic.`when`<Any> {
            EntryPointAccessors.fromApplication(
                mockApp,
                HiltEntryPoint::class.java
            )
        }.thenReturn(entryPoint)

        // TestActivity is Activity + WebBrowser so the cast in WebPageTab.init works
        mockActivity = mock(TestActivity::class.java)
        doAnswer { (it.getArgument<Runnable>(0)).run(); Unit }
            .`when`(mockActivity).runOnUiThread(any(Runnable::class.java))
        `when`(mockActivity.applicationInfo).thenReturn(realActivity.applicationInfo)
        `when`(mockActivity.applicationContext).thenReturn(realActivity.applicationContext)
        `when`(mockActivity.layoutInflater).thenReturn(realActivity.layoutInflater)
        `when`(mockActivity.resources).thenReturn(realActivity.resources)
        `when`(mockActivity.packageName).thenReturn(realActivity.packageName)
        `when`(mockActivity.theme).thenReturn(realActivity.theme)
        `when`(mockActivity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
        `when`(mockActivity.getString(anyInt())).thenAnswer { invocation ->
            realActivity.getString(invocation.getArgument(0))
        }
        `when`(mockActivity.obtainStyledAttributes(anyInt(), any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument(0),
                invocation.getArgument<IntArray>(1)
            )
        }
        `when`(mockActivity.obtainStyledAttributes(any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(invocation.getArgument<IntArray>(0))
        }
        `when`(mockActivity.obtainStyledAttributes(isNull(AttributeSet::class.java), any(IntArray::class.java), anyInt(), anyInt())).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<AttributeSet?>(0),
                invocation.getArgument<IntArray>(1),
                invocation.getArgument(2),
                invocation.getArgument(3)
            )
        }
        `when`(mockActivity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
        }
        `when`(mockActivity.application).thenReturn(realActivity.application)

        // Two sessions; currentSessionName flips to simulate a mid-switch state
        sessions = arrayListOf(Session(name = "session-a"), Session(name = "session-b"))
        val sessionsManager = mock(SessionsManager::class.java)
        `when`(sessionsManager.sessions()).thenReturn(sessions)
        `when`(sessionsManager.currentSessionName()).thenAnswer { currentSessionName }

        tabsManager = TabsManager(
            application = mockApp,
            searchEngineProvider = mock(SearchEngineProvider::class.java),
            homePageInitializer = mock(HomePageInitializer::class.java),
            incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
            bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
            historyPageInitializer = mock(HistoryPageInitializer::class.java),
            noOpPageInitializer = mock(NoOpInitializer::class.java),
            userPreferences = userPreferences,
            sessionsManager = sessionsManager
        )
        tabsManager.iWebBrowser = mockActivity
    }

    @After
    fun tearDown() {
        mockedStatic.close()
    }

    private fun frozenTabModel(): TabModel = TabModel(
        url = "https://example.com",
        title = "Example",
        desktopMode = false,
        darkMode = false,
        favicon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        searchQuery = "",
        searchActive = false,
        webView = null,
        tabId = 1
    )

    @Test
    fun `new tab updates the current session tab count`() {
        currentSessionName = "session-b"

        tabsManager.newTab(
            mockActivity,
            FreezableBundleInitializer(frozenTabModel()),
            NewTabPosition.END_OF_TAB_LIST
        )

        assertEquals(1, sessions[1].tabCount)
    }

    @Test
    fun `shutdown does not write stray tab count into the new session`() {
        currentSessionName = "session-b"
        tabsManager.newTab(
            mockActivity,
            FreezableBundleInitializer(frozenTabModel()),
            NewTabPosition.END_OF_TAB_LIST
        )
        assertEquals(1, sessions[1].tabCount)

        tabsManager.shutdown()

        // Regression: during a session switch the current session name is changed BEFORE
        // shutdown() destroys the old session's tabs, so the last delete notification used
        // to write tabCount = 0 into the brand-new session.
        assertEquals(1, sessions[1].tabCount)
    }

    abstract class TestActivity : Activity(), WebBrowser
}
```

- [ ] **Step 2: Run the test to verify it fails (RED)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.browser.TabsManagerTabCountTest"`

Expected: `shutdown does not write stray tab count into the new session` FAILS — `sessions[1].tabCount` is 0 instead of 1 (the last delete notification during `shutdown()` wrote 0 into the new session). The `new tab updates...` test PASSES. If the whole test file fails to compile, fix imports (the entryPoint mock above is intentionally minimal; add stubs from `WebPageTabTest.setup()` at `app/src/test/java/com/xhub/browser/view/WebPageTabTest.kt:57-145` if the WebPageTab constructor needs more).

- [ ] **Step 3: Implement the minimal fix**

In `app/src/main/java/com/xhub/browser/browser/TabsManager.kt`, replace the init listener block (lines 91-104, including the three TODO comments) with:

```kotlin
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
```

Then replace `shutdown()` (lines 542-549) with:

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes (GREEN)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.browser.TabsManagerTabCountTest"`

Expected: both tests PASS.

- [ ] **Step 5: Run the full unit suite (no regressions)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest"`

Expected: all 235 tests pass, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/xhub/browser/browser/TabsManager.kt app/src/test/java/com/xhub/browser/browser/TabsManagerTabCountTest.kt
git commit -m "fix(tabs): suppress stray session tab count during session switch shutdown"
```

---

### Task 2: Restore ALL tabs frozen, including special pages

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/browser/TabsManager.kt` — `loadSession()` branch at 365-369, `recoverClosedTab()` at 1149-1162, delete `tabInitializerForSpecialUrl()` at 477-488, remove the 5 now-unused imports at lines 16-20
- Modify: `app/src/main/java/com/xhub/browser/view/WebPageTab.kt:1188-1192` — replace the stale TODO comment
- Test: `app/src/test/java/com/xhub/browser/browser/TabsManagerSessionRestoreTest.kt` (new)

**Interfaces:**
- Consumes: `TabModel(url, title, desktopMode, darkMode, favicon, searchQuery, searchActive, webView, tabId)` (`com.xhub.browser.browser.TabModel`, toBundle() at TabModel.kt:51), `FileUtils.writeBundleToStorage(Application, Bundle, String)` (Java, FileUtils.java:46), `TabsManager.loadSession(filename: String): MutableList<TabInitializer>` — becomes `internal` in this task.
- Produces: `loadSession()` now returns ONLY `FreezableBundleInitializer` entries (never live page initializers). `tabInitializerForSpecialUrl` is deleted. `recoverClosedTab()` restores closed tabs via `FreezableBundleInitializer` unconditionally.

**Context / root cause:** `loadSession` (TabsManager.kt:365-369) and `recoverClosedTab` (TabsManager.kt:1151-1159) route special-URL tabs to live page initializers (`tabInitializerForSpecialUrl`), so they are created with a real WebView immediately at restore — while `freeze()` (WebPageTab.kt:1831-1847) freezes every background tab at runtime. Restore is thus inconsistent with the freeze design: restored special tabs never start frozen, defeating the feature's memory goal. The author flagged this at WebPageTab.kt:1190 ("TODO: it looks like our special URLs don't get frozen for some reason"). Safety: `BundleInitializer.initialize` (TabInitializer.kt:213-252) already falls back to `loadUrlFallback` (loads `url()`, which special pages intercept and regenerate) when the bundle is null, unreadable, or restore fails — so `RecoveredTabModel` (webView = null) and stale bundles are handled. For a history tab the initializer's `url()` returns `xhub://history` → WebPageClient regenerates the history page. Also removes the latent "History restored as Home" hazard: `tabInitializerForSpecialUrl`'s `when` (TabsManager.kt:480-488) only matches FILE-based URL predicates and its `else` branch is `homePageInitializer` — any `xhub://` URL reaching it becomes a Home tab.

- [ ] **Step 1: Write the failing regression test**

Create `app/src/test/java/com/xhub/browser/browser/TabsManagerSessionRestoreTest.kt` (complete file — the mock setup is the same pattern as `TabsManagerTabCountTest`):

```kotlin
package com.xhub.browser.browser

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.util.AttributeSet
import acr.browser.lightning.browser.sessions.Session
import com.xhub.browser.App
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.enums.LayerType
import com.xhub.browser.search.SearchEngineProvider
import com.xhub.browser.settings.NewTabPosition
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.utils.FileUtils
import com.xhub.browser.view.BookmarkPageInitializer
import com.xhub.browser.view.FreezableBundleInitializer
import com.xhub.browser.view.HistoryPageInitializer
import com.xhub.browser.view.HomePageInitializer
import com.xhub.browser.view.IncognitoPageInitializer
import com.xhub.browser.view.NoOpInitializer
import com.xhub.browser.view.RenderingMode
import dagger.hilt.android.EntryPointAccessors
import io.reactivex.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class TabsManagerSessionRestoreTest {

    private lateinit var mockedStatic: MockedStatic<EntryPointAccessors>
    private lateinit var sessions: ArrayList<Session>
    private lateinit var tabsManager: TabsManager
    private lateinit var mockActivity: TestActivity
    private var currentSessionName = "session-a"

    @Before
    fun setUp() {
        val realActivity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Point the global `app` at a mock so preference/domain delegates work in tests
        val mockApp = mock(App::class.java)
        `when`(mockApp.resources).thenReturn(RuntimeEnvironment.getApplication().resources)
        `when`(mockApp.applicationContext).thenReturn(RuntimeEnvironment.getApplication())
        `when`(mockApp.applicationInfo).thenReturn(RuntimeEnvironment.getApplication().applicationInfo)
        `when`(mockApp.packageName).thenReturn(RuntimeEnvironment.getApplication().packageName)
        `when`(mockApp.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            RuntimeEnvironment.getApplication().getSharedPreferences(
                invocation.getArgument(0),
                invocation.getArgument(1)
            )
        }
        `when`(mockApp.getString(anyInt())).thenAnswer { invocation ->
            RuntimeEnvironment.getApplication().getString(invocation.getArgument(0))
        }
        val appKtClass = Class.forName("com.xhub.browser.AppKt")
        val field = appKtClass.getDeclaredField("app")
        field.isAccessible = true
        field.set(null, mockApp)

        // UserPreferences + HiltEntryPoint mocks (same pattern as WebPageTabTest)
        val userPreferences = mock(UserPreferences::class.java)
        val mockUserPrefs = mock(SharedPreferences::class.java)
        `when`(userPreferences.preferences).thenReturn(mockUserPrefs)
        `when`(userPreferences.renderingMode).thenReturn(RenderingMode.NORMAL)
        `when`(userPreferences.layerType).thenReturn(LayerType.Hardware)
        `when`(userPreferences.textEncoding).thenReturn("UTF-8")
        `when`(userPreferences.browserTextSize).thenReturn(0)
        `when`(userPreferences.loadImages).thenReturn(true)
        `when`(userPreferences.popupsEnabled).thenReturn(false)
        `when`(userPreferences.overviewModeEnabled).thenReturn(false)
        `when`(userPreferences.textReflowEnabled).thenReturn(false)
        `when`(userPreferences.scrollbarSize).thenReturn(10f)
        `when`(userPreferences.scrollbarFading).thenReturn(true)
        `when`(userPreferences.scrollbarDelayBeforeFade).thenReturn(300f)
        `when`(userPreferences.scrollbarFadeDuration).thenReturn(250f)
        `when`(userPreferences.videoDetectionEnabled).thenReturn(false)
        `when`(userPreferences.userAgentChoice).thenReturn("agent_default")
        `when`(userPreferences.userAgentString).thenReturn("")
        `when`(userPreferences.newTabPosition).thenReturn(NewTabPosition.END_OF_TAB_LIST)

        val entryPoint = mock(HiltEntryPoint::class.java)
        `when`(entryPoint.userPreferences).thenReturn(userPreferences)
        `when`(entryPoint.databaseScheduler()).thenReturn(Schedulers.trampoline())
        `when`(entryPoint.mainScheduler()).thenReturn(Schedulers.trampoline())

        mockedStatic = mockStatic(EntryPointAccessors::class.java)
        mockedStatic.`when`<Any> {
            EntryPointAccessors.fromApplication(
                RuntimeEnvironment.getApplication(),
                HiltEntryPoint::class.java
            )
        }.thenReturn(entryPoint)
        mockedStatic.`when`<Any> {
            EntryPointAccessors.fromApplication(
                mockApp,
                HiltEntryPoint::class.java
            )
        }.thenReturn(entryPoint)

        // TestActivity is Activity + WebBrowser so the cast in WebPageTab.init works
        mockActivity = mock(TestActivity::class.java)
        doAnswer { (it.getArgument<Runnable>(0)).run(); Unit }
            .`when`(mockActivity).runOnUiThread(any(Runnable::class.java))
        `when`(mockActivity.applicationInfo).thenReturn(realActivity.applicationInfo)
        `when`(mockActivity.applicationContext).thenReturn(realActivity.applicationContext)
        `when`(mockActivity.layoutInflater).thenReturn(realActivity.layoutInflater)
        `when`(mockActivity.resources).thenReturn(realActivity.resources)
        `when`(mockActivity.packageName).thenReturn(realActivity.packageName)
        `when`(mockActivity.theme).thenReturn(realActivity.theme)
        `when`(mockActivity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
        `when`(mockActivity.getString(anyInt())).thenAnswer { invocation ->
            realActivity.getString(invocation.getArgument(0))
        }
        `when`(mockActivity.obtainStyledAttributes(anyInt(), any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument(0),
                invocation.getArgument<IntArray>(1)
            )
        }
        `when`(mockActivity.obtainStyledAttributes(any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(invocation.getArgument<IntArray>(0))
        }
        `when`(mockActivity.obtainStyledAttributes(isNull(AttributeSet::class.java), any(IntArray::class.java), anyInt(), anyInt())).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<AttributeSet?>(0),
                invocation.getArgument<IntArray>(1),
                invocation.getArgument(2),
                invocation.getArgument(3)
            )
        }
        `when`(mockActivity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
        }
        `when`(mockActivity.application).thenReturn(realActivity.application)

        sessions = arrayListOf(Session(name = "session-a"), Session(name = "session-b"))
        val sessionsManager = mock(SessionsManager::class.java)
        `when`(sessionsManager.sessions()).thenReturn(sessions)
        `when`(sessionsManager.currentSessionName()).thenAnswer { currentSessionName }

        tabsManager = TabsManager(
            application = mockApp,
            searchEngineProvider = mock(SearchEngineProvider::class.java),
            homePageInitializer = mock(HomePageInitializer::class.java),
            incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
            bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
            historyPageInitializer = mock(HistoryPageInitializer::class.java),
            noOpPageInitializer = mock(NoOpInitializer::class.java),
            userPreferences = userPreferences,
            sessionsManager = sessionsManager
        )
        tabsManager.iWebBrowser = mockActivity
    }

    @After
    fun tearDown() {
        mockedStatic.close()
    }

    private fun tabModel(url: String, title: String, tabId: Int): TabModel = TabModel(
        url = url,
        title = title,
        desktopMode = false,
        darkMode = false,
        favicon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        searchQuery = "",
        searchActive = false,
        webView = null,
        tabId = tabId
    )

    @Test
    fun `session restore returns frozen initializers for special URLs`() {
        val app = RuntimeEnvironment.getApplication()
        val filesDir = app.filesDir.path

        // A live history tab saves as xhub://history (WebPageTab.url substitutes the special
        // target URL for special WebView URLs); binary recovery can produce the raw file URL.
        val sessionBundle = Bundle().apply {
            putBundle("TAB_0", tabModel("xhub://history", "History", 1).toBundle())
            putBundle("TAB_1", tabModel("file://$filesDir/history.html", "History", 2).toBundle())
            putBundle("TAB_2", tabModel("https://example.com", "Example", 3).toBundle())
        }
        FileUtils.writeBundleToStorage(app, sessionBundle, "SESSION_TEST")

        val initializers = tabsManager.loadSession("SESSION_TEST")

        assertEquals(3, initializers.size)
        assertTrue("special xhub:// tab should restore frozen", initializers[0] is FreezableBundleInitializer)
        assertTrue("special file:// tab should restore frozen", initializers[1] is FreezableBundleInitializer)
        assertTrue("regular tab should restore frozen", initializers[2] is FreezableBundleInitializer)
    }

    abstract class TestActivity : Activity(), WebBrowser
}
```

- [ ] **Step 2: Run the test to verify it fails (RED)**

First make `loadSession` accessible: in `app/src/main/java/com/xhub/browser/browser/TabsManager.kt` change line 301 from `private fun loadSession` to `internal fun loadSession` (this is part of the fix and is harmless — it is called from `WebBrowserActivity` via `tabsManager` and stays visible to the test).

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.browser.TabsManagerSessionRestoreTest"`

Expected: FAILS on the `initializers[1]` assertion — `file://.../history.html` is special today, so `loadSession` returns `historyPageInitializer` (a live initializer) for it. Assertions for `[0]` and `[2]` pass (they are already `FreezableBundleInitializer` today).

- [ ] **Step 3: Implement the minimal fix**

In `app/src/main/java/com/xhub/browser/browser/TabsManager.kt`:

(a) Replace the `tabModels.forEach` block in `loadSession` (lines 365-369):

```kotlin
        tabModels.forEach {
            list.add(FreezableBundleInitializer(it))
        }
```

(b) Replace `recoverClosedTab` (lines 1149-1162):

```kotlin
    fun recoverClosedTab(show: Boolean = true) {
        closedTabs.popLast()?.let { bundle ->
            TabModelFromBundle(bundle).let {
                // Restore closed tabs the same way as session tabs: frozen until activated.
                // Special pages (home, history, bookmarks, ...) are regenerated by their
                // factories via BundleInitializer's URL fallback when the bundle is empty
                // or stale.
                newTab(FreezableBundleInitializer(it), show)
            }
            iWebBrowser.showSnackbar(R.string.reopening_recent_tab)
        }
    }
```

(c) Delete `tabInitializerForSpecialUrl` (lines 477-488, including its doc comment `Provide a tab initializer for the given special URL`).

(d) Remove the now-unused imports at lines 16-20:

```kotlin
import com.xhub.browser.utils.isBookmarkUrl
import com.xhub.browser.utils.isHistoryUrl
import com.xhub.browser.utils.isIncognitoPageUrl
import com.xhub.browser.utils.isSpecialUrl
import com.xhub.browser.utils.isStartPageUrl
```

(Verify with the compiler — `loadSession`/`recoverClosedTab` were the only remaining users. Do NOT remove `acr.browser.lightning.browser.sessions.Session` at line 5.)

(e) In `app/src/main/java/com/xhub/browser/view/WebPageTab.kt` (lines 1188-1192), replace the stale TODO:

```kotlin
        if (tabInitializer !is FreezableBundleInitializer) {
            // Create our WebView now. All tabs restored from sessions or closed-tab recovery
            // are FreezableBundleInitializer (see TabsManager.loadSession), so this branch
            // only runs for freshly opened tabs.
            createWebView()
            initializeContent(tabInitializer)
```

- [ ] **Step 4: Run the test to verify it passes (GREEN)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.browser.TabsManagerSessionRestoreTest"`

Expected: PASS.

- [ ] **Step 5: Run the full unit suite (no regressions)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest"`

Expected: all tests pass, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/xhub/browser/browser/TabsManager.kt app/src/main/java/com/xhub/browser/view/WebPageTab.kt app/src/test/java/com/xhub/browser/browser/TabsManagerSessionRestoreTest.kt
git commit -m "fix(tabs): restore all tabs frozen, including special pages"
```

---

### Task 3: Treat `xhub://` URLs as special URLs

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/utils/UrlUtils.kt:135-143` (`isSpecialUrl()`)
- Test: `app/src/test/java/com/xhub/browser/utils/UrlSpecialUrlTest.kt` (new)

**Interfaces:**
- Consumes: `isSpecialUrl(): Boolean` extension on `String?` (UrlUtils.kt:135), constants `FILE = "file://"` (Constants.kt:18) and `Schemes.Fulguris = "xhub"` (Constants.kt:22), the global `app` (for `app.filesDir`), `HistoryPageFactory.FILENAME = "history.html"` (`com.xhub.browser.html.history.HistoryPageFactory`), `HomePageFactory.FILENAME = "homepage.html"` (`com.xhub.browser.html.homepage.HomePageFactory`).
- Produces: `isSpecialUrl()` returns true for `xhub://home|start|incognito|bookmarks|history|noop` in addition to the existing file-based special pages. No new functions.

**Context / root cause:** The scheme check in `isSpecialUrl()` has been commented out since the file's creation (commit 9716a53, 2026-06-13) with the author's TODO "That's somehow causing History page to be restored as Home page". The `xhub://`-based URIs (`Uris.FulgurisHome` etc., Constants.kt:37-42) are therefore NOT classified as special, so the 19 remaining call sites (listed below) misclassify them: e.g. WebBrowserActivity.kt:2884 adds special pages to browser history; WebPageClient.kt:516/961, IntentUtils.kt:263, MenuPopupWindow.kt:356, AbpBlockerManager.kt:199, WebPageTab.kt:1147/2101 all fail to recognize them as internal pages. The History-restored-as-Home hazard is already eliminated by Task 2 (the `tabInitializerForSpecialUrl` fallthrough is deleted, and `loadSession`/`recoverClosedTab` no longer consult `isSpecialUrl`), so restoring the check is now safe.

Call-site audit (all become MORE correct; none break):
- `TabsManager.kt:367, 1152` — no longer exist after Task 2 (restore paths don't use it)
- `WebPageTab.kt:1147` — url getter: special WebView URLs already caught by the FILE clause; xhub:// never appears as a real WebView URL (intercepted), no change
- `WebPageTab.kt:2101` — share menu title: xhub:// pages now shown as special (correct)
- `MenuPopupWindow.kt:356` — already ORs `isAppScheme()`, no behavioral change
- `IntentUtils.kt:196` — requires `URLUtil.isFileUrl(url)`, xhub:// not a file URL, no change
- `IntentUtils.kt:263` — share guards: xhub:// now excluded from shareable URLs (correct)
- `BookmarksDrawerView.kt:137` — commented-out code only
- `WebPageClient.kt:516, 961` — history/state guards: xhub:// pages now treated as special (correct)
- `AbpBlockerManager.kt:199` — already ORs `isAppScheme()`, no behavioral change
- `WebBrowserActivity.kt:2884, 3644, 3727, 3748, 4691, 5390` — history and UI guards: xhub:// URLs no longer recorded/mislabeled (correct); line 5335 already ORs the individual `isXxxUri()` checks, becomes redundant but harmless

- [ ] **Step 1: Write the failing regression test**

Create `app/src/test/java/com/xhub/browser/utils/UrlSpecialUrlTest.kt`:

```kotlin
package com.xhub.browser.utils

import com.xhub.browser.App
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class UrlSpecialUrlTest {

    @Before
    fun setUp() {
        // isSpecialUrl() reads the global `app` (App.filesDir), so point it at the test app
        val mockApp = mock(App::class.java)
        `when`(mockApp.filesDir).thenReturn(File(RuntimeEnvironment.getApplication().filesDir.path))
        val appKtClass = Class.forName("com.xhub.browser.AppKt")
        val field = appKtClass.getDeclaredField("app")
        field.isAccessible = true
        field.set(null, mockApp)
    }

    @Test
    fun `xhub scheme URLs are special URLs`() {
        listOf(
            "xhub://home",
            "xhub://start",
            "xhub://incognito",
            "xhub://bookmarks",
            "xhub://history",
            "xhub://noop"
        ).forEach { url ->
            assertTrue("$url should be special", url.isSpecialUrl())
        }
    }

    @Test
    fun `ordinary web URLs are not special`() {
        listOf(
            "https://example.com",
            "http://example.com",
            "about:blank",
            "data:text/html,hi",
            "xhub-not-a-scheme"
        ).forEach { url ->
            assertFalse("$url should not be special", url.isSpecialUrl())
        }
    }

    @Test
    fun `file based special pages remain special`() {
        val filesDir = RuntimeEnvironment.getApplication().filesDir.path
        assertTrue("file://$filesDir/history.html".isSpecialUrl())
        assertTrue("file://$filesDir/homepage.html".isSpecialUrl())
    }

    @Test
    fun `null is not special`() {
        val nullUrl: String? = null
        assertFalse(nullUrl.isSpecialUrl())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (RED)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.utils.UrlSpecialUrlTest"`

Expected: `xhub scheme URLs are special URLs` FAILS (all six `xhub://` URLs return false today). The other three tests PASS.

- [ ] **Step 3: Implement the minimal fix**

In `app/src/main/java/com/xhub/browser/utils/UrlUtils.kt` (lines 135-143), replace the body of `isSpecialUrl()`:

```kotlin
fun String?.isSpecialUrl(): Boolean =
    this != null
            && ((this.startsWith(FILE + app.filesDir)
            && (this.endsWith(BookmarkPageFactory.FILENAME)
            || this.endsWith(HistoryPageFactory.FILENAME)
            || this.endsWith(HomePageFactory.FILENAME)
            || this.endsWith(IncognitoPageFactory.FILENAME)))
            || this.startsWith(Schemes.Fulguris + "://"))
```

This removes the `// TODO: That's somehow causing History page to be restored as Home page` comment and restores the `|| this.startsWith(Schemes.Fulguris + "://")` clause inside explicit parentheses. Do NOT re-add `DownloadPageFactory.FILENAME` (see Global Constraints).

- [ ] **Step 4: Run the test to verify it passes (GREEN)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.utils.UrlSpecialUrlTest"`

Expected: all 4 tests PASS.

- [ ] **Step 5: Run the full unit suite (no regressions)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest"`

Expected: all tests pass, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/xhub/browser/utils/UrlUtils.kt app/src/test/java/com/xhub/browser/utils/UrlSpecialUrlTest.kt
git commit -m "fix(tabs): classify xhub scheme URLs as special pages"
```

---

### Task 4: Bottom sheet below session pop-up without breaking status bar icons

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/extensions/ActivityExtensions.kt` (add `showBelowSessionPopup`)
- Modify: `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt:904-907` (`createBottomSheetDialog`)
- Test: `app/src/test/java/com/xhub/browser/extensions/DialogWindowTypeTest.kt` (new)

**Interfaces:**
- Consumes: `Window.setStatusBarIconsColor(dark: Boolean)` (ActivityExtensions.kt:97-109), `isDarkTheme()` (already used at WebBrowserActivity.kt:2958), `userPreferences.useBlackStatusBar`, `WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG`.
- Produces: `fun Dialog.showBelowSessionPopup(darkStatusIcons: Boolean)` — sets the dialog window type to `TYPE_APPLICATION_ATTACHED_DIALOG` (so the bottom sheet renders below the session pop-up) and mirrors the activity's status bar icon appearance on the dialog window. The dialog's `onSizeChange` observer at WebBrowserActivity.kt:925-936 is unaffected.

**Context / root cause:** In `createBottomSheetDialog` (WebBrowserActivity.kt:889-921) the line that sets the dialog window type is commented out with the TODO "that breaks status bar icon color with our light theme somehow" (line 905). Dialog windows do not inherit the activity's status-bar icon appearance; with `TYPE_APPLICATION_ATTACHED_DIALOG` the light-theme bottom sheet gets default light icons, which are invisible on the light sheet. The fix re-enables the window type and explicitly applies the same icon color logic the activity itself uses (WebBrowserActivity.kt:2958: `!isDarkTheme() && !userPreferences.useBlackStatusBar`), via the existing `Window.setStatusBarIconsColor` extension.

- [ ] **Step 1: Write the failing regression test**

Create `app/src/test/java/com/xhub/browser/extensions/DialogWindowTypeTest.kt`:

```kotlin
package com.xhub.browser.extensions

import android.app.Activity
import android.view.View
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class DialogWindowTypeTest {

    private fun shownBottomSheetDialog(): BottomSheetDialog {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val dialog = BottomSheetDialog(activity)
        dialog.show()
        return dialog
    }

    @Test
    fun `dialog window is application attached`() {
        val dialog = shownBottomSheetDialog()

        dialog.showBelowSessionPopup(darkStatusIcons = true)

        assertEquals(
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
            dialog.window?.attributes?.type
        )
    }

    @Test
    fun `dark status icons are requested for light backgrounds`() {
        val dialog = shownBottomSheetDialog()

        dialog.showBelowSessionPopup(darkStatusIcons = true)

        val flags = dialog.window?.decorView?.systemUiVisibility ?: 0
        assertTrue(flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0)
    }

    @Test
    fun `light status icons are requested for dark backgrounds`() {
        val dialog = shownBottomSheetDialog()

        dialog.showBelowSessionPopup(darkStatusIcons = false)

        val flags = dialog.window?.decorView?.systemUiVisibility ?: 0
        assertEquals(0, flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (RED)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.extensions.DialogWindowTypeTest"`

Expected: compile error — `showBelowSessionPopup` does not exist (the tests have not been written in a way that can pass without it). That compile failure is the RED. (If the compile error is unacceptable as RED, temporarily add a stub `fun Dialog.showBelowSessionPopup(darkStatusIcons: Boolean) = Unit` in `ActivityExtensions.kt`, run again — all three tests then FAIL — and proceed; Step 3 replaces the stub.)

- [ ] **Step 3: Implement the minimal fix**

(a) In `app/src/main/java/com/xhub/browser/extensions/ActivityExtensions.kt`, add the extension right after `setStatusBarIconsColor` (after line 109):

```kotlin
/**
 * Make the dialog's window an application-attached dialog so it stays below the session
 * pop-up, and mirror the activity's status bar icon appearance on it (attached dialogs do
 * not inherit the activity's icon color, which made icons invisible in the light theme).
 *
 * @param darkStatusIcons true to use dark status bar icons (light backgrounds), false for light icons.
 */
fun Dialog.showBelowSessionPopup(darkStatusIcons: Boolean) {
    window?.attributes?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        window?.setStatusBarIconsColor(darkStatusIcons)
    }
}
```

Add the missing imports at the top of `ActivityExtensions.kt`: `android.app.Dialog` and `android.view.WindowManager` (the file already imports `android.view.Window` and `android.view.View`).

(b) In `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`, replace lines 904-907 in `createBottomSheetDialog`:

```kotlin
        // Needed to make sure our bottom sheet shows below our session pop-up
        dialog.showBelowSessionPopup(!isDarkTheme() && !userPreferences.useBlackStatusBar)
```

(`isDarkTheme()` and `userPreferences` are already in scope in this file — the identical expression is used at WebBrowserActivity.kt:2958.)

- [ ] **Step 4: Run the test to verify it passes (GREEN)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest --tests com.xhub.browser.extensions.DialogWindowTypeTest"`

Expected: all 3 tests PASS.

- [ ] **Step 5: Run the full unit suite (no regressions)**

Run: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest"`

Expected: all tests pass, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/xhub/browser/extensions/ActivityExtensions.kt app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt app/src/test/java/com/xhub/browser/extensions/DialogWindowTypeTest.kt
git commit -m "fix(theme): keep bottom sheet below session pop-up without breaking status bar icons"
```

---

## Final Verification (after all four tasks)

- [ ] Full unit suite: `cmd //c "gradlew.bat testXhubFullDownloadDebugUnitTest"` → 0 failures (baseline 233 + 8 new tests = 241 expected)
- [ ] APK build: `cmd //c "gradlew.bat assembleXhubFullDownloadDebug"` → `BUILD SUCCESSFUL`
- [ ] Manual smoke on a device/emulator (release build optional):
  - Open the History page, switch sessions in the session pop-up, kill and relaunch the app → the History tab restores as History (not Home); session pop-up shows correct per-session tab counts
  - Light theme: open the tabs drawer and the bookmarks drawer → status-bar icons stay visible/readable
  - Restore a session containing home/history/bookmarks tabs → all tabs restore frozen (tabs show saved titles immediately; each tab renders its page when first opened, incl. regenerated special pages when the saved bundle is empty)
  - Reopen a closed tab (tab menu) → it opens in the same frozen manner

## Out of Scope (deferred, separate plans)

- WebBrowserActivity unit-test coverage (god class, 6,499 lines)
- Jacoco report task / coverage thresholds (dead config exists in build.gradle)
- Removal of the global `app` singleton (~216 usages)
- WebBrowserActivity slicing/refactor
- `TabsManagerMapTest` (mirror-test) replacement with real-TabsManager tests — Task 1/2 tests here are the first real-TabsManager tests and are intentionally scoped to the bugs
