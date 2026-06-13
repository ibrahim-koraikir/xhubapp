package com.xhub.browser.browser

import com.xhub.browser.database.Bookmark

interface BookmarksView {

    fun navigateBack()

    fun handleUpdatedUrl(url: String)

    fun handleBookmarkDeleted(bookmark: Bookmark)

}
