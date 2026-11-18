package com.example.mylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Glass card ke andar bada date number + din + mahina.
 * Teeno strings ek baar (tick pe) nikalte hain; onDraw mein SimpleDateFormat nahi banta.
 */
class DateWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 56f, resources.displayMetrics)
    }
    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
    }
    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }

    private val locale = Locale.getDefault()
    private val dayFormat = SimpleDateFormat("d", locale)
    private val weekdayFormat = SimpleDateFormat("EEEE", locale)
    private val monthFormat = SimpleDateFormat("MMMM", locale)
    private var day = ""
    private var weekday = ""
    private var month = ""
    private val tickListener = Runnable {
        updateDate()
        invalidate()
    }

    init {
        updateDate()
    }

    private fun updateDate() {
        val now = Date()
        day = dayFormat.format(now)
        weekday = weekdayFormat.format(now)
        month = monthFormat.format(now)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        weekdayPaint.color = primaryColor
        monthPaint.color = secondaryColor
        dayPaint.color = accent

        val cx = width / 2f
        val cy = height / 2f
        val dayBaseline = cy - (dayPaint.ascent() + dayPaint.descent()) / 2f - 10f * density
        canvas.drawText(day, cx, dayBaseline, dayPaint)
        canvas.drawText(weekday, cx, dayBaseline + 28f * density, weekdayPaint)
        canvas.drawText(month, cx, dayBaseline + 46f * density, monthPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TimeTicker.register(context, tickListener)
        updateDate()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        TimeTicker.unregister(tickListener)
        super.onDetachedFromWindow()
    }
}
