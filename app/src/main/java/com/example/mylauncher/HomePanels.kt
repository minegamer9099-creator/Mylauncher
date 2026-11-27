package com.example.mylauncher

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// ---------------------------------------------------------------------------------------
// Bottom sheet (widgets / pages): code se banta hai, koi extra layout nahi
// ---------------------------------------------------------------------------------------
internal fun MainActivity.openSheet(title: String, content: View, extra: View? = null) {
    dismissSheet(false)
    val scrim = FrameLayout(this)
    scrim.setBackgroundColor(Color.argb(110, 0, 0, 0))
    scrim.isClickable = true
    scrim.setOnClickListener { dismissSheet() }

    val inset = ViewCompat.getRootWindowInsets(rootLayout)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
    val panel = LinearLayout(this)
    panel.orientation = LinearLayout.VERTICAL
    val r = dp(26).toFloat()
    panel.background = GradientDrawable().apply {
        setColor(Color.parseColor("#F01C1C22"))
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }
    panel.setPadding(dp(16), dp(16), dp(16), dp(12) + inset)
    panel.isClickable = true

    val head = LinearLayout(this)
    head.orientation = LinearLayout.HORIZONTAL
    head.gravity = Gravity.CENTER_VERTICAL
    val t = TextView(this)
    t.text = title
    t.setTextColor(Color.WHITE)
    t.textSize = 18f
    t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
    head.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (extra != null) head.addView(extra)
    panel.addView(head)
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    lp.topMargin = dp(10)
    panel.addView(content, lp)

    val height = (rootLayout.height * 0.62f).toInt()
    scrim.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM))
    rootLayout.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    sheet = scrim
    panel.translationY = height.toFloat()
    panel.springTranslationY(0f, 380f, 0.9f) // bottom sheet halke bounce ke saath upar aata hai
}

private fun MainActivity.sectionTitle(text: String): TextView {
    val t = TextView(this)
    t.text = text
    t.setTextColor(Color.argb(170, 255, 255, 255))
    t.textSize = 12f
    t.setPadding(dp(4), dp(14), 0, dp(4))
    return t
}

private fun MainActivity.chip(text: String, onClick: () -> Unit): TextView {
    val c = TextView(this)
    c.text = text
    c.setTextColor(Color.WHITE)
    c.textSize = 13f
    c.setPadding(dp(14), dp(7), dp(14), dp(7))
    c.background = GradientDrawable().apply {
        setColor(Color.argb(46, 255, 255, 255))
        cornerRadius = dp(18).toFloat()
    }
    c.setOnClickListener { onClick() }
    return c
}

private fun MainActivity.pickerRow(
    title: String,
    sub: String,
    iconHolder: ImageView?,
    onClick: () -> Unit
): View {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(8), dp(8), dp(8), dp(8))
    row.isClickable = true
    row.setOnClickListener { onClick() }
    if (iconHolder != null) {
        row.addView(iconHolder, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(12) })
    }
    val texts = LinearLayout(this)
    texts.orientation = LinearLayout.VERTICAL
    val a = TextView(this)
    a.text = title
    a.setTextColor(Color.WHITE)
    a.textSize = 15f
    val b = TextView(this)
    b.text = sub
    b.setTextColor(Color.argb(160, 255, 255, 255))
    b.textSize = 12f
    texts.addView(a)
    texts.addView(b)
    row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    return row
}

// ---------------------------------------------------------------------------------------
// Widget picker
// ---------------------------------------------------------------------------------------
internal fun MainActivity.showWidgetPicker() {
    val list = LinearLayout(this)
    list.orientation = LinearLayout.VERTICAL
    val scroll = ScrollView(this)
    scroll.addView(list)

    list.addView(sectionTitle("Launcher widgets"))
    val have = state.entries.filter { it.kind == EntryKind.BUILTIN }.map { it.type }.toSet()
    for (type in WidgetTypes.ALL) {
        val sub = WidgetTypes.description(type) + if (type in have) "  (home pe hai)" else ""
        list.addView(pickerRow(WidgetTypes.title(type), sub, null) {
            dismissSheet()
            addBuiltinWidget(type)
        })
    }

    list.addView(sectionTitle("Apps ke widgets"))
    val cells = pagerAdapter?.cellsFor(homePager.currentItem)
    val cw = cells?.cellW?.takeIf { it > 0 } ?: dp(90)
    val ch = cells?.cellH?.takeIf { it > 0 } ?: rowPxFirst.coerceAtLeast(dp(90))
    val items = widgetHost.providers().map { info ->
        val label = try {
            info.loadLabel(packageManager) ?: info.provider.shortClassName
        } catch (e: Exception) {
            info.provider.shortClassName
        }
        Pair(info, label)
    }.sortedBy { it.second.lowercase() }

    for ((info, label) in items) {
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.provider.packageName, 0)).toString()
        } catch (e: Exception) {
            info.provider.packageName
        }
        val sp = widgetHost.spansFor(info, cw, ch, grid.cols, grid.minRows)
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        list.addView(pickerRow(label, "$appName  •  ${sp.sx}x${sp.sy}", iv) {
            dismissSheet()
            addSystemWidget(info)
        })
        // Preview doosre app se decode hota hai: background mein, ek ek karke
        bgExecutor.execute {
            val d = try {
                info.loadPreviewImage(this, 0) ?: info.loadIcon(this, 0)
            } catch (e: Exception) {
                null
            }
            if (d != null) uiHandler.post { if (!isDestroyed && sheet != null) iv.setImageDrawable(d) }
        }
    }
    openSheet("Widgets", scroll)
}

// ---------------------------------------------------------------------------------------
// Page manager
// ---------------------------------------------------------------------------------------
internal fun MainActivity.showPageManager() {
    val list = LinearLayout(this)
    list.orientation = LinearLayout.VERTICAL
    val scroll = ScrollView(this)
    scroll.addView(list)

    lateinit var rebuild: () -> Unit
    rebuild = {
        list.removeAllViews()
        for (p in 0 until state.pageCount) list.addView(pageRow(p, rebuild))
    }
    rebuild()
    val add = chip("+ Page") {
        if (state.pageCount >= HomeOps.MAX_PAGES) {
            Toast.makeText(this, "Isse zyada pages nahi ban sakte", Toast.LENGTH_SHORT).show()
        } else {
            ops.addPage()
            onPagesChanged()
            rebuild()
        }
    }
    openSheet("Pages", scroll, add)
}

private fun MainActivity.pageRow(p: Int, rebuild: () -> Unit): View {
    val count = state.entriesOn(p).size
    val current = homePager.currentItem == p
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(4), dp(6), dp(4), dp(6))

    val title = TextView(this)
    title.text = "Page ${p + 1}  •  $count items" + if (current) "  (abhi)" else ""
    title.setTextColor(Color.WHITE)
    title.textSize = 15f
    title.setOnClickListener {
        dismissSheet()
        homePager.setCurrentItem(p, true)
    }
    row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

    fun gap(v: View) {
        row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(6) })
    }
    if (p > 0) gap(chip("Up") { movePageBy(p, -1); rebuild() })
    if (p < state.pageCount - 1) gap(chip("Down") { movePageBy(p, 1); rebuild() })
    gap(chip("Delete") {
        when {
            !state.isPageEmpty(p) -> Toast.makeText(
                this, "Is page pe items hain: pehle unhe doosre page pe le jao ya hatao", Toast.LENGTH_LONG
            ).show()
            state.pageCount <= 1 -> Toast.makeText(this, "Kam se kam ek page chahiye", Toast.LENGTH_SHORT).show()
            else -> {
                ops.removePage(p)
                onPagesChanged()
                afterModelChange(false)
                rebuild()
            }
        }
    })
    return row
}

private fun MainActivity.movePageBy(p: Int, delta: Int) {
    val to = p + delta
    val cur = homePager.currentItem
    ops.movePage(p, to)
    val newCur = when (cur) {
        p -> to
        to -> p
        else -> cur
    }
    onPagesChanged()
    afterModelChange(false)
    homePager.setCurrentItem(newCur, false)
}

// ---------------------------------------------------------------------------------------
// Widget add flow
// ---------------------------------------------------------------------------------------
/** Launcher ke apne glass widget ko home pe jodo (ek type ek hi baar). */
internal fun MainActivity.addBuiltinWidget(type: String) {
    val existing = state.entries.firstOrNull { it.kind == EntryKind.BUILTIN && it.type == type }
    if (existing != null) {
        Toast.makeText(this, "Ye widget pehle se home pe hai", Toast.LENGTH_SHORT).show()
        homePager.setCurrentItem(existing.page, true)
        selectEntry(existing.id)
        return
    }
    prefs.widgetTypes = prefs.widgetTypes + type // Settings ke widgets page se sync rehta hai
    lastPrefsSignature = prefs.signature()
    placeNewEntry(ops.builtin(type))
}

/** Naya item current page se pehli free jagah pe (zarurat pe naya page), phir select. */
internal fun MainActivity.placeNewEntry(e: HomeEntry) {
    ops.placeFlow(e, homePager.currentItem)
    onPagesChanged()
    afterModelChange(true)
    if (homePager.currentItem != e.page) homePager.setCurrentItem(e.page, true)
    selectEntry(e.id)
}

/** System (app) widget: id lo, bind (zarurat pe user ki permission), configure, phir home pe. */
internal fun MainActivity.addSystemWidget(info: AppWidgetProviderInfo) {
    val id = widgetHost.allocateId()
    widgetHost.pending = WidgetHostManager.Pending(id, info)
    if (widgetHost.tryBind(id, info.provider)) {
        afterWidgetBound(id, info)
        return
    }
    try {
        @Suppress("DEPRECATION")
        startActivityForResult(widgetHost.bindIntent(id, info.provider), WidgetHostManager.REQ_BIND)
    } catch (e: Exception) {
        widgetHost.deleteId(id)
        widgetHost.pending = null
        Toast.makeText(this, "Widget add nahi ho paya", Toast.LENGTH_SHORT).show()
    }
}

private fun MainActivity.afterWidgetBound(id: Int, info: AppWidgetProviderInfo) {
    if (info.configure != null) {
        if (!widgetHost.startConfigure(id, WidgetHostManager.REQ_CONFIGURE)) placeWidget(id, info)
    } else {
        placeWidget(id, info)
    }
}

private fun MainActivity.placeWidget(id: Int, info: AppWidgetProviderInfo) {
    val cells = pagerAdapter?.cellsFor(homePager.currentItem)
    val cw = cells?.cellW?.takeIf { it > 0 } ?: dp(90)
    val ch = cells?.cellH?.takeIf { it > 0 } ?: rowPxFirst.coerceAtLeast(dp(90))
    val sp = widgetHost.spansFor(info, cw, ch, grid.cols, grid.minRows)
    val e = HomeEntry("widget:$id", EntryKind.WIDGET, 0, 0, 0, sp.sx, sp.sy)
    e.widgetId = id
    e.provider = info.provider.flattenToString()
    widgetHost.pending = null
    placeNewEntry(e)
}

/** Bind / configure activity ka result. true = ye result hamara tha. */
internal fun MainActivity.handleWidgetResult(requestCode: Int, resultCode: Int): Boolean {
    if (requestCode != WidgetHostManager.REQ_BIND && requestCode != WidgetHostManager.REQ_CONFIGURE) return false
    val p = widgetHost.pending ?: return true
    if (resultCode != Activity.RESULT_OK) {
        widgetHost.deleteId(p.widgetId)
        widgetHost.pending = null
        return true
    }
    if (requestCode == WidgetHostManager.REQ_BIND) afterWidgetBound(p.widgetId, p.info) else placeWidget(p.widgetId, p.info)
    return true
}
