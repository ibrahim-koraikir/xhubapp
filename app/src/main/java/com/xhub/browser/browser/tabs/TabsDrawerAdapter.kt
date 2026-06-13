package com.xhub.browser.browser.tabs

import com.xhub.browser.R
import com.xhub.browser.browser.WebBrowser
import com.xhub.browser.extensions.inflater
import com.xhub.browser.extensions.isDarkTheme
import com.xhub.browser.extensions.setImageForTheme
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * The adapter for vertical mobile style browser tabs.
 */
class TabsDrawerAdapter(
    webBrowser: WebBrowser
) : TabsAdapter(webBrowser) {

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TabViewHolder {
        val view = viewGroup.context.inflater.inflate(R.layout.tab_list_item, viewGroup, false)
        return TabViewHolder(view, webBrowser) //.apply { setIsRecyclable(false) }
    }

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.exitButton?.tag = position

        val tab = tabList[position]

        holder.txtTitle?.text = tab.title
        updateViewHolderAppearance(holder, tab)
        updateViewHolderFavicon(holder, tab.favicon, tab.isForeground)
        updateViewHolderBackground(holder, tab.isForeground)
        // Fetch preview from the single source of truth: TabThumbnailCache.
        // tab.previewVersion is used by DiffUtil to detect changes; the actual
        // bitmap is always fetched fresh here so we never display a stale image.
        val cached = TabThumbnailCache.get(tab.id, persistable = !tab.isIncognito, onLoaded = { bitmap ->
            if (holder.tab?.id == tab.id) {
                updateViewHolderPreview(holder, bitmap)
            }
        })
        updateViewHolderPreview(holder, cached)
        // Update our copy so that we can check for changes then
        holder.tab = tab.copy()
    }

    private fun updateViewHolderFavicon(viewHolder: TabViewHolder, favicon: Bitmap, isForeground: Boolean) {
        // Apply filter to favicon if needed, but verify it isn't recycled
        if (!favicon.isRecycled) {
            viewHolder.favicon?.setImageForTheme(favicon, (webBrowser as Context).isDarkTheme())
        } else {
            // Provide a fallback if the bitmap was somehow recycled
            viewHolder.favicon?.setImageResource(R.drawable.ic_explore_outline)
        }
    }

    private fun updateViewHolderBackground(viewHolder: TabViewHolder, isForeground: Boolean) {

        Timber.v("updateViewHolderBackground: $isForeground - ${viewHolder.txtTitle?.text}")
        viewHolder.iCardView?.apply {
            isActivated = isForeground
            background = context.getDrawable(if (isForeground) R.drawable.bg_tab_grid_card_active else R.drawable.bg_tab_grid_card)
        }

    }

    private fun updateViewHolderAppearance(viewHolder: TabViewHolder, tab: TabViewState) {
        viewHolder.txtTitle?.let {
            if (tab.isForeground) {
                TextViewCompat.setTextAppearance(it, R.style.boldText)
            } else if (tab.isFrozen) {
                TextViewCompat.setTextAppearance(it, R.style.italicText)
            } else {
                TextViewCompat.setTextAppearance(it, R.style.normalText)
            }
        }
    }

    private fun updateViewHolderPreview(viewHolder: TabViewHolder, preview: Bitmap?) {
        viewHolder.preview?.apply {
            // Clear any previous image first to prevent showing old thumbnails
            setImageDrawable(null)
            
            if (preview != null && !preview.isRecycled) {
                setImageBitmap(preview)
                alpha = 1.0f
            } else {
                // Show placeholder icon when no preview is available
                setImageResource(R.drawable.ic_explore_outline)
                alpha = 0.3f
            }
        }
    }
}


