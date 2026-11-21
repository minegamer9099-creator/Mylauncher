package com.example.mylauncher

import android.appwidget.AppWidgetHostView
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** Home screen mein kitne columns hain (ColorOS: 4). */
const val HOME_COLUMNS = 4

/**
 * ViewPager2 ka adapter: har item ek home screen page hai.
 * Pehle page (position 0) pe date + glass clock header hota hai; har page pe ek [CellLayout] jisme
 * [HomeState] ke items (apps, folders, widgets, search) apni cells mein dikhte hain.
 * Views [HomeViewFactory] se aate hain (cached), isliye page badalne / item hilne par dobara nahi bante.
 */
class HomePagerAdapter(private val a: MainActivity) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: View = view.findViewById(R.id.clockHeader)
        val clock: GlassClockView = view.findViewById(R.id.glassClock)
        val clockCentered: View = view.findViewById(R.id.clockCentered)
        val clockCompact: View = view.findViewById(R.id.clockCompact)
        val clockCompactView: GlassClockView = view.findViewById(R.id.glassClockCompact)
        val cells: CellLayout = view.findViewById(R.id.pageCells)
        var page = -1
    }

    private val bound = HashMap<Int, PageViewHolder>()
    private val notifiedSize = HashMap<Int, Long>() // widgetId -> last (w, h) jo widget ko batayi
    var editing = false
        private set

    override fun getItemCount(): Int = a.state.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.page_home, parent, false)
        val holder = PageViewHolder(view)
        holder.cells.onEditTap = { x, y -> a.onEditTap(holder.page, x, y) }
        return holder
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.page = position
        bound[position] = holder
        bindHeader(holder, position)
        syncHolder(holder, false)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        if (bound[holder.page] === holder) bound.remove(holder.page)
    }

    fun holderFor(page: Int): PageViewHolder? = bound[page]

    fun cellsFor(page: Int): CellLayout? = bound[page]?.cells

    private fun bindHeader(holder: PageViewHolder, position: Int) {
        val first = position == 0
        // Depth wallpaper on ho to bada clock peeche wali layer mein hai: yahan jagah bachi rehti hai (INVISIBLE), clock nahi dikhta
        holder.header.visibility = when {
            !first -> View.GONE
            a.depthActive -> View.INVISIBLE
            else -> View.VISIBLE
        }
        if (!first) return
        val compact = a.prefs.clockStyle == "compact"
        holder.clockCentered.visibility = if (compact) View.GONE else View.VISIBLE
        holder.clockCompact.visibility = if (compact) View.VISIBLE else View.GONE
        holder.clock.accentColor = a.effectiveAccent()
        holder.clockCompactView.compact = true
        holder.clockCompactView.accentColor = COMPACT_CLOCK_COLOR
    }

    /** Model ke hisaab se ek page ke views milao (naye jodo, purane hatao, position badlo). */
    fun syncHolder(h: PageViewHolder, animate: Boolean) {
        val page = h.page
        if (page < 0) return
        val cells = h.cells
        val grid = a.grid
        cells.cols = grid.cols
        cells.rows = grid.rows(page)
        cells.rowPx = if (page == 0) a.rowPxFirst else a.rowPxOther
        val pad = a.dp(8)
        cells.setPadding(pad, if (page == 0) 0 else a.pageTopPad, pad, 0)
        cells.editing = editing

        val entries = a.state.entriesOn(page)
        val ids = HashSet<String>()
        for (e in entries) ids.add(e.id)

        if (animate) cells.prepareFlip()
        for (i in cells.childCount - 1 downTo 0) {
            val c = cells.getChildAt(i)
            val tag = c.tag
            if (tag == ResizeFrame.FRAME_TAG) continue
            if (tag !is String || tag !in ids) cells.removeViewAt(i)
        }
        for (e in entries) {
            val v = a.viewFactory.viewFor(e)
            val parent = v.parent
            if (parent !== cells) {
                (parent as? ViewGroup)?.removeView(v)
                v.translationX = 0f
                v.translationY = 0f
                cells.addView(v, CellLayout.LP(e.cx, e.cy, e.sx, e.sy))
            } else {
                val lp = v.layoutParams as CellLayout.LP
                if (lp.cx != e.cx || lp.cy != e.cy || lp.sx != e.sx || lp.sy != e.sy) {
                    lp.cx = e.cx
                    lp.cy = e.cy
                    lp.sx = e.sx
                    lp.sy = e.sy
                    v.layoutParams = lp
                }
            }
        }
        cells.resizeFrame?.let { f -> if (f.parent === cells) f.bringToFront() }
        cells.requestLayout()
        notifyWidgetSizes(cells, entries)
    }

    /** System widgets ko unki cell size batao (sirf badalne par). */
    private fun notifyWidgetSizes(cells: CellLayout, entries: List<HomeEntry>) {
        for (e in entries) {
            if (e.kind != EntryKind.WIDGET) continue
            val v = cells.childForId(e.id) as? AppWidgetHostView ?: continue
            cells.post {
                val w = cells.cellW * e.sx
                val h = cells.cellH * e.sy
                val key = (w.toLong() shl 32) or h.toLong()
                if (w > 0 && h > 0 && notifiedSize[e.widgetId] != key) {
                    notifiedSize[e.widgetId] = key
                    a.widgetHost.notifySize(v, w, h)
                }
            }
        }
    }

    /** Bound saare pages sync (drop / add / remove ke baad). */
    fun syncAll(animate: Boolean = false) {
        for (h in bound.values.toList()) syncHolder(h, animate)
    }

    /** Header (clock style) + saare pages dobara (settings badli). */
    fun refreshAll() {
        for (h in bound.values.toList()) {
            bindHeader(h, h.page)
            syncHolder(h, false)
        }
    }

    fun setEditing(on: Boolean) {
        editing = on
        for (h in bound.values) {
            h.cells.editing = on
            if (!on) h.cells.clearPreview()
        }
    }

    private companion object {
        val COMPACT_CLOCK_COLOR: Int = Color.parseColor("#F2FFFFFF")
    }
}
