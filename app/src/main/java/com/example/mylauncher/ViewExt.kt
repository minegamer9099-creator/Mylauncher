package com.example.mylauncher

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View

/**
 * Dabane pe view halka chhota hota hai, chhodne pe wapas normal (ColorOS jaisa press effect).
 * Click event pe koi asar nahi padta. onPress(true/false) se extra effect (jaise glow) jod sakte ho.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.applyPressEffect(onPress: ((Boolean) -> Unit)? = null) {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Dabate hi tez (bina bounce) chhota hota hai
                v.springScale(0.88f, Springs.STIFF_HIGH, Springs.DAMPING_SMOOTH)
                onPress?.invoke(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Chhodte hi bouncy spring se wapas (thoda overshoot)
                v.springScale(1f, 480f, 0.45f)
                onPress?.invoke(false)
            }
        }
        false
    }
}
