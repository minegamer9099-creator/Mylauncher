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
        get() = if (lightStyle) TEXT_DARK else Color.WHITE

    /** Halka text (labels, ticks): primary ka thoda dheema version. */
    protected val secondaryColor: Int
        get() = if (lightStyle) TEXT_DARK_DIM else TEXT_LIGHT_DIM

    /** Ring / track ka halka background (battery, storage). */
    protected val trackColor: Int
        get() = if (lightStyle) TRACK_LIGHT_GLASS else TRACK_DARK_GLASS

    /** Chhote buttons / chips ka background (quick actions). */
    protected val chipFill: Int
        get() = if (lightStyle) CHIP_LIGHT_GLASS else CHIP_DARK_GLASS

    private var glowAnimator: ValueAnimator? = null
    private var appliedStrength = Float.NaN // init se pehle declare (property initializers textual order mein chalte hain)

    init {
        applyGlass()
        clipToOutline = true
        isClickable = true // touch consume karo taaki press/release (glow) milte rahein
    }

    /** Glass drawable sirf tab naya banta hai jab strength sach mein badli (har bind pe nahi). */
    // Colors ek baar parse hote hain (pehle har draw pe Color.parseColor chalta tha)
    private companion object {
        val TEXT_DARK = Color.parseColor("#1F2328")
        val TEXT_DARK_DIM = Color.parseColor("#8A1F2328")
        val TEXT_LIGHT_DIM = Color.parseColor("#CCFFFFFF")
        val TRACK_LIGHT_GLASS = Color.parseColor("#26000000")
        val TRACK_DARK_GLASS = Color.parseColor("#33FFFFFF")
        val CHIP_LIGHT_GLASS = Color.parseColor("#1A000000")
        val CHIP_DARK_GLASS = Color.parseColor("#26FFFFFF")
    }

    private fun applyGlass() {
        val strength = if (lightStyle) 0.55f else glassStrength
        if (strength == appliedStrength) return
        appliedStrength = strength
        background = LiquidGlassDrawable(24f * density, strength)
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
