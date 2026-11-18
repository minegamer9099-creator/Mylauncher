package com.example.mylauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * App drawer ka adapter: apps + (Categories tab mein) section headers.
 * List badalne pe (search likhna, tab badalna) diff background thread pe nikalta hai aur sirf badle hue
 * items update hote hain (pehle har baar notifyDataSetChanged se saare visible items rebind hote the).
 */
class DrawerAdapter(
    private var nameTint: Int,
    private var style: IconStyle,
    private val onAppLongClick: (AppInfo, View) -> Unit,
    private val onAppClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<DrawerItem>() {
        override fun areItemsTheSame(oldItem: DrawerItem, newItem: DrawerItem): Boolean = when {
            oldItem is DrawerItem.Header && newItem is DrawerItem.Header -> oldItem.title == newItem.title
            oldItem is DrawerItem.App && newItem is DrawerItem.App ->
                oldItem.app.packageName == newItem.app.packageName
            else -> false
        }

        override fun areContentsTheSame(oldItem: DrawerItem, newItem: DrawerItem): Boolean =
            oldItem == newItem
    })

    private val items: List<DrawerItem>
        get() = differ.currentList

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.headerTitle)
    }

    /** onCommitted: diff lag jaane ke baad (jaise list ko upar scroll karne ke liye). */
    fun submit(newItems: List<DrawerItem>, onCommitted: (() -> Unit)? = null) {
        differ.submitList(newItems, if (onCommitted != null) Runnable { onCommitted() } else null)
    }

    fun setNameTint(color: Int) {
        if (nameTint == color) return
        nameTint = color
        notifyDataSetChanged()
    }

    fun setStyle(newStyle: IconStyle) {
        if (style === newStyle) return
        style = newStyle
        notifyDataSetChanged()
    }

    fun isHeader(position: Int): Boolean =
        position in items.indices && items[position] is DrawerItem.Header

    /** A-Z index ke liye: is letter se shuru hone wala pehla app kis position pe hai (-1 = nahi mila). */
    fun positionOfLetter(letter: Char): Int {
        val list = items
        for (i in list.indices) {
            val item = list[i]
            if (item is DrawerItem.App && letterOf(item.app.label) == letter) return i
        }
        return -1
    }

    private fun letterOf(label: String): Char {
        val c = label.firstOrNull()?.uppercaseChar() ?: '#'
        return if (c in 'A'..'Z') c else '#'
    }

    override fun getItemViewType(position: Int): Int =
        if (isHeader(position)) TYPE_HEADER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_drawer_header, parent, false))
        } else {
            AppViewHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (item is DrawerItem.Header && holder is HeaderViewHolder) {
            holder.title.text = item.title
            holder.title.setTextColor(nameTint)
        } else if (item is DrawerItem.App && holder is AppViewHolder) {
            val app = item.app
            holder.icon.setImageDrawable(style.drawableFor(app))
            style.styleIcon(holder.icon)
            holder.name.text = app.label
            holder.name.setTextColor(nameTint)
            holder.name.visibility = if (style.showLabels) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onAppClick(app, holder.icon) }
            holder.itemView.setOnLongClickListener {
                onAppLongClick(app, holder.icon)
                true
            }
            holder.itemView.applyPressEffect()
        }
    }

    override fun getItemCount(): Int = items.size

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_APP = 1
    }
}
