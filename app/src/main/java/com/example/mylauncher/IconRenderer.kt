package com.example.mylauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import kotlin.math.max

/**
 * App ka icon EK BAAR (background thread pe) ek chhote bitmap mein badalta hai.
 *
 * Kyun: AdaptiveIconDrawable ki 2 layers (har ek ~100+ KB, kabhi zyada) har app ke liye memory mein rehti
 * thi aur har frame mask ke saath draw hoti thi. Ek 68dp ka bitmap ~100 KB hai aur sidha draw hota hai.
 * Dikhne mein icon wahi rehta hai (system mask pehle se lagi hui).
 *
 * Dark icon theme bhi yahin lagta hai (load ke waqt), isliye screen pe koi heavy kaam nahi.
 */
class IconRenderer(context: Context, prefs: Prefs) {

    private val resources = context.applicationContext.resources
    val sizePx: Int = (MAX_ICON_DP * resources.displayMetrics.density).toInt().coerceIn(96, 256)
    val dark: Boolean = darkIconsEnabled(context, prefs)

    fun render(raw: Drawable): Drawable {
        return try {
            val source = if (dark) makeDark(raw) else raw
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val w = source.intrinsicWidth
            val h = source.intrinsicHeight
            if (source !is AdaptiveIconDrawable && w > 0 && h > 0 && w != h) {
                // Chaukor nahi hai: centerCrop jaisa
                val scale = max(sizePx.toFloat() / w, sizePx.toFloat() / h)
                val dw = (w * scale).toInt()
                val dh = (h * scale).toInt()
                val left = (sizePx - dw) / 2
                val top = (sizePx - dh) / 2
                source.setBounds(left, top, left + dw, top + dh)
            } else {
                source.setBounds(0, 0, sizePx, sizePx)
            }
            source.draw(canvas)
            BitmapDrawable(resources, bitmap)
        } catch (e: Exception) {
            raw // kuch bigda to asli drawable hi use karo
        }
    }

    /** Dark icons: adaptive icon ka background gehra, (Android 13+) monochrome layer safed. */
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
                return AdaptiveIconDrawable(ColorDrawable(DARK_TILE), foreground)
            }
        }
        // Purana (non-adaptive) icon: gehre tile ke andar thoda chhota karke
        val inner = icon.constantState?.newDrawable()?.mutate() ?: icon
        return LayerDrawable(arrayOf(ColorDrawable(DARK_TILE), InsetDrawable(inner, 0.14f)))
    }

    companion object {
        /** Settings mein icon size ki max limit (dp). Bitmap isi size ka banta hai. */
        const val MAX_ICON_DP = 68
        private val DARK_TILE = Color.parseColor("#1F1F24")

        /** Dark icon theme: "dark", ya "auto" aur system dark mode on. */
        fun darkIconsEnabled(context: Context, prefs: Prefs): Boolean = when (prefs.iconTheme) {
            "dark" -> true
            "auto" -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
    }
}
