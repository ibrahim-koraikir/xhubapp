package fulguris.browser.tabs

import fulguris.R
import fulguris.activity.WebBrowserActivity
import fulguris.browser.WebBrowser
import fulguris.di.configPrefs
import fulguris.utils.ItemDragDropSwipeViewHolder
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * The [RecyclerView.ViewHolder] for both vertical and horizontal tabs.
 * That represents an item in our list, basically one tab.
 */
class TabViewHolder(
    view: View,
    private val webBrowser: WebBrowser
) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener,
    ItemDragDropSwipeViewHolder {

    // Using view binding won't give us much
    val txtTitle = view.findViewById<View>(R.id.textTab) as? TextView
    val favicon = view.findViewById<View>(R.id.faviconTab) as? ImageView
    val exitButton = view.findViewById<View>(R.id.deleteAction)
    val iCardView = view.findViewById<View>(R.id.tab_item_background)
    val preview = view.findViewById<View>(R.id.tabPreviewImage) as? ImageView
    // Keep a copy of our tab data to be able to understand what was changed on update
    // TODO: Is that how we should do things?
    var tab: TabViewState? = null

    init {
        iCardView?.clipToOutline = true
        exitButton?.setOnClickListener(this)
        iCardView?.setOnClickListener(this)
        iCardView?.setOnLongClickListener(this)
        // Is that the best way to access our preferences?
        // If not showing horizontal desktop tab bar, this one always shows close button.
        // Apply settings preference for showing close button on tabs.
        exitButton?.visibility = if (!view.context.configPrefs.verticalTabBar
                || (webBrowser as WebBrowserActivity).userPreferences.showCloseTabButton) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        val tabId = tab?.id ?: return
        val currentPosition = webBrowser.getTabModel().allTabs.indexOfFirst { it.id == tabId }
        if (currentPosition < 0) {
            return
        }
        if (v === exitButton) {
            webBrowser.tabCloseClicked(currentPosition)
        } else if (v === iCardView) {
            webBrowser.tabClicked(currentPosition)
        }
    }

    override fun onLongClick(v: View): Boolean {
        //uiController.showCloseDialog(adapterPosition)
        //return true
        return false
    }

    // From ItemTouchHelperViewHolder
    // Start dragging
    override fun onItemOperationStart() {
        // Handle drag state visually if needed
        // iCardView.isDragged = true (MaterialCardView specific)
    }

    // From ItemTouchHelperViewHolder
    // Stopped dragging
    override fun onItemOperationStop() {
        // Handle stop drag state visually if needed
        // iCardView.isDragged = false
    }


}
