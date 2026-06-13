package com.xhub.browser.html.homepage

import com.xhub.browser.App
import com.xhub.browser.R
import com.xhub.browser.constant.FILE
import com.xhub.browser.constant.UTF8
import com.xhub.browser.html.HtmlPageFactory
import com.xhub.browser.search.SearchEngineProvider
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.utils.ThemeUtils
import com.xhub.browser.utils.htmlColor
import android.app.Application
import dagger.Reusable
import com.xhub.browser.html.jsoup.andBuild
import com.xhub.browser.html.jsoup.body
import com.xhub.browser.html.jsoup.charset
import com.xhub.browser.html.jsoup.id
import com.xhub.browser.html.jsoup.parse
import com.xhub.browser.html.jsoup.tag
import io.reactivex.Single
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * A factory for the home page.
 */
@Reusable
class HomePageFactory @Inject constructor(
    private val application: Application,
    private val searchEngineProvider: SearchEngineProvider,
    private val homePageReader: HomePageReader,
    private var userPreferences: UserPreferences
) : HtmlPageFactory {

    override fun buildPage(): Single<String> = Single
        .just(searchEngineProvider.provideSearchEngine())
        .map { (iconUrl, queryUrl, _) ->
            App.setLocale() // Make sure locale is set as user specified
            
            parse(homePageReader.provideHtml()
                .replace("\${TITLE}", application.getString(R.string.home))
                .replace("\${backgroundColor}", htmlColor(ThemeUtils.getSurfaceColor(App.currentContext())))
            ) andBuild {
                charset { UTF8 }
                body {
                    when (userPreferences.searchChoice) {
                        0 -> id("image_url") { attr("src", userPreferences.imageUrlString) }
                        else -> id("image_url") { attr("src", iconUrl) }
                    }
                    tag("script") {
                        html(
                            html()
                                .replace("\${BASE_URL}", queryUrl)
                                .replace("&", "\\u0026")
                        )
                    }
                }
            }
        }
        .map { content -> Pair(createHomePage(), content) }
        .doOnSuccess { (page, content) ->
            FileWriter(page, false).use {
                it.write(content)
            }
        }
        .map { (page, _) -> "$FILE$page" }

    /**
     * Create the home page file.
     */
    fun createHomePage() = File(application.filesDir, FILENAME)

    companion object {

        const val FILENAME = "homepage.html"

    }

}
