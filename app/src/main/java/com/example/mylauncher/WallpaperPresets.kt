package com.example.mylauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/**
 * Built-in "liquid" gradient wallpapers (code se banaye jaate hain, koi image file nahi).
 */
object WallpaperPresets {

    class Preset(
        val name: String,
        val top: String,
        val bottom: String,
        val glowA: String,
        val glowB: String
    )

    val presets: List<Preset> = listOf(
        Preset("Peach", "#F4B393", "#B65F6E", "#FFE0C4", "#7B3F73"),
        Preset("Ocean", "#5B8CFF", "#1A2566", "#A9D0FF", "#4B2A8A"),
        Preset("Dusk", "#2C1B5A", "#D2675F", "#FFB27A", "#4A2C8F"),
        Preset("Mint", "#9AE3C9", "#2F7F8F", "#E4FFF3", "#1E4F7A"),
        Preset("Rose", "#FFB6C9", "#8F3F7E", "#FFE3EC", "#4A2A7E"),
        Preset("Night", "#141A33", "#050712", "#3B4DA8", "#6B2FA0")
    )

    fun render(preset: Preset, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()

        // Base gradient
        paint.shader = LinearGradient(
            0f, 0f, 0f, hf,
            Color.parseColor(preset.top), Color.parseColor(preset.bottom),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, paint)

        // Do bade soft glow (liquid blobs)
        fun glow(cx: Float, cy: Float, radius: Float, color: Int) {
            paint.shader = RadialGradient(
                cx, cy, radius,
                ColorUtils.setAlphaComponent(color, 200),
                ColorUtils.setAlphaComponent(color, 0),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }
        glow(wf * 0.80f, hf * 0.28f, wf * 0.85f, Color.parseColor(preset.glowA))
        glow(wf * 0.15f, hf * 0.78f, wf * 0.90f, Color.parseColor(preset.glowB))

        // Do halki lehrdaar glass waves
        paint.shader = null
        paint.color = Color.argb(34, 255, 255, 255)
        val wave1 = Path()
        wave1.moveTo(0f, hf * 0.58f)
        wave1.cubicTo(wf * 0.30f, hf * 0.46f, wf * 0.55f, hf * 0.72f, wf, hf * 0.52f)
        wave1.lineTo(wf, hf)
        wave1.lineTo(0f, hf)
        wave1.close()
        canvas.drawPath(wave1, paint)

        paint.color = Color.argb(24, 255, 255, 255)
        val wave2 = Path()
        wave2.moveTo(0f, hf * 0.74f)
        wave2.cubicTo(wf * 0.35f, hf * 0.62f, wf * 0.65f, hf * 0.86f, wf, hf * 0.68f)
        wave2.lineTo(wf, hf)
        wave2.lineTo(0f, hf)
        wave2.close()
        canvas.drawPath(wave2, paint)

        return bmp
    }
}
