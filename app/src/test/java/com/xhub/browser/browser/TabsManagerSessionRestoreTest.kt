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
        `when`(mockApp.getFilesDir()).thenReturn(RuntimeEnvironment.getApplication().filesDir)
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
                invocation.getArgument<Int>(0),
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
