package com.xhub.browser.view

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.graphics.Bitmap
import android.os.Bundle
import com.xhub.browser.App
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import com.xhub.browser.adblock.AbpBlockerManager
import com.xhub.browser.adblock.NoOpAdBlocker
import com.xhub.browser.browser.TabModel
import com.xhub.browser.browser.WebBrowser
import com.xhub.browser.constant.Uris
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.dialog.LightningDialogBuilder
import com.xhub.browser.download.DownloadHandler
import com.xhub.browser.html.homepage.HomePageFactory
import com.xhub.browser.js.InvertPage
import com.xhub.browser.js.SetMetaViewport
import com.xhub.browser.js.TextReflow
import com.xhub.browser.network.NetworkConnectivityModel
import com.xhub.browser.network.NetworkEngineManager
import com.xhub.browser.enums.LayerType
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.ssl.SslState
import com.xhub.browser.userscript.UserScriptManager
import com.xhub.browser.view.RenderingMode
import dagger.hilt.android.EntryPointAccessors
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.After
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
class WebPageTabTest {

    private lateinit var mockedStatic: MockedStatic<EntryPointAccessors>
    private lateinit var entryPoint: HiltEntryPoint
    private lateinit var userPreferences: UserPreferences
    private lateinit var mockApp: App
    private lateinit var realActivity: Activity

    @Before
    fun setup() {
        // Create the real Robolectric activity first so we can use its context for stubs
        realActivity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Set up a mock App so DomainPreferences / BooleanPreference delegates
        // (which access the global `app` for resources/getString) work in tests.
        mockApp = mock(App::class.java)
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

        // Inject mockApp into the global `app` lateinit var via reflection
        val appKtClass = Class.forName("com.xhub.browser.AppKt")
        val field = appKtClass.getDeclaredField("app")
        field.isAccessible = true
        field.set(null, mockApp)

        // Set up HiltEntryPoint mock
        entryPoint = mock(HiltEntryPoint::class.java)
        userPreferences = mock(UserPreferences::class.java)
        `when`(mockApp.userPreferences).thenReturn(userPreferences)

        // userPreferences.preferences is called in createWebView() to register a listener
        val mockUserPrefs = mock(SharedPreferences::class.java)
        `when`(userPreferences.preferences).thenReturn(mockUserPrefs)
        // Stub enum-typed properties to return safe defaults (plain mock returns null for object types)
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

        `when`(entryPoint.userPreferences).thenReturn(userPreferences)
        `when`(entryPoint.dialogBuilder).thenReturn(mock(LightningDialogBuilder::class.java))
        `when`(entryPoint.databaseScheduler()).thenReturn(Schedulers.trampoline())
        `when`(entryPoint.mainScheduler()).thenReturn(Schedulers.trampoline())

        val networkConnectivityModel = mock(NetworkConnectivityModel::class.java)
        `when`(entryPoint.networkConnectivityModel).thenReturn(networkConnectivityModel)
        `when`(networkConnectivityModel.connectivity()).thenReturn(Observable.empty())

        `when`(entryPoint.downloadHandler).thenReturn(mock(DownloadHandler::class.java))
        `when`(entryPoint.userSharedPreferences()).thenReturn(mock(SharedPreferences::class.java))
        `when`(entryPoint.textReflowJs).thenReturn(mock(TextReflow::class.java))
        `when`(entryPoint.invertPageJs).thenReturn(mock(InvertPage::class.java))
        `when`(entryPoint.setMetaViewport).thenReturn(mock(SetMetaViewport::class.java))
        `when`(entryPoint.homePageFactory).thenReturn(mock(HomePageFactory::class.java))
        `when`(entryPoint.abpBlockerManager).thenReturn(mock(AbpBlockerManager::class.java))
        `when`(entryPoint.noopBlocker).thenReturn(mock(NoOpAdBlocker::class.java))
        `when`(entryPoint.networkEngineManager).thenReturn(mock(NetworkEngineManager::class.java))
        `when`(entryPoint.userScriptManager).thenReturn(mock(UserScriptManager::class.java))

        // Mock the static EntryPointAccessors so both WebPageTab and WebPageClient
        // get our mock HiltEntryPoint when they call fromApplication(...)
        mockedStatic = mockStatic(EntryPointAccessors::class.java)
        mockedStatic.`when`<Any> {
            EntryPointAccessors.fromApplication(
                realActivity.applicationContext,
                HiltEntryPoint::class.java
            )
        }.thenReturn(entryPoint)
        mockedStatic.`when`<Any> {
            EntryPointAccessors.fromApplication(
                mockApp,
                HiltEntryPoint::class.java
            )
        }.thenReturn(entryPoint)
    }

    @After
    fun tearDown() {
        mockedStatic.close()
    }

    @Test
    fun `WebPageTab initializeSettings configures allowFileAccess to be false for security`() {
        // Wrap realActivity in a mock so WebBrowser interface methods are handled without NPE
        val activity = mock(TestActivity::class.java)
        `when`(activity.applicationInfo).thenReturn(realActivity.applicationInfo)
        `when`(activity.applicationContext).thenReturn(realActivity.applicationContext)
        `when`(activity.layoutInflater).thenReturn(realActivity.layoutInflater)
        `when`(activity.resources).thenReturn(realActivity.resources)
        `when`(activity.packageName).thenReturn(realActivity.packageName)
        `when`(activity.theme).thenReturn(realActivity.theme)
        `when`(activity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
        `when`(activity.getString(anyInt())).thenAnswer { invocation ->
            realActivity.getString(invocation.getArgument(0))
        }
        `when`(activity.obtainStyledAttributes(anyInt(), any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<Int>(0),
                invocation.getArgument<IntArray>(1)
            )
        }
        `when`(activity.obtainStyledAttributes(any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<IntArray>(0)
            )
        }
        `when`(activity.obtainStyledAttributes(isNull(AttributeSet::class.java), any(IntArray::class.java), anyInt(), anyInt())).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<AttributeSet?>(0),
                invocation.getArgument<IntArray>(1),
                invocation.getArgument<Int>(2),
                invocation.getArgument<Int>(3)
            )
        }
        // DomainPreferences uses context.getSharedPreferences for its preferences file
        `when`(activity.getSharedPreferences(anyString(), anyInt()))
            .thenAnswer { invocation ->
                realActivity.getSharedPreferences(
                    invocation.getArgument(0),
                    invocation.getArgument(1)
                )
            }
        // setUserAgentForPreference calls activity.application
        `when`(activity.application).thenReturn(realActivity.application)

        val mockTabInitializer = mock(TabInitializer::class.java)
        `when`(mockTabInitializer.url()).thenReturn(Uris.FulgurisHome)

        val tab = WebPageTab(
            activity = activity,
            tabInitializer = mockTabInitializer,
            isIncognito = false,
            homePageInitializer = mock(HomePageInitializer::class.java),
            incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
            bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
            historyPageInitializer = mock(HistoryPageInitializer::class.java)
        )

        assertThat(tab.webView).isNotNull
        val settings = tab.webView!!.settings
        // allowFileAccess must be false for security (prevents cross-app file/JavaScript attacks).
        // Internal pages use homeScreenOverlay or data: URIs, not file:// URLs.
        assertThat(settings.allowFileAccess).isFalse()
    }

    @Test
    fun `WebPageTab clearVideoDetectedState resets video parameters`() {
        val activity = mock(TestActivity::class.java)
        // runOnUiThread must execute the Runnable synchronously so onVideoDetected state assignments run in tests
        doAnswer { invocation -> (invocation.getArgument<Runnable>(0)).run(); Unit }
            .`when`(activity).runOnUiThread(any(Runnable::class.java))
        `when`(activity.applicationInfo).thenReturn(realActivity.applicationInfo)
        `when`(activity.applicationContext).thenReturn(realActivity.applicationContext)
        `when`(activity.layoutInflater).thenReturn(realActivity.layoutInflater)
        `when`(activity.resources).thenReturn(realActivity.resources)
        `when`(activity.packageName).thenReturn(realActivity.packageName)
        `when`(activity.theme).thenReturn(realActivity.theme)
        `when`(activity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
        `when`(activity.getString(anyInt())).thenAnswer { invocation ->
            realActivity.getString(invocation.getArgument(0))
        }
        `when`(activity.obtainStyledAttributes(anyInt(), any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<Int>(0),
                invocation.getArgument<IntArray>(1)
            )
        }
        `when`(activity.obtainStyledAttributes(any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(invocation.getArgument<IntArray>(0))
        }
        `when`(activity.obtainStyledAttributes(isNull(AttributeSet::class.java), any(IntArray::class.java), anyInt(), anyInt())).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<AttributeSet?>(0),
                invocation.getArgument<IntArray>(1),
                invocation.getArgument<Int>(2),
                invocation.getArgument<Int>(3)
            )
        }
        `when`(activity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
        }
        `when`(activity.application).thenReturn(realActivity.application)

        val mockTabInitializer = mock(TabInitializer::class.java)
        `when`(mockTabInitializer.url()).thenReturn(Uris.FulgurisHome)

        val tab = WebPageTab(
            activity = activity,
            tabInitializer = mockTabInitializer,
            isIncognito = false,
            homePageInitializer = mock(HomePageInitializer::class.java),
            incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
            bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
            historyPageInitializer = mock(HistoryPageInitializer::class.java)
        )

        // Simulate video detected
        tab.onVideoDetected("https://test.com/video.mp4", null, "720p", "direct")
        assertThat(tab.isVideoDetected).isTrue()
        assertThat(tab.detectedVideoUrl).isEqualTo("https://test.com/video.mp4")

        // Clear it
        tab.clearVideoDetectedState()
        assertThat(tab.isVideoDetected).isFalse()
        assertThat(tab.detectedVideoUrl).isNull()
    }

    /**
     * Frozen (session-restored) tabs defer createWebView() until they become foreground.
     * Until then webPageClient is uninitialized — callers must not crash.
     */
    private fun createFrozenTab(): WebPageTab {
        val activity = mock(TestActivity::class.java)
        doAnswer { invocation -> (invocation.getArgument<Runnable>(0)).run(); Unit }
            .`when`(activity).runOnUiThread(any(Runnable::class.java))
        `when`(activity.applicationInfo).thenReturn(realActivity.applicationInfo)
        `when`(activity.applicationContext).thenReturn(realActivity.applicationContext)
        `when`(activity.layoutInflater).thenReturn(realActivity.layoutInflater)
        `when`(activity.resources).thenReturn(realActivity.resources)
        `when`(activity.packageName).thenReturn(realActivity.packageName)
        `when`(activity.theme).thenReturn(realActivity.theme)
        `when`(activity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
        `when`(activity.getString(anyInt())).thenAnswer { invocation ->
            realActivity.getString(invocation.getArgument(0))
        }
        `when`(activity.obtainStyledAttributes(anyInt(), any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<Int>(0),
                invocation.getArgument<IntArray>(1)
            )
        }
        `when`(activity.obtainStyledAttributes(any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(invocation.getArgument<IntArray>(0))
        }
        `when`(activity.obtainStyledAttributes(isNull(AttributeSet::class.java), any(IntArray::class.java), anyInt(), anyInt())).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<AttributeSet?>(0),
                invocation.getArgument<IntArray>(1),
                invocation.getArgument<Int>(2),
                invocation.getArgument<Int>(3)
            )
        }
        `when`(activity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
        }
        `when`(activity.application).thenReturn(realActivity.application)

        val favicon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val frozenModel = TabModel(
            url = "https://example.com",
            title = "Example",
            desktopMode = false,
            darkMode = false,
            favicon = favicon,
            searchQuery = "",
            searchActive = false,
            webView = Bundle(),
            tabId = 99
        )
        val frozenInitializer = FreezableBundleInitializer(frozenModel)

        return WebPageTab(
            activity = activity,
            tabInitializer = frozenInitializer,
            isIncognito = false,
            homePageInitializer = mock(HomePageInitializer::class.java),
            incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
            bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
            historyPageInitializer = mock(HistoryPageInitializer::class.java)
        )
    }

    @Test
    fun `currentSslState returns None without throw when webPageClient not initialized`() {
        val tab = createFrozenTab()
        assertThat(tab.webView).isNull()
        assertThat(tab.currentSslState()).isEqualTo(SslState.None)
    }

    @Test
    fun `destroy does not throw when webPageClient not initialized`() {
        val tab = createFrozenTab()
        assertThat(tab.webView).isNull()
        assertThatCode { tab.destroy() }.doesNotThrowAnyException()
    }

    @Test
    fun `initializePreferences does not throw when webPageClient not initialized`() {
        val tab = createFrozenTab()
        assertThat(tab.webView).isNull()
        assertThatCode { tab.initializePreferences() }.doesNotThrowAnyException()
    }

    @Test
    fun `WebPageTab injectVideoSniffer checks videoDetectionEnabled preference`() {
        `when`(userPreferences.videoDetectionEnabled).thenReturn(true)
        val activity = mock(TestActivity::class.java)
        doAnswer { invocation -> (invocation.getArgument<Runnable>(0)).run(); Unit }
            .`when`(activity).runOnUiThread(any(Runnable::class.java))
        `when`(activity.applicationInfo).thenReturn(realActivity.applicationInfo)
        `when`(activity.applicationContext).thenReturn(realActivity.applicationContext)
        `when`(activity.layoutInflater).thenReturn(realActivity.layoutInflater)
        `when`(activity.resources).thenReturn(realActivity.resources)
        `when`(activity.packageName).thenReturn(realActivity.packageName)
        `when`(activity.theme).thenReturn(realActivity.theme)
        `when`(activity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
        `when`(activity.getString(anyInt())).thenAnswer { invocation ->
            realActivity.getString(invocation.getArgument(0))
        }
        `when`(activity.obtainStyledAttributes(anyInt(), any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<Int>(0),
                invocation.getArgument<IntArray>(1)
            )
        }
        `when`(activity.obtainStyledAttributes(any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(invocation.getArgument<IntArray>(0))
        }
        `when`(activity.obtainStyledAttributes(isNull(AttributeSet::class.java), any(IntArray::class.java), anyInt(), anyInt())).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<AttributeSet?>(0),
                invocation.getArgument<IntArray>(1),
                invocation.getArgument<Int>(2),
                invocation.getArgument<Int>(3)
            )
        }
        `when`(activity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
        }
        `when`(activity.application).thenReturn(realActivity.application)

        val mockTabInitializer = mock(TabInitializer::class.java)
        `when`(mockTabInitializer.url()).thenReturn(Uris.FulgurisHome)

        val tab = WebPageTab(
            activity = activity,
            tabInitializer = mockTabInitializer,
            isIncognito = false,
            homePageInitializer = mock(HomePageInitializer::class.java),
            incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
            bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
            historyPageInitializer = mock(HistoryPageInitializer::class.java)
        )

        // injectVideoSniffer should check the pref and not crash
        tab.injectVideoSniffer()
        verify(userPreferences, atLeastOnce()).videoDetectionEnabled
    }

    @Test
    fun `WebPageTab video detection events are rate limited per page`() {
        val tab = createVideoTab()
        val url = "https://test.com/video.mp4"

        // First 20 events are accepted
        repeat(20) { tab.onVideoDetected(url, null, null, "direct") }
        assertThat(tab.isVideoDetected).isTrue()
        assertThat(tab.detectedVideoUrl).isEqualTo(url)

        // Events beyond the per-page cap must be ignored
        tab.onVideoDetected("https://test.com/other.mp4", null, null, "direct")
        assertThat(tab.detectedVideoUrl).isEqualTo(url)

        // Navigating away resets the counter so detection works again
        tab.clearVideoDetectedState()
        tab.onVideoDetected("https://test.com/new.mp4", null, null, "direct")
        assertThat(tab.detectedVideoUrl).isEqualTo("https://test.com/new.mp4")
    }

    /**
     * Builds a WebPageTab backed by a mocked TestActivity whose runOnUiThread executes
     * synchronously so video detection state assignments run in tests.
     */
    private fun createVideoTab(): WebPageTab {
        val activity = mock(TestActivity::class.java)
        doAnswer { invocation -> (invocation.getArgument<Runnable>(0)).run(); Unit }
            .`when`(activity).runOnUiThread(any(Runnable::class.java))
        `when`(activity.applicationInfo).thenReturn(realActivity.applicationInfo)
        `when`(activity.applicationContext).thenReturn(realActivity.applicationContext)
        `when`(activity.layoutInflater).thenReturn(realActivity.layoutInflater)
        `when`(activity.resources).thenReturn(realActivity.resources)
        `when`(activity.packageName).thenReturn(realActivity.packageName)
        `when`(activity.theme).thenReturn(realActivity.theme)
        `when`(activity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
        `when`(activity.getString(anyInt())).thenAnswer { invocation ->
            realActivity.getString(invocation.getArgument(0))
        }
        `when`(activity.obtainStyledAttributes(anyInt(), any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<Int>(0),
                invocation.getArgument<IntArray>(1)
            )
        }
        `when`(activity.obtainStyledAttributes(any(IntArray::class.java))).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(invocation.getArgument<IntArray>(0))
        }
        `when`(activity.obtainStyledAttributes(isNull(AttributeSet::class.java), any(IntArray::class.java), anyInt(), anyInt())).thenAnswer { invocation ->
            realActivity.obtainStyledAttributes(
                invocation.getArgument<AttributeSet?>(0),
                invocation.getArgument<IntArray>(1),
                invocation.getArgument<Int>(2),
                invocation.getArgument<Int>(3)
            )
        }
        `when`(activity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
        }
        `when`(activity.application).thenReturn(realActivity.application)

        val mockTabInitializer = mock(TabInitializer::class.java)
        `when`(mockTabInitializer.url()).thenReturn(Uris.FulgurisHome)

        return WebPageTab(
            activity = activity,
            tabInitializer = mockTabInitializer,
            isIncognito = false,
            homePageInitializer = mock(HomePageInitializer::class.java),
            incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
            bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
            historyPageInitializer = mock(HistoryPageInitializer::class.java)
        )
    }

    abstract class TestActivity : Activity(), WebBrowser
}
