package com.example.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bada patla "glass" clock (ColorOS 17 jaisa): pehla digit accent color mein,
 * baaki translucent safed. Har minute apne aap update hota hai.
 */
class GlassClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    /** Pehle digit ka color (wallpaper palette se set hota hai). */
    var accentColor: Int = Color.parseColor("#FF3B30")
        set(value) {
            field = value
            render()
        }

    /** Compact (ColorOS default): chhota clock left mein. Nahi to bada, beech mein. */
    var compact: Boolean = false
        set(value) {
            field = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (value) 64f else 92f)
            gravity = if (value) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            render()
        }
    }

    init {
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 92f)
        includeFontPadding = false
        gravity = Gravity.CENTER
        letterSpacing = -0.02f
        setShadowLayer(12f, 0f, 3f, 0x40000000)
        render()
    }

    private fun render() {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm"
        val time = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
        val styled = SpannableString(time)
        styled.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        styled.setSpan(
            ForegroundColorSpan(Color.parseColor("#E8FFFFFF")),
            1, time.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text = styled
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, tickReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        render()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(tickReceiver)
        } catch (e: IllegalArgumentException) {
            // receiver pehle se registered nahi tha
        }
    }
}
