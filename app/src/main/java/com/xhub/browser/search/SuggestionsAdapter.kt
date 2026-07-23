package com.xhub.browser.search

import com.xhub.browser.R
import com.xhub.browser.database.Bookmark
import com.xhub.browser.database.HistoryEntry
import com.xhub.browser.database.SearchSuggestion
import com.xhub.browser.database.WebPage
import com.xhub.browser.extensions.drawable
import com.xhub.browser.search.suggestions.NoOpSuggestionsRepository
import com.xhub.browser.search.suggestions.SuggestionsRepository
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import dagger.hilt.android.EntryPointAccessors
import com.xhub.browser.di.HiltEntryPoint
import com.xhub.browser.di.configPrefs
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import java.util.*


import androidx.recyclerview.widget.RecyclerView
import io.reactivex.disposables.CompositeDisposable

class SuggestionsAdapter(
    context: Context,
    private val isIncognito: Boolean
) : RecyclerView.Adapter<SuggestionViewHolder>(), Filterable {

    private var filteredList: List<WebPage> = emptyList()

    private val hiltEntryPoint = EntryPointAccessors.fromApplication(context.applicationContext, HiltEntryPoint::class.java)

    val bookmarkRepository = hiltEntryPoint.bookmarkRepository
    val userPreferences = hiltEntryPoint.userPreferences
    val historyRepository = hiltEntryPoint.historyRepository
    val databaseScheduler = hiltEntryPoint.databaseScheduler()
    val networkScheduler = hiltEntryPoint.networkScheduler()
    val mainScheduler = hiltEntryPoint.mainScheduler()
    val searchEngineProvider = hiltEntryPoint.searchEngineProvider
    val faviconModel = hiltEntryPoint.faviconModel

    private var allBookmarks: List<Bookmark.Entry> = emptyList()
    private val searchFilter = SearchFilter(this)

    private val searchIcon = context.drawable(R.drawable.ic_find)
    private val webPageIcon = context.drawable(R.drawable.round_history_24)
    private val bookmarkIcon = context.drawable(R.drawable.round_star_border_24)
    private var suggestionsRepository: SuggestionsRepository

    val iContext: Context = context;

    /**
     * The listener that is fired when the insert button on a [SearchSuggestion] is clicked.
     */
    var onSuggestionInsertClick: ((WebPage) -> Unit)? = null

    /**
     * The listener that is fired when a suggestion is clicked.
     */
    var onSuggestionClick: ((WebPage) -> Unit)? = null

    private val onInsertClick = View.OnClickListener {
        onSuggestionInsertClick?.invoke(it.tag as WebPage)
    }

    private val onItemClick = View.OnClickListener {
        onSuggestionClick?.invoke(it.tag as WebPage)
    }

    private val layoutInflater = LayoutInflater.from(context)

    init {
        suggestionsRepository = if (isIncognito) {
            NoOpSuggestionsRepository()
        } else {
            searchEngineProvider.provideSearchSuggestions()
        }

        refreshBookmarks()

        searchFilter.input().results()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe(::publishResults)
    }

    fun refreshPreferences() {
        suggestionsRepository = if (isIncognito) {
            NoOpSuggestionsRepository()
        } else {
            searchEngineProvider.provideSearchSuggestions()
        }
    }

    fun refreshBookmarks() {
        bookmarkRepository.getAllBookmarksSorted()
            .subscribeOn(databaseScheduler)
            .subscribe { list ->
                allBookmarks = list
            }
    }

    override fun getItemCount(): Int = filteredList.size

    fun getItem(position: Int): WebPage? {
        if (position >= filteredList.size || position < 0) {
            return null
        }
        return filteredList[position]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = layoutInflater.inflate(R.layout.two_line_autocomplete, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val webPage: WebPage = filteredList[position]

        holder.titleView.text = webPage.title
        holder.urlView.text = webPage.url

        val image = when (webPage) {
            is Bookmark -> bookmarkIcon
            is SearchSuggestion -> searchIcon
            is HistoryEntry -> webPageIcon
        }

        holder.imageView.setImageDrawable(image)

        // Clear previous subscriptions to avoid leaks and incorrect updates
        holder.disposables.clear()

        // Load rich favicon for URL-based suggestions (History and Bookmarks)
        if (webPage is Bookmark || webPage is HistoryEntry) {
            val useDark = (iContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            holder.disposables.add(
                faviconModel.realFaviconForUrl(webPage.url, useDark, isIncognito)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe(
                        { bitmap ->
                            // View recycling safety check: Ensure the view is still showing the same URL
                            if (holder.urlView.text == webPage.url) {
                                holder.imageView.setImageBitmap(bitmap)
                            }
                        },
                        { error ->
                            Timber.e(error, "Failed to load rich suggestion favicon")
                        }
                    )
            )
        }

        holder.insertSuggestion.tag = webPage
        holder.insertSuggestion.setOnClickListener(onInsertClick)

        holder.itemView.tag = webPage
        holder.itemView.setOnClickListener(onItemClick)
    }

    override fun onViewRecycled(holder: SuggestionViewHolder) {
        super.onViewRecycled(holder)
        // Clear subscriptions when the view is recycled to prevent memory leaks
        holder.disposables.clear()
    }

    override fun getFilter(): Filter = searchFilter

    var onListUpdated: (() -> Unit)? = null

    private fun publishResults(list: List<WebPage>?) {
        if (list == null) {
            filteredList = emptyList()
            notifyDataSetChanged()
            onListUpdated?.invoke()
            return
        }
        if (list != filteredList) {
            filteredList = list
            notifyDataSetChanged()
            onListUpdated?.invoke()
        }
    }

    private fun getBookmarksForQuery(query: String): Single<List<Bookmark.Entry>> =
        Single.fromCallable {
            val choice: Int = userPreferences.suggestionChoice.value + 2

            (allBookmarks.filter {
                it.title.lowercase(Locale.getDefault()).startsWith(query)
            } + allBookmarks.filter {
                it.url.contains(query)
            }).distinct().take(choice)
        }

    private fun Observable<CharSequence>.results(): Flowable<List<WebPage>> = this
        .toFlowable(BackpressureStrategy.LATEST)
        .map { it.toString().lowercase(Locale.getDefault()).trim() }
        .share()
        .compose { upstream ->
            val searchEntries = upstream
                .flatMapSingle { query ->
                    if (query.isBlank() || query.startsWith("xhub://") || query.startsWith("about:")) {
                        Single.just(emptyList<SearchSuggestion>())
                    } else {
                        suggestionsRepository.resultsForSearch(query)
                    }
                }
                .subscribeOn(networkScheduler)
                .startWith(emptyList<List<SearchSuggestion>>())
                .share()

            val bookmarksEntries = upstream
                .flatMapSingle { query ->
                    if (query.isBlank() || query.startsWith("xhub://") || query.startsWith("about:")) {
                        Single.just(allBookmarks.take(3))
                    } else {
                        getBookmarksForQuery(query)
                    }
                }
                .subscribeOn(databaseScheduler)
                .startWith(emptyList<List<Bookmark.Entry>>())
                .share()

            val historyEntries = upstream
                .flatMapSingle { query ->
                    if (query.isBlank() || query.startsWith("xhub://") || query.startsWith("about:")) {
                        historyRepository.lastHundredVisitedHistoryEntries().map { it.take(5) }
                    } else {
                        historyRepository.findHistoryEntriesContaining(query)
                    }
                }
                .subscribeOn(databaseScheduler)
                .startWith(emptyList<HistoryEntry>())
                .share()

            bookmarksEntries
                .join(
                    historyEntries,
                    { bookmarksEntries },
                    { historyEntries }
                ) { t1, t2 -> Pair(t1, t2) }
                .compose { bookmarksAndHistory ->
                    bookmarksAndHistory.join(
                        searchEntries,
                        { bookmarksAndHistory },
                        { searchEntries }
                    ) { (bookmarks, history), t2 ->
                        Triple(bookmarks, history, t2)
                    }
                }
        }
        .map { (bookmarks, history, searches) ->
            val choice: Int = userPreferences.suggestionChoice.value + 2
            val bookmarkCount = choice - 2.coerceAtMost(history.size) - 1.coerceAtMost(searches.size)
            val historyCount = choice - bookmarkCount.coerceAtMost(bookmarks.size) - 1.coerceAtMost(searches.size)
            val searchCount = choice - bookmarkCount.coerceAtMost(bookmarks.size) - historyCount.coerceAtMost(history.size)
            val results = bookmarks.take(bookmarkCount) + history.take(historyCount) + searches.take(searchCount)
            // Reverse results if needed
            if (iContext.configPrefs.toolbarsBottom) results.reversed() else results
        }

    private class SearchFilter(
        private val suggestionsAdapter: SuggestionsAdapter
    ) : Filter() {

        private val publishSubject = PublishSubject.create<CharSequence>()

        fun input(): Observable<CharSequence> = publishSubject.hide()

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            Timber.v("performFiltering: $constraint")
            val cleanQuery = constraint?.toString()?.trim() ?: ""
            publishSubject.onNext(cleanQuery)
            return FilterResults().apply { count = 1 }
        }

        override fun convertResultToString(resultValue: Any) = (resultValue as WebPage).url

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            Timber.v("publishResults")
            // results are published via our Rx pipeline
        }
    }
}
