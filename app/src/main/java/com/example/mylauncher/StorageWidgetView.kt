package com.example.mylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.util.AttributeSet
import android.util.TypedValue
import kotlin.math.min

/** Glass card: phone ki storage ka ring + "used / total". */
class StorageWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private var usedPercent = 0
    private var usedText = ""
    private val oval = RectF()

    private val ringBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f * density
    }
    private val ringFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 9f * density
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }

    init {
        readStorage()
    }

    private fun readStorage() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - free
            usedPercent = if (total > 0) (used * 100 / total).toInt() else 0
            usedText = Formatter.formatShortFileSize(context, used) + " / " +
                Formatter.formatShortFileSize(context, total)
        } catch (e: Exception) {
            usedPercent = 0
            usedText = ""
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        readStorage() // page dikhte hi taaza value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ringBg.color = if (lightStyle) Color.parseColor("#26000000") else Color.parseColor("#33FFFFFF")
        ringFg.color = accent
        percentPaint.color = primaryColor
        subPaint.color = secondaryColor

        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 20f * density
        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, 0f, 360f, false, ringBg)
        canvas.drawArc(oval, -90f, 360f * usedPercent / 100f, false, ringFg)

        val baseline = cy - (percentPaint.ascent() + percentPaint.descent()) / 2f - 6f * density
        canvas.drawText("$usedPercent%", cx, baseline, percentPaint)
        canvas.drawText("Storage", cx, baseline + 16f * density, subPaint)
        if (usedText.isNotEmpty()) {
            canvas.drawText(usedText, cx, height - 12f * density, subPaint)
        }
    }
}
