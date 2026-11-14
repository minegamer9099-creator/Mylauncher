package com.example.mylauncher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/** Home screen mein kitne columns hain (ColorOS: 4). */
const val HOME_COLUMNS = 4

/** Widget row ki height (MainActivity isse page ki rows ginta hai). */
const val WIDGET_ROW_HEIGHT_DP = 172

/** Page 0 ke widgets ki settings. types = jo widgets on hain (WidgetTypes). */
data class WidgetConfig(
    val types: List<String>,
    val strength: Float,
    val light: Boolean,
    val touchGlow: Boolean,
    val compactClock: Boolean
)

/**
 * ViewPager2 ka adapter: har item ek home screen page hai.
 * Pehle page (position 0) pe date + clock + widgets dikhte hain, phir grid (search, folders, apps).
 * Edit mode mein items ko long-press karke drag-drop se hila sakte ho.
 */
class HomePagerAdapter(
    private val pages: List<MutableList<HomeItem>>,
    private var nameTint: Int,
    private var accent: Int,
    private var lightGlass: Boolean,
    private val style: IconStyle,
    private val widgets: WidgetConfig,
    private val suggestedApps: List<AppInfo>,
    private val actions: HomeActions,
    private val rowHeightFirstPx: Int,
    private val rowHeightOtherPx: Int,
    private val topPadPx: Int,
    private val isEditMode: () -> Boolean,
    private val onPageReordered: (Int, List<HomeItem>) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: View = view.findViewById(R.id.clockHeader)
        val clock: GlassClockView = view.findViewById(R.id.glassClock)
        val clockCentered: View = view.findViewById(R.id.clockCentered)
        val clockCompact: View = view.findViewById(R.id.clockCompact)
        val clockCompactView: GlassClockView = view.findViewById(R.id.glassClockCompact)
        val widgetArea: LinearLayout = view.findViewById(R.id.widgetArea)
        val grid: RecyclerView = view.findViewById(R.id.pageGrid)
    }

    /** Wallpaper palette se naye colors: app names + clock/widgets ka accent + glass style. */
    fun setColors(nameTint: Int, accent: Int, lightGlass: Boolean) {
        // Kuch badla nahi to pura home dobara bind mat karo
        if (this.nameTint == nameTint && this.accent == accent && this.lightGlass == lightGlass) return
        this.nameTint = nameTint
        this.accent = accent
        this.lightGlass = lightGlass
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_home, parent, false)
        val holder = PageViewHolder(view)

        // Page ke andar grid scroll nahi hota (page swipe ViewPager2 sambhalta hai)
        val layoutManager = object : GridLayoutManager(parent.context, HOME_COLUMNS) {
            override fun canScrollVertically(): Boolean = false
        }
        // Search widget poori row (4 columns) leta hai
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val adapter = holder.grid.adapter as? HomeGridAdapter
                return adapter?.spanSizeAt(position) ?: 1
            }
        }
        holder.grid.layoutManager = layoutManager

        // Drag-drop (sirf edit mode mein): item ko long-press karke hilao
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = isEditMode()

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val adapter = recyclerView.adapter as? HomeGridAdapter ?: return false
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val adapter = recyclerView.adapter as? HomeGridAdapter ?: return
                val page = holder.bindingAdapterPosition
                if (page >= 0) onPageReordered(page, adapter.currentItems())
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(holder.grid)
        return holder
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val first = position == 0
        holder.header.visibility = if (first) View.VISIBLE else View.GONE

        // Clock style
        holder.clockCentered.visibility = if (widgets.compactClock) View.GONE else View.VISIBLE
        holder.clockCompact.visibility = if (widgets.compactClock) View.VISIBLE else View.GONE
        holder.clock.accentColor = accent
        holder.clockCompactView.compact = true
        holder.clockCompactView.accentColor = Color.parseColor("#F2FFFFFF")

        bindWidgets(holder, first)

        // Grid: baaki pages pe upar thodi jagah, rows barabar failti hain
        holder.grid.setPadding(
            holder.grid.paddingLeft,
            if (first) 0 else topPadPx,
            holder.grid.paddingRight,
            0
        )
        holder.grid.adapter = HomeGridAdapter(
            pages[position], nameTint, lightGlass, style,
            if (first) rowHeightFirstPx else rowHeightOtherPx, actions
        )
    }

    /** Page 0 pe widgets banao: 2 per row, har row 172dp ki. */
    private fun bindWidgets(holder: PageViewHolder, first: Boolean) {
        val area = holder.widgetArea
        area.removeAllViews()
        if (!first || widgets.types.isEmpty()) {
            area.visibility = View.GONE
            return
        }
        area.visibility = View.VISIBLE

        val ctx = area.context
        val density = ctx.resources.displayMetrics.density
        val rowHeight = (WIDGET_ROW_HEIGHT_DP * density).toInt()
        val margin = (4 * density).toInt()

        for (chunk in widgets.types.chunked(2)) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            for (type in chunk) {
                val widget = WidgetTypes.create(ctx, type)
                styleWidget(widget)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                lp.setMargins(margin, margin, margin, margin)
                row.addView(widget, lp)
            }
            if (chunk.size == 1) {
                // Akela widget aadhi width mein rahe
                row.addView(View(ctx), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
            area.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight))
        }
    }

    /** Wallpaper / settings ke hisaab se widget ka style. */
    private fun styleWidget(widget: View) {
        if (widget is SuggestedAppsWidgetView) {
            widget.setApps(suggestedApps, style)
            widget.onAppClick = actions.onAppClick
        }
        if (widget is MusicWidgetView) {
            widget.glassStrength = widgets.strength
            widget.accent = accent
            widget.lightStyle = lightGlass
            widget.touchGlow = widgets.touchGlow
        } else if (widget is GlassCardView) {
            widget.glassStrength = widgets.strength
            widget.accent = accent
            widget.lightStyle = lightGlass
            widget.touchGlow = widgets.touchGlow
        }
    }

    override fun getItemCount(): Int = pages.size
}
