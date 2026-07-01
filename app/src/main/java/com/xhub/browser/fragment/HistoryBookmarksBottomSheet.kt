package com.xhub.browser.fragment

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.database.Bookmark
import com.xhub.browser.database.HistoryEntry
import com.xhub.browser.database.bookmark.BookmarkRepository
import com.xhub.browser.database.history.HistoryRepository
import com.xhub.browser.di.DatabaseScheduler
import com.xhub.browser.di.MainScheduler
import com.xhub.browser.extensions.removeFromParent
import com.xhub.browser.utils.WebUtils
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class HistoryBookmarksBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var historyRepository: HistoryRepository
    @Inject lateinit var bookmarkRepository: BookmarkRepository
    @Inject @DatabaseScheduler lateinit var databaseScheduler: Scheduler
    @Inject @MainScheduler lateinit var mainScheduler: Scheduler

    private enum class Mode { HISTORY, BOOKMARKS }
    private var currentMode = Mode.HISTORY

    private val disposables = CompositeDisposable()
    private val adapter = HistoryBookmarksAdapter { url ->
        (activity as? WebBrowserActivity)?.tabsManager?.loadUrlInCurrentView(url)
        dismiss()
    }

    private lateinit var searchEditText: EditText
    private lateinit var tabHistory: ViewGroup
    private lateinit var tabBookmarks: ViewGroup
    private lateinit var historyIcon: ImageView
    private lateinit var bookmarkIcon: ImageView
    private lateinit var historyText: TextView
    private lateinit var bookmarkText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history_bookmarks, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                it.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchEditText = view.findViewById(R.id.search_edit_text)
        tabHistory = view.findViewById(R.id.tab_history)
        tabBookmarks = view.findViewById(R.id.tab_bookmarks)
        
        historyIcon = tabHistory.getChildAt(0) as ImageView
        historyText = tabHistory.getChildAt(1) as TextView
        
        bookmarkIcon = tabBookmarks.getChildAt(0) as ImageView
        bookmarkText = tabBookmarks.getChildAt(1) as TextView

        val recyclerView = view.findViewById<RecyclerView>(R.id.list_content)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        tabHistory.setOnClickListener {
            if (currentMode != Mode.HISTORY) {
                currentMode = Mode.HISTORY
                updateTabUI()
                loadData()
            }
        }

        tabBookmarks.setOnClickListener {
            if (currentMode != Mode.BOOKMARKS) {
                currentMode = Mode.BOOKMARKS
                updateTabUI()
                loadData()
            }
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadData(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<View>(R.id.clear_btn).setOnClickListener {
            if (currentMode == Mode.HISTORY) {
                WebUtils.clearHistory(requireContext(), historyRepository, databaseScheduler)
                loadData()
            } else {
                bookmarkRepository.deleteAllBookmarks()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy(onComplete = { loadData() })
            }
        }

        updateTabUI()
        loadData()
    }

    private fun updateTabUI() {
        val inactiveColor = com.google.android.material.color.MaterialColors.getColor(requireContext(), android.R.attr.textColorSecondary, Color.GRAY)

        if (currentMode == Mode.HISTORY) {
            tabHistory.setBackgroundResource(R.drawable.bg_hb_tab_pill_active)
            historyIcon.setColorFilter(Color.WHITE)
            historyText.setTextColor(Color.WHITE)
            
            tabBookmarks.setBackgroundResource(R.drawable.bg_hb_tab_pill_inactive)
            bookmarkIcon.setColorFilter(inactiveColor)
            bookmarkText.setTextColor(inactiveColor)
            
            searchEditText.hint = "Search history"
        } else {
            tabBookmarks.setBackgroundResource(R.drawable.bg_hb_tab_pill_active)
            bookmarkIcon.setColorFilter(Color.WHITE)
            bookmarkText.setTextColor(Color.WHITE)
            
            tabHistory.setBackgroundResource(R.drawable.bg_hb_tab_pill_inactive)
            historyIcon.setColorFilter(inactiveColor)
            historyText.setTextColor(inactiveColor)
            
            searchEditText.hint = "Search bookmarks"
        }
    }

    private fun loadData(query: String = "") {
        disposables.clear()
        if (currentMode == Mode.HISTORY) {
            val observable = if (query.isEmpty()) {
                historyRepository.lastHundredVisitedHistoryEntries()
            } else {
                historyRepository.findHistoryEntriesContaining(query)
            }
            
            disposables.add(observable
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribeBy(onSuccess = { entries ->
                    adapter.items = groupHistoryByDate(entries)
                }))
        } else {
            disposables.add(bookmarkRepository.getAllBookmarksSorted()
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribeBy(onSuccess = { entries ->
                    val filtered = if (query.isEmpty()) entries else entries.filter { 
                        it.title.contains(query, true) || it.url.contains(query, true) 
                    }
                    adapter.items = filtered.map { it.toHbItem() }
                }))
        }
    }

    private fun groupHistoryByDate(entries: List<HistoryEntry>): List<HbItem> {
        val result = mutableListOf<HbItem>()
        val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
        
        var currentHeader = ""
        
        entries.forEach { entry ->
            val date = Calendar.getInstance().apply { timeInMillis = entry.lastTimeVisited }
            val headerText = when {
                isSameDay(date, today) -> "Today"
                isSameDay(date, yesterday) -> "Yesterday"
                else -> sdf.format(date.time)
            }
            
            if (headerText != currentHeader) {
                result.add(HbItem.Header(headerText))
                currentHeader = headerText
            }
            result.add(entry.toHbItem())
        }
        return result
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun HistoryEntry.toHbItem() = HbItem.Entry(
        title = if (title.isEmpty()) url else title,
        url = url,
        time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastTimeVisited)),
        iconRes = when {
            url.contains("google.com") -> 0
            else -> R.drawable.ic_history
        },
        isGoogle = url.contains("google.com"),
        isFaded = false
    )

    private fun Bookmark.Entry.toHbItem() = HbItem.Entry(
        title = title,
        url = url,
        time = "",
        iconRes = R.drawable.ic_bookmark,
        isGoogle = false,
        isFaded = false
    )

    override fun onDestroyView() {
        super.onDestroyView()
        disposables.clear()
    }
}

sealed class HbItem {
    data class Header(val date: String) : HbItem()
    data class Entry(
        val title: String,
        val url: String,
        val time: String,
        val iconRes: Int,
        val isGoogle: Boolean,
        val isFaded: Boolean
    ) : HbItem()
}

class HistoryBookmarksAdapter(private val onItemClick: (String) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items: List<HbItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HbItem.Header -> TYPE_HEADER
            is HbItem.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_hb_header, parent, false))
        } else {
            EntryViewHolder(inflater.inflate(R.layout.item_hb_entry, parent, false), onItemClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HbItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HbItem.Entry -> (holder as EntryViewHolder).bind(item)
        }
    }

    override fun getItemCount() = items.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textHeader: TextView = view.findViewById(R.id.text_header)
        fun bind(item: HbItem.Header) {
            textHeader.text = item.date
        }
    }

    class EntryViewHolder(view: View, private val onItemClick: (String) -> Unit) : RecyclerView.ViewHolder(view) {
        private val container: View = view.findViewById(R.id.item_container)
        private val iconContainer: View = view.findViewById(R.id.icon_container)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)
        private val iconText: TextView = view.findViewById(R.id.item_icon_text)
        private val textTitle: TextView = view.findViewById(R.id.item_title)
        private val textUrl: TextView = view.findViewById(R.id.item_url)
        private val textTime: TextView = view.findViewById(R.id.item_time)
        private val divider: View = view.findViewById(R.id.item_divider)

        fun bind(item: HbItem.Entry) {
            textTitle.text = item.title
            textUrl.text = item.url
            textTime.text = item.time
            textTime.visibility = if (item.time.isEmpty()) View.GONE else View.VISIBLE

            container.setOnClickListener { onItemClick(item.url) }

            // Set icon background color
            val bgDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.bg_hb_icon_circle)?.mutate() as? GradientDrawable
            if (bgDrawable != null) {
                val iconBgColor = if (item.isGoogle) {
                    Color.WHITE
                } else {
                    com.google.android.material.color.MaterialColors.getColor(
                        itemView.context,
                        com.google.android.material.R.attr.colorSurfaceVariant,
                        Color.DKGRAY
                    )
                }
                bgDrawable.setColor(iconBgColor)
                iconContainer.background = bgDrawable
            }

            if (item.isGoogle) {
                iconView.visibility = View.GONE
                iconText.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.VISIBLE
                iconText.visibility = View.GONE
                iconView.setImageResource(item.iconRes)
            }

            if (item.isFaded) {
                container.alpha = 0.5f
            } else {
                container.alpha = 1.0f
            }
            
            // Hide divider if it's the last item in a group or the list
            val adapter = bindingAdapter as? HistoryBookmarksAdapter
            if (adapter != null) {
                val isLast = absoluteAdapterPosition == adapter.itemCount - 1
                val isNextHeader = !isLast && adapter.items[absoluteAdapterPosition + 1] is HbItem.Header
                if (isLast || isNextHeader) {
                    divider.visibility = View.GONE
                } else {
                    divider.visibility = View.VISIBLE
                }
            }
        }
    }
}
