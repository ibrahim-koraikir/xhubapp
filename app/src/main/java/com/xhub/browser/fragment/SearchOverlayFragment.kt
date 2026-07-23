package com.xhub.browser.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.database.Bookmark
import com.xhub.browser.database.HistoryEntry
import com.xhub.browser.database.SearchSuggestion
import com.xhub.browser.database.WebPage
import com.xhub.browser.database.history.HistoryRepository
import com.xhub.browser.search.SuggestionsAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

@AndroidEntryPoint
class SearchOverlayFragment : BottomSheetDialogFragment() {

    @Inject lateinit var historyRepository: HistoryRepository
    private val disposables = CompositeDisposable()
    private lateinit var recentSearchAdapter: RecentSearchAdapter
    private var liveSuggestionsAdapter: SuggestionsAdapter? = null
    private var liveSuggestionsList: RecyclerView? = null
    private var suggestionsSection: View? = null
    private var defaultSection: View? = null
    private var searchInput: EditText? = null

    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val topResult = matches?.firstOrNull()
            if (!topResult.isNullOrEmpty()) {
                (activity as? WebBrowserActivity)?.searchTheWeb(topResult)
            }
        }
        dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.search_overlay, container, false)
    }

    override fun onStart() {
        super.onStart()
        // Expand the bottom sheet fully
        val dialog = dialog as? BottomSheetDialog
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            it.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        suggestionsSection = view.findViewById(R.id.suggestions_section)
        defaultSection = view.findViewById(R.id.default_section)
        liveSuggestionsList = view.findViewById(R.id.live_suggestions_list)
        searchInput = view.findViewById(R.id.search_input_expanded)
        val sendButton = view.findViewById<ImageButton>(R.id.wave_btn_expanded)
        val micButton = view.findViewById<ImageButton>(R.id.mic_btn_expanded)
        val recentList = view.findViewById<RecyclerView>(R.id.recent_list)

        // --- Recent searches adapter ---
        recentSearchAdapter = RecentSearchAdapter(
            onSearch = { query ->
                navigateTo(query)
            },
            onInsert = { text ->
                searchInput?.setText(text)
                searchInput?.setSelection(text.length)
            }
        )
        recentList.layoutManager = LinearLayoutManager(context)
        recentList.adapter = recentSearchAdapter

        // --- Live suggestions adapter ---
        liveSuggestionsAdapter = SuggestionsAdapter(requireContext(), false).also { adapter ->
            adapter.onSuggestionClick = { webPage ->
                val url = when (webPage) {
                    is HistoryEntry, is Bookmark.Entry -> webPage.url
                    is SearchSuggestion -> webPage.title
                    else -> null
                }
                url?.let { navigateTo(it) }
            }
            adapter.onSuggestionInsertClick = { webPage ->
                val text = if (webPage is SearchSuggestion) webPage.title else webPage.url
                searchInput?.setText(text)
                searchInput?.setSelection(text.length)
            }
        }

        liveSuggestionsList?.layoutManager = LinearLayoutManager(context)
        liveSuggestionsList?.adapter = liveSuggestionsAdapter

        // --- Search bar ---
        searchInput?.setOnEditorActionListener { _, _, _ ->
            val q = searchInput?.text?.toString() ?: ""
            if (q.isNotBlank()) navigateTo(q)
            true
        }

        sendButton?.setOnClickListener {
            val q = searchInput?.text?.toString() ?: ""
            if (q.isNotBlank()) navigateTo(q)
        }

        micButton?.setOnClickListener {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search…")
            }
            try { voiceSearchLauncher.launch(intent) } catch (_: Exception) {}
        }

        // --- Live filtering as user types ---
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString() ?: ""
                if (q.isBlank()) {
                    // Show default: site shortcuts + recent searches
                    suggestionsSection?.visibility = View.GONE
                    defaultSection?.visibility = View.VISIBLE
                } else {
                    // Show live suggestions
                    suggestionsSection?.visibility = View.VISIBLE
                    defaultSection?.visibility = View.GONE
                    liveSuggestionsAdapter?.filter?.filter(q)
                }
            }
        })

        // --- Site shortcuts ---
        setupSiteCircles(view.findViewById(R.id.sites_row))

        // --- Load recent history ---
        loadRecentSearches()

        // --- Focus and keyboard ---
        searchInput?.requestFocus()
        searchInput?.postDelayed({
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun navigateTo(query: String) {
        (activity as? WebBrowserActivity)?.searchTheWeb(query)
        dismiss()
    }

    private fun loadRecentSearches() {
        disposables.add(
            historyRepository.lastHundredVisitedHistoryEntries()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ history ->
                    recentSearchAdapter.submitList(history.take(6))
                }, {})
        )
    }

    private fun setupSiteCircles(container: LinearLayout?) {
        container ?: return

        val sites = listOf(
            Triple("G",  "Google",      android.graphics.Color.parseColor("#4285F4")),
            Triple("Y",  "YouTube",     android.graphics.Color.parseColor("#FF0000")),
            Triple("X",  "X",           android.graphics.Color.parseColor("#222222")),
            Triple("W",  "Wikipedia",   android.graphics.Color.parseColor("#3366CC")),
            Triple("R",  "Reddit",      android.graphics.Color.parseColor("#FF4500")),
            Triple("A",  "Amazon",      android.graphics.Color.parseColor("#FF9900"))
        )
        val urls = listOf(
            "https://www.google.com",
            "https://www.youtube.com",
            "https://www.x.com",
            "https://www.wikipedia.org",
            "https://www.reddit.com",
            "https://www.amazon.com"
        )

        val density = resources.displayMetrics.density
        val circleDp = (60 * density).toInt()
        val marginDp = (8 * density).toInt()

        sites.forEachIndexed { i, (letter, name, color) ->
            val item = layoutInflater.inflate(R.layout.site_suggestion_item, container, false)

            val iconContainer = item.findViewById<android.widget.FrameLayout>(R.id.site_icon_container)
            val logoView = item.findViewById<TextView>(R.id.site_logo)
            val nameView = item.findViewById<TextView>(R.id.site_name)

            // Draw a perfect circle with the brand color
            val circle = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
            }
            iconContainer.background = circle

            // Size the container
            val lp = iconContainer.layoutParams
            lp.width = circleDp
            lp.height = circleDp
            iconContainer.layoutParams = lp

            logoView.text = letter
            nameView.text = name

            // Margin between items
            val itemLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            itemLp.marginEnd = marginDp
            item.layoutParams = itemLp

            item.setOnClickListener {
                navigateTo(urls[i])
            }

            container.addView(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        liveSuggestionsAdapter?.onListUpdated = null
        disposables.clear()
    }
}

class RecentSearchAdapter(
    private val onSearch: (String) -> Unit,
    private val onInsert: (String) -> Unit = {}
) : RecyclerView.Adapter<RecentSearchAdapter.ViewHolder>() {

    private var items = listOf<HistoryEntry>()

    fun submitList(newList: List<HistoryEntry>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recent_search_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        // Show title if available and meaningful, otherwise show URL
        val display = if (!item.title.isNullOrBlank() &&
            !item.title.startsWith("http") &&
            item.title.length > 2) item.title else item.url
        holder.titleView.text = display
        holder.itemView.setOnClickListener { onSearch(item.url) }
        holder.insertView?.setOnClickListener { onInsert(display) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.recent_text)
        val insertView: View? = view.findViewById(R.id.recent_insert)
    }
}
