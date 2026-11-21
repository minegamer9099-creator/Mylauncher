package com.example.mylauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * 3D depth wallpaper ka editor: live preview (home jaisa hi: wallpaper -> clock -> subject), background / foreground
 * alag-alag chuno, auto cutout (classical, AI nahi), brush se cutout theek karo, transparent PNG import,
 * clock / subject ki position aur size. "Apply" dabane par hi save hota hai.
 */
class DepthEditorActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var preview: DepthLayerView
    private lateinit var maskView: MaskView
    private lateinit var mainControls: LinearLayout
    private lateinit var brushControls: LinearLayout
    private lateinit var panelScroll: ScrollView
    private lateinit var status: TextView

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var bgBitmap: Bitmap? = null
    private var fgBitmap: Bitmap? = null
    private var editMask: Bitmap? = null
    private var maskBackup: Bitmap? = null
    private var bgChanged = false
    private var fgChanged = false
    private var cfg = DepthConfig()
    private var enabled = true
    private var busy = false

    private val pickBg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importBackground(uri)
    }
    private val pickFg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importForeground(uri)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        cfg = DepthConfig.from(prefs)
        enabled = prefs.depthEnabled || !DepthStore.hasBg(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        preview = DepthLayerView(this)
        root.addView(preview, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        maskView = MaskView(this)
        maskView.visibility = View.GONE
        root.addView(maskView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Upar: status + controls chhupane ka button
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 12f
        status.setShadowLayer(6f, 0f, 1f, Color.BLACK)
        status.text = "Background chuno, phir subject banao ya PNG lagao"
        top.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = chip("Controls") {
            panelScroll.visibility = if (panelScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        top.addView(toggle)
        root.addView(top, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Neeche: controls panel
        panelScroll = ScrollView(this)
        val bgShape = GradientDrawable()
        val r = dp(24).toFloat()
        bgShape.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bgShape.setColor(Color.parseColor("#E61C1C22"))
        panelScroll.background = bgShape
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panelScroll.addView(panel)
        root.addView(
            panelScroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.46f).toInt(),
                Gravity.BOTTOM
            )
        )

        mainControls = LinearLayout(this)
        mainControls.orientation = LinearLayout.VERTICAL
        brushControls = LinearLayout(this)
        brushControls.orientation = LinearLayout.VERTICAL
        brushControls.visibility = View.GONE
        panel.addView(mainControls)
        panel.addView(brushControls)
        buildMainControls()
        buildBrushControls()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            top.setPadding(dp(14), bars.top + dp(8), dp(10), dp(8))
            panel.setPadding(dp(16), dp(14), dp(16), bars.bottom + dp(14))
            insets
        }
        setContentView(root)

        preview.applyConfig(cfg)
        // Maujooda assets background mein load (editor ko software bitmaps chahiye)
        run("Load ho raha hai…") {
            val bg = DepthStore.loadSoftware(DepthStore.bgFile(this))
            val fg = if (DepthStore.hasFg(this)) DepthStore.loadSoftware(DepthStore.fgFile(this)) else null
            post {
                bgBitmap = bg
                fgBitmap = fg
                preview.setBitmaps(bg, fg)
                if (bg == null) status.text = "Pehle background chuno" else status.text = "Preview ready"
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Controls
    // ---------------------------------------------------------------------------------------
    private fun buildMainControls() {
        val c = mainControls
        c.addView(switchRow("3D depth effect on", enabled) { enabled = it })

        c.addView(sectionLabel("Images"))
        c.addView(row(chip("Background…") { pickBg.launch("image/*") }, chip("Foreground PNG…") { pickFg.launch("image/*") }))
        c.addView(row(chip("Auto cutout") { autoCutout() }, chip("Brush cutout") { startBrush() }, chip("Subject hatao") { removeForeground() }))

        c.addView(sectionLabel("Clock"))
        c.addView(slider("Left / right", -50, 50, (cfg.clockX * 100).toInt()) { v ->
            cfg = cfg.copy(clockX = v / 100f)
            preview.applyConfig(cfg)
        })
        c.addView(slider("Upar / neeche", 0, 80, (cfg.clockY * 100).toInt()) { v ->
            cfg = cfg.copy(clockY = v / 100f)
            preview.applyConfig(cfg)
        })
        c.addView(slider("Size", 50, 180, (cfg.clockScale * 100).toInt()) { v ->
            cfg = cfg.copy(clockScale = v / 100f)
            preview.applyConfig(cfg)
        })
        c.addView(switchRow("Date dikhao", cfg.showDate) { on ->
            cfg = cfg.copy(showDate = on)
            preview.applyConfig(cfg)
        })

        c.addView(sectionLabel("Subject (foreground) alignment"))
        c.addView(slider("Left / right", -30, 30, (cfg.fgX * 100).toInt()) { v ->
            cfg = cfg.copy(fgX = v / 100f)
            preview.applyConfig(cfg)
        })
        c.addView(slider("Upar / neeche", -30, 30, (cfg.fgY * 100).toInt()) { v ->
            cfg = cfg.copy(fgY = v / 100f)
            preview.applyConfig(cfg)
        })
        c.addView(slider("Scale", 70, 150, (cfg.fgScale * 100).toInt()) { v ->
            cfg = cfg.copy(fgScale = v / 100f)
            preview.applyConfig(cfg)
        })

        c.addView(row(chip("Apply") { apply() }, chip("Cancel") { finish() }))
    }

    private fun buildBrushControls() {
        val c = brushControls
        c.addView(sectionLabel("Brush: subject ko rangte raho (laal = subject)"))
        c.addView(row(
            chip("Add subject") { maskView.erase = false; status.text = "Brush: subject jodo" },
            chip("Erase") { maskView.erase = true; status.text = "Brush: subject hatao" }
        ))
        c.addView(slider("Brush size", 10, 120, 40) { v -> maskView.brushPx = v * resources.displayMetrics.density })
        c.addView(row(chip("Done") { finishBrush(true) }, chip("Cancel brush") { finishBrush(false) }))
    }

    private fun chip(text: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 13f
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(9), dp(14), dp(9))
        val shape = GradientDrawable()
        shape.setColor(Color.argb(56, 255, 255, 255))
        shape.cornerRadius = dp(18).toFloat()
        t.background = shape
        t.setOnClickListener { onClick() }
        return t
    }

    private fun row(vararg views: View): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.HORIZONTAL
        l.setPadding(0, dp(4), 0, dp(4))
        for (v in views) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6)
            l.addView(v, lp)
        }
        return l
    }

    private fun sectionLabel(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.argb(170, 255, 255, 255))
        t.textSize = 12f
        t.setPadding(0, dp(12), 0, dp(2))
        return t
    }

    private fun switchRow(text: String, value: Boolean, onChange: (Boolean) -> Unit): View {
        val s = SwitchCompat(this)
        s.text = text
        s.setTextColor(Color.WHITE)
        s.isChecked = value
        s.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        s.setPadding(0, dp(6), 0, dp(6))
        return s
    }

    private fun slider(label: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.setTextColor(Color.WHITE)
        t.textSize = 12f
        val sb = SeekBar(this)
        sb.max = max - min
        val v0 = value.coerceIn(min, max)
        sb.progress = v0 - min
        t.text = "$label: $v0"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress + min
                t.text = "$label: $v"
                onChange(v)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        box.addView(t)
        box.addView(sb)
        return box
    }

    // ---------------------------------------------------------------------------------------
    // Background thread helper
    // ---------------------------------------------------------------------------------------
    private fun post(block: () -> Unit) {
        ui.post { if (!isDestroyed) block() }
    }

    /** Ek time pe ek hi bhaari kaam; UI thread free rehta hai. */
    private fun run(message: String, block: () -> Unit) {
        if (busy) return
        busy = true
        status.text = message
        try {
            io.execute {
                try {
                    block()
                } catch (e: Throwable) {
                    post { status.text = "Kaam fail ho gaya"; toast("Fail: memory ya image ki dikkat") }
                } finally {
                    ui.post { busy = false }
                }
            }
        } catch (e: Exception) {
            busy = false
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---------------------------------------------------------------------------------------
    // Import
    // ---------------------------------------------------------------------------------------
    private fun importBackground(uri: Uri) {
        run("Background load ho raha hai…") {
            val bmp = DepthStore.decodeUri(this, uri)
            post {
                if (bmp == null) {
                    status.text = "Ye image padh nahi payi"
                    toast("Image kharab hai ya bahut badi hai")
                } else {
                    bgBitmap = bmp
                    bgChanged = true
                    editMask?.recycle()
                    editMask = null
                    preview.setBitmaps(bgBitmap, fgBitmap)
                    status.text = if (fgBitmap != null) {
                        "Background badla. Purana subject abhi laga hai: Auto cutout karo ya hata do"
                    } else {
                        "Background set. Ab Auto cutout ya PNG lagao"
                    }
                }
            }
        }
    }

    private fun importForeground(uri: Uri) {
        run("PNG load ho raha hai…") {
            val bmp = DepthStore.decodeUri(this, uri, applyExif = false)
            val ok = bmp != null && hasTransparency(bmp)
            post {
                if (bmp == null) {
                    status.text = "Ye image padh nahi payi"
                    toast("PNG kharab hai")
                } else if (!ok) {
                    bmp.recycle()
                    status.text = "Is image mein transparent hissa nahi hai"
                    toast("Transparent PNG chahiye (subject ke bahar ka hissa transparent ho)")
                } else {
                    fgBitmap = bmp
                    fgChanged = true
                    editMask?.recycle()
                    editMask = null
                    preview.setBitmaps(bgBitmap, fgBitmap)
                    status.text = "Subject PNG laga. Left/right, Upar/neeche aur Scale se milao"
                }
            }
        }
    }

    /** Kam se kam kuch pixels transparent hain? (chhote copy pe check) */
    private fun hasTransparency(b: Bitmap): Boolean {
        if (!b.hasAlpha()) return false
        val s = 64f / max(b.width, b.height)
        val w = max(1, (b.width * s).toInt())
        val h = max(1, (b.height * s).toInt())
        val small = Bitmap.createScaledBitmap(b, w, h, false)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== b) small.recycle()
        for (p in px) if ((p ushr 24) < 250) return true
        return false
    }

    // ---------------------------------------------------------------------------------------
    // Auto cutout / brush
    // ---------------------------------------------------------------------------------------
    private fun autoCutout() {
        val bg = bgBitmap
        if (bg == null) {
            toast("Pehle background chuno")
            return
        }
        run("Cutout ban raha hai…") {
            val result = SubjectSegmenter.segment(bg)
            val small = result.mask
            if (small == null) {
                post { status.text = result.message; toast(result.message) }
                return@run
            }
            val maskEdit = SubjectSegmenter.scaleMask(small, maskW(bg), maskH(bg))
            val fg = SubjectSegmenter.composeForeground(bg, small)
            small.recycle()
            post {
                editMask?.recycle()
                editMask = maskEdit
                fgBitmap = fg
                fgChanged = true
                preview.setBitmaps(bgBitmap, fgBitmap)
                status.text = result.message
            }
        }
    }

    private fun maskW(bg: Bitmap): Int = max(8, (bg.width * min(1f, 720f / max(bg.width, bg.height))).toInt())
    private fun maskH(bg: Bitmap): Int = max(8, (bg.height * min(1f, 720f / max(bg.width, bg.height))).toInt())

    private fun startBrush() {
        val bg = bgBitmap
        if (bg == null) {
            toast("Pehle background chuno")
            return
        }
        var m = editMask
        if (m == null) {
            m = Bitmap.createBitmap(maskW(bg), maskH(bg), Bitmap.Config.ALPHA_8)
            val fg = fgBitmap
            if (fg != null) {
                val alpha = fg.extractAlpha()
                Canvas(m).drawBitmap(alpha, null, Rect(0, 0, m.width, m.height), Paint(Paint.FILTER_BITMAP_FLAG))
                alpha.recycle()
            }
            editMask = m
        }
        maskBackup?.recycle()
        maskBackup = m.copy(Bitmap.Config.ALPHA_8, true)
        maskView.bind(m, bg.width, bg.height)
        maskView.visibility = View.VISIBLE
        mainControls.visibility = View.GONE
        brushControls.visibility = View.VISIBLE
        status.text = "Brush: subject ke upar rango (laal)"
    }

    private fun finishBrush(commit: Boolean) {
        maskView.visibility = View.GONE
        brushControls.visibility = View.GONE
        mainControls.visibility = View.VISIBLE
        val bg = bgBitmap
        val m = editMask
        if (!commit) {
            // Wapas purana mask
            val backup = maskBackup
            if (backup != null && m != null) {
                Canvas(m).drawBitmap(backup, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) })
            }
            status.text = "Brush cancel"
            return
        }
        if (bg == null || m == null) return
        run("Subject ban raha hai…") {
            val fg = SubjectSegmenter.composeForeground(bg, m)
            post {
                fgBitmap = fg
                fgChanged = true
                preview.setBitmaps(bgBitmap, fgBitmap)
                status.text = "Subject update ho gaya"
            }
        }
    }

    private fun removeForeground() {
        fgBitmap = null
        fgChanged = true
        editMask?.recycle()
        editMask = null
        preview.setBitmaps(bgBitmap, null)
        status.text = "Subject hata diya. Background alag se kaam karega"
    }

    // ---------------------------------------------------------------------------------------
    // Apply
    // ---------------------------------------------------------------------------------------
    private fun apply() {
        val bg = bgBitmap
        if (enabled && bg == null && !DepthStore.hasBg(this)) {
            toast("Pehle background chuno")
            return
        }
        val saveBg = bgChanged
        val saveFg = fgChanged
        val fg = fgBitmap
        val wantEnabled = enabled
        run("Save ho raha hai…") {
            var ok = true
            if (saveBg && bg != null) ok = DepthStore.saveBackground(this, bg)
            if (ok && saveFg) {
                if (fg != null) ok = DepthStore.saveForeground(this, fg) else DepthStore.clearForeground(this)
            }
            post {
                if (!ok) {
                    status.text = "Save fail ho gaya"
                    toast("Storage mein save nahi ho paya")
                } else {
                    cfg.save(prefs)
                    prefs.depthEnabled = wantEnabled && DepthStore.hasBg(this)
                    prefs.depthAssetVersion = System.currentTimeMillis()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        preview.clear() // bitmaps recycle
        bgBitmap = null
        fgBitmap = null
        editMask?.recycle()
        maskBackup?.recycle()
        editMask = null
        maskBackup = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------
    // Brush surface: mask ko background ke saath same centerCrop matrix pe dikhata aur edit karta hai
    // ---------------------------------------------------------------------------------------
    private class MaskView(context: Context) : View(context) {
        private var mask: Bitmap? = null
        private var imgW = 1
        private var imgH = 1
        private val toView = Matrix()
        private val toMask = Matrix()
        private val overlay = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.argb(120, 255, 64, 64) }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }
        private var lastX = 0f
        private var lastY = 0f
        private val pts = FloatArray(2)
        var brushPx = 40f * resources.displayMetrics.density
        var erase = false

        fun bind(m: Bitmap, w: Int, h: Int) {
            mask = m
            imgW = w
            imgH = h
            rebuild()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuild()
        }

        /** Mask pixel -> view: bg image ke centerCrop jaisa (mask image ka chhota roop hai). */
        private fun rebuild() {
            val m = mask ?: return
            if (width <= 0 || height <= 0) return
            val s = max(width.toFloat() / imgW, height.toFloat() / imgH)
            val sx = s * imgW / m.width
            val sy = s * imgH / m.height
            toView.reset()
            toView.postScale(sx, sy)
            toView.postTranslate((width - imgW * s) / 2f, (height - imgH * s) / 2f)
            toView.invert(toMask)
        }

        override fun onDraw(canvas: Canvas) {
            val m = mask ?: return
            canvas.drawBitmap(m, toView, overlay)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val m = mask ?: return false
            pts[0] = ev.x
            pts[1] = ev.y
            toMask.mapPoints(pts)
            val maskScale = m.width / (max(width.toFloat() / imgW, height.toFloat() / imgH) * imgW)
            stroke.strokeWidth = max(1f, brushPx * maskScale)
            stroke.xfermode = if (erase) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            val c = Canvas(m)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    lastX = pts[0]
                    lastY = pts[1]
                    c.drawPoint(lastX, lastY, stroke)
                }
                MotionEvent.ACTION_MOVE -> {
                    c.drawLine(lastX, lastY, pts[0], pts[1], stroke)
                    lastX = pts[0]
                    lastY = pts[1]
                }
            }
            invalidate()
            return true
        }
    }
}
