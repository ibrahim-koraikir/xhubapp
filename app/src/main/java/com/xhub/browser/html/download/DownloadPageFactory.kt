package com.xhub.browser.html.download

import com.xhub.browser.R
import com.xhub.browser.constant.FILE
import com.xhub.browser.database.downloads.DownloadEntry
import com.xhub.browser.database.downloads.DownloadsRepository
import com.xhub.browser.html.HtmlPageFactory
import com.xhub.browser.html.ListPageReader
import com.xhub.browser.settings.preferences.UserPreferences
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
import io.reactivex.Single
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * The factory for the downloads page.
 */
@Reusable
class DownloadPageFactory @Inject constructor(
    private val application: Application,
    private val userPreferences: UserPreferences,
    private val manager: DownloadsRepository,
    private val listPageReader: ListPageReader
) : HtmlPageFactory {

    override fun buildPage(): Single<String> = manager
        .getAllDownloads()
        .map { list ->
            parse(listPageReader.provideHtml()) andBuild {
                title { application.getString(R.string.action_downloads) }
                body {
                    val repeatableElement = id("repeated").removeElement()
                    id("content") {
                        list.forEach {
                            appendChild(repeatableElement.clone {
                                tag("a") { attr("href", createFileUrl(it.title)) }
                                id("title") { text(createFileTitle(it)) }
                                id("url") { text(it.url) }
                            })
                        }
                    }
                }
            }
        }
        .map { content -> Pair(createDownloadsPageFile(), content) }
        .doOnSuccess { (page, content) ->
            FileWriter(page, false).use { it.write(content) }
        }
        .map { (page, _) -> "$FILE$page" }


    private fun createDownloadsPageFile(): File = File(application.filesDir, FILENAME)

    private fun createFileUrl(fileName: String): String = "$FILE${userPreferences.downloadDirectory}/$fileName"

    private fun createFileTitle(downloadItem: DownloadEntry): String {
        val contentSize = if (downloadItem.contentSize.isNotBlank()) {
            "[${downloadItem.contentSize}]"
        } else {
            ""
        }

        return "${downloadItem.title} $contentSize"
    }

    companion object {

        const val FILENAME = "downloads.html"

    }

}
