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
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                onPress?.invoke(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                onPress?.invoke(false)
            }
        }
        false
    }
}
