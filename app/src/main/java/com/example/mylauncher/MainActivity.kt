package com.example.mylauncher

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.concurrent.Executors
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.max

/**
 * ColorOS-style launcher:
 *  - Home pages (3D transition), glass clock, widgets, folders, dock, search pill
 *  - Long-press -> edit mode; swipe-up / search -> glass app drawer (tabs, A-Z, categories)
 *  - Settings screen (SettingsActivity) se sab kuch manage hota hai
 */
class MainActivity : AppCompatActivity() {

    // ---- Views ----
    private lateinit var rootLayout: FrameLayout
    private lateinit var homeContainer: View
    private lateinit var homePager: ViewPager2
    private lateinit var pageIndicator: LinearLayout
    private lateinit var dockContainer: LinearLayout
    private lateinit var drawerContainer: View
    private lateinit var drawerRecycler: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var drawerAdapter: DrawerAdapter
    private lateinit var drawerLayoutManager: GridLayoutManager
    private lateinit var tabAll: TextView
    private lateinit var tabCategories: TextView
    private lateinit var drawerMenu: View
    private lateinit var alphabetIndex: AlphabetIndexView
    private lateinit var editOverlay: View
    private lateinit var folderOverlay: View
    private lateinit var folderPanel: View
    private lateinit var folderTitle: TextView
    private lateinit var folderRecycler: RecyclerView

    // ---- Helpers ----
    private lateinit var paletteEngine: PaletteEngine
    private lateinit var prefs: Prefs
    private lateinit var iconStyle: IconStyle
    private lateinit var stats: LaunchStats

    // ---- State ----
    private enum class DrawerTab { ALL, CATEGORIES }

    private var allApps: List<AppInfo> = emptyList()
    private var pagerAdapter: HomePagerAdapter? = null
    private var wallpaperTheme: ThemeColors? = null
    private var nameTint: Int = Color.WHITE
    private var accentColor: Int = Color.parseColor("#FF3B30")
    private var wallpaperLight = false
    private var drawerTab = DrawerTab.ALL
    private var drawerOpen = false
    private var editMode = false
    private var folderOpen = false
    private var pagerHooksAttached = false
    private var lastBuiltHeight = 0
    private var lastPrefsSignature = ""
    private var installedCount = 0
    private var homeItemsGlobal: MutableList<HomeItem> = ArrayList()
    private var pageSizes: List<Int> = emptyList()

    // Blur (Android 12+)
    private var systemBlurOk = false
    private var blurRadius = 0

    // App kholna (sirf opening animation; closing / return Android ka default hai)
    private enum class LaunchState { IDLE, LAUNCHING }

    private lateinit var launchOverlayView: LaunchOverlayView
    private var launchState = LaunchState.IDLE
    private var pageBeforeLaunch = -1
    private var pendingPage = -1 // saved state se restore hone wala page
    private var resumed = false
    private val iconColorCache = HashMap<String, Int>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    // Safety net: agar app khula hi nahi (koi error nahi aaya, launcher bhi stop nahi hua) to launch state
    // wapas IDLE ho jaye, taaki launcher permanently lock na ho.
    private val launchTimeout = Runnable {
        if (launchState == LaunchState.LAUNCHING) cleanupLaunch()
    }

    // Touch handling
    private var downX = 0f
    private var downY = 0f
    private var drawerStartedAtTop = true
    private var ignoreTouches = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onHomeLongPress() }
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val wallpaperManager by lazy { WallpaperManager.getInstance(this) }

    // Home ke saare tap / long-press callbacks
    private val homeActions by lazy {
        HomeActions(
            onAppClick = { app, view -> launchApp(app, view) },
            onAppLongClick = { app, view -> showAppMenu(app, view) },
            onFolderClick = { folder, tile -> openFolder(folder, tile) },
            onSearchClick = { openDrawer(focusSearch = true) },
            onMicClick = { safeStart(Intent(RecognizerIntent.ACTION_WEB_SEARCH)) }
        )
    }

    // System blur on/off hone pe (battery saver etc.)
    private val blurListener = Consumer<Boolean> { enabled ->
        systemBlurOk = enabled
        updateDrawerBackground()
    }

    companion object {
        private const val ROW_HEIGHT_DP = 96      // item_app.xml / item_folder.xml ki height
        private const val HEADER_HEIGHT_DP = 150  // page_home.xml ka clock header
        private const val MAX_ROW_DP = 120        // rows is se zyada nahi failti
        private const val PAGE_TOP_PAD_DP = 28    // baaki pages pe upar ki jagah
        private const val DRAWER_BLUR_PX = 90
        private const val LAUNCH_BLUR_PX = 60
        private const val FOLDER_BLUR_PX = 20f

        // Opening animation: tez shuru, naram ruk (ease-out). 320ms.
        private const val OPEN_DURATION_MS = 320L
        private const val LAUNCH_TIMEOUT_MS = 4000L
        private const val KEY_PAGE = "home_page"
        private val OPEN_INTERPOLATOR = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    }

    // ---------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: wallpaper status bar / nav bar ke peeche bhi dikhe
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        pendingPage = savedInstanceState?.getInt(KEY_PAGE, -1) ?: -1
        prefs = Prefs(this)
        iconStyle = IconStyle(this, prefs)
        stats = LaunchStats(this)
        lastPrefsSignature = prefs.signature()

        rootLayout = findViewById(R.id.rootLayout)
        homeContainer = findViewById(R.id.homeContainer)
        homePager = findViewById(R.id.homePager)
        pageIndicator = findViewById(R.id.pageIndicator)
        dockContainer = findViewById(R.id.dockContainer)
        drawerContainer = findViewById(R.id.drawerContainer)
        drawerRecycler = findViewById(R.id.appsRecyclerView)
        searchBar = findViewById(R.id.searchBar)
        tabAll = findViewById(R.id.tabAll)
        tabCategories = findViewById(R.id.tabCategories)
        drawerMenu = findViewById(R.id.drawerMenu)
        alphabetIndex = findViewById(R.id.alphabetIndex)
        editOverlay = findViewById(R.id.editOverlay)
        folderOverlay = findViewById(R.id.folderOverlay)
        folderPanel = findViewById(R.id.folderPanel)
        folderTitle = findViewById(R.id.folderTitle)
        folderRecycler = findViewById(R.id.folderRecycler)
        launchOverlayView = findViewById(R.id.launchOverlay)

        // System bars aur keyboard ki jagah chhodo
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            homeContainer.setPadding(0, bars.top, 0, bars.bottom)
            drawerContainer.setPadding(0, bars.top, 0, max(bars.bottom, ime.bottom))
            editOverlay.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        allApps = loadInstalledApps()

        setupBlurState()
        setupDrawer()
        setupFolderPopup()
        setupHome()
        setupExtras()
        setupBackHandling()
        applyLookFromPrefs()

        // Wallpaper se colors nikalo; wallpaper badle to apne aap dobara apply hoga
        paletteEngine = PaletteEngine(this)
        paletteEngine.start { theme -> applyTheme(theme) }
    }

    override fun onResume() {
        super.onResume()
        resumed = true

        // Settings se wapas aaye? To badli hui settings apply karo
        val signature = prefs.signature()
        if (signature != lastPrefsSignature) {
            lastPrefsSignature = signature
            refreshFromPrefs()
        }

        // Naya app install / uninstall hua? (background thread pe check, UI nahi rukta)
        reloadAppsIfChanged()

        // Launcher pause hua par stop nahi (app khula hi nahi / translucent tha): bacha hua launch state saaf karo.
        // Normal case mein (app khula) ye onStop mein ho chuka hota hai. Koi return animation nahi.
        if (launchState != LaunchState.IDLE) {
            cleanupLaunch()
        } else if (!drawerOpen) {
            setWindowBlur(0)
        }

        // Launch se pehle wala page wahi rahe
        if (pageBeforeLaunch >= 0) {
            val adapter = pagerAdapter
            if (adapter != null && pageBeforeLaunch < adapter.itemCount && homePager.currentItem != pageBeforeLaunch) {
                homePager.setCurrentItem(pageBeforeLaunch, false)
            }
            pageBeforeLaunch = -1
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    override fun onStop() {
        super.onStop()
        // App ne screen le li, launcher ab dikh nahi raha: overlay / blur / khule panels turant hata do
        // (koi animation nahi). Wapas aate waqt Android ki apni transition chalegi.
        if (launchState != LaunchState.IDLE) cleanupLaunch()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, homePager.currentItem)
    }

    /** Home button dabane pe (singleTask): sab band, ya pehle page pe jao. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Launcher pehle se screen pe tha (aur Home dabaya) tabhi panels band / pehla page.
        // Kisi app se wapas aate waqt launcher stopped hota hai: tab page bilkul nahi badalna.
        if (!resumed) return
        when {
            folderOpen -> closeFolder()
            editMode -> exitEditMode()
            drawerOpen -> closeDrawer()
            else -> homePager.setCurrentItem(0, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        paletteEngine.stop()
        uiHandler.removeCallbacks(launchTimeout)
        launchOverlayView.reset()
        longPressHandler.removeCallbacks(longPressRunnable)
        bgExecutor.shutdown()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Settings apply
    // ---------------------------------------------------------------------------------------

    /** Settings badli: icon style, glass, transition aur home pages dobara. */
    private fun refreshFromPrefs() {
        iconStyle = IconStyle(this, prefs)
        applyLookFromPrefs()
        if (lastBuiltHeight > 0) buildHome(lastBuiltHeight)
        if (!prefs.parallax) {
            homePager.windowToken?.let { wallpaperManager.setWallpaperOffsets(it, 0.5f, 0.5f) }
        }
    }

    /** Wo cheezein jo sirf prefs pe depend karti hain (home rebuild ke bina). */
    private fun applyLookFromPrefs() {
        drawerAdapter.setStyle(iconStyle)
        if (pagerHooksAttached) DepthCurveTransformer.setEnabled(homePager, prefs.transition3d)
        updateDrawerBackground()
    }

    private fun glassStrength(): Float = prefs.glassStrength / 100f

    /** Widgets halke (frosted white) glass mein ya gehre mein: settings ya wallpaper ke hisaab se. */
    private fun useLightGlass(): Boolean = when (prefs.widgetGlass) {
        "light" -> true
        "dark" -> false
        else -> wallpaperLight
    }

    private fun effectiveAccent(): Int =
        if (prefs.colorfulClock) accentColor else Color.parseColor("#E8FFFFFF")

    // ---------------------------------------------------------------------------------------
    // Home screen
    // ---------------------------------------------------------------------------------------
    private fun setupHome() {
        // Pager ki height badalne pe (insets, rotation) pages dobara banao taaki rows sahi fit ho
        homePager.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val h = bottom - top
            if (h > 0 && h != lastBuiltHeight) {
                lastBuiltHeight = h
                homePager.post { buildHome(h) }
            }
        }
    }

    private fun buildHome(pagerHeight: Int) {
        val dock = resolveDockApps()
        val dockPackages = dock.map { it.packageName }.toSet()
        val homeApps = allApps.filter { it.packageName !in dockPackages }
        val homeItems = buildHomeItems(homeApps)

        // Kitni rows fit hongi: pehle page pe clock + widgets ki jagah kam.
        // Rows ko page ki poori height mein barabar failate hain (ColorOS jaisa).
        val widgetTypes = prefs.widgetTypes
        val widgetRows = (widgetTypes.size + 1) / 2
        val firstPageHeaderDp = HEADER_HEIGHT_DP + widgetRows * WIDGET_ROW_HEIGHT_DP
        val minRow = dp(ROW_HEIGHT_DP)
        val maxRow = dp(MAX_ROW_DP)
        val topPad = dp(PAGE_TOP_PAD_DP)
        val firstAvail = (pagerHeight - dp(firstPageHeaderDp)).coerceAtLeast(minRow)
        val otherAvail = (pagerHeight - topPad).coerceAtLeast(minRow)
        val firstRows = (firstAvail / minRow).coerceAtLeast(1)
        val otherRows = (otherAvail / minRow).coerceAtLeast(1)
        val firstRowPx = minOf(firstAvail / firstRows, maxRow)
        val otherRowPx = minOf(otherAvail / otherRows, maxRow)

        // Items ko pages mein baanto (search widget poori row leta hai)
        homeItemsGlobal = ArrayList(homeItems)
        val pages = ArrayList<MutableList<HomeItem>>()
        var currentPage = ArrayList<HomeItem>()
        var cells = 0
        var capacity = firstRows * HOME_COLUMNS
        for (item in homeItems) {
            val isSearch = item is HomeItem.Search
            var needed = if (isSearch) HOME_COLUMNS else 1
            if (isSearch && cells % HOME_COLUMNS != 0) needed += HOME_COLUMNS - cells % HOME_COLUMNS
            if (cells + needed > capacity && currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = ArrayList()
                cells = 0
                capacity = otherRows * HOME_COLUMNS
                needed = if (isSearch) HOME_COLUMNS else 1
            }
            currentPage.add(item)
            cells += needed
        }
        pages.add(currentPage)
        pageSizes = pages.map { it.size }

        val widgetConfig = WidgetConfig(
            widgetTypes, glassStrength(),
            useLightGlass(), prefs.touchGlow, prefs.clockStyle == "compact"
        )
        val suggested = stats.top(homeApps, 4)
        val adapter = HomePagerAdapter(
            pages, nameTint, effectiveAccent(), useLightGlass(), iconStyle, widgetConfig, suggested,
            homeActions, firstRowPx, otherRowPx, topPad,
            { editMode },
            { page, items -> onPageReordered(page, items) }
        )
        pagerAdapter = adapter
        val current = if (pendingPage >= 0) pendingPage else homePager.currentItem
        pendingPage = -1
        homePager.adapter = adapter
        homePager.setCurrentItem(current.coerceAtMost(pages.size - 1), false)

        if (!pagerHooksAttached) {
            pagerHooksAttached = true
            DepthCurveTransformer.attach(homePager)
            DepthCurveTransformer.requestHighRefreshRate(this)
            homePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    // Wallpaper pages ke saath halka sa khisakta hai (parallax)
                    val count = pagerAdapter?.itemCount ?: 1
                    if (prefs.parallax && count > 1) {
                        val x = ((position + positionOffset) / (count - 1)).coerceIn(0f, 1f)
                        homePager.windowToken?.let { wallpaperManager.setWallpaperOffsets(it, x, 0.5f) }
                    }
                }

                override fun onPageSelected(position: Int) {
                    updateIndicator(position)
                    homePager.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            })
        }
        DepthCurveTransformer.setEnabled(homePager, prefs.transition3d)

        buildIndicator(pages.size, homePager.currentItem)
        buildDock(dock)
    }

    /** Home ke items: search widget, (auto) folders, apps. Phir user ka saved order lagta hai. */
    private fun buildHomeItems(apps: List<AppInfo>): List<HomeItem> {
        val defaults = ArrayList<HomeItem>()
        if (prefs.searchWidget) defaults.add(HomeItem.Search)

        if (!prefs.autoFolders) {
            for (app in apps) defaults.add(HomeItem.App(app))
            return applySavedOrder(defaults)
        }

        val remaining = apps.toMutableList()
        val folders = ArrayList<HomeItem>()

        val google = remaining.filter { it.packageName.startsWith("com.google.") }
        if (google.size >= 3) {
            folders.add(HomeItem.Folder("Google", google))
            remaining.removeAll(google.toSet())
        }

        val byCategory = remaining.groupBy { categoryName(it.category) }
        for (name in listOf("Social", "Media", "Games", "Productivity")) {
            val group = byCategory[name] ?: continue
            if (group.size >= 3) {
                folders.add(HomeItem.Folder(name, group))
                remaining.removeAll(group.toSet())
            }
        }

        defaults.addAll(folders)
        for (app in remaining) defaults.add(HomeItem.App(app))
        return applySavedOrder(defaults)
    }

    /** Drag-drop se bana order lagao; naye apps (jo order mein nahi) end mein jaate hain. */
    private fun applySavedOrder(defaults: List<HomeItem>): List<HomeItem> {
        val saved = prefs.homeOrder
        if (saved.isEmpty()) return defaults
        val rank = HashMap<String, Int>()
        for (i in saved.indices) rank[saved[i]] = i
        val known = defaults.filter { rank.containsKey(it.key) }.sortedBy { rank[it.key] ?: 0 }
        val unknown = defaults.filter { !rank.containsKey(it.key) }
        return known + unknown
    }

    /** Ek page ke andar drag-drop khatam hua: global order update karke save karo. */
    private fun onPageReordered(page: Int, items: List<HomeItem>) {
        if (page < 0 || page >= pageSizes.size) return
        var start = 0
        for (i in 0 until page) start += pageSizes[i]
        if (start + items.size > homeItemsGlobal.size) return
        for (i in items.indices) homeItemsGlobal[start + i] = items[i]
        prefs.homeOrder = homeItemsGlobal.map { it.key }
        lastPrefsSignature = prefs.signature() // rebuild na ho
    }

    /** Phone, Messages, Browser, Camera ke default apps dhundta hai. */
    private fun resolveDockApps(): List<AppInfo> {
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val result = ArrayList<AppInfo>()
        for (probe in probes) {
            val pkg = packageManager
                .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            if (pkg == null || pkg == "android") continue
            val app = allApps.firstOrNull { it.packageName == pkg }
            if (app != null && result.none { it.packageName == pkg }) result.add(app)
        }
        return result
    }

    private fun buildDock(dock: List<AppInfo>) {
        dockContainer.removeAllViews()
        for (app in dock) {
            val item = layoutInflater.inflate(R.layout.item_dock, dockContainer, false)
            val icon = item.findViewById<ImageView>(R.id.dockIcon)
            icon.setImageDrawable(iconStyle.drawableFor(app))
            iconStyle.styleIcon(icon)
            item.setOnClickListener { launchApp(app, icon) }
            item.setOnLongClickListener {
                showAppMenu(app, icon)
                true
            }
            item.applyPressEffect()
            dockContainer.addView(item)
        }
    }

    private fun buildIndicator(count: Int, selected: Int) {
        pageIndicator.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this)
            dot.setBackgroundResource(R.drawable.indicator_dot)
            val lp = LinearLayout.LayoutParams(dp(6), dp(6))
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            dot.layoutParams = lp
            pageIndicator.addView(dot)
        }
        updateIndicator(selected)
    }

    private fun updateIndicator(selected: Int) {
        for (i in 0 until pageIndicator.childCount) {
            val active = i == selected
            pageIndicator.getChildAt(i).animate()
                .alpha(if (active) 1f else 0.45f)
                .scaleX(if (active) 1.25f else 1f)
                .scaleY(if (active) 1.25f else 1f)
                .setDuration(150)
                .start()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Folder popup (liquid glass panel + peeche home blur)
    // ---------------------------------------------------------------------------------------
    private fun setupFolderPopup() {
        folderRecycler.layoutManager = GridLayoutManager(this, HOME_COLUMNS)
        folderOverlay.setOnClickListener { closeFolder() }
    }

    private fun openFolder(folder: HomeItem.Folder, tile: View) {
        if (folderOpen || drawerOpen || editMode || launchState != LaunchState.IDLE) return
        folderOpen = true

        folderTitle.text = folder.name
        folderRecycler.adapter = HomeGridAdapter(
            ArrayList<HomeItem>(folder.apps.map { HomeItem.App(it) }),
            nameTint, false, iconStyle, 0, homeActions
        )
        val rows = ((folder.apps.size + HOME_COLUMNS - 1) / HOME_COLUMNS).coerceIn(1, 4)
        val lp = folderRecycler.layoutParams
        lp.height = rows * dp(ROW_HEIGHT_DP)
        folderRecycler.layoutParams = lp

        folderPanel.background = LiquidGlassDrawable(dp(28).toFloat(), glassStrength().coerceAtLeast(0.3f))

        folderOverlay.animate().cancel()
        folderOverlay.alpha = 0f
        folderOverlay.visibility = View.VISIBLE
        folderOverlay.animate().alpha(1f).setDuration(240).start()

        // Panel folder icon ke center se phoolta hai (ek hi scale animation, pivot icon pe)
        val tileLoc = IntArray(2)
        tile.getLocationOnScreen(tileLoc)
        folderPanel.animate().cancel()
        folderPanel.alpha = 0f
        folderPanel.scaleX = 1f
        folderPanel.scaleY = 1f
        folderPanel.doOnLayout {
            val panelLoc = IntArray(2)
            folderPanel.getLocationOnScreen(panelLoc)
            folderPanel.pivotX = (tileLoc[0] + tile.width / 2f) - panelLoc[0]
            folderPanel.pivotY = (tileLoc[1] + tile.height / 2f) - panelLoc[1]
            folderPanel.scaleX = 0.2f
            folderPanel.scaleY = 0.2f
            folderPanel.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(360)
                .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
                .withLayer()
                // Blur animation ke baad lagta hai (animation ke dauran heavy blur lag karta hai)
                .withEndAction { if (folderOpen) setHomeBlur(true) }
                .start()
        }
    }

    private fun closeFolder(animated: Boolean = true) {
        if (!folderOpen) return
        folderOpen = false
        setHomeBlur(false)

        folderPanel.animate().cancel()
        folderOverlay.animate().cancel()
        if (animated) {
            folderPanel.animate()
                .scaleX(0.2f)
                .scaleY(0.2f)
                .alpha(0f)
                .setDuration(260)
                .setInterpolator(PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
                .withLayer()
                .start()
            folderOverlay.animate()
                .alpha(0f)
                .setDuration(260)
                .withEndAction { if (!folderOpen) folderOverlay.visibility = View.GONE }
                .start()
        } else {
            folderOverlay.visibility = View.GONE
        }
    }

    /** Home ko blur karta hai (folder khula ho tab), sirf ek baar set hota hai, animate nahi. Android 12+ */
    private fun setHomeBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            homeContainer.setRenderEffect(
                if (on && prefs.blurEnabled) {
                    RenderEffect.createBlurEffect(FOLDER_BLUR_PX, FOLDER_BLUR_PX, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // App drawer (glass + blur + tabs + A-Z + categories)
    // ---------------------------------------------------------------------------------------
    private fun setupDrawer() {
        drawerLayoutManager = GridLayoutManager(this, HOME_COLUMNS)
        // Section header poori width (4 columns) leta hai, app 1 column
        drawerLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (drawerAdapter.isHeader(position)) HOME_COLUMNS else 1
        }
        drawerRecycler.layoutManager = drawerLayoutManager

        drawerAdapter = DrawerAdapter(
            nameTint, iconStyle,
            { app, iconView -> showAppMenu(app, iconView) }
        ) { app, iconView -> launchApp(app, iconView) }
        drawerRecycler.adapter = drawerAdapter

        // Right edge A-Z index: letter pe jao
        alphabetIndex.onLetterSelected = { letter ->
            val pos = drawerAdapter.positionOfLetter(letter)
            if (pos >= 0) drawerLayoutManager.scrollToPositionWithOffset(pos, 0)
        }

        tabAll.setOnClickListener { selectTab(DrawerTab.ALL) }
        tabCategories.setOnClickListener { selectTab(DrawerTab.CATEGORIES) }
        drawerMenu.setOnClickListener { showDrawerMenu() }

        // Search: type karte hi list filter hoti hai
        searchBar.doAfterTextChanged { refreshDrawerList() }

        drawerContainer.visibility = View.GONE
        updateDrawerBackground()
        selectTab(DrawerTab.ALL)
    }

    private fun selectTab(tab: DrawerTab) {
        drawerTab = tab
        styleTab(tabAll, tab == DrawerTab.ALL)
        styleTab(tabCategories, tab == DrawerTab.CATEGORIES)
        refreshDrawerList()
    }

    private fun styleTab(view: TextView, selected: Boolean) {
        view.setBackgroundResource(if (selected) R.drawable.bg_tab_selected else 0)
        view.alpha = if (selected) 1f else 0.7f
        view.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Search text + selected tab ke hisaab se drawer ki list banata hai. */
    private fun refreshDrawerList() {
        val query = searchBar.text.toString().trim()
        val items: List<DrawerItem>
        if (query.isNotEmpty()) {
            items = allApps
                .filter { it.label.contains(query, ignoreCase = true) }
                .map { DrawerItem.App(it) }
            alphabetIndex.visibility = View.GONE
        } else if (drawerTab == DrawerTab.ALL) {
            items = allApps.map { DrawerItem.App(it) }
            alphabetIndex.visibility = View.VISIBLE
        } else {
            items = buildCategoryItems()
            alphabetIndex.visibility = View.GONE
        }
        drawerAdapter.submit(items)
        drawerRecycler.scrollToPosition(0)
    }

    /** Apps ko category ke hisaab se sections mein baanto. */
    private fun buildCategoryItems(): List<DrawerItem> {
        val groups = allApps.groupBy { categoryName(it.category) }
        val order = listOf("Social", "Media", "Games", "Productivity", "Maps & Travel", "News", "Others")
        val result = ArrayList<DrawerItem>()
        for (name in order) {
            val apps = groups[name] ?: continue
            result.add(DrawerItem.Header(name))
            for (app in apps) result.add(DrawerItem.App(app))
        }
        return result
    }

    private fun showDrawerMenu() {
        val popup = PopupMenu(this, drawerMenu)
        popup.menu.add(0, 1, 0, "Launcher settings")
        popup.menu.add(0, 2, 1, "Wallpaper")
        popup.menu.add(0, 3, 2, "Phone settings")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(SettingsActivity.pageIntent(this, SettingsActivity.PAGE_HUB))
                2 -> safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                3 -> safeStart(Intent(Settings.ACTION_SETTINGS))
            }
            true
        }
        popup.show()
    }

    private fun openDrawer(focusSearch: Boolean = false) {
        if (drawerOpen || editMode || folderOpen || launchState != LaunchState.IDLE) return
        drawerOpen = true

        drawerContainer.animate().cancel()
        drawerContainer.translationY = screenHeight()
        drawerContainer.visibility = View.VISIBLE
        drawerContainer.animate()
            .translationY(0f)
            .setDuration(340)
            .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
            .withLayer()
            // Window blur animation ke baad (animation ke dauran blur lag karta hai)
            .withEndAction { if (drawerOpen) setWindowBlur(DRAWER_BLUR_PX) }
            .start()

        homeContainer.animate().cancel()
        homeContainer.animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(300)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .withLayer()
            .start()

        if (focusSearch) {
            searchBar.postDelayed({
                searchBar.requestFocus()
                getSystemService(InputMethodManager::class.java)
                    .showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT)
            }, 340)
        }
    }

    private fun closeDrawer(animated: Boolean = true) {
        if (!drawerOpen) return
        drawerOpen = false
        setWindowBlur(0)

        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(searchBar.windowToken, 0)
        searchBar.setText("")
        searchBar.clearFocus()

        drawerContainer.animate().cancel()
        homeContainer.animate().cancel()
        if (animated) {
            drawerContainer.animate()
                .translationY(screenHeight())
                .setDuration(280)
                .setInterpolator(PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
                .withLayer()
                .withEndAction { if (!drawerOpen) drawerContainer.visibility = View.GONE }
                .start()
            homeContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .withLayer()
                .start()
        } else {
            drawerContainer.visibility = View.GONE
            homeContainer.alpha = 1f
            homeContainer.scaleX = 1f
            homeContainer.scaleY = 1f
        }
    }

    /** Drawer ka background: wallpaper ke color wala dark gradient (blur ke upar tint). */
    private fun updateDrawerBackground() {
        if (!::drawerContainer.isInitialized || !::prefs.isInitialized) return
        val t = wallpaperTheme
        val colors = if (t != null) {
            intArrayOf(t.drawerTop, t.drawerBottom)
        } else {
            intArrayOf(Color.parseColor("#1A1A24"), Color.parseColor("#101018"))
        }
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
        // Blur chal raha ho to halka tint (glass strength se), warna gehra taaki icons padhe ja sakein
        val blurActive = systemBlurOk && prefs.blurEnabled
        bg.alpha = if (blurActive) 80 + (prefs.glassStrength * 0.9f).toInt() else 215
        drawerContainer.background = bg
    }

    // ---------------------------------------------------------------------------------------
    // Wallpaper theme sync
    // ---------------------------------------------------------------------------------------
    private fun applyTheme(t: ThemeColors) {
        wallpaperTheme = t
        nameTint = t.appNameTint
        accentColor = t.accent
        wallpaperLight = t.isLight
        pagerAdapter?.setColors(t.appNameTint, effectiveAccent(), useLightGlass())
        drawerAdapter.setNameTint(t.appNameTint)
        alphabetIndex.tint = t.appNameTint

        // Search bar: border + text + hint
        (searchBar.background.mutate() as GradientDrawable).setStroke(dp(2), t.searchBorder)
        searchBar.setTextColor(t.appNameTint)
        searchBar.setHintTextColor(ColorUtils.setAlphaComponent(t.appNameTint, 0x99))

        updateDrawerBackground()
    }

    // ---------------------------------------------------------------------------------------
    // Window blur (Android 12+): drawer kholne pe aur app launch/exit pe wallpaper blur
    // ---------------------------------------------------------------------------------------
    private fun setupBlurState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            systemBlurOk = windowManager.isCrossWindowBlurEnabled
            windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
        }
    }

    /**
     * Window ke peeche wallpaper blur. Radius ek baar set hota hai (animate nahi), kyunki
     * har frame radius badalna GPU pe bhari padta hai aur lag deta hai.
     * animation = true: "Animation blur" toggle se control hota hai, nahi to "Visual blur" se.
     */
    private fun setWindowBlur(radius: Int, animation: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !systemBlurOk) return
        val allowed = if (animation) prefs.animationBlur else prefs.blurEnabled
        val target = if (allowed) radius else 0
        if (target == blurRadius) return
        blurRadius = target
        window.setBackgroundBlurRadius(target)
    }

    // ---------------------------------------------------------------------------------------
    // Touch: long-press (edit mode), swipe-up / swipe-down (drawer)
    // ---------------------------------------------------------------------------------------
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoreTouches = false
            downX = ev.x
            downY = ev.y
            drawerStartedAtTop = !drawerRecycler.canScrollVertically(-1) &&
                ev.x < rootLayout.width - dp(40)

            longPressHandler.removeCallbacks(longPressRunnable)
            // Sirf home pager ke upar (dock/search ke upar nahi) long-press ginenge
            if (!drawerOpen && !editMode && !folderOpen && launchState == LaunchState.IDLE && ev.y <= homePager.bottom) {
                // App icon ka apna long-press (menu) pehle chale, khaali jagah pe edit mode baad mein
                longPressHandler.postDelayed(
                    longPressRunnable,
                    (ViewConfiguration.getLongPressTimeout() + 150).toLong()
                )
            }
        }

        // Long-press ke baad bacha hua touch (finger uthana) app click na bane
        if (ignoreTouches) {
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_CANCEL -> longPressHandler.removeCallbacks(longPressRunnable)
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                val dx = ev.x - downX
                val dy = ev.y - downY
                val mostlyVertical = abs(dy) > abs(dx) * 1.5f
                if (mostlyVertical) {
                    if (!drawerOpen && !editMode && !folderOpen && dy < -dp(140)) {
                        openDrawer()
                    } else if (drawerOpen && dy > dp(140) && drawerStartedAtTop) {
                        closeDrawer()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // ---------------------------------------------------------------------------------------
    // Edit mode (ColorOS jaisa: pages chhote, neeche 4 buttons, upar Done)
    // ---------------------------------------------------------------------------------------
    private fun onHomeLongPress() {
        if (drawerOpen || editMode || folderOpen) return
        // Neeche ke views ko CANCEL bhejo taaki pressed state / click na bane
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        window.superDispatchTouchEvent(cancel)
        cancel.recycle()
        ignoreTouches = true
        enterEditMode()
    }

    private fun enterEditMode() {
        if (editMode || drawerOpen || folderOpen) return
        editMode = true
        homePager.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        homePager.animate().cancel()
        homePager.animate()
            .scaleX(0.84f)
            .scaleY(0.84f)
            .translationY(-dp(24).toFloat())
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        dockContainer.animate().cancel()
        dockContainer.animate().alpha(0f).setDuration(200)
            .withEndAction { if (editMode) dockContainer.visibility = View.INVISIBLE }
            .start()

        Toast.makeText(this, "Item ko dabake rakho aur drag karke hilao", Toast.LENGTH_SHORT).show()
        pageIndicator.background = LiquidGlassDrawable(dp(10).toFloat(), 0.5f)
        pageIndicator.setPadding(dp(10), 0, dp(10), 0)
        pageIndicator.animate().translationY(-dp(84).toFloat()).setDuration(260).start()

        editOverlay.animate().cancel()
        editOverlay.alpha = 0f
        editOverlay.visibility = View.VISIBLE
        editOverlay.animate().alpha(1f).setDuration(260).start()
    }

    private fun exitEditMode() {
        if (!editMode) return
        editMode = false

        homePager.animate().cancel()
        homePager.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        dockContainer.visibility = View.VISIBLE
        dockContainer.animate().cancel()
        dockContainer.animate().alpha(1f).setDuration(220).start()

        pageIndicator.background = null
        pageIndicator.setPadding(0, 0, 0, 0)
        pageIndicator.animate().translationY(0f).setDuration(260).start()

        editOverlay.animate().cancel()
        editOverlay.animate().alpha(0f).setDuration(200)
            .withEndAction { if (!editMode) editOverlay.visibility = View.GONE }
            .start()
    }

    /** Edit mode ke buttons + search pill ka mic. */
    private fun setupExtras() {
        findViewById<View>(R.id.editDone).setOnClickListener { exitEditMode() }
        findViewById<View>(R.id.editWidgets).setOnClickListener {
            startActivity(SettingsActivity.pageIntent(this, SettingsActivity.PAGE_WIDGETS))
        }
        findViewById<View>(R.id.editWallpaper).setOnClickListener {
            startActivity(SettingsActivity.pageIntent(this, SettingsActivity.PAGE_WALLPAPER))
        }
        findViewById<View>(R.id.editLayout).setOnClickListener {
            startActivity(SettingsActivity.pageIntent(this, SettingsActivity.PAGE_LAYOUT))
        }
        findViewById<View>(R.id.editSettings).setOnClickListener {
            startActivity(SettingsActivity.pageIntent(this, SettingsActivity.PAGE_GENERAL))
        }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Ye option is phone pe available nahi hai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackHandling() {
        // Launcher mein Back dabane pe app band nahi honi chahiye
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    folderOpen -> closeFolder()
                    editMode -> exitEditMode()
                    drawerOpen -> closeDrawer()
                    homePager.currentItem != 0 -> homePager.setCurrentItem(0, true)
                }
            }
        })
    }

    // ---------------------------------------------------------------------------------------
    // Apps
    // ---------------------------------------------------------------------------------------
    private fun loadInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        installedCount = resolveInfos.size
        val hidden = prefs.hiddenApps

        return resolveInfos
            .map { info ->
                AppInfo(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = info.activityInfo.packageName,
                    icon = info.loadIcon(packageManager),
                    category = info.activityInfo.applicationInfo.category
                )
            }
            .filter { it.packageName != packageName && it.packageName !in hidden } // khud ko aur hide kiye apps ko hatao
            .sortedBy { it.label.lowercase() }
    }

    /** Naya app install / purana uninstall hua ho to app list dobara banao (background mein check). */
    private fun reloadAppsIfChanged() {
        bgExecutor.execute {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val count = packageManager.queryIntentActivities(intent, 0).size
            if (count != installedCount) {
                val apps = loadInstalledApps()
                uiHandler.post {
                    allApps = apps
                    refreshDrawerList()
                    if (lastBuiltHeight > 0) buildHome(lastBuiltHeight)
                }
            }
        }
    }

    private fun reloadApps() {
        allApps = loadInstalledApps()
        refreshDrawerList()
        if (lastBuiltHeight > 0) buildHome(lastBuiltHeight)
    }

    /** App icon pe long-press: App info / Uninstall / Hide (ColorOS jaisa menu). */
    private fun showAppMenu(app: AppInfo, anchor: View) {
        if (editMode) return
        longPressHandler.removeCallbacks(longPressRunnable)
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val isSystem = try {
            (packageManager.getApplicationInfo(app.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            true
        }

        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "App info")
        if (!isSystem) popup.menu.add(0, 2, 1, "Uninstall")
        popup.menu.add(0, 3, 2, "Hide app")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> safeStart(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                )
                2 -> safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                3 -> hideApp(app)
            }
            true
        }
        popup.show()
    }

    private fun hideApp(app: AppInfo) {
        prefs.hiddenApps = prefs.hiddenApps + app.packageName
        lastPrefsSignature = prefs.signature()
        Toast.makeText(this, "${app.label} hide ho gaya (Settings se wapas la sakte ho)", Toast.LENGTH_SHORT).show()
        reloadApps()
    }

    /**
     * App kholna (ek tap = ek launch):
     *  1. Launch state turant LAUNCHING ho jata hai, isliye doosra tap / callback ignore hota hai.
     *  2. Tapped icon ki asli jagah se rounded surface poori screen tak failta hai (custom opening animation).
     *  3. Usi waqt, bina kisi delay ke, startActivity() ek hi baar. App ke upar halka fade-in (ActivityOptions).
     * Wapas aane pe koi launcher animation nahi: Android ki default closing / return transition chalti hai.
     */
    private fun launchApp(app: AppInfo, iconView: View) {
        if (launchState != LaunchState.IDLE || editMode) return
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "App open nahi ho paya", Toast.LENGTH_SHORT).show()
            return
        }
        launchState = LaunchState.LAUNCHING
        pageBeforeLaunch = homePager.currentItem
        stats.record(app.packageName)

        // Icon ki jagah (nahi mili to screen ke beech se fallback)
        val screenW = rootLayout.width.toFloat()
        val screenH = rootLayout.height.toFloat()
        val origin = RectF()
        if (!computeOriginRect(iconView, origin)) {
            val half = dp(32).toFloat()
            origin.set(screenW / 2f - half, screenH / 2f - half, screenW / 2f + half, screenH / 2f + half)
        }

        val iconDrawable = iconStyle.drawableFor(app)
        launchOverlayView.start(
            origin, iconStyle.cornerRadiusFor(origin.width()), iconDrawable, iconColorFor(app, iconDrawable),
            screenW, screenH, OPEN_DURATION_MS, OPEN_INTERPOLATOR
        ) {
            // Surface ne poori screen dhak li: peeche khula drawer / folder ab chupchap band kar do
            if (launchState == LaunchState.LAUNCHING) {
                if (drawerOpen) closeDrawer(animated = false)
                if (folderOpen) closeFolder(animated = false)
            }
        }
        setWindowBlur(LAUNCH_BLUR_PX, animation = true) // sirf agar "Animation blur" settings mein on ho

        try {
            val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.launch_fade_in, R.anim.launch_hold)
            startActivity(launchIntent, options.toBundle())
        } catch (e: Exception) {
            Toast.makeText(this, "App open nahi ho paya", Toast.LENGTH_SHORT).show()
            cleanupLaunch()
            return
        }
        uiHandler.postDelayed(launchTimeout, LAUNCH_TIMEOUT_MS)
    }

    /**
     * Launch ka saara temporary state turant saaf (koi animation nahi): overlay hide, blur 0,
     * khule drawer / folder band, state IDLE. onStop / onResume / error / timeout se call hota hai.
     */
    private fun cleanupLaunch() {
        uiHandler.removeCallbacks(launchTimeout)
        launchOverlayView.reset()
        setWindowBlur(0)
        if (drawerOpen) closeDrawer(animated = false)
        if (folderOpen) closeFolder(animated = false)
        launchState = LaunchState.IDLE
    }

    /**
     * Tapped icon ki asli screen position (rootLayout ke coordinates). false = icon ab screen pe nahi hai
     * (detached / hidden / bahar), tab fallback use hota hai.
     */
    private fun computeOriginRect(view: View, out: RectF): Boolean {
        if (!view.isAttachedToWindow || !view.isShown) return false

        // Press effect (0.9 scale) hata do taaki rect icon ke asli size ka mile
        (view.parent as? View)?.let { cell ->
            cell.animate().cancel()
            cell.scaleX = 1f
            cell.scaleY = 1f
        }

        val loc = IntArray(2)
        val rootLoc = IntArray(2)
        view.getLocationOnScreen(loc)
        rootLayout.getLocationOnScreen(rootLoc)
        val left = (loc[0] - rootLoc[0]).toFloat()
        val top = (loc[1] - rootLoc[1]).toFloat()

        if (view is SuggestedAppsWidgetView) {
            // Widget ke andar jis icon pe tap hua wahi origin
            val cell = RectF()
            if (!view.tappedIconBounds(cell)) return false
            out.set(left + cell.left, top + cell.top, left + cell.right, top + cell.bottom)
        } else {
            if (view.width <= 0 || view.height <= 0) return false
            out.set(left, top, left + view.width, top + view.height)
        }

        // Poori tarah screen ke bahar (page ke beech mein scroll) to fallback
        return out.right > 0 && out.bottom > 0 &&
            out.left < rootLayout.width && out.top < rootLayout.height
    }

    /** Icon ka mukhya rang (overlay ka surface isi rang ka hota hai). Ek baar nikalke yaad rakhte hain. */
    private fun iconColorFor(app: AppInfo, drawable: Drawable): Int =
        iconColorCache.getOrPut(app.packageName) { dominantColor(drawable) }

    private fun dominantColor(drawable: Drawable): Int {
        val fallback = Color.parseColor("#2B2B33")
        return try {
            val size = 24
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
            copy.setBounds(0, 0, size, size)
            copy.draw(Canvas(bmp))
            val color = Palette.from(bmp).maximumColorCount(8).generate().getDominantColor(fallback)
            bmp.recycle()
            color
        } catch (e: Exception) {
            fallback
        }
    }

    // ---------------------------------------------------------------------------------------
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun screenHeight(): Float = resources.displayMetrics.heightPixels.toFloat()
}
