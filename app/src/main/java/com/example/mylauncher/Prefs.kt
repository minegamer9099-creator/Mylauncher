package com.example.mylauncher

import android.content.Context
import android.content.SharedPreferences

/** Launcher settings ka storage (SharedPreferences). Settings screen yahan likhta hai, launcher yahan se padhta hai. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var iconSizeDp: Int
        get() = sp.getInt("icon_size", 60)
        set(value) { sp.edit().putInt("icon_size", value).apply() }

    /** "squircle" ya "circle" */
    var iconShape: String
        get() = sp.getString("icon_shape", "squircle") ?: "squircle"
        set(value) { sp.edit().putString("icon_shape", value).apply() }

    /** "original", "dark" ya "auto" (system dark mode ke saath) */
    var iconTheme: String
        get() = sp.getString("icon_theme", "original") ?: "original"
        set(value) { sp.edit().putString("icon_theme", value).apply() }

    /** "compact" (ColorOS default: left mein clock, right mein date) ya "centered" (bada, beech mein) */
    var clockStyle: String
        get() = sp.getString("clock_style", "compact") ?: "compact"
        set(value) { sp.edit().putString("clock_style", value).apply() }

    /** "auto" (wallpaper ke hisaab se), "light" ya "dark" */
    var widgetGlass: String
        get() = sp.getString("widget_glass", "auto") ?: "auto"
        set(value) { sp.edit().putString("widget_glass", value).apply() }

    /** Touch karne pe glass cards ke kinare chamakte hain. */
    var touchGlow: Boolean
        get() = sp.getBoolean("touch_glow", true)
        set(value) { sp.edit().putBoolean("touch_glow", value).apply() }

    /** App open/close pe wallpaper blur (alag toggle, jaise ColorOS mein). */
    var animationBlur: Boolean
        get() = sp.getBoolean("animation_blur", false)
        set(value) { sp.edit().putBoolean("animation_blur", value).apply() }

    var iconRim: Boolean
        get() = sp.getBoolean("icon_rim", true)
        set(value) { sp.edit().putBoolean("icon_rim", value).apply() }

    var showLabels: Boolean
        get() = sp.getBoolean("show_labels", true)
        set(value) { sp.edit().putBoolean("show_labels", value).apply() }

    var blurEnabled: Boolean
        get() = sp.getBoolean("blur", true)
        set(value) { sp.edit().putBoolean("blur", value).apply() }

    /** 0..100: glass kitna "dhundhla/gehra" dikhe */
    var glassStrength: Int
        get() = sp.getInt("glass_strength", 40)
        set(value) { sp.edit().putInt("glass_strength", value).apply() }

    var transition3d: Boolean
        get() = sp.getBoolean("transition_3d", true)
        set(value) { sp.edit().putBoolean("transition_3d", value).apply() }

    var parallax: Boolean
        get() = sp.getBoolean("parallax", true)
        set(value) { sp.edit().putBoolean("parallax", value).apply() }

    var colorfulClock: Boolean
        get() = sp.getBoolean("colorful_clock", true)
        set(value) { sp.edit().putBoolean("colorful_clock", value).apply() }

    var autoFolders: Boolean
        get() = sp.getBoolean("auto_folders", true)
        set(value) { sp.edit().putBoolean("auto_folders", value).apply() }

    /** Jo apps user ne "Hide app" kiye (package names). */
    var hiddenApps: Set<String>
        get() = sp.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        set(value) { sp.edit().putStringSet("hidden_apps", HashSet(value)).apply() }

    /** Home pe dikhne wale widgets (order WidgetTypes.ALL ke hisaab se). Default: clock + battery. */
    var widgetTypes: List<String>
        get() {
            val raw = sp.getString("widget_types", null) ?: return listOf("clock", "battery")
            return raw.split(",").filter { it.isNotEmpty() }
        }
        set(value) {
            // Hamesha ek fixed order mein save karo
            val ordered = WidgetTypes.ALL.filter { it in value }
            sp.edit().putString("widget_types", ordered.joinToString(",")).apply()
        }

    /** Search bar home pe widget ki tarah dikhe. */
    var searchWidget: Boolean
        get() = sp.getBoolean("search_widget", true)
        set(value) { sp.edit().putBoolean("search_widget", value).apply() }

    /** Home ke items ka order (drag-drop ke baad save hota hai). Khaali = default order. */
    var homeOrder: List<String>
        get() = (sp.getString("home_order", "") ?: "").split("\n").filter { it.isNotEmpty() }
        set(value) { sp.edit().putString("home_order", value.joinToString("\n")).apply() }

    /** Settings badli ya nahi, ye pata karne ke liye. */
    fun signature(): String =
        sp.all.entries.sortedBy { it.key }.joinToString("|") { entry ->
            val v = entry.value
            val text = if (v is Set<*>) v.map { it.toString() }.sorted().joinToString(",") else v.toString()
            "${entry.key}=$text"
        }

    /** Sab settings default pe (overlay permission wala flag bacha ke). */
    fun reset() {
        val asked = sp.getBoolean("asked_overlay", false)
        sp.edit().clear().putBoolean("asked_overlay", asked).apply()
    }

    companion object {
        const val NAME = "launcher_prefs"
    }
}
