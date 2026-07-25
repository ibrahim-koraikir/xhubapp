package com.xhub.browser.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xhub.browser.R
import com.xhub.browser.shortcuts.ShortcutGroup
import com.xhub.browser.shortcuts.ShortcutRepository
import com.xhub.browser.shortcuts.ShortcutSite

class ManageShortcutsActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
    private lateinit var adapter: GroupAdapter
    private val groups = mutableListOf<ShortcutGroup>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_shortcuts)

        rvGroups = findViewById(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(this)
        adapter = GroupAdapter()
        rvGroups.adapter = adapter

        // Load data in background
        io.reactivex.Single.fromCallable { ShortcutRepository.loadGroups(this) }
            .subscribeOn(io.reactivex.schedulers.Schedulers.io())
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe { loadedGroups ->
                groups.clear()
                groups.addAll(loadedGroups)
                adapter.notifyDataSetChanged()
            }

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finishWithSave() }

        // Save / check button
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { finishWithSave() }

        // Add group
        val addGroupRoot = findViewById<LinearLayout>(R.id.btnAddGroupOverlay)
        addGroupRoot.setOnClickListener { showAddGroupDialog() }

        // Drag-to-reorder groups
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, th: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = th.adapterPosition
                groups.add(to, groups.removeAt(from))
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

            override fun isLongPressDragEnabled() = true
        }
        ItemTouchHelper(callback).attachToRecyclerView(rvGroups)
    }

    override fun onBackPressed() {
        finishWithSave()
    }

    private fun finishWithSave() {
        ShortcutRepository.saveGroups(this, groups)
        setResult(RESULT_OK)
        finish()
    }

    // -----------------------------------------------------------------------
    // Dialogs
    // -----------------------------------------------------------------------
    private fun showAddGroupDialog() {
        val editText = EditText(this).apply {
            hint = "Group name (e.g. Sports)"
            setPadding(48, 32, 48, 16)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Group")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    groups.add(ShortcutGroup(name, mutableListOf()))
                    adapter.notifyItemInserted(groups.lastIndex)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditGroupDialog(position: Int) {
        val editText = EditText(this).apply {
            hint = "Group name"
            setText(groups[position].name)
            setPadding(48, 32, 48, 16)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Group")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    val old = groups[position]
                    groups[position] = ShortcutGroup(name, old.sites)
                    adapter.notifyItemChanged(position)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteGroupDialog(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Group")
            .setMessage("Remove \"${groups[position].name}\" and all its sites?")
            .setPositiveButton("Delete") { _, _ ->
                groups.removeAt(position)
                adapter.notifyItemRemoved(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Normalizes and validates a user-entered shortcut URL.
     *
     * COMMENT 7: the previous check `if (!url.startsWith("http"))` was too loose — it would leave
     * `ftp://...`, `javascript:...`, `data:...` etc. untouched, and would not prefix `http://`
     * schemes (it only caught the bare "http" prefix). We now:
     *  1. Reject dangerous schemes (`javascript:`, `data:`) outright.
     *  2. Prefix `https://` only when the input is NOT already `https://` or `http://`.
     *  3. Re-validate the result with URLUtil so malformed inputs are rejected with a toast.
     *
     * @return the normalized, valid https/http URL, or null if the input is invalid.
     */
    private fun normalizeShortcutUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isEmpty()) return null

        // Explicitly reject dangerous schemes regardless of normalization.
        val lower = url.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:")) return null

        // Prefix https:// only when the user didn't already supply an http(s) scheme.
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            url = "https://$url"
        }

        // Final validation: must be a well-formed http(s) URL.
        val valid = android.webkit.URLUtil.isHttpsUrl(url) || android.webkit.URLUtil.isHttpUrl(url)
        return if (valid) url else null
    }

    private fun showInvalidUrlToast() {
        android.widget.Toast.makeText(
            this,
            "Please enter a valid web address (e.g. example.com)",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun showAddSiteDialog(groupIndex: Int, onAdded: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_site, null)
        val etName = dialogView.findViewById<EditText>(R.id.etSiteName)
        val etUrl  = dialogView.findViewById<EditText>(R.id.etSiteUrl)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Website")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val url  = normalizeShortcutUrl(etUrl.text.toString())
                if (name.isNotEmpty() && url != null) {
                    groups[groupIndex].sites.add(ShortcutSite(name, url))
                    onAdded()
                } else if (etUrl.text.toString().trim().isNotEmpty()) {
                    // Name may be valid but the URL failed validation.
                    showInvalidUrlToast()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditSiteDialog(groupIndex: Int, siteIndex: Int, onEdited: () -> Unit) {
        val site = groups[groupIndex].sites[siteIndex]
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_site, null)
        val etName = dialogView.findViewById<EditText>(R.id.etSiteName)
        val etUrl  = dialogView.findViewById<EditText>(R.id.etSiteUrl)
        etName.setText(site.name)
        etUrl.setText(site.url)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Website")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val url  = normalizeShortcutUrl(etUrl.text.toString())
                if (name.isNotEmpty() && url != null) {
                    groups[groupIndex].sites[siteIndex] = ShortcutSite(name, url)
                    onEdited()
                } else if (etUrl.text.toString().trim().isNotEmpty()) {
                    showInvalidUrlToast()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -----------------------------------------------------------------------
    // Group adapter
    // -----------------------------------------------------------------------
    private inner class GroupAdapter : RecyclerView.Adapter<GroupAdapter.VH>() {

        inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val tvName: TextView          = root.findViewById(R.id.tvGroupName)
            val btnEdit: ImageButton      = root.findViewById(R.id.btnEditGroup)
            val btnDelete: ImageButton    = root.findViewById(R.id.btnDeleteGroup)
            val sitesContainer: LinearLayout = root.findViewById(R.id.sitesContainer)
            val btnAddSite: LinearLayout  = root.findViewById(R.id.btnAddSite)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_shortcut_group, parent, false)
            return VH(v)
        }

        override fun getItemCount() = groups.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val group = groups[position]
            holder.tvName.text = group.name

            holder.btnEdit.setOnClickListener   { showEditGroupDialog(holder.adapterPosition) }
            holder.btnDelete.setOnClickListener { showDeleteGroupDialog(holder.adapterPosition) }

            // Rebuild sites list
            rebuildSites(holder, position)

            holder.btnAddSite.setOnClickListener {
                showAddSiteDialog(holder.adapterPosition) {
                    rebuildSites(holder, holder.adapterPosition)
                }
            }
        }

        private fun rebuildSites(holder: VH, groupIndex: Int) {
            val container = holder.sitesContainer
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)
            val sites = groups[groupIndex].sites

            sites.forEachIndexed { siteIndex, site ->
                val siteView = inflater.inflate(R.layout.item_shortcut_site, container, false)
                val tvInitial = siteView.findViewById<TextView>(R.id.tvSiteInitial)
                val tvName    = siteView.findViewById<TextView>(R.id.tvSiteName)
                val tvUrl     = siteView.findViewById<TextView>(R.id.tvSiteUrl)
                val btnRemove = siteView.findViewById<ImageButton>(R.id.btnRemoveSite)
                val ivDrag    = siteView.findViewById<ImageView>(R.id.ivSiteDrag)

                tvInitial.text = site.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                tvName.text    = site.name
                tvUrl.text     = site.url.removePrefix("https://").removePrefix("http://")
                    .removeSuffix("/").take(40)

                // Tap site row → edit it.
                // COMMENT 9: look up the current index dynamically rather than capturing siteIndex.
                // If the list changed between renders (drag, prior remove), the captured index could
                // point at the wrong item or be out of bounds. Re-resolve from the container each time.
                siteView.setOnClickListener {
                    val currentIndex = container.indexOfChild(siteView)
                    if (currentIndex >= 0 && currentIndex < sites.size) {
                        showEditSiteDialog(groupIndex, currentIndex) {
                            rebuildSites(holder, groupIndex)
                        }
                    }
                }

                btnRemove.setOnClickListener {
                    val currentIndex = container.indexOfChild(siteView)
                    if (currentIndex >= 0 && currentIndex < sites.size) {
                        sites.removeAt(currentIndex)
                        rebuildSites(holder, groupIndex)
                    }
                }

                // Long-press drag (within group — simple up/down swap)
                ivDrag.setOnTouchListener { _, _ ->
                    false // drag handled by ItemTouchHelper at group level
                }

                container.addView(siteView)
            }
        }
    }

    companion object {
        // COMMENT 8: REQUEST_CODE (1001) removed — ManageShortcutsActivity is now launched via
        // ActivityResultLauncher from WebBrowserActivity, so no request code is needed.

        fun start(context: Context) {
            context.startActivity(Intent(context, ManageShortcutsActivity::class.java))
        }
    }
}
