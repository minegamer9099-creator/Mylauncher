package com.example.mylauncher

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/**
 * Home entries ke views banata aur style karta hai. Views id ke hisaab se cache hote hain, isliye page badalne
 * ya item hilne par dobara inflate nahi hote (aur system widgets dobara bante hi nahi).
 */
class HomeViewFactory(private val a: MainActivity) {

    private val cache = HashMap<String, View>()
    private val signatures = HashMap<String, String>()
    private var styleVersion = 0

    /** Entry ka view (cache se ya naya); content / style badla ho to dobara bind. */
    fun viewFor(e: HomeEntry): View {
        var v = cache[e.id]
        if (v == null) {
            v = create(e)
            cache[e.id] = v
        }
        val sig = signature(e)
        if (signatures[e.id] != sig) {
            signatures[e.id] = sig
            bind(e, v)
        }
        return v
    }

    /** Style / colors badle: agle sync pe sab dobara bind honge. */
    fun invalidateStyle() {
        styleVersion++
    }

    /** Jo entries ab nahi hain unke views cache se hatao. */
    fun retain(ids: Set<String>) {
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.key !in ids) {
                (entry.value.parent as? ViewGroup)?.removeView(entry.value)
                signatures.remove(entry.key)
                it.remove()
            }
        }
    }

    fun clear() {
        cache.clear()
        signatures.clear()
    }

    /** Drag ke liye floating copy (cache / listeners ke bina). */
    fun ghostFor(e: HomeEntry): View {
        if (e.kind == EntryKind.APP || e.kind == EntryKind.FOLDER) {
            val v = a.layoutInflater.inflate(
                if (e.kind == EntryKind.APP) R.layout.item_app else R.layout.item_folder, null, false
            )
            bind(e, v)
            return v
        }
        val t = TextView(a)
        t.text = when (e.kind) {
            EntryKind.SEARCH -> "Search"
            EntryKind.BUILTIN -> e.type.replaceFirstChar { it.uppercase() }
            else -> a.widgetHost.infoFor(e.widgetId)?.loadLabel(a.packageManager)?.toString() ?: "Widget"
        }
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.textSize = 13f
        t.background = LiquidGlassDrawable(18f * a.resources.displayMetrics.density, 0.45f)
        return t
    }

    private fun signature(e: HomeEntry): String = when (e.kind) {
        EntryKind.APP -> "$styleVersion|${e.pkg}"
        EntryKind.FOLDER -> "$styleVersion|${e.name}|${e.packages.joinToString(",")}"
        else -> "$styleVersion"
    }

    private fun create(e: HomeEntry): View {
        val inflater = a.layoutInflater
        val v: View = when (e.kind) {
            EntryKind.APP -> {
                val view = inflater.inflate(R.layout.item_app, null, false)
                view.setOnClickListener {
                    val app = a.appByPkg[e.pkg]
                    if (app != null) a.homeActions.onAppClick(app, view.findViewById(R.id.appIcon))
                }
                view.applyPressEffect()
                view
            }
            EntryKind.FOLDER -> {
                val view = inflater.inflate(R.layout.item_folder, null, false)
                val tile = view.findViewById<FolderTileView>(R.id.folderTile)
                view.setOnClickListener { a.openFolderEntry(e.id, tile) }
                view.applyPressEffect { pressed ->
                    if (a.iconStyle.touchGlow) tile.glowTo(if (pressed) 1f else 0f)
                }
                view
            }
            EntryKind.SEARCH -> {
                val view = inflater.inflate(R.layout.item_search, null, false)
                view.setOnClickListener { a.homeActions.onSearchClick() }
                view.findViewById<View>(R.id.searchMic).setOnClickListener { a.homeActions.onMicClick() }
                view
            }
            EntryKind.BUILTIN -> WidgetTypes.create(a, e.type)
            EntryKind.WIDGET -> a.widgetHost.viewFor(e.widgetId) ?: placeholder()
        }
        v.tag = e.id
        return v
    }

    private fun bind(e: HomeEntry, v: View) {
        val style = a.iconStyle
        when (e.kind) {
            EntryKind.APP -> {
                val app = a.appByPkg[e.pkg] ?: return
                val icon = v.findViewById<ImageView>(R.id.appIcon)
                val name = v.findViewById<TextView>(R.id.appName)
                icon.setImageDrawable(style.drawableFor(app))
                style.styleIcon(icon)
                name.text = app.label
                name.setTextColor(a.nameTint)
                name.visibility = if (style.showLabels) View.VISIBLE else View.GONE
            }
            EntryKind.FOLDER -> {
                val tile = v.findViewById<FolderTileView>(R.id.folderTile)
                val name = v.findViewById<TextView>(R.id.folderName)
                val lp = tile.layoutParams
                if (lp.width != style.sizePx || lp.height != style.sizePx) {
                    lp.width = style.sizePx
                    lp.height = style.sizePx
                    tile.layoutParams = lp
                }
                tile.setApps(e.packages.mapNotNull { a.appByPkg[it] }, style)
                name.text = e.name
                name.setTextColor(a.nameTint)
                name.visibility = if (style.showLabels) View.VISIBLE else View.GONE
            }
            EntryKind.SEARCH -> bindSearch(v)
            EntryKind.BUILTIN -> styleBuiltin(v)
            EntryKind.WIDGET -> {}
        }
    }

    private fun bindSearch(v: View) {
        val light = a.useLightGlass()
        val primary: Int = if (light) SEARCH_TEXT_DARK else Color.WHITE
        val secondary: Int = if (light) SEARCH_TEXT_DARK_SOFT else SEARCH_TEXT_LIGHT_SOFT
        val density = v.resources.displayMetrics.density
        v.findViewById<View>(R.id.searchPill).background =
            LiquidGlassDrawable(26f * density, if (light) 0.6f else 0.5f)
        v.findViewById<TextView>(R.id.searchLabel).setTextColor(secondary)
        val tint = ColorStateList.valueOf(primary)
        v.findViewById<ImageView>(R.id.searchIcon).imageTintList = tint
        v.findViewById<ImageView>(R.id.searchMic).imageTintList = tint
    }

    /** Launcher ke apne glass widgets pe wallpaper / settings ke hisaab se style. */
    private fun styleBuiltin(widget: View) {
        val strength = a.glassStrength()
        val light = a.useLightGlass()
        val accent = a.effectiveAccent()
        val glow = a.prefs.touchGlow
        if (widget is SuggestedAppsWidgetView) {
            widget.setApps(a.suggestedApps(), a.iconStyle)
            widget.onAppClick = a.homeActions.onAppClick
        }
        if (widget is MusicWidgetView) {
            widget.refreshGlass()
            widget.glassStrength = strength
            widget.accent = accent
            widget.lightStyle = light
            widget.touchGlow = glow
        } else if (widget is GlassCardView) {
            widget.refreshGlass()
            widget.glassStrength = strength
            widget.accent = accent
            widget.lightStyle = light
            widget.touchGlow = glow
        }
    }

    private fun placeholder(): View {
        val t = TextView(a)
        t.text = "Widget available nahi\n(x dabake hatao)"
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.textSize = 12f
        t.background = LiquidGlassDrawable(18f * a.resources.displayMetrics.density, 0.4f)
        return t
    }

    private companion object {
        // Search pill ke colors (pehle HomeGridAdapter mein parseColor se aate the)
        val SEARCH_TEXT_DARK: Int = Color.parseColor("#1F2328")
        val SEARCH_TEXT_DARK_SOFT: Int = Color.parseColor("#8A1F2328")
        val SEARCH_TEXT_LIGHT_SOFT: Int = Color.parseColor("#CCFFFFFF")
    }
}
