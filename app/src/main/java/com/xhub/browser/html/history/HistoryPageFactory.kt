package com.xhub.browser.html.history

import com.xhub.browser.App
import com.xhub.browser.R
import com.xhub.browser.constant.FILE
import com.xhub.browser.database.history.HistoryRepository
import com.xhub.browser.html.HtmlPageFactory
import com.xhub.browser.html.ListPageReader
import com.xhub.browser.utils.ThemeUtils
import com.xhub.browser.utils.htmlColor
import android.app.Application
import dagger.Reusable
import com.xhub.browser.html.jsoup.andBuild
import com.xhub.browser.html.jsoup.body
import com.xhub.browser.html.jsoup.clone
import com.xhub.browser.html.jsoup.id
import com.xhub.browser.html.jsoup.parse
import com.xhub.browser.html.jsoup.removeElement
import com.xhub.browser.html.jsoup.tag
import com.xhub.browser.html.jsoup.title
import io.reactivex.Completable
import io.reactivex.Single
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * Factory for the history page.
 */
@Reusable
class HistoryPageFactory @Inject constructor(
    private val listPageReader: ListPageReader,
    private val application: Application,
    private val historyRepository: HistoryRepository
) : HtmlPageFactory {

    private val title = application.getString(R.string.action_history)

    override fun buildPage(): Single<String> = historyRepository
        .lastHundredVisitedHistoryEntries()
        .map { list ->
            parse(listPageReader.provideHtml()
                    // Show localized page title
                    .replace("\${pageTitle}", application.getString(R.string.action_history))
                    // Theme our page first
                    .replace("\${backgroundColor}", htmlColor(ThemeUtils.getSurfaceColor(App.currentContext())))
                    .replace("\${textColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOnSurface)))
                    .replace("\${secondaryTextColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorSecondary)))
                    .replace("\${dividerColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOutline)))
            ) andBuild {
                title { title }
                body {
                    val repeatedElement = id("repeated").removeElement()
                    id("content") {
                        list.forEach {
                            appendChild(repeatedElement.clone {
                                tag("a") { attr("href", it.url) }
                                id("title") { text(it.title) }
                                id("url") { text(it.url) }
                            })
                        }
                    }
                }
            }
        }
        .map { content -> Pair(createHistoryPage(), content) }
        .doOnSuccess { (page, content) ->
            FileWriter(page, false).use { it.write(content) }
        }
        .map { (page, _) -> "$FILE$page" }

    /**
     * Use this observable to immediately delete the history page. This will clear the cached
     * history page that was stored on file.
     *
     * @return a completable that deletes the history page when subscribed to.
     */
    fun deleteHistoryPage(): Completable = Completable.fromAction {
        with(createHistoryPage()) {
            if (exists()) {
                delete()
            }
        }
    }

    private fun createHistoryPage() = File(application.filesDir, FILENAME)

    companion object {
        const val FILENAME = "history.html"
    }

}
