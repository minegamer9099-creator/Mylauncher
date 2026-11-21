package com.example.mylauncher

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * 3D depth layers (neeche se upar):
 *   1. Background wallpaper   2. Bada GlassClockView (+ date)   3. Foreground subject (transparent PNG)
 * Launcher ka baaki UI (pager, dock) is view ke UPAR hai, isliye foreground sirf clock ko dhakta hai, icons ko nahi.
 *
 * Background aur foreground ek hi matrix se (centerCrop) draw hote hain: dono ek hi photo ke hisse ki tarah aligned rehte
 * hain, kisi bhi screen size pe. Foreground ke liye user adjust (x, y, scale) alag se lagta hai.
 */
class DepthLayerView(context: Context) : FrameLayout(context) {

    private val bgLayer = ImageLayer(context, true)
    private val fgLayer = ImageLayer(context, false)
    private val column = LinearLayout(context)
    val clock = GlassClockView(context)
    private val date = DateText(context)

    private var config = DepthConfig()
    private var progress = 1f
    private var unlockAnim: ValueAnimator? = null
    private var blurAnim: ValueAnimator? = null
    private var fitBase = 0f

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        addView(bgLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.clipChildren = false
        column.addView(clock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(date, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        addView(fgLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Bitmaps lagao. Purane (agar alag hon) recycle ho jaate hain. */
    fun setBitmaps(bg: Bitmap?, fg: Bitmap?) {
        bgLayer.set(bg)
        fgLayer.set(fg)
    }

    fun hasForeground(): Boolean = fgLayer.hasBitmap()

    /** Sab bitmaps chhodo (memory pressure / depth off). */
    fun clear() {
        cancelAnimations()
        setBitmaps(null, null)
    }

    fun applyConfig(cfg: DepthConfig) {
        config = cfg
        fgLayer.setUser(cfg.fgX, cfg.fgY, cfg.fgScale)
        date.visibility = if (cfg.showDate) View.VISIBLE else View.GONE
        layoutClock()
    }

    fun setAccent(color: Int) {
        clock.accentColor = color
    }

    /** 0..1: page 0 pe 1 (clock dikhe), doosre pages ki taraf jaate hue 0. Wallpaper aur subject apni jagah rehte hain. */
    fun setClockProgress(p: Float) {
        val v = p.coerceIn(0f, 1f)
        if (v == progress) return
        progress = v
        column.alpha = v
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutClock()
    }

    /** Clock ki size (screen width ke hisaab se, auto-fit) aur position (screen ke fractions). */
    private fun layoutClock() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        if (fitBase <= 0f) {
            val tp = TextPaint(clock.paint)
            tp.textSize = 100f
            fitBase = 100f / max(tp.measureText("00:00"), 1f) // 1 px width ke liye kitna text size
        }
        val sizePx = max(fitBase * w * 0.86f * config.clockScale, 24f * resources.displayMetrics.density)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, max(sizePx * 0.15f, 13f * resources.displayMetrics.density))
        column.translationX = config.clockX * w
        column.translationY = config.clockY * h
    }

    // ---- Unlock animation (sirf depth wallpaper ke liye: system wallpaper ko hum zoom nahi kar sakte) ----
    fun cancelAnimations() {
        unlockAnim?.cancel()
        blurAnim?.cancel()
        unlockAnim = null
        blurAnim = null
        setZoom(1f)
        setBlur(0f)
    }

    /** Wallpaper halka zoom-in se normal tak (zoom-out) + (API 31+, low-RAM nahi) blur se clear. */
    fun playUnlock(withBlur: Boolean) {
        cancelAnimations()
        setZoom(UNLOCK_ZOOM)
        unlockAnim = ValueAnimator.ofFloat(UNLOCK_ZOOM, 1f).apply {
            duration = 720
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { setZoom(it.animatedValue as Float) }
            start()
        }
        if (withBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setBlur(UNLOCK_BLUR)
            blurAnim = ValueAnimator.ofFloat(UNLOCK_BLUR, 0f).apply {
                duration = 480
                interpolator = DecelerateInterpolator(1.2f)
                addUpdateListener { setBlur(it.animatedValue as Float) }
                start()
            }
        }
    }

    private fun setZoom(z: Float) {
        bgLayer.scaleX = z
        bgLayer.scaleY = z
        fgLayer.scaleX = z // dono ek saath: alignment nahi bigadta
        fgLayer.scaleY = z
    }

    /** Static blur (drawer / folder khulne pe) ya animation ka step. 0 = blur band. API 31 se neeche kuch nahi. */
    fun setBlur(px: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val effect = if (px > 0.5f) RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP) else null
        bgLayer.setRenderEffect(effect)
        fgLayer.setRenderEffect(effect)
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------------------------
    // Ek image layer: centerCrop + (foreground ke liye) user x / y / scale
    // ---------------------------------------------------------------------------------------
    private class ImageLayer(context: Context, private val isBackground: Boolean) : View(context) {
        private var bitmap: Bitmap? = null
        private val matrix = Matrix()
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var ux = 0f
        private var uy = 0f
        private var us = 1f

        init {
            isClickable = false
            isFocusable = false
        }

        fun hasBitmap(): Boolean = bitmap != null

        fun set(b: Bitmap?) {
            val old = bitmap
            bitmap = b
            if (old != null && old !== b && !old.isRecycled) old.recycle()
            rebuild()
            invalidate()
        }

        fun setUser(x: Float, y: Float, s: Float) {
            if (x == ux && y == uy && s == us) return
            ux = x
            uy = y
            us = s
            rebuild()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuild()
        }

        private fun rebuild() {
            val b = bitmap ?: return
            val vw = width.toFloat()
            val vh = height.toFloat()
            if (vw <= 0f || vh <= 0f) return
            val s = max(vw / b.width, vh / b.height)
            matrix.reset()
            matrix.postScale(s, s)
            matrix.postTranslate((vw - b.width * s) / 2f, (vh - b.height * s) / 2f)
            if (!isBackground) {
                matrix.postScale(us, us, vw / 2f, vh / 2f)
                matrix.postTranslate(ux * vw, uy * vh)
            }
        }

        override fun onDraw(canvas: Canvas) {
            val b = bitmap ?: return
            if (b.isRecycled) return
            canvas.drawBitmap(b, matrix, paint)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Date: din + tareekh. Sirf din badalne (ya timezone / time set) pe update hota hai.
    // ---------------------------------------------------------------------------------------
    private class DateText(context: Context) : TextView(context) {
        private val format = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        private var registered = false

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                refresh()
            }
        }

        init {
            setTextColor(GlassColorsLocal.TEXT)
            setShadowLayer(10f, 0f, 2f, 0x55000000)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            refresh()
        }

        fun refresh() {
            format.timeZone = java.util.TimeZone.getDefault()
            val t = format.format(Date())
            if (t != text.toString()) text = t
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            refresh()
            if (!registered) {
                val f = IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
            }
        }

        override fun onDetachedFromWindow() {
            if (registered) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    // already unregistered
                }
                registered = false
            }
            super.onDetachedFromWindow()
        }
    }

    private object GlassColorsLocal {
        val TEXT: Int = Color.argb(235, 255, 255, 255)
    }

    companion object {
        private const val UNLOCK_ZOOM = 1.08f
        private const val UNLOCK_BLUR = 18f
    }
}
