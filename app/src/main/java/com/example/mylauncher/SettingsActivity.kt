package com.example.mylauncher

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Launcher settings (liquid glass look). Alag-alag pages:
 *  - hub: 4 section cards
 *  - wallpaper: Wallpapers & style (wallpaper + uske colors / glass)
 *  - widgets: kaun se widgets home pe dikhen
 *  - layout: icons, clock style, search, folders
 *  - general: blur, glow, transitions, system
 * Edit mode ke bottom buttons seedha apne page pe kholte hain.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) setWallpaperFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = Prefs(this)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_HUB

        // Peeche wallpaper blur (Android 12+) + halka dark scrim = liquid glass feel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            windowManager.isCrossWindowBlurEnabled && prefs.blurEnabled
        ) {
            window.setBackgroundBlurRadius(70)
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#66101018"))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL

        column.addView(buildTopBar(titleFor(page), page != PAGE_HUB))

        val scroll = ScrollView(this)
        scroll.clipToPadding = false
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(4), dp(16), dp(24))
        scroll.addView(content)
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        when (page) {
            PAGE_WALLPAPER -> buildWallpaperPage()
            PAGE_WIDGETS -> buildWidgetsPage()
            PAGE_LAYOUT -> buildLayoutPage()
            PAGE_GENERAL -> buildGeneralPage()
            else -> buildHubPage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun titleFor(page: String): String = when (page) {
        PAGE_WALLPAPER -> "Wallpapers & style"
        PAGE_WIDGETS -> "Widgets"
        PAGE_LAYOUT -> "Layout"
        PAGE_GENERAL -> "Home screen settings"
        else -> "Launcher settings"
    }

    // ---------------------------------------------------------------------------------------
    // Top bar + hub
    // ---------------------------------------------------------------------------------------
    private fun buildTopBar(title: String, showBack: Boolean): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(8), dp(16), dp(8))

        if (showBack) {
            val back = label("‹", 32f, Color.WHITE, bold = true)
            back.gravity = Gravity.CENTER
            back.background = LiquidGlassDrawable(dp(20).toFloat(), 0.45f)
            back.isClickable = true
            back.setOnClickListener { finish() }
            bar.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        val titleView = label(title, 22f, Color.WHITE, bold = true)
        titleView.setPadding(dp(if (showBack) 12 else 4), 0, 0, 0)
        bar.addView(titleView)
        return bar
    }

    private fun buildHubPage() {
        addNavCard("Wallpapers & style", "Wallpaper lagao, colors aur glass ka look", PAGE_WALLPAPER)
        addNavCard("Widgets", "Home pe kaun se widgets dikhen (clock, battery, notes...)", PAGE_WIDGETS)
        addNavCard("Layout", "Icons, clock style, search bar, folders", PAGE_LAYOUT)
        addNavCard("Home screen settings", "Blur, glow, transitions aur system options", PAGE_GENERAL)
    }

    private fun addNavCard(title: String, subtitle: String, page: String) {
        val card = addCard()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(10), 0, dp(10))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(title, 17f, Color.WHITE, bold = true))
        texts.addView(label(subtitle, 12f, DIM))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(label("›", 26f, DIM))

        row.isClickable = true
        row.setOnClickListener { startActivity(pageIntent(this, page)) }
        card.addView(row)
    }

    // ---------------------------------------------------------------------------------------
    // Page: Wallpapers & style
    // ---------------------------------------------------------------------------------------
    private fun buildWallpaperPage() {
        addSectionTitle("Wallpaper")
        val wallpaper = addCard()
        wallpaper.addView(label("Preset chuno ya gallery se apni photo lagao", 12f, DIM))
        buildWallpaperPicker(wallpaper)
        addButton(wallpaper, "Gallery se chuno") { pickImage.launch("image/*") }

        addSectionTitle("Wallpaper effects")
        val effects = addCard()
        addSwitch(effects, "Wallpaper parallax", "Pages ke saath wallpaper khisakta hai", prefs.parallax) { prefs.parallax = it }

        addSectionTitle("Colors from wallpaper")
        val colors = addCard()
        addSwitch(colors, "Colorful clock", "Clock ka pehla digit wallpaper ke color mein", prefs.colorfulClock) { prefs.colorfulClock = it }
        addChoice(
            colors, "Widget glass",
            listOf("auto" to "Auto (wallpaper ke hisaab se)", "light" to "Light glass", "dark" to "Dark glass"),
            prefs.widgetGlass
        ) { prefs.widgetGlass = it }
        addSeek(colors, "Glass strength", 0, 100, prefs.glassStrength, { "$it%" }) { prefs.glassStrength = it }
    }

    // ---------------------------------------------------------------------------------------
    // Page: Widgets
    // ---------------------------------------------------------------------------------------
    private fun buildWidgetsPage() {
        addSectionTitle("Home widgets")
        val card = addCard()
        card.addView(label("Jo widgets on karoge wo home ke pehle page pe dikhenge (2 per row).", 12f, DIM))
        for (type in WidgetTypes.ALL) {
            addSwitch(
                card, WidgetTypes.title(type), WidgetTypes.description(type),
                type in prefs.widgetTypes
            ) { on ->
                val current = prefs.widgetTypes.toMutableList()
                if (on) {
                    if (type !in current) current.add(type)
                } else {
                    current.remove(type)
                }
                prefs.widgetTypes = current
            }
        }

        addSectionTitle("Music widget")
        val music = addCard()
        music.addView(label("Music widget ke liye Notification access allow karna padta hai.", 12f, DIM))
        addButton(music, "Notification access kholo") {
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Page: Layout
    // ---------------------------------------------------------------------------------------
    private fun buildLayoutPage() {
        addSectionTitle("Icons")
        val icons = addCard()
        addSeek(icons, "Icon size", 48, 68, prefs.iconSizeDp, { "$it dp" }) { prefs.iconSizeDp = it }
        addChoice(
            icons, "Icon shape",
            listOf("squircle" to "Rounded square", "circle" to "Circle"),
            prefs.iconShape
        ) { prefs.iconShape = it }
        addChoice(
            icons, "Icon theme",
            listOf("original" to "Original", "dark" to "Dark icons", "auto" to "System ke saath"),
            prefs.iconTheme
        ) { prefs.iconTheme = it }
        addSwitch(icons, "Contour glow (icon rim)", "Icons ke kinare pe chamakdaar line", prefs.iconRim) { prefs.iconRim = it }
        addSwitch(icons, "App names dikhao", null, prefs.showLabels) { prefs.showLabels = it }

        addSectionTitle("Home screen")
        val home = addCard()
        addChoice(
            home, "Clock style",
            listOf("compact" to "Compact (ColorOS default)", "centered" to "Bada, beech mein"),
            prefs.clockStyle
        ) { prefs.clockStyle = it }
        addSwitch(home, "Search widget", "Home pe search bar (edit mode mein drag karke hila sakte ho)", prefs.searchWidget) { prefs.searchWidget = it }
        addSwitch(home, "Auto folders", "Google aur category ke apps glass folders mein", prefs.autoFolders) { prefs.autoFolders = it }

        addSectionTitle("Reset")
        val reset = addCard()
        addButton(reset, "Home layout reset (icons ka order)") {
            prefs.homeOrder = emptyList()
            toast("Home layout default ho gaya")
        }
        addButton(reset, "Hide kiye hue apps wapas laao") {
            prefs.hiddenApps = emptySet()
            toast("Sab apps wapas aa gaye")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Page: Home screen settings (general)
    // ---------------------------------------------------------------------------------------
    private fun buildGeneralPage() {
        addSectionTitle("Textured visual effects")
        val effects = addCard()
        addSwitch(effects, "Visual blur", "Drawer aur folder ke peeche real-time blur (Android 12+)", prefs.blurEnabled) { prefs.blurEnabled = it }
        addSwitch(effects, "Animation blur", "App kholte/band karte waqt wallpaper blur (kam-power phones pe lag kar sakta hai)", prefs.animationBlur) { prefs.animationBlur = it }
        addSwitch(effects, "Glow on touch", "Widgets aur folders ko dabane pe kinare chamakte hain", prefs.touchGlow) { prefs.touchGlow = it }

        addSectionTitle("Transitions")
        val transitions = addCard()
        addSwitch(transitions, "3D page transition", "Pages swipe karte waqt depth effect", prefs.transition3d) { prefs.transition3d = it }

        addSectionTitle("System")
        val system = addCard()
        addButton(system, "Default launcher chuno") { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) }
        addButton(system, "Sab settings reset karo") {
            prefs.reset()
            recreate()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Wallpaper
    // ---------------------------------------------------------------------------------------
    private fun buildWallpaperPicker(card: LinearLayout) {
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(10), 0, dp(6))

        val w = dp(72)
        val h = dp(128)
        for (preset in WallpaperPresets.presets) {
            val thumb = ImageView(this)
            thumb.setImageBitmap(WallpaperPresets.render(preset, w, h))
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.contentDescription = preset.name
            val shape = GradientDrawable()
            shape.setCornerRadius(dp(16).toFloat())
            shape.setColor(Color.DKGRAY)
            thumb.background = shape
            thumb.clipToOutline = true
            thumb.setOnClickListener { applyPreset(preset) }
            val lp = LinearLayout.LayoutParams(w, h)
            lp.marginEnd = dp(10)
            row.addView(thumb, lp)
        }
        scroll.addView(row)
        card.addView(scroll)
    }

    private fun applyPreset(preset: WallpaperPresets.Preset) {
        toast("Wallpaper set ho raha hai...")
        executor.execute {
            try {
                val dm = resources.displayMetrics
                val bmp = WallpaperPresets.render(preset, dm.widthPixels, dm.heightPixels)
                setSystemWallpaper(bmp)
                toastOnMain("Wallpaper set ho gaya")
            } catch (e: Exception) {
                toastOnMain("Wallpaper set nahi ho paya")
            }
        }
    }

    private fun setWallpaperFromUri(uri: Uri) {
        toast("Wallpaper set ho raha hai...")
        executor.execute {
            try {
                val dm = resources.displayMetrics
                val target = max(dm.widthPixels, dm.heightPixels)

                // Pehle sirf size padho, phir bade image ko chhota karke decode karo
                val bounds = BitmapFactory.Options()
                bounds.inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) {
                    sample *= 2
                }
                val decode = BitmapFactory.Options()
                decode.inSampleSize = sample
                val bmp = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decode)
                }
                if (bmp == null) {
                    toastOnMain("Image padh nahi paye")
                    return@execute
                }
                setSystemWallpaper(bmp)
                toastOnMain("Wallpaper set ho gaya")
            } catch (e: Exception) {
                toastOnMain("Wallpaper set nahi ho paya")
            }
        }
    }

    private fun setSystemWallpaper(bmp: Bitmap) {
        WallpaperManager.getInstance(this).setBitmap(
            bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        )
    }

    // ---------------------------------------------------------------------------------------
    // Row builders (liquid glass cards)
    // ---------------------------------------------------------------------------------------
    private fun addSectionTitle(text: String) {
        val t = label(text.uppercase(), 12f, Color.parseColor("#99FFFFFF"), bold = true)
        t.setPadding(dp(8), dp(14), dp(8), dp(6))
        content.addView(t)
    }

    private fun addCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(10), dp(16), dp(10))
        card.background = LiquidGlassDrawable(dp(24).toFloat(), 0.4f)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = dp(10)
        content.addView(card, lp)
        return card
    }

    private fun addSwitch(
        parent: LinearLayout,
        title: String,
        desc: String?,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(title, 15f, Color.WHITE))
        if (desc != null) texts.addView(label(desc, 12f, DIM))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        val toggle = SwitchCompat(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        toggle.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.parseColor("#C8C8D0")))
        toggle.trackTintList = ColorStateList(states, intArrayOf(Color.parseColor("#4C8DFF"), Color.parseColor("#55FFFFFF")))
        toggle.isChecked = checked
        toggle.setOnCheckedChangeListener { _, value -> onChange(value) }
        row.addView(toggle)
        parent.addView(row)
    }

    private fun addSeek(
        parent: LinearLayout,
        title: String,
        min: Int,
        max: Int,
        current: Int,
        format: (Int) -> String,
        onChange: (Int) -> Unit
    ) {
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.setPadding(0, dp(8), 0, 0)
        header.addView(label(title, 15f, Color.WHITE), LinearLayout.LayoutParams(0, WRAP, 1f))
        val valueView = label(format(current), 13f, DIM)
        header.addView(valueView)
        parent.addView(header)

        val seek = SeekBar(this)
        seek.progressTintList = ColorStateList.valueOf(Color.parseColor("#9CC3FF"))
        seek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        seek.max = max - min
        seek.progress = current - min
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueView.text = format(progress + min)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {
                onChange((bar?.progress ?: 0) + min)
            }
        })
        parent.addView(seek)
    }

    private fun addChoice(
        parent: LinearLayout,
        title: String,
        options: List<Pair<String, String>>,
        current: String,
        onChange: (String) -> Unit
    ) {
        var selected = current
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        row.isClickable = true

        row.addView(label(title, 15f, Color.WHITE), LinearLayout.LayoutParams(0, WRAP, 1f))
        val currentLabel = options.firstOrNull { it.first == selected }?.second ?: selected
        val valueView = label(currentLabel, 14f, Color.parseColor("#9CC3FF"))
        row.addView(valueView)

        row.setOnClickListener {
            val labels = options.map { it.second as CharSequence }.toTypedArray()
            val checkedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                    selected = options[which].first
                    valueView.text = options[which].second
                    onChange(selected)
                    dialog.dismiss()
                }
                .show()
        }
        parent.addView(row)
    }

    private fun addButton(parent: LinearLayout, text: String, onClick: () -> Unit) {
        val button = label(text, 14f, Color.WHITE, bold = true)
        button.gravity = Gravity.CENTER
        button.setPadding(dp(16), dp(12), dp(16), dp(12))
        button.background = LiquidGlassDrawable(dp(18).toFloat(), 0.6f)
        button.isClickable = true
        button.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(8)
        lp.bottomMargin = dp(4)
        parent.addView(button, lp)
    }

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(color)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------
    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Ye option is phone pe available nahi hai")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun toastOnMain(msg: String) {
        mainHandler.post { toast(msg) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_HUB = "hub"
        const val PAGE_WALLPAPER = "wallpaper"
        const val PAGE_WIDGETS = "widgets"
        const val PAGE_LAYOUT = "layout"
        const val PAGE_GENERAL = "general"

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val DIM = Color.parseColor("#B3FFFFFF")

        fun pageIntent(context: Context, page: String): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page)
    }
}
