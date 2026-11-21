package com.example.mylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** ColorOS ke "AI Suggest" jaisa glass card: sabse zyada use hone wale 4 apps (2x2). */
class SuggestedAppsWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private var apps: List<AppInfo> = emptyList()
    private var icons: List<Drawable> = emptyList()
    var onAppClick: ((AppInfo, View) -> Unit)? = null

    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }
    private val clipPath = Path()
    private val cell = RectF()
    private var downIndex = -1
    private var lastTapIndex = -1

    fun setApps(list: List<AppInfo>, style: IconStyle) {
        apps = list.take(4)
        // Drawable ki copy: bounds alag-alag jagah ke icons ko bigaade nahi
        icons = apps.map { app ->
            val d = style.drawableFor(app)
            d.constantState?.newDrawable(resources)?.mutate() ?: d
        }
        invalidate()
    }

    private fun cellBounds(index: Int, out: RectF) {
        val pad = 14f * density
        val captionHeight = 18f * density
        val gap = 10f * density
        val availW = width - 2 * pad
        val availH = height - 2 * pad - captionHeight
        val size = min((availW - gap) / 2f, (availH - gap) / 2f)
        val gridW = size * 2 + gap
        val left0 = (width - gridW) / 2f
        val col = index % 2
        val row = index / 2
        val l = left0 + col * (size + gap)
        val t = pad + row * (size + gap)
        out.set(l, t, l + size, t + size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        captionPaint.color = secondaryColor
        for (i in icons.indices) {
            cellBounds(i, cell)
            val radius = cell.width() * 0.28f
            clipPath.reset()
            clipPath.addRoundRect(cell, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            icons[i].setBounds(cell.left.toInt(), cell.top.toInt(), cell.right.toInt(), cell.bottom.toInt())
            icons[i].draw(canvas)
            canvas.restore()
        }
        canvas.drawText("Suggested", width / 2f, height - 10f * density, captionPaint)
    }

    /** Jis icon pe tap hua uska rect (is view ke coordinates mein). Launch animation isi se shuru hoti hai. */
    fun tappedIconBounds(out: RectF): Boolean {
        if (lastTapIndex < 0 || lastTapIndex >= apps.size) return false
        cellBounds(lastTapIndex, out)
        return true
    }

    private fun hit(x: Float, y: Float): Int {
        for (i in apps.indices) {
            cellBounds(i, cell)
            if (cell.contains(x, y)) return i
        }
        return -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        handleGlow(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downIndex = hit(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val index = hit(event.x, event.y)
                if (index >= 0 && index == downIndex) {
                    lastTapIndex = index
                    onAppClick?.invoke(apps[index], this)
                }
                downIndex = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
