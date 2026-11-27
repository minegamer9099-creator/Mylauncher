package com.example.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.content.ContextCompat
import kotlin.math.min

/** Glass card ke andar battery ring + percent. */
class BatteryWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private var percent = 100
    private var charging = false
    private val oval = RectF()

    private val ringBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 9f * density
    }
    private val ringFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 9f * density
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24f, resources.displayMetrics)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            readBattery(intent)
        }
    }

    private fun readBattery(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) percent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ringBg.color = if (lightStyle) Color.parseColor("#26000000") else Color.parseColor("#33FFFFFF")
        percentPaint.color = primaryColor
        subPaint.color = secondaryColor
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 20f * density

        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, 0f, 360f, false, ringBg)

        ringFg.color = if (percent <= 15 && !charging) Color.parseColor("#FF453A") else accent
        canvas.drawArc(oval, -90f, 360f * percent / 100f, false, ringFg)

        val baseline = cy - (percentPaint.ascent() + percentPaint.descent()) / 2f - 4f * density
        canvas.drawText("$percent%", cx, baseline, percentPaint)
        canvas.drawText(if (charging) "Charging" else "Battery", cx, baseline + 18f * density, subPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Battery broadcast "sticky" hai: registerReceiver current value turant de deta hai
        val sticky = ContextCompat.registerReceiver(
            context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        readBattery(sticky)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // pehle se registered nahi tha
        }
    }
}
