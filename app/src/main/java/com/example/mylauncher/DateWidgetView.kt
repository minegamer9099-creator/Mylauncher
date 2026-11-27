package com.example.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Glass card ke andar bada date number + din + mahina. */
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

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        weekdayPaint.color = primaryColor
        monthPaint.color = secondaryColor
        val now = Date()
        val locale = Locale.getDefault()
        val day = SimpleDateFormat("d", locale).format(now)
        val weekday = SimpleDateFormat("EEEE", locale).format(now)
        val month = SimpleDateFormat("MMMM", locale).format(now)

        val cx = width / 2f
        val cy = height / 2f

        dayPaint.color = accent
        val dayBaseline = cy - (dayPaint.ascent() + dayPaint.descent()) / 2f - 10f * density
        canvas.drawText(day, cx, dayBaseline, dayPaint)
        canvas.drawText(weekday, cx, dayBaseline + 28f * density, weekdayPaint)
        canvas.drawText(month, cx, dayBaseline + 46f * density, monthPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, tickReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(tickReceiver)
        } catch (e: IllegalArgumentException) {
            // pehle se registered nahi tha
        }
    }
}
