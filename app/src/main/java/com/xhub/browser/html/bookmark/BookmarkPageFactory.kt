package com.xhub.browser.html.bookmark

import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.constant.FILE
import com.xhub.browser.constant.FOLDER
import com.xhub.browser.database.Bookmark
import com.xhub.browser.database.bookmark.BookmarkRepository
import com.xhub.browser.di.DatabaseScheduler
import com.xhub.browser.di.DiskScheduler
import com.xhub.browser.di.configPrefs
import com.xhub.browser.extensions.isDarkTheme
import com.xhub.browser.extensions.safeUse
import com.xhub.browser.favicon.FaviconModel
import com.xhub.browser.favicon.toValidUri
import com.xhub.browser.html.HtmlPageFactory
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.utils.ThemeUtils
import com.xhub.browser.utils.htmlColor
import android.app.Application
import android.graphics.Bitmap
import androidx.core.net.toUri
import dagger.Reusable
import com.xhub.browser.App
import com.xhub.browser.html.jsoup.andBuild
import com.xhub.browser.html.jsoup.body
import com.xhub.browser.html.jsoup.clone
import com.xhub.browser.html.jsoup.id
import com.xhub.browser.html.jsoup.parse
import com.xhub.browser.html.jsoup.removeElement
import com.xhub.browser.html.jsoup.tag
import com.xhub.browser.html.jsoup.title
import io.reactivex.Scheduler
import io.reactivex.Single
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import javax.inject.Inject

/**
 * Created by anthonycr on 9/23/18.
 *
 * Generates our bookmarks HTML page.
 * We actually use it as our default home page these days.
 */
@Reusable
class BookmarkPageFactory @Inject constructor(
    private val application: Application,
    private val bookmarkModel: BookmarkRepository,
    private val faviconModel: FaviconModel,
    @DatabaseScheduler private val databaseScheduler: Scheduler,
    @DiskScheduler private val diskScheduler: Scheduler,
    private val bookmarkPageReader: BookmarkPageReader,
    private val userPreferences: UserPreferences,
) : HtmlPageFactory {

    private val title = application.getString(R.string.action_bookmarks)
    private val folderIconFile by lazy { File(application.cacheDir, FOLDER_ICON) }
    private val folderIconFileOnDark by lazy { File(application.cacheDir, FOLDER_ICON_ON_DARK) }
    private val defaultIconFile by lazy { File(application.cacheDir, DEFAULT_ICON) }

    override fun buildPage(): Single<String> = bookmarkModel
        .getFoldersSorted()
        .flatMap { allFolders ->
            // Build a list of all folders we need to create pages for (including root)
            val foldersToProcess = listOf(Bookmark.Folder.Root) + allFolders.filterIsInstance<Bookmark.Folder.Entry>()

            Single.just(foldersToProcess)
                .flattenAsObservable { it }
                .flatMapSingle { folder ->
                    // For each folder, get its direct bookmarks and immediate subfolders
                    val folderPath = if (folder is Bookmark.Folder.Root) null else folder.title

                    // Get subfolders and bookmarks
                    Single.zip(
                        bookmarkModel.getSubFoldersSorted(folderPath),
                        bookmarkModel.getBookmarksFromFolderSorted(folderPath)
                    ) { folders, bookmarks ->
                        // Combine folders first, then bookmarks
                        val items = (folders + bookmarks).map { it.asViewModel() }.toMutableList()

                        // Add parent folder navigation item at the beginning (except for root)
                        if (folder !is Bookmark.Folder.Root) {
                            items.add(0, createParentFolderViewModel(folder))
                        }

                        Pair(folder, items)
                    }
                }
                .toList()
        }
        .flattenAsObservable { it }
        .map { (folder, viewModels) -> Pair(folder, construct(viewModels)) }
        .subscribeOn(databaseScheduler)
        .observeOn(diskScheduler)
        .doOnNext { (folder, content) ->
            FileWriter(createBookmarkPage(folder), false).use {
                it.write(content)
            }
        }
        .ignoreElements()
        .toSingle {
            cacheIcon(ThemeUtils.createThemedBitmap(application, R.drawable.ic_folder, false), folderIconFile)
            cacheIcon(ThemeUtils.createThemedBitmap(application, R.drawable.ic_folder, true), folderIconFileOnDark)
            cacheIcon(faviconModel.createDefaultBitmapForTitle(null), defaultIconFile)

            "$FILE${createBookmarkPage(null)}"
        }

    private fun cacheIcon(icon: Bitmap, file: File) = FileOutputStream(file).safeUse {
        icon.compress(Bitmap.CompressFormat.PNG, 100, it)
        icon.recycle()
    }

    private fun construct(list: List<BookmarkViewModel>): String {
        val useDarkTheme = (App.currentContext() as? WebBrowserActivity)?.isDarkTheme() == false
        return parse(bookmarkPageReader.provideHtml()
            // Theme our page first
            .replace("\${useDarkTheme}", useDarkTheme.toString()) // Not actually used for now
            .replace("\${colorBackground}", htmlColor(ThemeUtils.getBackgroundColor(App.currentContext())))
            .replace("\${colorOnBackground}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOnSurface)))
            .replace("\${colorControl}", htmlColor(ThemeUtils.getSearchBarColor(ThemeUtils.getSurfaceColor(App.currentContext()))))
            .replace("\${colorBorder}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOutline)))
        ) andBuild {
            title { title }
            body {
                val repeatableElement = id("repeated").removeElement()
                id("content") {
                    // Render all items in order: ".." first (if present), then folders and bookmarks
                    list.forEach {
                        val newElement = repeatableElement.clone {
                            tag("a") { attr("href", it.url) }
                            // Make sure we use proper icon for dark themes
                            tag("img") { attr("src", if (useDarkTheme && it.iconUrlOnDark.isNotEmpty()) it.iconUrlOnDark else it.iconUrl) }
                            id("title") { appendText(it.title) }
                        }
                        appendChild(newElement)
                    }
                }
            }
        }
    }

    /**
     * Creates a view model for the parent folder navigation item ("..").
     */
    private fun createParentFolderViewModel(currentFolder: Bookmark.Folder): BookmarkViewModel {
        // Get parent folder path
        val currentPath = (currentFolder as? Bookmark.Folder.Entry)?.title ?: ""
        val parentPath = currentPath.substringBeforeLast('/', "")

        // Create parent folder bookmark object
        val parentFolder = if (parentPath.isEmpty()) {
            Bookmark.Folder.Root
        } else {
            Bookmark.Folder.Entry(
                url = "$FOLDER$parentPath",
                title = parentPath
            )
        }

        val parentPage = createBookmarkPage(parentFolder)
        val url = "$FILE$parentPage"

        return BookmarkViewModel(
            title = "..",
            url = url,
            iconUrl = folderIconFile.toString(),
            iconUrlOnDark = folderIconFileOnDark.toString()
        )
    }

    private fun Bookmark.asViewModel(): BookmarkViewModel = when (this) {
        is Bookmark.Folder -> createViewModelForFolder(this)
        is Bookmark.Entry -> createViewModelForBookmark(this)
    }

    private fun createViewModelForFolder(folder: Bookmark.Folder): BookmarkViewModel {
        val folderPage = createBookmarkPage(folder)
        val url = "$FILE$folderPage"
        // Display only the last segment of the folder path
        val displayTitle = folder.title.substringAfterLast('/', folder.title)

        return BookmarkViewModel(
            title = displayTitle,
            url = url,
            iconUrl = folderIconFile.toString(),
            iconUrlOnDark = folderIconFileOnDark.toString()
        )
    }

    private fun createViewModelForBookmark(entry: Bookmark.Entry): BookmarkViewModel {
        val bookmarkUri = entry.url.toUri().toValidUri()

        // Fetch icon URL for light theme
        val iconUrl = if (bookmarkUri != null) {
            val faviconFile = FaviconModel.getFaviconCacheFile(application, bookmarkUri,false)
            if (!faviconFile.exists()) {
                val defaultFavicon = faviconModel.createDefaultBitmapForTitle(entry.title)
                faviconModel.cacheFaviconForUrl(defaultFavicon, entry.url)
                    .subscribeOn(diskScheduler)
                    .subscribe()
            }

            faviconFile
        } else {
            defaultIconFile
        }

        // Fetch icon URL for dark theme if any
        val iconUrlOnDark = if (bookmarkUri != null) {
            val faviconFile = FaviconModel.getFaviconCacheFile(application, bookmarkUri,true)
            if (!faviconFile.exists()) {
                ""
            }
            else {
                faviconFile.toString()
            }
        }
        else
        {
            ""
        }


        return BookmarkViewModel(
            title = entry.title,
            url = entry.url,
            iconUrl = iconUrl.toString(),
            iconUrlOnDark = iconUrlOnDark
        )
    }

    /**
     * Create the bookmark page file.
     */
    fun createBookmarkPage(folder: Bookmark.Folder?): File {
        val file = if (folder?.title?.isNotBlank() == true) {
            // For nested folders, create directory structure
            // Replace "/" with File.separator to ensure proper path handling
            val folderPath = folder.title.replace("/", File.separator)
            File(application.filesDir, "$folderPath${File.separator}$FILENAME")
        } else {
            // Root level bookmarks
            File(application.filesDir, FILENAME)
        }

        // Ensure parent directories exist for nested folders
        file.parentFile?.mkdirs()

        return file
    }

    companion object {

        const val FILENAME = "bookmarks.html"

        private const val FOLDER_ICON = "folder.png"
        private const val FOLDER_ICON_ON_DARK = "folder-on-dark.png"
        private const val DEFAULT_ICON = "default.png"

    }
}
