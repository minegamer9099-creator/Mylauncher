package com.example.mylauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Liquid glass card: sab widgets ka base. Subclass onDraw mein apna content banate hain.
 * - lightStyle: halka (frosted white) glass + gehre rang ka text (ColorOS ke light widgets jaisa)
 * - touchGlow: touch karne pe kinare chamakte hain
 */
open class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    protected val density: Float = resources.displayMetrics.density

    var glassStrength: Float = 0.5f
        set(value) {
            field = value
            applyGlass()
        }

    var lightStyle: Boolean = false
        set(value) {
            field = value
            applyGlass()
            invalidate()
        }

    var touchGlow: Boolean = true

    var accent: Int = Color.parseColor("#FF3B30")
        set(value) {
            field = value
            invalidate()
        }

    /** Text / hands ka main color: dark glass pe safed, light glass pe gehra. */
    protected val primaryColor: Int
        get() = if (lightStyle) Color.parseColor("#1F2328") else Color.WHITE

    /** Halka text (labels, ticks): primary ka thoda dheema version. */
    protected val secondaryColor: Int
        get() = if (lightStyle) Color.parseColor("#8A1F2328") else Color.parseColor("#CCFFFFFF")

    private var glowAnimator: ValueAnimator? = null

    init {
        applyGlass()
        clipToOutline = true
        isClickable = true // touch consume karo taaki press/release (glow) milte rahein
    }

    private fun applyGlass() {
        background = LiquidGlassDrawable(
            24f * density,
            if (lightStyle) 0.55f else glassStrength
        )
    }

    /** Touch glow animate karo. Subclass jo onTouchEvent override karte hain, wo bhi isse call karte hain. */
    protected fun handleGlow(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animateGlow(1f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animateGlow(0f)
        }
    }

    private fun animateGlow(target: Float) {
        val drawable = background as? LiquidGlassDrawable ?: return
        glowAnimator?.cancel()
        if (!touchGlow && target > 0f) return
        val animator = ValueAnimator.ofFloat(drawable.glow, target)
        animator.setDuration(if (target > 0f) 120L else 320L)
        animator.addUpdateListener { anim -> drawable.glow = anim.animatedValue as Float }
        animator.start()
        glowAnimator = animator
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        handleGlow(event)
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
    }
}
