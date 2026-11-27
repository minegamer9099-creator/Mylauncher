package com.example.mylauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.widget.ImageView

/**
 * Icons ka look (size, shape, rim glow, labels) settings se.
 * Har adapter icon bind karte waqt styleIcon() call karta hai.
 */
class IconStyle(context: Context, prefs: Prefs) {

    private val density = context.resources.displayMetrics.density
    val sizePx: Int = (prefs.iconSizeDp * density).toInt()
    val showLabels: Boolean = prefs.showLabels
    val touchGlow: Boolean = prefs.touchGlow
    private val circle: Boolean = prefs.iconShape == "circle"
    private val rim: Boolean = prefs.iconRim
    private val radiusPx: Float = sizePx * 0.28f

    /** Dark icon theme: "dark", ya "auto" aur system dark mode on. */
    private val darkIcons: Boolean = when (prefs.iconTheme) {
        "dark" -> true
        "auto" -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val darkTile = Color.parseColor("#1F1F24")
    private val iconCache = HashMap<String, Drawable>()

    /**
     * App ka icon, theme ke hisaab se. Dark theme mein: adaptive icon ka background gehra karke
     * (Android 13+ pe monochrome layer safed karke) ColorOS ke "dark icons" jaisa look.
     */
    fun drawableFor(app: AppInfo): Drawable {
        if (!darkIcons) return app.icon
        return iconCache.getOrPut(app.packageName) { makeDark(app.icon) }
    }

    private fun makeDark(icon: Drawable): Drawable {
        if (icon is AdaptiveIconDrawable) {
            var foreground: Drawable? = icon.foreground
            if (Build.VERSION.SDK_INT >= 33) {
                val mono = icon.monochrome
                if (mono != null) {
                    val copy = mono.constantState?.newDrawable()?.mutate() ?: mono.mutate()
                    copy.setTint(Color.WHITE)
                    foreground = copy
                }
            }
            if (foreground != null) {
                return AdaptiveIconDrawable(ColorDrawable(darkTile), foreground)
            }
        }
        // Purana (non-adaptive) icon: gehre tile ke andar thoda chhota karke
        val inner = icon.constantState?.newDrawable()?.mutate() ?: icon
        return LayerDrawable(arrayOf(ColorDrawable(darkTile), InsetDrawable(inner, 0.14f)))
    }

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
