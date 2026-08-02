package com.xhub.browser.view

import android.app.Activity
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.xhub.browser.App
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import com.xhub.browser.adblock.AbpBlockerManager
import com.xhub.browser.adblock.NoOpAdBlocker
import com.xhub.browser.browser.WebBrowser
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.html.homepage.HomePageFactory
import com.xhub.browser.js.InvertPage
import com.xhub.browser.js.SetMetaViewport
import com.xhub.browser.js.TextReflow
import com.xhub.browser.network.NetworkEngineManager
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.userscript.UserScriptManager
import dagger.hilt.android.EntryPointAccessors
import java.io.File
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

/**
 * A raw xhub:// special URL only reaches WebPageClient.shouldOverrideUrlLoading when the
 * frozen-state fallback fired (null/stale WebView bundle). It must be rerouted through
 * WebPageTab.loadUrl (which knows how to build the page) instead of being stopped, which
 * would leave the tab blank. file:// special pages load natively and must not be rerouted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class WebPageClientSpecialUrlFallbackTest {

    private lateinit var mockedStatic: MockedStatic<EntryPointAccessors>
    private lateinit var mockApp: App
    private lateinit var realActivity: Activity
    private lateinit var filesDirPath: String
    private lateinit var mockTab: WebPageTab
    private lateinit var client: WebPageClient
    private val mockWebView: WebView = mock(WebView::class.java)

    @Before
    fun setup() {
        // Create the real Robolectric activity first so we can use its context for stubs
        realActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        filesDirPath = RuntimeEnvironment.getApplication().filesDir.path

        // Set up a mock App so DomainPreferences / BooleanPreference delegates
        // (which access the global `app`) and String.isSpecialUrl() (app.filesDir) work.
        mockApp = mock(App::class.java)
        `when`(mockApp.resources).thenReturn(RuntimeEnvironment.getApplication().resources)
        `when`(mockApp.applicationContext).thenReturn(RuntimeEnvironment.getApplication())
        `when`(mockApp.applicationInfo).thenReturn(RuntimeEnvironment.getApplication().applicationInfo)
        `when`(mockApp.packageName).thenReturn(RuntimeEnvironment.getApplication().packageName)
        `when`(mockApp.filesDir).thenReturn(File(filesDirPath))
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
        val entryPoint = mock(HiltEntryPoint::class.java)
        val userPreferences = mock(UserPreferences::class.java)
        `when`(mockApp.userPreferences).thenReturn(userPreferences)
        `when`(entryPoint.userPreferences).thenReturn(userPreferences)
        `when`(entryPoint.userSharedPreferences()).thenReturn(mock(SharedPreferences::class.java))
        `when`(entryPoint.textReflowJs).thenReturn(mock(TextReflow::class.java))
        `when`(entryPoint.invertPageJs).thenReturn(mock(InvertPage::class.java))
        `when`(entryPoint.setMetaViewport).thenReturn(mock(SetMetaViewport::class.java))
        `when`(entryPoint.homePageFactory).thenReturn(mock(HomePageFactory::class.java))
        `when`(entryPoint.abpBlockerManager).thenReturn(mock(AbpBlockerManager::class.java))
        `when`(entryPoint.noopBlocker).thenReturn(mock(NoOpAdBlocker::class.java))
        `when`(entryPoint.networkEngineManager).thenReturn(mock(NetworkEngineManager::class.java))
        `when`(entryPoint.userScriptManager).thenReturn(mock(UserScriptManager::class.java))

        // Mock the static EntryPointAccessors so WebPageClient gets our mock HiltEntryPoint
        // (client calls fromApplication(activity.applicationContext, ...) — activity
        // applicationContext is stubbed to realActivity.applicationContext below)
        mockedStatic = mockStatic(EntryPointAccessors::class.java)
        mockedStatic.`when`<Any> {
            EntryPointAccessors.fromApplication(
                realActivity.applicationContext,
                HiltEntryPoint::class.java
            )
        }.thenReturn(entryPoint)

        // Mock activity so the `activity as WebBrowser` cast in the client works
        val mockActivity = mock(TestActivity::class.java)
        `when`(mockActivity.applicationContext).thenReturn(realActivity.applicationContext)

        // Mock tab with the minimal state the shouldOverrideUrlLoading path reads
        mockTab = mock(WebPageTab::class.java)
        `when`(mockTab.isIncognito).thenReturn(false)
        `when`(mockTab.isShowingDirectAd).thenReturn(false)
        // requestHeaders is internal (name-mangled getter returning androidx.collection.ArrayMap,
        // which is not on the unit-test compile classpath) — build one via reflection so the
        // getter's runtime cast succeeds.
        val emptyArrayMap = Class.forName("androidx.collection.ArrayMap").getConstructor().newInstance()
        doAnswer { emptyArrayMap as Map<String, String> }.`when`(mockTab).requestHeaders

        client = WebPageClient(mockActivity, mockTab)
    }

    @After
    fun tearDown() {
        mockedStatic.close()
    }

    @Test
    fun `xhub scheme special url is routed through the tab loader instead of being stopped`() {
        val request = requestFor("xhub://history")
        val result = client.shouldOverrideUrlLoading(mockWebView, request)
        verify(mockTab).loadUrl("xhub://history")
        assertEquals(true, result)
    }

    @Test
    fun `file special url keeps loading directly and is not rerouted`() {
        val request = requestFor("file://$filesDirPath/history.html")
        val result = client.shouldOverrideUrlLoading(mockWebView, request)
        verify(mockTab, never()).loadUrl(anyString(), anyBoolean(), any())
        assertEquals(false, result)
    }

    private fun requestFor(url: String): WebResourceRequest {
        val request = mock(WebResourceRequest::class.java)
        `when`(request.url).thenReturn(Uri.parse(url))
        `when`(request.isForMainFrame).thenReturn(true)
        return request
    }

    abstract class TestActivity : Activity(), WebBrowser
}
