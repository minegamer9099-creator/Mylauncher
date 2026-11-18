package com.example.mylauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/**
 * Wallpaper se nikle hue colors, aur unse bane UI colors.
 */
data class ThemeColors(
    val dominant: Int,      // wallpaper ka sabse zyada dikhne wala color
    val vibrant: Int,       // sabse "punchy" color
    val muted: Int,         // shaant/soft color
    val appNameTint: Int,   // app ke naam ka text color
    val searchBorder: Int,  // search bar ke border ka color
    val drawerTop: Int,     // drawer background gradient (upar)
    val drawerBottom: Int,  // drawer background gradient (neeche)
    val accent: Int,        // clock ke pehle digit ka strong accent color
    val isLight: Boolean    // wallpaper halka (light) hai ya gehra
)

/**
 * PaletteEngine: system wallpaper se colors nikalta hai aur wallpaper badalne pe
 * khud dobara nikalta hai.
 *
 * Do tareeqe (pehla try hota hai, fail ho to doosra):
 *  1. Wallpaper ka bitmap -> Palette API (zyada accurate: vibrant/dominant/muted)
 *  2. WallpaperColors (Android 8.1+, bina permission ke)
 *     Android 13+ pe bitmap padhne ke liye storage permission lag sakti hai, isliye ye fallback zaroori hai.
 *
 * Use:
 *   val engine = PaletteEngine(this)
 *   engine.start { theme -> /* UI pe apply karo */ }
 *   ...
 *   engine.stop()   // onDestroy mein
 */
class PaletteEngine(context: Context) {

    private val appContext = context.applicationContext
    private val wallpaperManager = WallpaperManager.getInstance(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Pichla nikala hua theme + us wallpaper ki id (alag file, taaki launcher ki settings pe asar na pade)
    private val store = appContext.getSharedPreferences("launcher_theme", Context.MODE_PRIVATE)

    @Volatile
    private var stopped = false

    private var callback: ((ThemeColors) -> Unit)? = null
    private var colorsListener: WallpaperManager.OnColorsChangedListener? = null

    /** Colors nikalna shuru karo, aur wallpaper badalne pe auto-update. */
    fun start(onThemeReady: (ThemeColors) -> Unit) {
        stopped = false
        callback = onThemeReady

        // Pichli baar ka theme turant lagao (wallpaper decode ka intezaar nahi), phir dekho wallpaper badla ya nahi
        val cached = loadCached()
        if (cached != null) onThemeReady(cached.second)
        refresh(force = false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val listener = WallpaperManager.OnColorsChangedListener { _, _ -> refresh(force = true) }
            colorsListener = listener
            wallpaperManager.addOnColorsChangedListener(listener, mainHandler)
        }
    }

    fun stop() {
        stopped = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            colorsListener?.let { wallpaperManager.removeOnColorsChangedListener(it) }
        }
        colorsListener = null
        callback = null
    }

    /**
     * Background thread pe colors nikalo, phir main thread pe callback do.
     * force = false: wallpaper pichli baar jaisa hi hai to kuch nahi karna (poora wallpaper decode karna bachta hai).
     */
    fun refresh(force: Boolean = false) {
        Background.io.execute {
            if (stopped) return@execute
            val id = currentWallpaperId()
            if (!force && id != NO_ID) {
                val cached = loadCached()
                if (cached != null && cached.first == id) return@execute
            }
            val theme = fromBitmapPalette() ?: fromWallpaperColors() ?: buildTheme(
                DEFAULT_DOMINANT, DEFAULT_VIBRANT, DEFAULT_MUTED
            )
            saveCached(id, theme)
            mainHandler.post {
                if (!stopped) callback?.invoke(theme)
            }
        }
    }

    private fun currentWallpaperId(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        } else {
            NO_ID
        }

    private fun saveCached(id: Int, t: ThemeColors) {
        store.edit()
            .putInt("id", id)
            .putInt("dominant", t.dominant)
            .putInt("vibrant", t.vibrant)
            .putInt("muted", t.muted)
            .putInt("tint", t.appNameTint)
            .putInt("border", t.searchBorder)
            .putInt("top", t.drawerTop)
            .putInt("bottom", t.drawerBottom)
            .putInt("accent", t.accent)
            .putBoolean("light", t.isLight)
            .apply()
    }

    private fun loadCached(): Pair<Int, ThemeColors>? {
        if (!store.contains("id") || !store.contains("accent")) return null
        val theme = ThemeColors(
            dominant = store.getInt("dominant", DEFAULT_DOMINANT),
            vibrant = store.getInt("vibrant", DEFAULT_VIBRANT),
            muted = store.getInt("muted", DEFAULT_MUTED),
            appNameTint = store.getInt("tint", Color.WHITE),
            searchBorder = store.getInt("border", Color.WHITE),
            drawerTop = store.getInt("top", DEFAULT_DOMINANT),
            drawerBottom = store.getInt("bottom", DEFAULT_MUTED),
            accent = store.getInt("accent", DEFAULT_VIBRANT),
            isLight = store.getBoolean("light", false)
        )
        return Pair(store.getInt("id", NO_ID), theme)
    }

    // ---------------------------------------------------------------------------------------
    // Method 1: Palette API
    // ---------------------------------------------------------------------------------------
    private fun fromBitmapPalette(): ThemeColors? {
        return try {
            val drawable = wallpaperManager.drawable ?: return null
            // Chhota bitmap kaafi hai, isse fast + kam memory
            val bitmap = drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            bitmap.recycle() // chhota bitmap hai, par turant chhod do

            val dominant = palette.getDominantColor(DEFAULT_DOMINANT)
            // Vibrant na mile to light vibrant, phir muted, phir dominant try karo
            val vibrant = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: dominant
            val muted = palette.getMutedColor(dominant)

            buildTheme(dominant, vibrant, muted)
        } catch (e: Exception) {
            null // SecurityException ya null drawable (live wallpaper) -> fallback
        }
    }

    // ---------------------------------------------------------------------------------------
    // Method 2: WallpaperColors (Android 8.1+)
    // ---------------------------------------------------------------------------------------
    private fun fromWallpaperColors(): ThemeColors? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return try {
            val wc = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
            val primary = wc.primaryColor.toArgb()
            val secondary = wc.secondaryColor?.toArgb() ?: primary
            val tertiary = wc.tertiaryColor?.toArgb() ?: primary
            buildTheme(primary, secondary, tertiary)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Raw colors -> UI colors
    // ---------------------------------------------------------------------------------------
    private fun buildTheme(dominant: Int, vibrant: Int, muted: Int): ThemeColors {
        val readable = makeReadableOnDark(vibrant)
        return ThemeColors(
            dominant = dominant,
            vibrant = vibrant,
            muted = muted,
            // Safed ke paas, bas halka sa vibrant touch: padhne mein asaan
            appNameTint = ColorUtils.blendARGB(Color.WHITE, readable, 0.40f),
            searchBorder = ColorUtils.setAlphaComponent(readable, 0xCC),
            // Kaale ko wallpaper ke color se halka mix karo -> accent wala dark glass
            drawerTop = ColorUtils.blendARGB(Color.BLACK, dominant, 0.35f),
            drawerBottom = ColorUtils.blendARGB(Color.BLACK, muted, 0.20f),
            accent = makeAccent(vibrant),
            isLight = ColorUtils.calculateLuminance(dominant) > 0.55
        )
    }

    /** Dark glass background pe color dikhe, isliye lightness 65%-85% ke beech rakho. */
    private fun makeReadableOnDark(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.35f)        // saturation: bilkul grey na ho
        hsl[2] = hsl[2].coerceIn(0.65f, 0.85f)      // lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /** Clock ke accent ke liye: saturated, na bahut halka na bahut gehra. */
    private fun makeAccent(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.65f)
        hsl[2] = hsl[2].coerceIn(0.50f, 0.62f)
        return ColorUtils.HSLToColor(hsl)
    }

    private companion object {
        const val NO_ID = -1
        val DEFAULT_DOMINANT = Color.parseColor("#26324D")
        val DEFAULT_VIBRANT = Color.parseColor("#7C9CFF")
        val DEFAULT_MUTED = Color.parseColor("#3A4660")
    }
}
