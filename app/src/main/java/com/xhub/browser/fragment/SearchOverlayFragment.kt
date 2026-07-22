package com.xhub.browser.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xhub.browser.R
import com.xhub.browser.database.HistoryEntry
import com.xhub.browser.database.history.HistoryRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import com.xhub.browser.activity.WebBrowserActivity
import android.widget.EditText
import android.widget.ImageButton
import android.view.inputmethod.EditorInfo

@AndroidEntryPoint
class SearchOverlayFragment : BottomSheetDialogFragment() {

    @Inject lateinit var historyRepository: HistoryRepository
    private val disposables = CompositeDisposable()
    private lateinit var recentSearchAdapter: RecentSearchAdapter

    // Launcher for the system voice-recognition activity. Replaces the deprecated
    // activity?.startActivityForResult(intent, 10101) delegation to WebBrowserActivity: the spoken
    // query is now handled here in the fragment's own result callback, then the overlay dismisses
    // itself. This removes the fragile shared-request-code coupling with WebBrowserActivity.
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recentList = view.findViewById<RecyclerView>(R.id.recent_list)
        recentSearchAdapter = RecentSearchAdapter { query ->
            (activity as? WebBrowserActivity)?.searchTheWeb(query)
            dismiss()
        }
        recentList.layoutManager = LinearLayoutManager(context)
        recentList.adapter = recentSearchAdapter

        val searchInput = view.findViewById<EditText>(R.id.search_input_expanded)
        val sendButton = view.findViewById<ImageButton>(R.id.wave_btn_expanded)
        val micButton = view.findViewById<ImageButton>(R.id.mic_btn_expanded)

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || 
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                val query = searchInput.text.toString()
                if (query.isNotBlank()) {
                    (activity as? WebBrowserActivity)?.searchTheWeb(query)
                    dismiss()
                }
                true
            } else {
                false
            }
        }

        sendButton.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotBlank()) {
                (activity as? WebBrowserActivity)?.searchTheWeb(query)
                dismiss()
            }
        }

        micButton.setOnClickListener {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_hint))
            }
            try {
                voiceSearchLauncher.launch(intent)
            } catch (e: Exception) {
                // Ignore if voice recognition is not available
            }
        }

        loadRecentSearches()
        setupSiteSuggestions(view.findViewById(R.id.suggestions_grid))

        // Auto-focus the search input and show the system keyboard
        searchInput.requestFocus()
        searchInput.postDelayed({
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun loadRecentSearches() {
        disposables.add(
            historyRepository.lastHundredVisitedHistoryEntries()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ history ->
                    recentSearchAdapter.submitList(history.take(4)) // Just show top 4 for now
                }, {
                    // Handle error
                })
        )
    }

    private fun setupSiteSuggestions(grid: GridLayout) {
        val sites = listOf(
            SiteSuggestion("G",  "Google",     "#4285F4", "https://www.google.com"),
            SiteSuggestion("Y",  "YouTube",    "#FF0000", "https://www.youtube.com"),
            SiteSuggestion("X",  "X (Twitter)","#1A1A1A", "https://www.x.com"),
            SiteSuggestion("R",  "Reddit",     "#FF4500", "https://www.reddit.com"),
            SiteSuggestion("W",  "Wikipedia",  "#3366CC", "https://www.wikipedia.org"),
            SiteSuggestion("A",  "Amazon",     "#FF9900", "https://www.amazon.com")
        )

        sites.forEach { site ->
            val itemView = layoutInflater.inflate(R.layout.site_suggestion_item, grid, false)
            val logo = itemView.findViewById<TextView>(R.id.site_logo)
            val nameLabel = itemView.findViewById<TextView>(R.id.site_name)

            logo.text = site.logo
            nameLabel.text = site.name

            // Use mutate() so each tile gets its own independent drawable copy
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(site.color))
            }
            logo.background = bg

            itemView.setOnClickListener {
                // Use searchTheWeb — smartUrlFilter correctly detects https:// URLs
                (activity as? WebBrowserActivity)?.searchTheWeb(site.url)
                dismiss()
            }

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            itemView.layoutParams = lp
            grid.addView(itemView)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        disposables.clear()
    }

    private data class SiteSuggestion(val logo: String, val name: String, val color: String, val url: String)
}

class RecentSearchAdapter(private val onSearch: (String) -> Unit) : RecyclerView.Adapter<RecentSearchAdapter.ViewHolder>() {
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
        holder.titleView.text = item.title
        holder.itemView.setOnClickListener { onSearch(item.url) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.recent_text)
    }
}
