package com.xhub.browser.search

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xhub.browser.R

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
        if (anchorView.isAttachedToWindow) {
            anchorView.post {
                val loc = IntArray(2)
                anchorView.getLocationOnScreen(loc)
                val screenHeight = anchorView.resources.displayMetrics.heightPixels
                val yFromBottom = screenHeight - loc[1]
                if (!popupWindow.isShowing) {
                    popupWindow.showAtLocation(anchorView, Gravity.BOTTOM, 0, yFromBottom)
                } else {
                    popupWindow.update(0, yFromBottom, popupWindow.width, popupWindow.height)
                }
            }
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
