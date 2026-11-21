package com.example.mylauncher

import android.appwidget.AppWidgetHostView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Edit mode mein selected widget ke upar: border + 4 resize handles (left, top, right, bottom) + remove (x) button.
 * Resize cells pe snap hota hai, widget ki min / max limits aur doosre items ke overlap ka dhyan rakhta hai.
 * Ye CellLayout ka child hai (widget jaisi hi jagah), isliye widget ke saath apne aap hilta hai.
 */
class ResizeFrame(
    private val a: MainActivity,
    val entry: HomeEntry,
    private val minSx: Int,
    private val minSy: Int,
    private val maxSx: Int,
    private val maxSy: Int,
    private val canH: Boolean,
    private val canV: Boolean
) : View(a) {

    private val d = resources.displayMetrics.density
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = Color.WHITE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(28, 255, 255, 255)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 69, 58) }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val inset = 4f * d
    private val handleR = 7f * d
    private val closeR = 12f * d
    private val hitR = 26f * d

    private var active = -1 // 0 left, 1 top, 2 right, 3 bottom, 4 close
    private var startRawX = 0f
    private var startRawY = 0f
    private var sCx = 0
    private var sCy = 0
    private var sSx = 1
    private var sSy = 1

    init {
        tag = FRAME_TAG
    }

    private fun handleX(i: Int): Float = when (i) {
        0 -> inset
        2 -> width - inset
        else -> width / 2f
    }

    private fun handleY(i: Int): Float = when (i) {
        1 -> inset
        3 -> height - inset
        else -> height / 2f
    }

    private fun enabled(i: Int): Boolean = if (i == 0 || i == 2) canH else canV

    private fun hit(x: Float, y: Float): Int {
        if (hypot(x - (width - inset - closeR), y - (inset + closeR)) < hitR) return 4
        for (i in 0..3) {
            if (enabled(i) && hypot(x - handleX(i), y - handleY(i)) < hitR) return i
        }
        return -1
    }

    fun hitsControl(x: Float, y: Float): Boolean = hit(x, y) >= 0

    override fun onDraw(canvas: Canvas) {
        rect.set(inset, inset, width - inset, height - inset)
        val r = 18f * d
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(rect, r, r, borderPaint)
        for (i in 0..3) {
            if (enabled(i)) canvas.drawCircle(handleX(i), handleY(i), handleR, handlePaint)
        }
        val cx = width - inset - closeR
        val cy = inset + closeR
        canvas.drawCircle(cx, cy, closeR, closePaint)
        val k = closeR * 0.4f
        canvas.drawLine(cx - k, cy - k, cx + k, cy + k, xPaint)
        canvas.drawLine(cx - k, cy + k, cx + k, cy - k, xPaint)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val h = hit(ev.x, ev.y)
                if (h < 0) return false
                active = h
                parent?.requestDisallowInterceptTouchEvent(true) // pager page na badal de
                startRawX = ev.rawX
                startRawY = ev.rawY
                sCx = entry.cx
                sCy = entry.cy
                sSx = entry.sx
                sSy = entry.sy
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (active in 0..3) resizeBy(ev.rawX - startRawX, ev.rawY - startRawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (active == 4 && hit(ev.x, ev.y) == 4) {
                    active = -1
                    a.removeEntry(entry)
                    return true
                }
                if (active in 0..3) a.persistHome()
                active = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (active in 0..3) a.persistHome()
                active = -1
                return true
            }
        }
        return true
    }

    private fun resizeBy(dx: Float, dy: Float) {
        val cell = parent as? CellLayout ?: return
        val scale = if (a.homePager.scaleX > 0f) a.homePager.scaleX else 1f
        val dxc = (dx / scale / cell.cellW.coerceAtLeast(1)).roundToInt()
        val dyc = (dy / scale / cell.cellH.coerceAtLeast(1)).roundToInt()
        var cx = sCx
        var cy = sCy
        var sx = sSx
        var sy = sSy
        when (active) {
            0 -> { cx = sCx + dxc; sx = sSx - dxc }
            2 -> sx = sSx + dxc
            1 -> { cy = sCy + dyc; sy = sSy - dyc }
            3 -> sy = sSy + dyc
        }
        if (sx < minSx) { if (active == 0) cx -= (minSx - sx); sx = minSx }
        if (sx > maxSx) { if (active == 0) cx += (sx - maxSx); sx = maxSx }
        if (sy < minSy) { if (active == 1) cy -= (minSy - sy); sy = minSy }
        if (sy > maxSy) { if (active == 1) cy += (sy - maxSy); sy = maxSy }
        if (cx == entry.cx && cy == entry.cy && sx == entry.sx && sy == entry.sy) return
        if (!a.ops.isFree(entry.page, cx, cy, sx, sy, entry.id)) return // overlap nahi hone dena

        entry.cx = cx
        entry.cy = cy
        entry.sx = sx
        entry.sy = sy
        applyGeometry(cell)
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Entry ki nayi cells widget view aur frame dono pe lagao, aur widget ko nayi size batao. */
    fun applyGeometry(cell: CellLayout) {
        (layoutParams as? CellLayout.LP)?.let {
            it.cx = entry.cx
            it.cy = entry.cy
            it.sx = entry.sx
            it.sy = entry.sy
        }
        val child = cell.childForId(entry.id)
        (child?.layoutParams as? CellLayout.LP)?.let {
            it.cx = entry.cx
            it.cy = entry.cy
            it.sx = entry.sx
            it.sy = entry.sy
        }
        cell.requestLayout()
        if (child is AppWidgetHostView) {
            cell.post { a.widgetHost.notifySize(child, cell.cellW * entry.sx, cell.cellH * entry.sy) }
        }
    }

    companion object {
        const val FRAME_TAG = "resize_frame"
    }
}
