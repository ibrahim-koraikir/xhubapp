package com.xhub.browser.di

import com.xhub.browser.browser.cleanup.DelegatingExitCleanup
import com.xhub.browser.browser.cleanup.ExitCleanup
import com.xhub.browser.database.adblock.UserRulesDatabase
import com.xhub.browser.database.adblock.UserRulesRepository
import com.xhub.browser.database.bookmark.BookmarkDatabase
import com.xhub.browser.database.bookmark.BookmarkRepository
import com.xhub.browser.database.downloads.DownloadsDatabase
import com.xhub.browser.database.downloads.DownloadsRepository
import com.xhub.browser.database.history.HistoryDatabase
import com.xhub.browser.database.history.HistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dependency injection module used to bind implementations to interfaces.
 * SL: Looks like those are still actually needed.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AppBindsModule {

    @Binds
    fun bindsExitCleanup(delegatingExitCleanup: DelegatingExitCleanup): ExitCleanup

    @Binds
    fun bindsBookmarkModel(bookmarkDatabase: BookmarkDatabase): BookmarkRepository

    @Binds
    fun bindsDownloadsModel(downloadsDatabase: DownloadsDatabase): DownloadsRepository

    @Binds
    fun bindsHistoryModel(historyDatabase: HistoryDatabase): HistoryRepository

    @Binds
    fun bindsAbpRulesRepository(apbRulesDatabase: UserRulesDatabase): UserRulesRepository

}
