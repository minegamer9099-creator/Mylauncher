package com.example.mylauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View

/**
 * Folder ka icon: liquid glass tile jiske andar 3x3 chhote app icons dikhte hain.
 */
class FolderTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var icons: List<Drawable> = emptyList()
    private val clipPath = Path()
    private val cellRect = RectF()
    private var content: Bitmap? = null
    private var contentDirty = true

    private var glowAnimator: ValueAnimator? = null

    init {
        clipToOutline = true
    }

    /** Touch glow: parent (cell) ko touch milta hai, isliye yahan dispatch level pe dekhte hain. */
    fun glowTo(target: Float) {
        val drawable = background as? LiquidGlassDrawable ?: return
        glowAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(drawable.glow, target)
        animator.setDuration(if (target > 0f) 120L else 320L)
        animator.addUpdateListener { anim -> drawable.glow = anim.animatedValue as Float }
        animator.start()
        glowAnimator = animator
    }

    fun setApps(apps: List<AppInfo>, style: IconStyle) {
        // Drawable ki copy lo: warna chhote size ke bounds baaki jagah ke icons ko bigaad denge
        icons = apps.take(9).map { app ->
            val d = style.drawableFor(app)
            d.constantState?.newDrawable(resources)?.mutate() ?: d
        }
        contentDirty = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        background = LiquidGlassDrawable(w * 0.28f, 0.4f)
        contentDirty = true // size badla: bitmap dobara banega
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
        content = null // recycle() nahi: render thread ke paas abhi reference ho sakta hai, GC sambhal lega
        contentDirty = true
    }

    /**
     * 3x3 mini icons ko rounded clip ke saath ek baar bitmap mein banao; har frame sirf drawBitmap.
     * (Pehle har frame 9 baar clipPath + icon draw hota tha: page swipe / scale animation mein bhari.)
     */
    private fun rebuildContent() {
        val w = width
        val h = height
        var bmp = content
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            content = bmp
        } else {
            bmp.eraseColor(Color.TRANSPARENT)
        }
        val c = Canvas(bmp!!)
        val pad = w * 0.13f
        val gap = w * 0.05f
        val cell = (w - 2 * pad - 2 * gap) / 3f
        val radius = cell * 0.28f

        for (i in icons.indices) {
            val row = i / 3
            val col = i % 3
            val left = pad + col * (cell + gap)
            val top = pad + row * (cell + gap)
            cellRect.set(left, top, left + cell, top + cell)

            clipPath.reset()
            clipPath.addRoundRect(cellRect, radius, radius, Path.Direction.CW)
            c.save()
            c.clipPath(clipPath)
            icons[i].setBounds(left.toInt(), top.toInt(), (left + cell).toInt(), (top + cell).toInt())
            icons[i].draw(c)
            c.restore()
        }
        contentDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (contentDirty || content == null) rebuildContent()
        content?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }
}
