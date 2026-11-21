package com.example.mylauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import kotlin.math.ceil
import kotlin.math.max

/**
 * Android ke asli AppWidget system ka host: widget id lena, bind (user ki permission ke saath), configure
 * activity, view banana aur hatana. Views id ke hisaab se cache hoti hain, isliye widget ko ek jagah se
 * doosri jagah (ya page) le jane par dobara nahi banta.
 */
class WidgetHostManager(private val activity: Activity) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private val views = HashMap<Int, AppWidgetHostView>()
    private var listening = false

    /** Add flow jo activity result ka intezaar kar raha hai. */
    var pending: Pending? = null

    class Pending(val widgetId: Int, val info: AppWidgetProviderInfo)

    /** Widget ki size cells mein: default size aur resize limits. */
    class Spans(val sx: Int, val sy: Int, val minSx: Int, val minSy: Int, val maxSx: Int, val maxSy: Int, val mode: Int)

    fun start() {
        if (listening) return
        try {
            host.startListening()
            listening = true
        } catch (e: Exception) {
            // kuch phones pe system service abhi ready nahi
        }
    }

    fun stop() {
        if (!listening) return
        try {
            host.stopListening()
        } catch (e: Exception) {
            // ignore
        }
        listening = false
    }

    fun providers(): List<AppWidgetProviderInfo> {
        val all = try {
            manager.installedProviders
        } catch (e: Exception) {
            emptyList<AppWidgetProviderInfo>()
        }
        return all.filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
    }

    fun infoFor(widgetId: Int): AppWidgetProviderInfo? =
        try {
            manager.getAppWidgetInfo(widgetId)
        } catch (e: Exception) {
            null
        }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(widgetId: Int) {
        views.remove(widgetId)
        try {
            host.deleteAppWidgetId(widgetId)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Bina dialog ke bind ho gaya to true; nahi to caller ACTION_APPWIDGET_BIND chalaye. */
    fun tryBind(widgetId: Int, provider: ComponentName): Boolean =
        try {
            manager.bindAppWidgetIdIfAllowed(widgetId, provider)
        } catch (e: Exception) {
            false
        }

    fun bindIntent(widgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    fun startConfigure(widgetId: Int, requestCode: Int): Boolean =
        try {
            host.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, requestCode, null)
            true
        } catch (e: Exception) {
            false
        }

    /** Cached view (ya naya). Provider uninstall ho gaya ho to null. */
    fun viewFor(widgetId: Int): AppWidgetHostView? {
        views[widgetId]?.let { return it }
        val info = infoFor(widgetId) ?: return null
        return try {
            val v = host.createView(activity, widgetId, info)
            views[widgetId] = v
            v
        } catch (e: Exception) {
            null
        }
    }

    /** Widget ko uski nayi size batao (dp) taaki wo apna layout badal sake. */
    fun notifySize(view: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        val d = activity.resources.displayMetrics.density
        val w = (widthPx / d).toInt()
        val h = (heightPx / d).toInt()
        try {
            view.updateAppWidgetSize(null, w, h, w, h)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Provider ki min / resize limits ko cells mein badlo. */
    fun spansFor(info: AppWidgetProviderInfo, cellWpx: Int, cellHpx: Int, cols: Int, maxRows: Int): Spans {
        val cw = max(cellWpx, 1)
        val ch = max(cellHpx, 1)
        var sx = ceil(info.minWidth.toFloat() / cw).toInt()
        var sy = ceil(info.minHeight.toFloat() / ch).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (info.targetCellWidth > 0) sx = info.targetCellWidth
            if (info.targetCellHeight > 0) sy = info.targetCellHeight
        }
        sx = sx.coerceIn(1, cols)
        sy = sy.coerceIn(1, maxRows)
        val mode = info.resizeMode
        val canH = (mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        val canV = (mode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
        var minSx = if (info.minResizeWidth > 0) ceil(info.minResizeWidth.toFloat() / cw).toInt() else sx
        var minSy = if (info.minResizeHeight > 0) ceil(info.minResizeHeight.toFloat() / ch).toInt() else sy
        var maxSx = cols
        var maxSy = maxRows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (info.maxResizeWidth > 0) maxSx = (info.maxResizeWidth / cw).coerceAtLeast(1)
            if (info.maxResizeHeight > 0) maxSy = (info.maxResizeHeight / ch).coerceAtLeast(1)
        }
        minSx = minSx.coerceIn(1, sx)
        minSy = minSy.coerceIn(1, sy)
        maxSx = maxSx.coerceIn(sx, cols)
        maxSy = maxSy.coerceIn(sy, maxRows)
        if (!canH) {
            minSx = sx
            maxSx = sx
        }
        if (!canV) {
            minSy = sy
            maxSy = sy
        }
        return Spans(sx, sy, minSx, minSy, maxSx, maxSy, mode)
    }

    fun clearViews() {
        views.clear()
    }

    companion object {
        private const val HOST_ID = 4242
        const val REQ_BIND = 9101
        const val REQ_CONFIGURE = 9102
    }
}
