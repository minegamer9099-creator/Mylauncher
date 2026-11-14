package com.example.mylauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View

/**
 * App KHOLNE ki animation: tapped icon ki jagah se ek rounded surface poori screen tak failta hai
 * (icon beech mein thoda bada hota hai). Sirf opening ke liye hai; closing / return ke liye koi animation
 * nahi, wo Android ka default system transition hai.
 *
 * Ek hi draw call per frame, koi allocation nahi, ek hi animator (start() purane ko cancel karta hai).
 */
class LaunchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startRect = RectF()
    private val endRect = RectF()
    private val rect = RectF()
    private var baseColor = 0
    private var startRadius = 0f
    private var endRadius = 0f
    private var icon: Drawable? = null
    private var iconSize = 0f
    private var animator: ValueAnimator? = null

    /** 0 = icon ke size ka, 1 = poori screen. */
    private var progress = 0f

    init {
        isClickable = true // animation ke dauran neeche ke taps rok do (duplicate launch se bachav)
    }

    /**
     * Opening animation shuru karo. Pehle se koi animation chal rahi ho to wo cancel ho jati hai.
     * onCovered: jab surface poori screen dhak le (drawer / folder band karne ke liye).
     */
    fun start(
        iconRect: RectF,
        radius: Float,
        iconDrawable: Drawable?,
        color: Int,
        screenW: Float,
        screenH: Float,
        durationMs: Long,
        interpolator: TimeInterpolator,
        onCovered: () -> Unit
    ) {
        animator?.cancel()
        startRect.set(iconRect)
        endRect.set(0f, 0f, screenW, screenH)
        startRadius = radius
        endRadius = 0f
        iconSize = iconRect.width()
        icon = iconDrawable?.constantState?.newDrawable()?.mutate() ?: iconDrawable
        baseColor = color or (0xFF shl 24)
        progress = 0f
        visibility = VISIBLE

        val a = ValueAnimator.ofFloat(0f, 1f)
        a.setDuration(durationMs)
        a.interpolator = interpolator
        a.addUpdateListener { anim ->
            progress = anim.animatedValue as Float
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
                canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!canceled) onCovered()
            }
        })
        animator = a
        a.start()
    }

    /** Sab kuch turant saaf: animator cancel, icon release, overlay invisible. Koi animation nahi. */
    fun reset() {
        animator?.cancel()
        animator = null
        icon = null
        progress = 0f
        visibility = INVISIBLE
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    override fun onDraw(canvas: Canvas) {
        val t = progress
        rect.set(
            lerp(startRect.left, endRect.left, t),
            lerp(startRect.top, endRect.top, t),
            lerp(startRect.right, endRect.right, t),
            lerp(startRect.bottom, endRect.bottom, t)
        )
        // Corners pehle tez seedhe hote hain, phir poori screen pe 0
        val radius = lerp(startRadius, endRadius, if (t * 1.5f > 1f) 1f else t * 1.5f)

        surfacePaint.color = baseColor
        canvas.drawRoundRect(rect, radius, radius, surfacePaint)

        val d = icon ?: return
        val size = iconSize * (1f + 0.2f * t)
        val cx = rect.centerX()
        val cy = rect.centerY()
        d.setBounds(
            (cx - size / 2f).toInt(), (cy - size / 2f).toInt(),
            (cx + size / 2f).toInt(), (cy + size / 2f).toInt()
        )
        d.draw(canvas)
    }
}
