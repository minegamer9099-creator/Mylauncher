package com.example.mylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.min

/**
 * Ek home page ka grid: [cols] x [rows] cells. Har child ki jagah (cx, cy) aur size (sx, sy) cells mein hoti hai,
 * isliye apps, folders aur alag-alag size ke widgets ek hi tarah se rakhe jaate hain.
 * Edit mode mein children ko touch nahi milta (tap = select, long-press = drag, swipe = page badalna).
 */
class CellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    class LP(var cx: Int, var cy: Int, var sx: Int, var sy: Int) :
        ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

    var cols = 4
    var rows = 6

    /** > 0 ho to har row itni height ki (pehle jaisa: rows top se shuru, baaki jagah khaali). */
    var rowPx = 0

    var editing = false
    var onEditTap: ((Float, Float) -> Unit)? = null

    /** Selected widget ka resize frame (apne touch khud sambhalta hai). */
    var resizeFrame: View? = null

    var cellW = 0
        private set
    var cellH = 0
        private set

    // Drop preview: mode 0 = koi nahi, 1 = valid, 2 = invalid, 3 = folder banega / folder mein jayega
    private var pvX = 0
    private var pvY = 0
    private var pvSx = 1
    private var pvSy = 1
    private var pvMode = 0
    private val pvPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pvRect = RectF()

    // Position badalne ka animation (FLIP): purani jagah se nayi jagah tak
    private var oldPositions: HashMap<View, IntArray>? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var tapX = 0f
    private var tapY = 0f
    private var tapTime = 0L
    private var childGesture = false

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LP(0, 0, 1, 1)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LP(0, 0, 1, 1)
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LP(0, 0, 1, 1)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LP

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        cellW = ((w - paddingLeft - paddingRight) / cols.coerceAtLeast(1)).coerceAtLeast(0)
        cellH = if (rowPx > 0) rowPx else ((h - paddingTop - paddingBottom) / rows.coerceAtLeast(1)).coerceAtLeast(0)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val lp = c.layoutParams as LP
            c.measure(
                MeasureSpec.makeMeasureSpec(cellW * lp.sx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellH * lp.sy, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(w, resolveSize(rows * cellH + paddingTop + paddingBottom, heightSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val lp = c.layoutParams as LP
            val left = paddingLeft + lp.cx * cellW
            val top = paddingTop + lp.cy * cellH
            c.layout(left, top, left + cellW * lp.sx, top + cellH * lp.sy)
        }
        playFlip()
    }

    // ---- position badalne ka smooth animation ----
    /** Position badalne se pehle call karo: agle layout mein children purani jagah se nayi jagah tak slide karenge. */
    fun prepareFlip() {
        val map = HashMap<View, IntArray>()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            map[c] = intArrayOf(c.left, c.top)
        }
        oldPositions = map
    }

    private fun playFlip() {
        val old = oldPositions ?: return
        oldPositions = null
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val o = old[c] ?: continue
            val dx = o[0] - c.left
            val dy = o[1] - c.top
            if (dx == 0 && dy == 0) continue
            c.animate().cancel()
            c.translationX = dx.toFloat()
            c.translationY = dy.toFloat()
            c.animate().translationX(0f).translationY(0f).setDuration(180).start()
        }
    }

    // ---- helpers ----
    fun childForId(id: String): View? {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.tag == id) return c
        }
        return null
    }

    /** Local pixel -> cell (grid ke andar clamp). */
    fun cellAt(localX: Float, localY: Float): IntArray {
        val cw = cellW.coerceAtLeast(1)
        val ch = cellH.coerceAtLeast(1)
        val x = ((localX - paddingLeft) / cw).toInt().coerceIn(0, cols - 1)
        val y = ((localY - paddingTop) / ch).toInt().coerceIn(0, rows - 1)
        return intArrayOf(x, y)
    }

    /** Screen coordinates -> local pixels. [scale] = is page ke upar ke views ka total scale (edit mode mein pager chhota hota hai). */
    fun toLocal(rawX: Float, rawY: Float, scale: Float): FloatArray {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val s = if (scale > 0f) scale else 1f
        return floatArrayOf((rawX - loc[0]) / s, (rawY - loc[1]) / s)
    }

    /** Cell rect ka screen par rect (preview / drop animation ke liye). */
    fun screenRect(cx: Int, cy: Int, sx: Int, sy: Int, scale: Float): RectF {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val left = loc[0] + (paddingLeft + cx * cellW) * scale
        val top = loc[1] + (paddingTop + cy * cellH) * scale
        return RectF(left, top, left + cellW * sx * scale, top + cellH * sy * scale)
    }

    fun setPreview(x: Int, y: Int, sx: Int, sy: Int, mode: Int) {
        if (x == pvX && y == pvY && sx == pvSx && sy == pvSy && mode == pvMode) return
        pvX = x
        pvY = y
        pvSx = sx
        pvSy = sy
        pvMode = mode
        invalidate()
    }

    fun clearPreview() {
        if (pvMode == 0) return
        pvMode = 0
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (pvMode == 0) return
        val left = (paddingLeft + pvX * cellW).toFloat()
        val top = (paddingTop + pvY * cellH).toFloat()
        val inset = 6f * resources.displayMetrics.density
        pvRect.set(left + inset, top + inset, left + cellW * pvSx - inset, top + cellH * pvSy - inset)
        val radius = min(pvRect.width(), pvRect.height()) * 0.28f
        when (pvMode) {
            3 -> {
                // Folder: icon ke upar gol glow ring
                val cx = pvRect.centerX()
                val cy = pvRect.centerY()
                val r = min(pvRect.width(), pvRect.height()) * 0.5f
                pvPaint.style = Paint.Style.FILL
                pvPaint.color = Color.argb(60, 255, 255, 255)
                canvas.drawCircle(cx, cy, r, pvPaint)
                pvPaint.style = Paint.Style.STROKE
                pvPaint.strokeWidth = 2f * resources.displayMetrics.density
                pvPaint.color = Color.argb(200, 255, 255, 255)
                canvas.drawCircle(cx, cy, r, pvPaint)
            }
            else -> {
                val ok = pvMode == 1
                pvPaint.style = Paint.Style.FILL
                pvPaint.color = if (ok) Color.argb(48, 255, 255, 255) else Color.argb(64, 255, 69, 58)
                canvas.drawRoundRect(pvRect, radius, radius, pvPaint)
                pvPaint.style = Paint.Style.STROKE
                pvPaint.strokeWidth = 2f * resources.displayMetrics.density
                pvPaint.color = if (ok) Color.argb(190, 255, 255, 255) else Color.argb(220, 255, 69, 58)
                canvas.drawRoundRect(pvRect, radius, radius, pvPaint)
            }
        }
    }

    // ---- edit mode touch ----
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!editing) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val f = resizeFrame
                childGesture = f is ResizeFrame && f.visibility == VISIBLE &&
                    f.hitsControl(ev.x - f.left, ev.y - f.top)
                return !childGesture
            }
        }
        return !childGesture
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!editing) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapX = ev.x
                tapY = ev.y
                tapTime = ev.eventTime
            }
            MotionEvent.ACTION_UP -> {
                if (abs(ev.x - tapX) < slop && abs(ev.y - tapY) < slop && ev.eventTime - tapTime < 400) {
                    onEditTap?.invoke(ev.x, ev.y)
                }
            }
        }
        return true
    }
}
