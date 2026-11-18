package com.example.mylauncher

import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bada patla "glass" clock (ColorOS 17 jaisa): pehla digit accent color mein,
 * baaki translucent safed. Har minute ek shared TimeTicker se update hota hai.
 * Text tabhi badalta hai jab time ya accent sach mein badla ho (warna TextView relayout hota).
 */
class GlassClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    /** Pehle digit ka color (wallpaper palette se set hota hai). */
    var accentColor: Int = Color.parseColor("#FF3B30")
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** Compact (ColorOS default): chhota clock left mein. Nahi to bada, beech mein. */
    var compact: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (value) 64f else 92f)
            gravity = if (value) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        }

    // Ek baar bane hue formatter / color (har render pe naye nahi banate)
    private val format24 = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val format12 = SimpleDateFormat("hh:mm", Locale.getDefault())
    private val whiteColor = Color.parseColor("#E8FFFFFF")
    private var lastTime = ""
    private var lastAccent = 0
    private val tickListener = Runnable { render() }

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
        val format = if (DateFormat.is24HourFormat(context)) format24 else format12
        val time = format.format(Date())
        if (time == lastTime && accentColor == lastAccent) return
        lastTime = time
        lastAccent = accentColor

        val styled = SpannableString(time)
        styled.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        styled.setSpan(ForegroundColorSpan(whiteColor), 1, time.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = styled
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TimeTicker.register(context, tickListener)
        render()
    }

    override fun onDetachedFromWindow() {
        TimeTicker.unregister(tickListener)
        super.onDetachedFromWindow()
    }
}
