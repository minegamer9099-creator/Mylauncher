package com.example.mylauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView

/**
 * Icons ka look (size, shape, rim glow, labels) settings se.
 * Har adapter icon bind karte waqt styleIcon() call karta hai (ek view pe ek hi baar lagta hai).
 * Icon ka theme (dark / original) ab load ke waqt IconRenderer laga deta hai, yahan sirf frame / size.
 */
class IconStyle(context: Context, prefs: Prefs) {

    private val density = context.resources.displayMetrics.density
    val sizePx: Int = (prefs.iconSizeDp * density).toInt()
    val showLabels: Boolean = prefs.showLabels
    val touchGlow: Boolean = prefs.touchGlow
    private val circle: Boolean = prefs.iconShape == "circle"
    private val rim: Boolean = prefs.iconRim
    private val radiusPx: Float = sizePx * 0.28f

    /** App ka icon (theme load ke waqt hi lag chuka hai). */
    fun drawableFor(app: AppInfo): Drawable = app.icon

    /** Icon ke background / rim ka corner radius (launch animation isse match karti hai). */
    val cornerRadiusPx: Float
        get() = if (circle) sizePx / 2f else radiusPx

    /** Kisi bhi size ke icon ke liye corner radius (circle ho to aadha size). */
    fun cornerRadiusFor(size: Float): Float = if (circle) size / 2f else size * 0.28f

    fun styleIcon(icon: ImageView) {
        // Ek hi style baar-baar lagana (scroll ke dauran) bekaar kaam hai: pehle se laga ho to chhod do
        if (icon.tag === this) return
        icon.tag = this
        val lp = icon.layoutParams
        if (lp.width != sizePx || lp.height != sizePx) {
            lp.width = sizePx
            lp.height = sizePx
            icon.layoutParams = lp
        }
        icon.background = makeShape(Color.WHITE, 0, 0)
        icon.foreground = if (rim) {
            makeShape(Color.TRANSPARENT, Color.argb(85, 255, 255, 255), density.toInt().coerceAtLeast(1))
        } else {
            null
        }
        icon.clipToOutline = true
    }

    private fun makeShape(fill: Int, stroke: Int, strokeWidth: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setShape(if (circle) GradientDrawable.OVAL else GradientDrawable.RECTANGLE)
        if (!circle) d.setCornerRadius(radiusPx)
        d.setColor(fill)
        if (stroke != 0) d.setStroke(strokeWidth, stroke)
        return d
    }
}
