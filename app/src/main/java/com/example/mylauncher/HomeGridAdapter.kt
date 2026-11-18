package com.example.mylauncher

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Ek app ka cell (icon + naam). Home, drawer aur folder popup teeno use karte hain. */
class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val icon: ImageView = view.findViewById(R.id.appIcon)
    val name: TextView = view.findViewById(R.id.appName)
}

/** Ek folder ka cell (glass tile + naam). */
class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val tile: FolderTileView = view.findViewById(R.id.folderTile)
    val name: TextView = view.findViewById(R.id.folderName)
}

/** Search widget ka cell. */
class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val pill: View = view.findViewById(R.id.searchPill)
    val searchIcon: ImageView = view.findViewById(R.id.searchIcon)
    val label: TextView = view.findViewById(R.id.searchLabel)
    val mic: ImageView = view.findViewById(R.id.searchMic)
}

/** Home screen ke saare tap / long-press callbacks ek jagah. */
class HomeActions(
    val onAppClick: (AppInfo, View) -> Unit,
    val onAppLongClick: (AppInfo, View) -> Unit,
    val onFolderClick: (HomeItem.Folder, View) -> Unit,
    val onSearchClick: () -> Unit,
    val onMicClick: () -> Unit
)

/**
 * Home page / folder popup ka grid adapter: apps, folders aur search widget dikhata hai.
 * rowHeightPx > 0 ho to har row us height ki hoti hai (rows page mein barabar failti hain).
 *
 * Ek page ka adapter dobara-dobara naya nahi banta: update() se wahi adapter naye data ke saath chalta hai,
 * aur kuch badla na ho to kuch nahi hota (item views recycle pool mein rehte hain, dobara inflate nahi hote).
 */
class HomeGridAdapter(
    private var items: MutableList<HomeItem>,
    private var nameTint: Int,
    private var lightGlass: Boolean,
    private var style: IconStyle,
    private var rowHeightPx: Int,
    private val actions: HomeActions
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun currentItems(): List<HomeItem> = items

    /** Naya data / colors. Sab kuch same ho to turant return (koi rebind nahi). */
    fun update(
        newItems: MutableList<HomeItem>,
        tint: Int,
        light: Boolean,
        newStyle: IconStyle,
        rowHeight: Int
    ) {
        if (items === newItems && nameTint == tint && lightGlass == light &&
            style === newStyle && rowHeightPx == rowHeight
        ) {
            return
        }
        items = newItems
        nameTint = tint
        lightGlass = light
        style = newStyle
        rowHeightPx = rowHeight
        notifyDataSetChanged()
    }

    /** Drag-drop: item ko ek jagah se doosri jagah le jao. */
    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    /** Search widget 4 columns leta hai, baaki 1. */
    fun spanSizeAt(position: Int): Int =
        if (position in items.indices && items[position] is HomeItem.Search) HOME_COLUMNS else 1

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeItem.Folder -> TYPE_FOLDER
        is HomeItem.Search -> TYPE_SEARCH
        else -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderViewHolder(inflater.inflate(R.layout.item_folder, parent, false))
            TYPE_SEARCH -> SearchViewHolder(inflater.inflate(R.layout.item_search, parent, false))
            else -> AppViewHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        if (rowHeightPx > 0) {
            val lp = holder.itemView.layoutParams
            if (lp.height != rowHeightPx) {
                lp.height = rowHeightPx
                holder.itemView.layoutParams = lp
            }
        }

        if (item is HomeItem.App && holder is AppViewHolder) {
            val app = item.app
            holder.icon.setImageDrawable(style.drawableFor(app))
            style.styleIcon(holder.icon)
            holder.name.text = app.label
            holder.name.setTextColor(nameTint)
            holder.name.visibility = if (style.showLabels) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { actions.onAppClick(app, holder.icon) }
            holder.itemView.setOnLongClickListener {
                actions.onAppLongClick(app, holder.icon)
                true
            }
            holder.itemView.applyPressEffect()
        } else if (item is HomeItem.Folder && holder is FolderViewHolder) {
            val lp = holder.tile.layoutParams
            if (lp.width != style.sizePx || lp.height != style.sizePx) {
                lp.width = style.sizePx
                lp.height = style.sizePx
                holder.tile.layoutParams = lp
            }
            holder.tile.setApps(item.apps, style)
            holder.name.text = item.name
            holder.name.setTextColor(nameTint)
            holder.name.visibility = if (style.showLabels) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { actions.onFolderClick(item, holder.tile) }
            holder.itemView.applyPressEffect { pressed ->
                if (style.touchGlow) holder.tile.glowTo(if (pressed) 1f else 0f)
            }
        } else if (item is HomeItem.Search && holder is SearchViewHolder) {
            val density = holder.itemView.resources.displayMetrics.density
            val primary = if (lightGlass) Color.parseColor("#1F2328") else Color.WHITE
            val secondary = if (lightGlass) Color.parseColor("#8A1F2328") else Color.parseColor("#CCFFFFFF")
            // Glass background sirf tab naya banta hai jab light/dark badla ho (shader allocation bachti hai)
            if (holder.pill.tag != lightGlass) {
                holder.pill.background = LiquidGlassDrawable(26f * density, if (lightGlass) 0.6f else 0.5f)
                holder.pill.tag = lightGlass
            }
            holder.label.setTextColor(secondary)
            val tint = ColorStateList.valueOf(primary)
            holder.searchIcon.imageTintList = tint
            holder.mic.imageTintList = tint
            holder.itemView.setOnClickListener { actions.onSearchClick() }
            holder.mic.setOnClickListener { actions.onMicClick() }
        }
    }

    override fun getItemCount(): Int = items.size

    companion object {
        const val TYPE_APP = 0
        const val TYPE_FOLDER = 1
        const val TYPE_SEARCH = 2
    }
}
