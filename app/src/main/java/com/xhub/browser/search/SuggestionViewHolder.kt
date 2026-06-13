package com.xhub.browser.search

import com.xhub.browser.R
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import io.reactivex.disposables.CompositeDisposable

class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val imageView: ImageView = view.findViewById(R.id.suggestionIcon)
    val titleView: TextView = view.findViewById(R.id.title)
    val urlView: TextView = view.findViewById(R.id.url)
    val insertSuggestion: View = view.findViewById(R.id.complete_search)
    val disposables = CompositeDisposable()
}
