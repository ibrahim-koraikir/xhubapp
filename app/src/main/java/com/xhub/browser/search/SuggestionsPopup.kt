package com.xhub.browser.search

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xhub.browser.R
import com.xhub.browser.database.WebPage

class SuggestionsPopup(
    private val context: Context,
    private val adapter: SuggestionsAdapter,
    private val anchorView: View
) {
    private val popupWindow: PopupWindow
    private val recyclerView: RecyclerView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.suggestions_recycler_view, null)
        recyclerView = view.findViewById(R.id.suggestions_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        popupWindow = PopupWindow(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = false // Don't steal focus from the address bar
    }

    fun show() {
        if (!popupWindow.isShowing) {
            popupWindow.showAsDropDown(anchorView)
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    fun isShowing(): Boolean = popupWindow.isShowing

    fun updateHeight(height: Int) {
        popupWindow.height = height
    }
}
