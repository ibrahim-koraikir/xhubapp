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

        popupWindow = PopupWindow(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, false)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = false // Don't steal focus from the address bar

        adapter.onListUpdated = {
            if (adapter.itemCount > 0) {
                show()
            } else {
                dismiss()
            }
        }
    }

    fun show() {
        if (!anchorView.isAttachedToWindow) return
        if (adapter.itemCount == 0) {
            dismiss()
            return
        }

        anchorView.post {
            val loc = IntArray(2)
            anchorView.getLocationOnScreen(loc)
            val screenHeight = anchorView.resources.displayMetrics.heightPixels
            val yFromBottom = screenHeight - loc[1]

            // Calculate height based on item count (max 5 items, each ~56dp + padding)
            val density = context.resources.displayMetrics.density
            val itemHeight = (56 * density).toInt()
            val maxItemsHeight = (adapter.itemCount.coerceAtMost(5) * itemHeight) + (16 * density).toInt()
            val maxHeight = (screenHeight * 0.45).toInt().coerceAtMost(maxItemsHeight)

            popupWindow.height = maxHeight

            if (!popupWindow.isShowing) {
                try {
                    popupWindow.showAtLocation(anchorView, Gravity.BOTTOM, 0, yFromBottom)
                } catch (e: Exception) {
                    // Ignored if window token is invalid
                }
            } else {
                try {
                    popupWindow.update(0, yFromBottom, ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            try {
                popupWindow.dismiss()
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    fun isShowing(): Boolean = popupWindow.isShowing

    fun updateHeight(height: Int) {
        popupWindow.height = height
    }
}
