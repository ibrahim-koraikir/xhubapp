package com.xhub.browser.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import com.xhub.browser.R
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.database.SearchSuggestion
import com.xhub.browser.database.history.HistoryRepository
import com.xhub.browser.search.SearchEngineProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AndroidEntryPoint
class AskOverlayFragment : BottomSheetDialogFragment() {

    @Inject lateinit var searchEngineProvider: SearchEngineProvider
    @Inject lateinit var historyRepository: HistoryRepository

    private val disposables = CompositeDisposable()
    private lateinit var suggestionsAdapter: AskSuggestionsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_ask_overlay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val suggestionsList = view.findViewById<RecyclerView>(R.id.suggestions_list)
        suggestionsAdapter = AskSuggestionsAdapter(
            onQuerySelect = { query ->
                (activity as? WebBrowserActivity)?.searchTheWeb(query)
                dismiss()
            },
            onPerplexitySelect = { query ->
                (activity as? WebBrowserActivity)?.searchTheWeb("https://www.perplexity.ai/search?q=${android.net.Uri.encode(query)}")
                dismiss()
            },
            onFillSelect = { query ->
                val input = view.findViewById<EditText>(R.id.search_input_ask)
                input.setText(query)
                input.setSelection(query.length)
            }
        )
        suggestionsList.layoutManager = LinearLayoutManager(context)
        suggestionsList.adapter = suggestionsAdapter

        val searchInput = view.findViewById<EditText>(R.id.search_input_ask)
        val submitButton = view.findViewById<ImageButton>(R.id.submit_btn_ask)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                fetchSuggestions(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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

        submitButton.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotBlank()) {
                (activity as? WebBrowserActivity)?.searchTheWeb(query)
                dismiss()
            }
        }

        // Initial load
        fetchSuggestions("")

        // Auto-focus
        searchInput.requestFocus()
        searchInput.postDelayed({
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun fetchSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            // Load history if empty
            disposables.add(
                historyRepository.lastHundredVisitedHistoryEntries()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ history ->
                        val historyTitles = history.map { it.title }.filter { it.isNotBlank() }.distinct().take(8)
                        suggestionsAdapter.submitList(trimmed, historyTitles)
                    }, {
                        suggestionsAdapter.submitList(trimmed, emptyList())
                    })
            )
        } else {
            val repository = searchEngineProvider.provideSearchSuggestions()
            disposables.add(
                repository.resultsForSearch(trimmed)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ suggestions ->
                        val titles = suggestions.map { it.title }.take(8)
                        suggestionsAdapter.submitList(trimmed, titles)
                    }, {
                        suggestionsAdapter.submitList(trimmed, emptyList())
                    })
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposables.clear()
    }
}

class AskSuggestionsAdapter(
    private val onQuerySelect: (String) -> Unit,
    private val onPerplexitySelect: (String) -> Unit,
    private val onFillSelect: (String) -> Unit
) : RecyclerView.Adapter<AskSuggestionsAdapter.ViewHolder>() {

    private var currentQuery: String = ""
    private var suggestions: List<String> = emptyList()

    fun submitList(query: String, list: List<String>) {
        currentQuery = query
        suggestions = list
        notifyDataSetChanged()
    }

    // Number of items: if query is empty, just suggestions. If not empty, query + query + suggestions
    override fun getItemCount(): Int {
        return if (currentQuery.isEmpty()) suggestions.size else 2 + suggestions.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion_ask, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (currentQuery.isNotEmpty()) {
            when (position) {
                0 -> {
                    holder.title.text = currentQuery
                    holder.textAction.visibility = View.VISIBLE
                    holder.textAction.text = "Search Google ->"
                    holder.iconAction.visibility = View.GONE
                    holder.itemView.setOnClickListener { onQuerySelect(currentQuery) }
                }
                1 -> {
                    holder.title.text = currentQuery
                    holder.textAction.visibility = View.VISIBLE
                    holder.textAction.text = "Ask Perplexity ->"
                    holder.iconAction.visibility = View.GONE
                    holder.itemView.setOnClickListener { onPerplexitySelect(currentQuery) }
                }
                else -> {
                    val suggestion = suggestions[position - 2]
                    holder.title.text = suggestion
                    holder.textAction.visibility = View.GONE
                    holder.iconAction.visibility = View.VISIBLE
                    holder.itemView.setOnClickListener { onQuerySelect(suggestion) }
                    holder.iconAction.setOnClickListener { onFillSelect(suggestion) }
                }
            }
        } else {
            val suggestion = suggestions[position]
            holder.title.text = suggestion
            holder.textAction.visibility = View.GONE
            holder.iconAction.visibility = View.VISIBLE
            holder.itemView.setOnClickListener { onQuerySelect(suggestion) }
            holder.iconAction.setOnClickListener { onFillSelect(suggestion) }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val textAction: TextView = view.findViewById(R.id.textAction)
        val iconAction: ImageView = view.findViewById(R.id.complete_search)
    }
}
