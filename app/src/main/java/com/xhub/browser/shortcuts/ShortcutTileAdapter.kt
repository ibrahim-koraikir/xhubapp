package com.xhub.browser.shortcuts

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.xhub.browser.R
import com.xhub.browser.favicon.FaviconModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

/**
 * Flat list item used by [ShortcutTileAdapter].
 *
 * COMMENT 6: the previous implementation built every shortcut tile programmatically on each home
 * screen visit, inflating and discarding the full grid (including all favicon image views) every
 * time. This sealed type is the model behind a [RecyclerView] + [DiffUtil] based grid: only items
 * whose identity/content changed are rebound, and tile view holders are recycled.
 */
sealed class ShortcutItem {
    /** Full-span uppercase group header. */
    data class Header(val name: String) : ShortcutItem()

    /**
     * A single site shortcut tile (icon + label).
     *
     * [sectionKey] is the name of the group this tile belongs to. It is part of the tile's DiffUtil
     * identity so the SAME url appearing in two sections (e.g. a starred site that also lives in a
     * regular group, so it shows in both the pinned "⭐ Favorites" row AND its own group) yields two
     * distinct items. Without it, DiffUtil would see two items with identical identity and could
     * mis-animate or crash. Defaults to empty so callers that don't care about sectioning still work.
     */
    data class Tile(val site: ShortcutSite, val sectionKey: String = "") : ShortcutItem()

    /**
     * Full-span "Show more (N)" / "Show less" toggle rendered at the end of a group that has more
     * than the visible cap of tiles. [groupName] identifies which group it belongs to,
     * [hiddenCount] is how many tiles are currently hidden (0 when expanded), and [expanded]
     * drives the label.
     */
    data class ShowMore(
        val groupName: String,
        val hiddenCount: Int,
        val expanded: Boolean
    ) : ShortcutItem()

    /** Full-span spacer rendered at the bottom of each group for vertical rhythm. */
    object Spacer : ShortcutItem()

    /**
     * Empty 1×1 cell used to pad an incomplete last row of a group so trailing tiles don't stretch
     * across all 4 columns. Distinct type so [SpanSizeLookup] can leave it at span 1, but it reuses
     * the empty-[View] body for simplicity.
     */
    object PlaceholderCell : ShortcutItem()

    /** Full-span empty state shown when there are no sites at all. */
    object Empty : ShortcutItem()
}

/**
 * RecyclerView adapter for the home screen shortcut grid.
 *
 * Uses a [GridLayoutManager] with span 4 in the host activity; headers, spacers and the empty
 * state span all 4 columns via [SpanSizeLookup]. Favicon subscriptions are tied to the tile
 * view holder's lifecycle: started in [onBindViewHolder], disposed in [onViewRecycled], so they
 * don't leak and don't keep updating a recycled view.
 */
class ShortcutTileAdapter(
    private val faviconModel: FaviconModel,
    private val mainScheduler: io.reactivex.Scheduler,
    private val onTileClick: (View) -> Unit,
    private val onAddFirstShortcut: () -> Unit,
    private val onTileLongClick: (View) -> Unit = {},
    private val onShowMoreClick: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_HEADER = 1
        const val VIEW_TYPE_TILE = 2
        const val VIEW_TYPE_SPACER = 3
        const val VIEW_TYPE_EMPTY = 4
        const val VIEW_TYPE_PLACEHOLDER = 5
        const val VIEW_TYPE_SHOWMORE = 6
    }

    private val items = mutableListOf<ShortcutItem>()

    fun submitList(newItems: List<ShortcutItem>) {
        val old = ArrayList(items)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = old[oldPos]
                val b = newItems[newPos]
                return when {
                    a is ShortcutItem.Header && b is ShortcutItem.Header -> a.name == b.name
                    a is ShortcutItem.Tile && b is ShortcutItem.Tile ->
                        a.site.url == b.site.url && a.sectionKey == b.sectionKey
                    // ShowMore items are identified by the group they belong to.
                    a is ShortcutItem.ShowMore && b is ShortcutItem.ShowMore -> a.groupName == b.groupName
                    // Spacers, placeholders and empty cells have no identity beyond their type.
                    a is ShortcutItem.Spacer && b is ShortcutItem.Spacer -> true
                    a is ShortcutItem.PlaceholderCell && b is ShortcutItem.PlaceholderCell -> true
                    a is ShortcutItem.Empty && b is ShortcutItem.Empty -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                old[oldPos] == newItems[newPos]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Cancel every favicon subscription currently held by attached tile holders. Called by the
     * host when leaving the home screen so in-flight downloads don't write into detached views.
     */
    fun cancelAllFavicons(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            (recyclerView.getChildViewHolder(child) as? TileViewHolder)?.cancelFavicon()
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ShortcutItem.Header -> VIEW_TYPE_HEADER
        is ShortcutItem.Tile -> VIEW_TYPE_TILE
        is ShortcutItem.Spacer -> VIEW_TYPE_SPACER
        is ShortcutItem.Empty -> VIEW_TYPE_EMPTY
        is ShortcutItem.PlaceholderCell -> VIEW_TYPE_PLACEHOLDER
        is ShortcutItem.ShowMore -> VIEW_TYPE_SHOWMORE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(buildGroupHeader(ctx, density))
            VIEW_TYPE_SPACER -> SpacerViewHolder(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ctx.resources.getDimensionPixelSize(R.dimen.home_group_gap)
                )
            })
            VIEW_TYPE_PLACEHOLDER -> SpacerViewHolder(View(ctx).apply {
                // 1×1 invisible cell padding the last row of a group.
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            VIEW_TYPE_EMPTY -> EmptyViewHolder(buildEmptyState(ctx, density))
            VIEW_TYPE_SHOWMORE -> ShowMoreViewHolder(buildShowMore(ctx, density))
            else -> TileViewHolder(buildTile(ctx, density))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ShortcutItem.Header -> {
                (holder as HeaderViewHolder).textView.text = item.name.uppercase()
            }
            is ShortcutItem.Tile -> (holder as TileViewHolder).bind(
                item.site, onTileClick, onTileLongClick, faviconModel, mainScheduler
            )
            is ShortcutItem.ShowMore -> (holder as ShowMoreViewHolder).bind(item, onShowMoreClick)
            is ShortcutItem.Spacer, is ShortcutItem.Empty, is ShortcutItem.PlaceholderCell -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        // Dispose the favicon subscription bound to this tile so the download doesn't outlive the
        // view. This is the core lifecycle fix the comment calls for.
        if (holder is TileViewHolder) holder.cancelFavicon()
        // Reset animated properties so a tile recycled mid-entrance animation is never
        // left in a frozen semi-transparent / scaled / translated state when rebound.
        holder.itemView.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationY = 0f
        }
        super.onViewRecycled(holder)
    }

    // ------------------------------------------------------------------
    // Programmatic view construction (ported verbatim from the old buildDynamicShortcuts so
    // visual fidelity is preserved — same MaterialCardView, ripple, press-scale, letter fallback)
    // ------------------------------------------------------------------
    private fun buildTile(ctx: android.content.Context, density: Float): View {
        val tilePad = ctx.resources.getDimensionPixelSize(R.dimen.home_tile_padding)
        val tileSize = ctx.resources.getDimensionPixelSize(R.dimen.home_tile_frame_size)
        val labelGap = ctx.resources.getDimensionPixelSize(R.dimen.home_tile_label_margin_top)
        val initialSizeSp = ctx.resources.getDimension(R.dimen.home_tile_initial_size) / density
        val labelSizeSp = ctx.resources.getDimension(R.dimen.home_tile_label_size) / density

        // Tile wrapper (vertical: icon + label)
        val tile = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(tilePad, tilePad, tilePad, tilePad)
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(100).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
                false
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Icon circle.
        // Was a MaterialCardView, but MaterialCardView measures MATCH_PARENT children to 0×0 in a
        // RecyclerView holder (confirmed via runtime logging: iv.w=0 iv.h=0) and its foreground
        // ripple is an opaque rectangle drawn over children, hiding the favicon. A plain
        // FrameLayout with a background shape gives the same squircle look AND lays out its
        // children correctly. The ripple moves to the tile wrapper below.
        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(tileSize, tileSize).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_shortcut_tile_frame)
        }

        // Letter initial.
        // FIX: use explicit tileSize dimensions instead of MATCH_PARENT. Inside a MaterialCardView,
        // children with MATCH_PARENT were measuring to 0×0 (confirmed via runtime logging:
        // iv.w=0 iv.h=0), so a FIT_CENTER ImageView had no bounds to draw into and the favicon
        // never appeared even though setImageBitmap succeeded. A TextView still rendered its letter
        // because it self-sizes to its text, masking the zero-size problem for the initial but not
        // for the favicon. Explicit pixel dimensions fix both.
        val initial = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.home_initial_text))
            textSize = initialSizeSp
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(tileSize, tileSize)
        }
        frame.addView(initial)

        // Favicon overlay (hidden until loaded) — explicit tileSize, same reason as above.
        val faviPad = ctx.resources.getDimensionPixelSize(R.dimen.home_tile_favicon_padding)
        val faviconIv = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(faviPad, faviPad, faviPad, faviPad)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isVisible = false
            layoutParams = FrameLayout.LayoutParams(tileSize, tileSize)
        }
        frame.addView(faviconIv)

        tile.addView(frame)

        // Label
        tile.addView(TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.home_dim_foreground))
            textSize = labelSizeSp
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = labelGap }
        })

        return tile
    }

    /**
     * Group header: bold uppercase label with a short orange accent underline beneath it.
     * The accent line mirrors bg_home_quote_divider so the visual language stays consistent
     * with the hero (Task 2 / U1).
     */
    private fun buildGroupHeader(ctx: android.content.Context, density: Float): View {
        val labelBottomMargin = ctx.resources.getDimensionPixelSize(R.dimen.home_group_label_margin_bottom)
        val underlineWidth = ctx.resources.getDimensionPixelSize(R.dimen.home_group_underline_width)
        val underlineHeight = ctx.resources.getDimensionPixelSize(R.dimen.home_group_underline_height)
        val underlineTopMargin = ctx.resources.getDimensionPixelSize(R.dimen.home_group_underline_margin_top)
        val labelSizeSp = ctx.resources.getDimension(R.dimen.home_group_label_size) / density

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = labelBottomMargin }
        }

        val label = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.home_group_label))
            textSize = labelSizeSp
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
        }
        container.addView(label)

        val underline = View(ctx).apply {
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_home_group_underline)
            layoutParams = LinearLayout.LayoutParams(underlineWidth, underlineHeight).also {
                it.topMargin = underlineTopMargin
                it.gravity = Gravity.START
            }
        }
        container.addView(underline)

        return container
    }

    /**
     * Full-span "Show more (N)" / "Show less" chip rendered at the end of a group that exceeds the
     * visible cap. Styled to match the group underline accent language: a centered, bold, dim label
     * with a ripple and comfortable touch target.
     */
    private fun buildShowMore(ctx: android.content.Context, density: Float): View {
        val vPad = (10 * density).toInt()
        val hPad = (16 * density).toInt()
        return TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.home_initial_text))
            textSize = ctx.resources.getDimension(R.dimen.home_group_label_size) / density
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minHeight = (48 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            // Bounded ripple for tap feedback.
            val outValue = android.util.TypedValue()
            ctx.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true
            )
            setBackgroundResource(outValue.resourceId)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildEmptyState(ctx: android.content.Context, density: Float): View {
        val emptyPad = ctx.resources.getDimensionPixelSize(R.dimen.home_empty_padding_v)
        val emptySize = ctx.resources.getDimensionPixelSize(R.dimen.home_empty_icon_size)
        val emptyTextSp = ctx.resources.getDimension(R.dimen.home_empty_text_size) / density
        val colorStroke = ContextCompat.getColor(ctx, R.color.home_tile_stroke)
        val colorGroupLabel = ContextCompat.getColor(ctx, R.color.home_group_label)
        val colorInitialText = ContextCompat.getColor(ctx, R.color.home_initial_text)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, emptyPad, 0, emptyPad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(ImageView(ctx).apply {
                setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_bookmarks))
                imageTintList = ColorStateList.valueOf(colorStroke)
                layoutParams = LinearLayout.LayoutParams(emptySize, emptySize).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                    it.bottomMargin = (12 * density).toInt()
                }
            })

            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.home_empty_state_message)
                setTextColor(colorGroupLabel)
                textSize = emptyTextSp
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (4 * density).toInt() }
            })

            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.home_empty_state_action)
                setTextColor(colorInitialText)
                textSize = emptyTextSp
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                minHeight = (48 * density).toInt()
                minWidth = (48 * density).toInt()
                setPadding((16 * density).toInt(), (12 * density).toInt(),
                           (16 * density).toInt(), (12 * density).toInt())
                setOnClickListener { onAddFirstShortcut() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    // ------------------------------------------------------------------
    // View holders
    // ------------------------------------------------------------------
    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = (view as LinearLayout).getChildAt(0) as TextView
    }
    private class SpacerViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class ShowMoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view as TextView

        fun bind(item: ShortcutItem.ShowMore, onShowMoreClick: (String) -> Unit) {
            label.text = if (item.expanded) {
                label.context.getString(R.string.shortcut_show_less)
            } else {
                label.context.getString(R.string.shortcut_show_more, item.hiddenCount)
            }
            itemView.setOnClickListener { onShowMoreClick(item.groupName) }
        }
    }

    private class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // frame is a FrameLayout (was MaterialCardView — see buildTile for why it changed).
        private val frame: FrameLayout = (view as LinearLayout).getChildAt(0) as FrameLayout
        private val initial: TextView = frame.getChildAt(0) as TextView
        private val faviconIv: ImageView = frame.getChildAt(1) as ImageView
        private val label: TextView = ((view as LinearLayout).getChildAt(1) as TextView)

        private var faviconDisposable: Disposable? = null

        fun bind(
            site: ShortcutSite,
            onClick: (View) -> Unit,
            onLongClick: (View) -> Unit,
            faviconModel: FaviconModel,
            mainScheduler: io.reactivex.Scheduler
        ) {
            // Reset to letter state before any favicon arrives so a recycled view holding a
            // previous site's icon doesn't briefly show the wrong logo.
            initial.text = site.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            initial.isVisible = true
            faviconIv.isVisible = false
            faviconIv.setImageDrawable(null)
            frame.background = ContextCompat.getDrawable(frame.context, R.drawable.bg_shortcut_tile_frame)
            label.text = site.name

            itemView.tag = site.url
            itemView.setOnClickListener { onClick(itemView) }
            // Long-press opens the tile context menu. Fire a haptic here so it happens regardless
            // of what the host does with the callback. Returning true consumes the event so the
            // subsequent ACTION_UP doesn't also trigger the normal click.
            itemView.setOnLongClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick(v)
                true
            }

            cancelFavicon()
            // Home-screen shortcuts are never incognito and are sites the user explicitly added,
            // so we force the reliable third-party lookups (see FaviconModel.realFaviconForUrl).
            faviconDisposable = faviconModel
                .realFaviconForUrl(site.url, true, isIncognito = false, forceThirdParty = true)
                .subscribeOn(Schedulers.io())
                .observeOn(mainScheduler)
                .subscribe(
                    Consumer { bmp ->
                        faviconIv.setImageBitmap(bmp)
                        faviconIv.isVisible = true
                        initial.isVisible = false
                        // Swap to the white-frame background so transparent favicons stay visible.
                        frame.background = ContextCompat.getDrawable(frame.context, R.drawable.bg_shortcut_tile_frame_white)
                    },
                    Consumer { err ->
                        Timber.w(err, "Favicon fetch error for ${site.url}")
                    }
                )
        }

        fun cancelFavicon() {
            faviconDisposable?.takeIf { !it.isDisposed }?.dispose()
            faviconDisposable = null
        }
    }
}
