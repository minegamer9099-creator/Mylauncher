package com.example.mylauncher

import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Home / dock / folder ke saare items ka drag-and-drop.
 *
 *  - Ghost (floating copy) dragLayer mein ghoomta hai; asli view chhup jata hai.
 *  - Hover pe destination ka preview: khaali cell, push (aas-paas ke icons khisakte hain), naya folder, folder mein add,
 *    dock slot, folder ke andar reorder.
 *  - Screen ke kinare 0.6 s rukne par page badalta hai (aakhri page ke baad naya page banta hai).
 *  - Drop pe model badalta hai, save hota hai, aur ghost destination pe utarta hai.
 * Saare touches [MainActivity.dispatchTouchEvent] se yahan aate hain, isliye pager / icons beech mein nahi aate.
 */
class DragController(private val a: MainActivity) {

    enum class Src { HOME, DOCK, FOLDER }

    private enum class Mode { NONE, MOVE, PUSH, FOLDER_NEW, FOLDER_ADD, DOCK, FOLDER_REORDER, INVALID }

    var active = false
        private set

    private var entry: HomeEntry? = null
    private var src = Src.HOME
    private var srcFolderId: String? = null
    private var ghost: View? = null
    private var origin: View? = null
    private var grabX = 0f
    private var grabY = 0f
    private var startRawX = 0f
    private var startRawY = 0f
    private var moved = false
    private val slop = ViewConfiguration.get(a).scaledTouchSlop * 2

    // Hover ka natija
    private var mode = Mode.NONE
    private var tPage = 0
    private var tX = 0
    private var tY = 0
    private var tTarget: HomeEntry? = null
    private var tIndex = 0
    private var previewCells: CellLayout? = null
    private var lastX = 0f
    private var lastY = 0f

    // Page edge pe rukna
    private val handler = Handler(Looper.getMainLooper())
    private var edgeDir = 0
    private val edgeRunnable = Runnable { onEdgeHold() }
    private val autoPages = HashSet<Int>()

    private class Landing(val rect: RectF, val hideId: String?, val shrink: Boolean, val dockIndex: Int = -1)

    /** [e] ko uthao. [view] = asli view (chhupa diya jata hai), [folderId] = folder popup se uthane par. */
    fun start(e: HomeEntry, source: Src, view: View?, rawX: Float, rawY: Float, folderId: String? = null) {
        if (active) return
        entry = e
        src = source
        srcFolderId = folderId
        origin = view
        startRawX = rawX
        startRawY = rawY
        moved = false
        mode = Mode.NONE
        autoPages.clear()
        a.prepareForDrag()

        val g = a.viewFactory.ghostFor(e)
        val inPager = view != null && isInside(view, a.homePager)
        val visScale = if (inPager) max(a.homePager.scaleX, 0.1f) else 1f
        val multi = e.sx > 1 || e.sy > 1 || !(e.kind == EntryKind.APP || e.kind == EntryKind.FOLDER)
        val w: Int
        val h: Int
        if (multi && view != null) {
            w = max((view.width * visScale).toInt(), a.dp(40))
            h = max((view.height * visScale).toInt(), a.dp(40))
        } else {
            w = if (view != null && view.width > 0) view.width else a.dp(80)
            h = if (view != null && view.height > 0) view.height else a.dp(96)
        }
        a.dragLayer.addView(g, FrameLayout.LayoutParams(w, h))
        ghost = g
        val startScale = if (multi) 1f else visScale
        g.scaleX = startScale
        g.scaleY = startScale
        if (multi && view != null) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            grabX = (rawX - loc[0]).coerceIn(0f, w.toFloat())
            grabY = (rawY - loc[1]).coerceIn(0f, h.toFloat())
        } else {
            grabX = w / 2f
            grabY = h / 2f
        }
        positionGhost(rawX, rawY)
        g.animate().scaleX(startScale * 1.1f).scaleY(startScale * 1.1f).setDuration(120).start()
        view?.alpha = 0f
        a.dragLayer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        active = true
        updateHover(rawX, rawY)
    }

    fun onTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.rawX - startRawX) > slop || abs(ev.rawY - startRawY) > slop) moved = true
                updateHover(ev.rawX, ev.rawY)
            }
            MotionEvent.ACTION_UP -> {
                if (abs(ev.rawX - startRawX) > slop || abs(ev.rawY - startRawY) > slop) moved = true
                updateHover(ev.rawX, ev.rawY)
                drop()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Hover
    // ---------------------------------------------------------------------------------------
    private fun positionGhost(rawX: Float, rawY: Float) {
        val g = ghost ?: return
        val dl = IntArray(2)
        a.dragLayer.getLocationOnScreen(dl)
        g.translationX = rawX - dl[0] - grabX
        g.translationY = rawY - dl[1] - grabY
    }

    private fun clearPreviews() {
        previewCells?.clearPreview()
        previewCells = null
        a.setDockHighlight(false)
    }

    private fun updateHover(rawX: Float, rawY: Float) {
        val e = entry ?: return
        lastX = rawX
        lastY = rawY
        positionGhost(rawX, rawY)

        // 1) Folder popup khula hai
        val panel = a.folderPanelRect()
        if (panel != null) {
            if (panel.contains(rawX.toInt(), rawY.toInt())) {
                clearPreviews()
                setEdge(0)
                if (e.kind == EntryKind.APP && src == Src.FOLDER) {
                    mode = Mode.FOLDER_REORDER
                    tIndex = a.folderIndexAt(rawX, rawY)
                } else {
                    mode = Mode.NONE
                }
                return
            }
            if (src == Src.FOLDER) a.closeFolder() // folder se bahar nikla: popup band, home pe drop ho sakta hai
        }

        // 2) Dock
        val overDock = a.dockRect().contains(rawX.toInt(), rawY.toInt())
        if (overDock && e.kind != EntryKind.APP) {
            clearPreviews()
            setEdge(0)
            mode = Mode.INVALID // dock mein sirf apps
            return
        }
        if (overDock) {
            previewCells?.clearPreview()
            previewCells = null
            setEdge(0)
            val full = src != Src.DOCK && a.state.dock.size >= HomeOps.MAX_DOCK
            mode = if (full) Mode.INVALID else Mode.DOCK
            tIndex = a.dockIndexAt(rawX, if (src == Src.DOCK) e.pkg else null)
            a.setDockHighlight(!full)
            return
        }
        a.setDockHighlight(false)

        // 3) Home page
        updateHomeHover(e, rawX, rawY)
        checkEdge(rawX)
    }

    private fun updateHomeHover(e: HomeEntry, rawX: Float, rawY: Float) {
        val pager = a.homePager
        val page = pager.currentItem
        val cells = a.pagerAdapter?.cellsFor(page)
        if (cells == null || pager.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
            clearPreviews()
            mode = Mode.NONE
            return
        }
        if (previewCells !== cells) {
            previewCells?.clearPreview()
            previewCells = cells
        }
        val local = cells.toLocal(rawX, rawY, pager.scaleX)
        val cell = cells.cellAt(local[0], local[1])
        var cx = cell[0]
        var cy = cell[1]
        val sx = e.sx
        val sy = e.sy
        if (sx > 1 || sy > 1) {
            cx = (cx - (sx - 1) / 2).coerceIn(0, max(0, a.grid.cols - sx))
            cy = (cy - (sy - 1) / 2).coerceIn(0, max(0, a.grid.rows(page) - sy))
        }
        val ops = a.ops
        tPage = page
        tTarget = null

        if (sx == 1 && sy == 1) {
            val occ = ops.occupantAt(page, cx, cy, e.id)
            val central = isCentral(cells, local, cx, cy)
            when {
                occ == null -> setHome(cells, Mode.MOVE, cx, cy, 1, 1, 1)
                occ.isSingleCell && occ.kind == EntryKind.APP && e.kind == EntryKind.APP && central -> {
                    tTarget = occ
                    setHome(cells, Mode.FOLDER_NEW, occ.cx, occ.cy, 1, 1, 3)
                }
                occ.isSingleCell && occ.kind == EntryKind.FOLDER && e.kind == EntryKind.APP && central -> {
                    tTarget = occ
                    setHome(cells, Mode.FOLDER_ADD, occ.cx, occ.cy, 1, 1, 3)
                }
                occ.isSingleCell -> setHome(cells, Mode.PUSH, cx, cy, 1, 1, 1)
                else -> {
                    val spot = ops.findFree(page, 1, 1, e.id, cx, cy)
                    if (spot != null) setHome(cells, Mode.MOVE, spot[0], spot[1], 1, 1, 1)
                    else setHome(cells, Mode.INVALID, cx, cy, 1, 1, 2)
                }
            }
        } else {
            if (ops.isFree(page, cx, cy, sx, sy, e.id)) {
                setHome(cells, Mode.MOVE, cx, cy, sx, sy, 1)
            } else {
                val spot = ops.findFree(page, sx, sy, e.id, cx, cy)
                if (spot != null) setHome(cells, Mode.MOVE, spot[0], spot[1], sx, sy, 1)
                else setHome(cells, Mode.INVALID, cx, cy, sx, sy, 2)
            }
        }
    }

    private fun setHome(cells: CellLayout, m: Mode, x: Int, y: Int, sx: Int, sy: Int, previewMode: Int) {
        mode = m
        tX = x
        tY = y
        cells.setPreview(x, y, sx, sy, previewMode)
    }

    /** Pointer cell ke beech ke hisse mein hai (folder banane / folder mein daalne ka iraada)? */
    private fun isCentral(cells: CellLayout, local: FloatArray, cx: Int, cy: Int): Boolean {
        val centerX = cells.paddingLeft + (cx + 0.5f) * cells.cellW
        val centerY = cells.paddingTop + (cy + 0.5f) * cells.cellH
        return abs(local[0] - centerX) < cells.cellW * 0.28f && abs(local[1] - centerY) < cells.cellH * 0.28f
    }

    // ---------------------------------------------------------------------------------------
    // Page edge
    // ---------------------------------------------------------------------------------------
    private fun checkEdge(rawX: Float) {
        val edge = a.dp(28)
        val w = a.rootLayout.width
        setEdge(
            when {
                rawX < edge -> -1
                rawX > w - edge -> 1
                else -> 0
            }
        )
    }

    private fun setEdge(dir: Int) {
        if (dir == edgeDir) return
        edgeDir = dir
        handler.removeCallbacks(edgeRunnable)
        if (dir != 0) handler.postDelayed(edgeRunnable, EDGE_HOLD_MS)
    }

    private fun onEdgeHold() {
        if (!active || edgeDir == 0) return
        val e = entry ?: return
        val cur = a.homePager.currentItem
        val target = cur + edgeDir
        if (target < 0) return
        if (target >= a.state.pageCount) {
            // Aakhri page ke baad naya page: sirf tab jab aakhri page pe dragged item ke alawa kuch ho
            val last = a.state.pageCount - 1
            val hasContent = a.state.entries.any { it.page == last && it.id != e.id }
            if (!hasContent || a.state.pageCount >= HomeOps.MAX_PAGES) return
            a.ops.addPage()
            autoPages.add(target)
            a.onPagesChanged()
        }
        previewCells?.clearPreview()
        previewCells = null
        a.homePager.setCurrentItem(target, true)
        handler.postDelayed(edgeRunnable, EDGE_REPEAT_MS)
    }

    private fun stopEdge() {
        handler.removeCallbacks(edgeRunnable)
        edgeDir = 0
    }

    // ---------------------------------------------------------------------------------------
    // Drop / cancel
    // ---------------------------------------------------------------------------------------
    private fun drop() {
        val e = entry ?: return
        stopEdge()
        clearPreviews()
        val o = origin

        // Bina hile chhoda: app = menu, widget = select
        if (!moved) {
            o?.alpha = 1f
            val kind = e.kind
            val folderId = srcFolderId
            val pkg = e.pkg
            finish()
            when (kind) {
                EntryKind.APP -> a.showAppMenuFor(pkg, o, folderId)
                EntryKind.WIDGET, EntryKind.BUILTIN -> a.selectEntry(e.id)
                else -> {}
            }
            return
        }

        val landing = commit(e)
        if (landing == null) {
            cancel()
            return
        }
        a.afterModelChange(true)
        pruneAutoPages()
        val hide = if (landing.dockIndex >= 0) a.dockChildAt(landing.dockIndex)
        else landing.hideId?.let { id -> findDestView(id) }
        hide?.alpha = 0f
        o?.alpha = 1f
        landGhost(landing) { hide?.alpha = 1f }
    }

    fun cancel() {
        if (!active) return
        stopEdge()
        clearPreviews()
        val o = origin
        val g = release()
        pruneAutoPages()
        if (g == null) {
            o?.alpha = 1f
            return
        }
        if (o == null || !o.isAttachedToWindow) {
            a.dragLayer.removeView(g)
            return
        }
        val loc = IntArray(2)
        o.getLocationOnScreen(loc)
        val dl = IntArray(2)
        a.dragLayer.getLocationOnScreen(dl)
        g.animate()
            .translationX(loc[0] - dl[0] + (o.width - g.width) / 2f)
            .translationY(loc[1] - dl[1] + (o.height - g.height) / 2f)
            .setDuration(150)
            .withEndAction {
                o.alpha = 1f
                a.dragLayer.removeView(g)
            }
            .start()
    }

    private fun landGhost(l: Landing, done: () -> Unit) {
        val g = release()
        if (g == null) {
            done()
            return
        }
        val dl = IntArray(2)
        a.dragLayer.getLocationOnScreen(dl)
        val rect = if (l.rect.isEmpty) RectF(lastX - 1f, lastY - 1f, lastX + 1f, lastY + 1f) else l.rect
        val tx = rect.centerX() - dl[0] - g.width / 2f
        val ty = rect.centerY() - dl[1] - g.height / 2f
        val s = if (l.shrink) 0.3f else (rect.width() / max(g.width, 1)).coerceIn(0.3f, 2f)
        g.animate()
            .translationX(tx)
            .translationY(ty)
            .scaleX(s)
            .scaleY(s)
            .alpha(if (l.shrink) 0f else 1f)
            .setDuration(170)
            .withEndAction {
                a.dragLayer.removeView(g)
                done()
            }
            .start()
    }

    /** Drag state saaf; ghost view wapas deta hai (caller usse animate karke hatata hai, naye drag se takrav nahi). */
    private fun release(): View? {
        val g = ghost
        ghost = null
        origin = null
        entry = null
        srcFolderId = null
        mode = Mode.NONE
        tTarget = null
        active = false
        a.setDockHighlight(false)
        return g
    }

    /** Bina animation ke drag khatam (ghost turant hata do). */
    private fun finish() {
        release()?.let {
            it.animate().cancel()
            a.dragLayer.removeView(it)
        }
    }

    private fun findDestView(id: String): View? {
        val st = a.state
        val e = st.find(id) ?: return null
        return a.pagerAdapter?.cellsFor(e.page)?.childForId(id)
    }

    /** Model badlo (drop ka asli kaam). Landing = ghost kahan utarega. null = kuch nahi hua. */
    private fun commit(e: HomeEntry): Landing? {
        val st = a.state
        val ops = a.ops
        when (mode) {
            Mode.MOVE, Mode.PUSH -> {
                detach(e)
                if (mode == Mode.PUSH) {
                    ops.pushInsert(e, tPage, tX, tY)
                } else {
                    e.page = tPage
                    e.cx = tX
                    e.cy = tY
                    if (!st.entries.contains(e)) st.entries.add(e)
                }
                ops.cleanFolders()
                val cells = a.pagerAdapter?.cellsFor(e.page)
                val rect = cells?.screenRect(e.cx, e.cy, e.sx, e.sy, a.homePager.scaleX) ?: RectF()
                return Landing(rect, e.id, false)
            }
            Mode.FOLDER_NEW -> {
                val t = tTarget ?: return null
                detach(e)
                st.entries.remove(e)
                val f = ops.createFolder(t, e.pkg, defaultFolderName(t.pkg, e.pkg))
                ops.cleanFolders()
                val cells = a.pagerAdapter?.cellsFor(f.page)
                return Landing(cells?.screenRect(f.cx, f.cy, 1, 1, a.homePager.scaleX) ?: RectF(), f.id, true)
            }
            Mode.FOLDER_ADD -> {
                val t = tTarget ?: return null
                detach(e)
                st.entries.remove(e)
                if (e.pkg !in t.packages) t.packages.add(e.pkg)
                ops.cleanFolders()
                val cells = a.pagerAdapter?.cellsFor(t.page)
                return Landing(cells?.screenRect(t.cx, t.cy, 1, 1, a.homePager.scaleX) ?: RectF(), t.id, true)
            }
            Mode.DOCK -> {
                detach(e)
                st.entries.remove(e)
                val idx = tIndex.coerceIn(0, st.dock.size)
                st.dock.add(idx, e.pkg)
                ops.cleanFolders()
                return Landing(a.dockSlotRect(idx, st.dock.size), null, false, idx)
            }
            Mode.FOLDER_REORDER -> {
                val f = st.find(srcFolderId ?: return null) ?: return null
                f.packages.remove(e.pkg)
                f.packages.add(tIndex.coerceIn(0, f.packages.size), e.pkg)
                a.refreshOpenFolder()
                return Landing(RectF(), null, true)
            }
            else -> return null
        }
    }

    /** Source se hatao (HOME ka asli entry nahi: wo move hota hai ya caller hata deta hai). */
    private fun detach(e: HomeEntry) {
        val st = a.state
        when (src) {
            Src.DOCK -> st.dock.remove(e.pkg)
            Src.FOLDER -> srcFolderId?.let { id -> st.find(id)?.packages?.remove(e.pkg) }
            Src.HOME -> {}
        }
    }

    private fun defaultFolderName(p1: String, p2: String): String {
        val c1 = a.appByPkg[p1]?.category ?: -1
        val c2 = a.appByPkg[p2]?.category ?: -1
        if (c1 >= 0 && c1 == c2) {
            val n = categoryName(c1)
            if (n != "Others") return n
        }
        return "Folder"
    }

    /** Khaali auto-bane aakhri pages hata do (user ne unme kuch rakha nahi). */
    private fun pruneAutoPages() {
        var changed = false
        while (a.state.pageCount > 1) {
            val last = a.state.pageCount - 1
            if (last in autoPages && a.state.isPageEmpty(last)) {
                a.ops.removePage(last)
                autoPages.remove(last)
                changed = true
            } else {
                break
            }
        }
        if (changed) a.onPagesChanged()
        autoPages.clear()
    }

    private fun isInside(v: View, ancestor: View): Boolean {
        var p: Any? = v
        while (p is View) {
            if (p === ancestor) return true
            p = p.parent
        }
        return false
    }

    companion object {
        private const val EDGE_HOLD_MS = 600L
        private const val EDGE_REPEAT_MS = 900L
    }
}
