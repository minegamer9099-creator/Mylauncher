package com.example.mylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

/**
 * Glass card ke andar analog clock: ticks, date aur hands.
 * Time / date ek baar (har minute) nikalke yaad rakhte hain; onDraw mein koi allocation nahi.
 */
class AnalogClockWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3FFFFFF")
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.6f * density
    }
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF")
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f * density
    }
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF")
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f * density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }

    private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private var dateText = ""
    private var hourAngle = 0f
    private var minuteAngle = 0f
    private val tickListener = Runnable {
        updateTime()
        invalidate()
    }

    init {
        updateTime()
    }

    private fun updateTime() {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR)
        val minute = cal.get(Calendar.MINUTE)
        hourAngle = (hour + minute / 60f) * 30f
        minuteAngle = minute * 6f
        dateText = dateFormat.format(cal.time)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        tickPaint.color = secondaryColor
        hourPaint.color = primaryColor
        minutePaint.color = primaryColor
        datePaint.color = secondaryColor
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 14f * density

        // 12 ticks
        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            val len = if (i % 3 == 0) 0.14f else 0.08f
            canvas.drawLine(cx, cy - r, cx, cy - r * (1f - len), tickPaint)
            canvas.restore()
        }

        canvas.drawText(dateText, cx, cy - r * 0.46f, datePaint)

        drawHand(canvas, cx, cy, hourAngle, r * 0.50f, hourPaint)
        drawHand(canvas, cx, cy, minuteAngle, r * 0.80f, minutePaint)
        dotPaint.color = accent
        canvas.drawCircle(cx, cy, 4f * density, dotPaint)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, angle: Float, length: Float, paint: Paint) {
        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - length, paint)
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TimeTicker.register(context, tickListener)
        updateTime() // screen pe aate hi taaza time
        invalidate()
    }

    override fun onDetachedFromWindow() {
        TimeTicker.unregister(tickListener)
        super.onDetachedFromWindow()
    }
}
