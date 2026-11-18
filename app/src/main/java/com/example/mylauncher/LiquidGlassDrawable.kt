package com.example.mylauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * "Liquid glass" look: translucent fill + upar ka soft gloss + chamakdaar gradient rim
 * (top-left aur bottom-right pe tez, beech mein halka).
 * strength: 0 = bahut transparent, 1 = zyada dhundhla/bhara hua glass.
 */
class LiquidGlassDrawable(
    private val radiusPx: Float,
    private val strength: Float = 0.5f,
    private val tint: Int = Color.WHITE
) : Drawable() {

    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glowRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    /** 0..1: touch karne pe kinare chamakte hain ("glow effect on interaction"). */
    var glow: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return

        val rimWidth = maxOf(2f, radiusPx * 0.06f)
        rimPaint.strokeWidth = rimWidth
        glowRimPaint.strokeWidth = rimWidth * 1.6f
        val inset = rimWidth / 2f
        rect.set(
            bounds.left + inset, bounds.top + inset,
            bounds.right - inset, bounds.bottom - inset
        )

        val s = strength.coerceIn(0f, 1f)
        val topAlpha = (18 + 78 * s).toInt()
        val bottomAlpha = (8 + 52 * s).toInt()

        fillPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            withAlpha(tint, topAlpha), withAlpha(tint, bottomAlpha),
            Shader.TileMode.CLAMP
        )
        glossPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.55f,
            Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                Color.argb(185, 255, 255, 255),
                Color.argb(22, 255, 255, 255),
                Color.argb(105, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)
        canvas.drawRoundRect(rect, radiusPx, radiusPx, glossPaint)
        canvas.drawRoundRect(rect, radiusPx, radiusPx, rimPaint)
        if (glow > 0f) {
            glowFillPaint.alpha = (60 * glow).toInt()
            canvas.drawRoundRect(rect, radiusPx, radiusPx, glowFillPaint)
            glowRimPaint.alpha = (235 * glow).toInt()
            canvas.drawRoundRect(rect, radiusPx, radiusPx, glowRimPaint)
        }
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radiusPx)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        glossPaint.alpha = alpha
        rimPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        glossPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
