package com.xhub.browser.bookmark

import com.xhub.browser.database.Bookmark
import com.xhub.browser.database.bookmark.BookmarkExporter
import java.io.InputStream
import javax.inject.Inject

/**
 * A [BookmarkImporter] that imports bookmark files that were produced by [BookmarkExporter].
 */
class LegacyBookmarkImporter @Inject constructor() :
    BookmarkImporter {

    override fun importBookmarks(inputStream: InputStream): List<Bookmark.Entry> {
        return BookmarkExporter.importBookmarksFromFileStream(inputStream)
    }

}
