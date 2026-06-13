package com.xhub.browser.browser.tabs

import com.xhub.browser.R
import com.xhub.browser.browser.TabsView
import com.xhub.browser.activity.WebBrowserActivity
import com.xhub.browser.browser.WebBrowser
import com.xhub.browser.databinding.TabDrawerViewBinding
import com.xhub.browser.di.configPrefs
import com.xhub.browser.extensions.inflater
import com.xhub.browser.utils.ItemDragDropSwipeHelper
import com.xhub.browser.utils.fixScrollBug
import com.xhub.browser.view.WebPageTab
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber


/**
 * A view which displays tabs in a vertical [RecyclerView].
 */
class TabsDrawerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),
    TabsView {

    private val webBrowser: WebBrowser? = context as? WebBrowser
    private val tabsAdapter: TabsDrawerAdapter? = webBrowser?.let { TabsDrawerAdapter(it) }

    private var mItemTouchHelper: ItemTouchHelper? = null

    var iBinding: TabDrawerViewBinding? = null

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true

        // Inflate our layout with binding support
        iBinding = TabDrawerViewBinding.inflate(context.inflater, this, true)
        // Provide UI controller for data binding to work
        iBinding?.uiController = webBrowser

        tabsAdapter?.let { adapter ->
            val tabsList = iBinding?.root?.findViewById<RecyclerView>(R.id.tabs_list)
            tabsList?.apply {
                (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
                // Use Grid Layout for the new Tabs design
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 2)
                this.adapter = adapter
                // That would prevent our recycler to resize as needed with bottom sheets
                setHasFixedSize(false)

                val callback: ItemTouchHelper.Callback = ItemDragDropSwipeHelper(adapter)
                mItemTouchHelper = ItemTouchHelper(callback)
                mItemTouchHelper?.attachToRecyclerView(this)
            }
        }
    }

    /**
     * Enable tool bar buttons according to current state of things
     */
    private fun updateTabActionButtons() {
        val browser = webBrowser ?: return
        val binding = iBinding ?: return

        // If more than one tab, enable close all tabs button
        binding.root.findViewById<View>(R.id.action_close_all_tabs)?.isEnabled = browser.getTabModel().allTabs.count() > 1
        // No sessions in incognito mode
        if (browser.isIncognito()) {
            binding.root.findViewById<View>(R.id.action_sessions)?.visibility = View.GONE
        }

        // Update tab count label in the header
        val tabCount = browser.getTabModel().allTabs.count()
        val tabGroupStandard = binding.root.findViewById<TextView>(R.id.tabGroupStandard)
        tabGroupStandard?.text = resources.getQuantityString(R.plurals.number_of_tabs, tabCount, tabCount)

        val tabGroupPrivate = binding.root.findViewById<View>(R.id.tabGroupPrivate)

        // Highlight active toggle
        if (browser.isIncognito()) {
            tabGroupStandard?.background = null
            tabGroupStandard?.alpha = 0.7f
            tabGroupPrivate?.background = context.getDrawable(R.drawable.bg_tab_grid_pill_active)
            tabGroupPrivate?.alpha = 1.0f
        } else {
            tabGroupStandard?.background = context.getDrawable(R.drawable.bg_tab_grid_pill_active)
            tabGroupStandard?.alpha = 1.0f
            tabGroupPrivate?.background = null
            tabGroupPrivate?.alpha = 0.7f
        }

        // Wire up switching logic
        tabGroupPrivate?.setOnClickListener {
            if (!browser.isIncognito()) {
                browser.executeAction(R.id.action_incognito)
                browser.closePanels()
            }
        }
        tabGroupStandard?.setOnClickListener {
            if (browser.isIncognito()) {
                browser.closePanels()
            }
        }
    }

    override fun tabAdded() {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabRemoved(position: Int) {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabChanged(position: Int) {
        displayTabs()
    }

    /**
     * Full rebuild of tabs list. Use only for add/remove/reorder cases.
     */
    private fun displayTabs() {
        val browser = webBrowser ?: return
        val binding = iBinding ?: return
        
        Timber.d("displayTabs")
        tabsAdapter?.showTabs(browser.getTabModel().allTabs.map(WebPageTab::asTabViewState))

        val tabsList = binding.root.findViewById<RecyclerView>(R.id.tabs_list)
        if (tabsList != null && fixScrollBug(tabsList)) {
            // Scroll bug was fixed trigger a scroll to current item then
            (context as? WebBrowserActivity)?.apply {
                mainHandler.postDelayed({ tryScrollToCurrentTab() }, 0)
            }
        }
    }

    override fun tabsInitialized() {
        tabsAdapter?.notifyDataSetChanged()
        updateTabActionButtons()
    }


}
