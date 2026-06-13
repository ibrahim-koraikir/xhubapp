package com.xhub.browser.list

import com.xhub.browser.R
import com.xhub.browser.dialog.DialogItem
import com.xhub.browser.extensions.inflater
import android.graphics.PorterDuff
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

/**
 * A [RecyclerView.Adapter] that displays [DialogItem] with icons.
 */
class RecyclerViewDialogItemAdapter(
    private val listItems: List<DialogItem>
) : RecyclerView.Adapter<DialogItemViewHolder>() {

    var onItemClickListener: ((DialogItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DialogItemViewHolder =
        DialogItemViewHolder(
            parent.context.inflater.inflate(R.layout.dialog_list_item, parent, false)
        )

    override fun getItemCount(): Int = listItems.size

    override fun onBindViewHolder(holder: DialogItemViewHolder, position: Int) {
        val item = listItems[position]
        holder.icon.setImageDrawable(item.icon)
        holder.icon.isVisible = item.icon != null
        item.colorTint?.let { holder.icon.setColorFilter(it, PorterDuff.Mode.SRC_IN) }
        holder.title.setText(item.title)

        // Handle secondary text (subtitle)
        if (item.text.isNullOrEmpty()) {
            holder.text.isVisible = false
        } else {
            holder.text.isVisible = true
            holder.text.text = item.text
        }

        holder.itemView.setOnClickListener { onItemClickListener?.invoke(item) }
    }

}

/**
 * A [RecyclerView.ViewHolder] that displays an icon and a title.
 */
class DialogItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    /**
     * The icon to display.
     */
    val icon: ImageView = view.findViewById(R.id.icon)

    /**
     * The title to display.
     */
    val title: TextView = view.findViewById(R.id.title_text)

    /**
     * The secondary text to display.
     */
    val text: TextView = view.findViewById(R.id.text)

}
