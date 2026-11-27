package com.example.mylauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class EntryKind { APP, FOLDER, WIDGET, BUILTIN, SEARCH }

/**
 * Home screen pe rakhi ek cheez (app, folder, widget...) aur uski grid position.
 * Position (page, cx, cy) aur size (sx, sy) cells mein hoti hai: koi hardcoded pixel nahi.
 */
class HomeEntry(
    val id: String,
    val kind: EntryKind,
    var page: Int = 0,
    var cx: Int = 0,
    var cy: Int = 0,
    var sx: Int = 1,
    var sy: Int = 1
) {
    /** Folder ka naam. */
    var name: String = ""

    /** Folder ke andar ke apps (package names, order yahi hai). */
    val packages: MutableList<String> = ArrayList()

    /** System widget: AppWidgetHost ka id aur provider (flattened ComponentName). */
    var widgetId: Int = -1
    var provider: String = ""

    /** Launcher ke apne glass widget ka type (WidgetTypes). */
    var type: String = ""

    val pkg: String get() = id.removePrefix("app:")

    val isSingleCell: Boolean get() = sx == 1 && sy == 1

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("page", page)
        put("cx", cx)
        put("cy", cy)
        put("sx", sx)
        put("sy", sy)
        if (name.isNotEmpty()) put("name", name)
        if (packages.isNotEmpty()) put("pkgs", JSONArray(packages))
        if (widgetId >= 0) put("wid", widgetId)
        if (provider.isNotEmpty()) put("prov", provider)
        if (type.isNotEmpty()) put("type", type)
    }

    companion object {
        fun fromJson(o: JSONObject): HomeEntry {
            val e = HomeEntry(
                o.getString("id"), EntryKind.valueOf(o.getString("kind")),
                o.optInt("page", 0), o.optInt("cx", 0), o.optInt("cy", 0),
                o.optInt("sx", 1).coerceAtLeast(1), o.optInt("sy", 1).coerceAtLeast(1)
            )
            e.name = o.optString("name", "")
            val pk = o.optJSONArray("pkgs")
            if (pk != null) for (i in 0 until pk.length()) e.packages.add(pk.getString(i))
            e.widgetId = o.optInt("wid", -1)
            e.provider = o.optString("prov", "")
            e.type = o.optString("type", "")
            return e
        }

        fun app(pkg: String, page: Int = 0, cx: Int = 0, cy: Int = 0) =
            HomeEntry("app:$pkg", EntryKind.APP, page, cx, cy)

        fun newFolder(name: String, page: Int, cx: Int, cy: Int) =
            HomeEntry("folder:" + UUID.randomUUID().toString().take(8), EntryKind.FOLDER, page, cx, cy).also { it.name = name }
    }
}

/** Poora home layout: pages, items aur dock. */
class HomeState {
    var pageCount = 1
    var resetToken = 0L
    val entries = ArrayList<HomeEntry>()

    /** Dock ke apps (package names, left se right). */
    val dock = ArrayList<String>()

    fun entriesOn(page: Int): List<HomeEntry> = entries.filter { it.page == page }
    fun find(id: String): HomeEntry? = entries.firstOrNull { it.id == id }
    fun isPageEmpty(page: Int): Boolean = entries.none { it.page == page }
}

/** Persistent storage: alag SharedPreferences file (settings ke signature ko nahi chhedta). */
class HomeStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)

    fun load(): HomeState? {
        val raw = sp.getString(KEY_LAYOUT, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val st = HomeState()
            st.pageCount = o.optInt("pages", 1).coerceAtLeast(1)
            st.resetToken = o.optLong("reset", 0L)
            val dock = o.optJSONArray("dock")
            if (dock != null) for (i in 0 until dock.length()) st.dock.add(dock.getString(i))
            val arr = o.getJSONArray("entries")
            for (i in 0 until arr.length()) st.entries.add(HomeEntry.fromJson(arr.getJSONObject(i)))
            st
        } catch (e: Exception) {
            null // kharab data: default layout bano
        }
    }

    fun save(st: HomeState) {
        val o = JSONObject()
        o.put("v", 1)
        o.put("pages", st.pageCount)
        o.put("reset", st.resetToken)
        o.put("dock", JSONArray(st.dock))
        val arr = JSONArray()
        for (e in st.entries) arr.put(e.toJson())
        o.put("entries", arr)
        sp.edit().putString(KEY_LAYOUT, o.toString()).apply()
    }

    var currentPage: Int
        get() = sp.getInt(KEY_PAGE, 0)
        set(value) { sp.edit().putInt(KEY_PAGE, value).apply() }

    private companion object {
        const val KEY_LAYOUT = "layout"
        const val KEY_PAGE = "current_page"
    }
}

/** Grid ki shape: columns fixed, rows page ke hisaab se (pehle page pe clock header ki wajah se kam). */
class GridSpec(val cols: Int, private val rowsFirst: Int, private val rowsOther: Int) {
    fun rows(page: Int): Int = if (page == 0) rowsFirst else rowsOther
    val minRows: Int get() = minOf(rowsFirst, rowsOther).coerceAtLeast(1)
}

/** Layout badalne ke saare operations (UI se alag, sirf model pe). */
class HomeOps(val state: HomeState, var grid: GridSpec) {

    fun covers(e: HomeEntry, x: Int, y: Int): Boolean =
        x >= e.cx && x < e.cx + e.sx && y >= e.cy && y < e.cy + e.sy

    fun occupantAt(page: Int, x: Int, y: Int, ignore: String? = null): HomeEntry? =
        state.entries.firstOrNull { it.page == page && it.id != ignore && covers(it, x, y) }

    fun isFree(page: Int, x: Int, y: Int, sx: Int, sy: Int, ignore: String? = null): Boolean {
        if (page < 0 || x < 0 || y < 0 || sx < 1 || sy < 1) return false
        if (x + sx > grid.cols || y + sy > grid.rows(page)) return false
        for (e in state.entries) {
            if (e.page != page || e.id == ignore) continue
            if (x < e.cx + e.sx && x + sx > e.cx && y < e.cy + e.sy && y + sy > e.cy) return false
        }
        return true
    }

    /** Free rect dhundo: near diya ho to sabse paas wala, warna row-major pehla. */
    fun findFree(page: Int, sx: Int, sy: Int, ignore: String? = null, nearX: Int = -1, nearY: Int = -1): IntArray? {
        var best: IntArray? = null
        var bestD = Int.MAX_VALUE
        for (y in 0..(grid.rows(page) - sy)) {
            for (x in 0..(grid.cols - sx)) {
                if (!isFree(page, x, y, sx, sy, ignore)) continue
                if (nearX < 0) return intArrayOf(x, y)
                val d = (x - nearX) * (x - nearX) + (y - nearY) * (y - nearY)
                if (d < bestD) {
                    bestD = d
                    best = intArrayOf(x, y)
                }
            }
        }
        return best
    }

    /** Entry ko [startPage] se pehli free jagah pe rakho; zarurat pe naya page banta hai. */
    fun placeFlow(e: HomeEntry, startPage: Int = 0) {
        state.entries.remove(e)
        e.sx = e.sx.coerceIn(1, grid.cols)
        e.sy = e.sy.coerceIn(1, grid.minRows)
        var p = startPage.coerceAtLeast(0)
        while (true) {
            if (p >= state.pageCount) state.pageCount = p + 1
            val spot = findFree(p, e.sx, e.sy)
            if (spot != null) {
                e.page = p
                e.cx = spot[0]
                e.cy = spot[1]
                state.entries.add(e)
                return
            }
            p++
        }
    }

    /**
     * 1x1 item ko (page, x, y) pe daalo aur wahan ke items ko aage dhakelo (row-major order mein),
     * jab tak pehla khaali cell na mil jaye. Page poora bhara ho to aakhri item agle page pe jata hai.
     */
    fun pushInsert(d: HomeEntry, page: Int, x: Int, y: Int) {
        state.entries.remove(d)
        val cols = grid.cols
        val rows = grid.rows(page)
        val occ = arrayOfNulls<HomeEntry>(cols * rows)
        for (e in state.entries) {
            if (e.page != page) continue
            for (yy in e.cy until minOf(rows, e.cy + e.sy)) {
                for (xx in e.cx until minOf(cols, e.cx + e.sx)) occ[yy * cols + xx] = e
            }
        }
        val t = y * cols + x
        val slots = ArrayList<Int>()
        for (i in t until cols * rows) {
            val o = occ[i]
            if (o == null || o.isSingleCell) slots.add(i)
        }
        if (slots.isEmpty()) {
            placeNearest(d, page, x, y)
            return
        }
        var k = -1
        for (j in slots.indices) {
            if (occ[slots[j]] == null) {
                k = j
                break
            }
        }
        if (k < 0) {
            val last = occ[slots[slots.size - 1]]
            if (last != null) placeFlow(last, page + 1)
            k = slots.size - 1
        }
        for (j in k - 1 downTo 0) {
            val e = occ[slots[j]] ?: continue
            val ni = slots[j + 1]
            e.cx = ni % cols
            e.cy = ni / cols
        }
        d.page = page
        d.cx = slots[0] % cols
        d.cy = slots[0] / cols
        state.entries.add(d)
    }

    /** Kisi bhi size ke item ko (x, y) ke sabse paas free jagah pe rakho; na mile to false. */
    fun placeNearest(d: HomeEntry, page: Int, x: Int, y: Int): Boolean {
        state.entries.remove(d)
        val spot = findFree(page, d.sx, d.sy, null, x, y)
        if (spot == null) return false
        d.page = page
        d.cx = spot[0]
        d.cy = spot[1]
        state.entries.add(d)
        return true
    }

    /** Do apps se naya folder: target ki jagah pe banta hai. */
    fun createFolder(target: HomeEntry, draggedPkg: String, name: String): HomeEntry {
        val f = HomeEntry.newFolder(name, target.page, target.cx, target.cy)
        f.packages.add(target.pkg)
        if (draggedPkg != target.pkg) f.packages.add(draggedPkg)
        state.entries.remove(target)
        state.entries.add(f)
        return f
    }

    /** Folder mein 0 / 1 app bache to folder hata do (1 app ho to wo app folder ki jagah aa jata hai). */
    fun cleanFolders() {
        val replace = ArrayList<HomeEntry>()
        val it = state.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.kind != EntryKind.FOLDER) continue
            if (e.packages.isEmpty()) {
                it.remove()
            } else if (e.packages.size == 1) {
                replace.add(HomeEntry.app(e.packages[0], e.page, e.cx, e.cy))
                it.remove()
            }
        }
        state.entries.addAll(replace)
    }

    // ---- Pages ----
    fun addPage(): Int {
        state.pageCount++
        return state.pageCount - 1
    }

    fun removePage(p: Int): Boolean {
        if (state.pageCount <= 1 || p !in 0 until state.pageCount || !state.isPageEmpty(p)) return false
        for (e in state.entries) if (e.page > p) e.page--
        state.pageCount--
        return true
    }

    fun movePage(from: Int, to: Int) {
        if (from == to || from !in 0 until state.pageCount || to !in 0 until state.pageCount) return
        for (e in state.entries) {
            e.page = when {
                e.page == from -> to
                from < to && e.page > from && e.page <= to -> e.page - 1
                from > to && e.page >= to && e.page < from -> e.page + 1
                else -> e.page
            }
        }
        fixOverflow()
    }

    /** Jo items grid ke bahar reh gaye (rows kam hui, page 0 badla) unhe dobara rakho. */
    fun fixOverflow() {
        for (e in ArrayList(state.entries)) {
            if (e.cx + e.sx > grid.cols || e.cy + e.sy > grid.rows(e.page) || e.cx < 0 || e.cy < 0) {
                placeFlow(e, e.page)
            }
        }
        val maxPage = state.entries.maxOfOrNull { it.page } ?: 0
        if (state.pageCount <= maxPage) state.pageCount = maxPage + 1
    }

    /**
     * Installed apps aur settings ke saath layout milao:
     * hata hue apps nikalo, duplicates hatao, naye apps aakhir mein jodo, widgets / search ka on-off lagao.
     */
    fun reconcile(installed: Collection<String>, builtinTypes: List<String>, wantSearch: Boolean, maxDock: Int) {
        val pkgs = HashSet(installed)
        val seen = HashSet<String>()
        state.dock.removeAll { it !in pkgs || !seen.add(it) }
        while (state.dock.size > maxDock) state.dock.removeAt(state.dock.size - 1).also { seen.remove(it) }

        for (e in state.entries) {
            if (e.kind == EntryKind.FOLDER) e.packages.removeAll { it !in pkgs || !seen.add(it) }
        }
        val drop = ArrayList<HomeEntry>()
        for (e in state.entries) {
            if (e.kind == EntryKind.APP && (e.pkg !in pkgs || !seen.add(e.pkg))) drop.add(e)
        }
        state.entries.removeAll(drop.toSet())
        cleanFolders()
        for (e in state.entries) if (e.kind == EntryKind.APP) seen.add(e.pkg)
        for (e in state.entries) if (e.kind == EntryKind.FOLDER) seen.addAll(e.packages)

        // Built-in glass widgets aur search: settings ke hisaab se
        state.entries.removeAll { it.kind == EntryKind.BUILTIN && it.type !in builtinTypes }
        for (t in builtinTypes) {
            if (state.entries.none { it.kind == EntryKind.BUILTIN && it.type == t }) {
                placeFlow(builtin(t))
            }
        }
        if (!wantSearch) state.entries.removeAll { it.kind == EntryKind.SEARCH }
        if (wantSearch && state.entries.none { it.kind == EntryKind.SEARCH }) {
            placeFlow(HomeEntry("search", EntryKind.SEARCH, 0, 0, 0, grid.cols, 1))
        }

        fixOverflow()
        // Jo apps kahin nahi hain (naye install) unhe aakhir mein rakho
        for (p in installed) {
            if (p !in seen) {
                seen.add(p)
                placeFlow(HomeEntry.app(p))
            }
        }
        if (state.pageCount < 1) state.pageCount = 1
    }

    fun builtin(type: String): HomeEntry =
        HomeEntry("builtin:$type", EntryKind.BUILTIN, 0, 0, 0, 2, 2).also { it.type = type }

    companion object {
        const val MAX_PAGES = 9
        const val MAX_DOCK = 5
    }
}
