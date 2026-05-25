package fulguris.activity

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
import fulguris.R
import fulguris.shortcuts.ShortcutGroup
import fulguris.shortcuts.ShortcutRepository
import fulguris.shortcuts.ShortcutSite

class ManageShortcutsActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
    private lateinit var adapter: GroupAdapter
    private val groups = mutableListOf<ShortcutGroup>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_shortcuts)

        // Load data
        groups.addAll(ShortcutRepository.loadGroups(this))

        rvGroups = findViewById(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(this)
        adapter = GroupAdapter()
        rvGroups.adapter = adapter

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

    private fun showAddSiteDialog(groupIndex: Int, onAdded: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_site, null)
        val etName = dialogView.findViewById<EditText>(R.id.etSiteName)
        val etUrl  = dialogView.findViewById<EditText>(R.id.etSiteUrl)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Website")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                var url  = etUrl.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    if (!url.startsWith("http")) url = "https://$url"
                    groups[groupIndex].sites.add(ShortcutSite(name, url))
                    onAdded()
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
                var url  = etUrl.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    if (!url.startsWith("http")) url = "https://$url"
                    groups[groupIndex].sites[siteIndex] = ShortcutSite(name, url)
                    onEdited()
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

                // Tap site row → edit it
                siteView.setOnClickListener {
                    showEditSiteDialog(groupIndex, siteIndex) {
                        rebuildSites(holder, groupIndex)
                    }
                }

                btnRemove.setOnClickListener {
                    sites.removeAt(siteIndex)
                    rebuildSites(holder, groupIndex)
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
        const val REQUEST_CODE = 1001

        fun start(context: Context) {
            context.startActivity(Intent(context, ManageShortcutsActivity::class.java))
        }
    }
}
