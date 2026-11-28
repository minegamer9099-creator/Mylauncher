package com.example.mylauncher

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
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
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.view.ViewGroup
import android.appwidget.AppWidgetProviderInfo
import androidx.appcompat.app.AlertDialog
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
import kotlin.math.min

/**
 * ColorOS-style launcher:
 *  - Home pages (3D transition), glass clock, widgets, folders, dock, search pill
 *  - Long-press -> edit mode; swipe-up / search -> glass app drawer (tabs, A-Z, categories)
 *  - Settings screen (SettingsActivity) se sab kuch manage hota hai
 */
class MainActivity : AppCompatActivity() {

    // ---- Views ----
    internal lateinit var rootLayout: FrameLayout
    private lateinit var homeContainer: View
    internal lateinit var homePager: ViewPager2
    private lateinit var pageIndicator: LinearLayout
    internal lateinit var dockContainer: LinearLayout
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
    internal lateinit var folderPanel: View
    internal lateinit var folderTitle: TextView
    internal lateinit var folderRecycler: RecyclerView

    // ---- Helpers ----
    private lateinit var paletteEngine: PaletteEngine
    internal lateinit var prefs: Prefs
    internal lateinit var iconStyle: IconStyle
    internal lateinit var stats: LaunchStats

    // ---- State ----
    private enum class DrawerTab { ALL, CATEGORIES }

    internal var allApps: List<AppInfo> = emptyList()
    internal var pagerAdapter: HomePagerAdapter? = null
    private var wallpaperTheme: ThemeColors? = null
    internal var nameTint: Int = Color.WHITE
    internal var accentColor: Int = Color.parseColor("#FF3B30")
    private var wallpaperLight = false
    private var drawerTab = DrawerTab.ALL
    internal var drawerOpen = false
    internal var editMode = false
    internal var folderOpen = false
    private var pagerHooksAttached = false
    private var lastBuiltHeight = 0
    internal var lastPrefsSignature = ""
    @Volatile private var installedCount = 0
    private var appsLoaded = false     // pehli baar apps ki list aane tak home build nahi hota (warna layout saaf ho jata)
    private var loadGeneration = 0     // purani (late) load ka nateeja ignore karne ke liye
    private var lastWallpaperX = -1f   // parallax: aakhri bheja gaya wallpaper offset

    // ---- Home layout (cells): state persist hota hai, ops usse badalta hai ----
    internal lateinit var state: HomeState
    internal lateinit var ops: HomeOps
    internal lateinit var grid: GridSpec
    internal lateinit var homeStore: HomeStore
    internal lateinit var widgetHost: WidgetHostManager
    internal lateinit var viewFactory: HomeViewFactory
    internal lateinit var drag: DragController
    internal lateinit var dragLayer: FrameLayout
    // ---- Unlock animation ----
    private var lastUnlockAnimAt = 0L
    private var userPresentAt = 0L
    private var lockedAtPause = false
    private val unlockViews = ArrayList<View>()
    private val unlockPending = ArrayList<Pair<View, Runnable>>() // stagger ke delayed start

    // Edit screen: Done + neeche ke 4 buttons (spring pop-in) aur hint
    private val editChrome: List<View> by lazy {
        listOf(R.id.editDone, R.id.editWidgets, R.id.editWallpaper, R.id.editLayout, R.id.editSettings)
            .map { findViewById<View>(it) }
    }
    private val editHint: TextView by lazy { findViewById(R.id.editHint) }
    private val editHintHide = Runnable { editHint.springAlpha(0f, Springs.STIFF_SOFT) }
    private val editPending = ArrayList<Pair<View, Runnable>>() // pop-in ke delayed start
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                userPresentAt = SystemClock.elapsedRealtime()
                if (resumed) playUnlockAnimation()
            }
        }
    }
    internal var rowPxFirst = 0
    internal var rowPxOther = 0
    internal var pageTopPad = 0
    internal var appByPkg: Map<String, AppInfo> = emptyMap()
    internal var sheet: View? = null
    private var openFolderId: String? = null
    private var selectedId: String? = null
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dockHighlight: Drawable? = null

    // App kholna (sirf opening animation; closing / return Android ka default hai)
    private enum class LaunchState { IDLE, LAUNCHING }

    private lateinit var launchOverlayView: LaunchOverlayView
    private var launchState = LaunchState.IDLE
    private var pageBeforeLaunch = -1
    private var pendingPage = -1 // saved state se restore hone wala page
    private var resumed = false
    private val iconColorCache = HashMap<String, Int>()
    internal val uiHandler = Handler(Looper.getMainLooper())

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
    internal val homeActions by lazy {
        HomeActions(
            onAppClick = { app, view -> launchApp(app, view) },
            onAppLongClick = { app, view -> if (folderOpen) startFolderDrag(app, view) else showAppMenu(app, view) },
            onFolderClick = { _, _ -> },
            onSearchClick = { openDrawer(focusSearch = true) },
            onMicClick = { safeStart(Intent(RecognizerIntent.ACTION_WEB_SEARCH)) }
        )
    }

    companion object {
        private const val ROW_HEIGHT_DP = 96      // item_app.xml / item_folder.xml ki height
        private const val HEADER_HEIGHT_DP = 150  // page_home.xml ka clock header
        private const val MAX_ROW_DP = 120        // rows is se zyada nahi failti
        private const val PAGE_TOP_PAD_DP = 28    // baaki pages pe upar ki jagah
        private const val EDIT_SCALE = 0.8f       // edit mode mein pager itna chhota

        // Opening animation: spring jaisa (halka bounce). Fixed duration, spring-shape interpolator.
        private const val OPEN_DURATION_MS = 480L
        private const val LAUNCH_TIMEOUT_MS = 4000L
        private const val KEY_PAGE = "home_page"
        private val OPEN_INTERPOLATOR = SpringInterpolator(damping = 0.72f, omega = 18f)
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
        dragLayer = findViewById(R.id.dragLayer)
        // Unlock detect: ek hi dynamic receiver (onDestroy mein unregister)
        ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        homeStore = HomeStore(this)
        widgetHost = WidgetHostManager(this)
        viewFactory = HomeViewFactory(this)
        drag = DragController(this)
        if (pendingPage < 0) pendingPage = homeStore.currentPage

        // System bars aur keyboard ki jagah chhodo
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            homeContainer.setPadding(0, bars.top, 0, bars.bottom)
            drawerContainer.setPadding(0, bars.top, 0, max(bars.bottom, ime.bottom))
            editOverlay.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        // Apps ki list: cache ho to turant; nahi to background mein (pehle yahan main thread pe sab icons load hote the
        // = launcher dobara bante hi screen kuch second freeze). Tab tak khaali home / wallpaper dikhta hai.
        val cached = AppCache.get()
        if (cached != null) {
            allApps = withoutHidden(cached.apps)
            installedCount = AppCache.installedCount
            appsLoaded = true
        }

        setupDrawer()
        setupFolderPopup()
        setupHome()
        setupExtras()
        setupBackHandling()
        applyLookFromPrefs()

        // Wallpaper se colors nikalo; wallpaper badle to apne aap dobara apply hoga
        paletteEngine = PaletteEngine(this)
        paletteEngine.start { theme -> applyTheme(theme) }

        if (!appsLoaded) reloadAppsAsync()
    }

    override fun onStart() {
        super.onStart()
        widgetHost.start() // widgets ke updates sirf tab jab launcher dikh raha ho
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Focus milne par system refresh mode badal sakta hai (app switch ke baad): 120Hz dobara maango
        if (hasFocus) requestHighRefreshRate()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        requestHighRefreshRate()
        checkUnlockOnResume()

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
        if (launchState != LaunchState.IDLE) cleanupLaunch()

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
        // Pause ke waqt keyguard state: unlock ke fallback detection ke liye
        lockedAtPause = (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true)
        super.onPause()
        resumed = false
    }

    override fun onStop() {
        super.onStop()
        // App ne screen le li, launcher ab dikh nahi raha: overlay / blur / khule panels turant hata do
        // (koi animation nahi). Wapas aate waqt Android ki apni transition chalegi.
        if (launchState != LaunchState.IDLE) cleanupLaunch()
        // Drag adhura ho to safe cancel; widget updates band; current page save
        cancelUnlockAnimation()
        drag.cancel()
        widgetHost.stop()
        if (::state.isInitialized && pagerAdapter != null) homeStore.currentPage = homePager.currentItem
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
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: IllegalArgumentException) {
            // register nahi hua tha
        }
        drag.cancel()
        dismissSheet(false)
        viewFactory.clear()
        widgetHost.clearViews()
        paletteEngine.stop()
        uiHandler.removeCallbacks(launchTimeout)
        launchOverlayView.reset()
        longPressHandler.removeCallbacks(longPressRunnable)
    }

    // ---------------------------------------------------------------------------------------
    // Settings apply
    // ---------------------------------------------------------------------------------------

    /** Settings badli: icon style, glass, transition aur home pages dobara. */
    private fun refreshFromPrefs() {
        iconStyle = IconStyle(this, prefs)
        viewFactory.invalidateStyle()
        applyLookFromPrefs()
        // Hidden apps badle ho sakte hain (Settings se): cache wali poori list ko dobara filter karo (sasta, icons dobara load nahi)
        AppCache.get()?.let { allApps = withoutHidden(it.apps) }
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

    internal fun glassStrength(): Float = prefs.glassStrength / 100f

    /** Widgets halke (frosted white) glass mein ya gehre mein: settings ya wallpaper ke hisaab se. */
    internal fun useLightGlass(): Boolean = when (prefs.widgetGlass) {
        "light" -> true
        "dark" -> false
        else -> wallpaperLight
    }

    internal fun effectiveAccent(): Int =
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
        // Apps ki list abhi load ho rahi hai: abhi build kiya to reconcile() layout ke saare apps hata deta.
        // Load poora hone par reloadAppsAsync() khud buildHome dobara chalata hai.
        if (!appsLoaded) return
        val map = HashMap<String, AppInfo>()
        for (app in allApps) if (!map.containsKey(app.packageName)) map[app.packageName] = app
        appByPkg = map

        // Rows pehle jaise: pehle page pe clock header ki jagah kam, rows page ki height mein barabar (ColorOS jaisa).
        val minRow = dp(ROW_HEIGHT_DP)
        val maxRow = dp(MAX_ROW_DP)
        pageTopPad = dp(PAGE_TOP_PAD_DP)
        val firstAvail = (pagerHeight - dp(HEADER_HEIGHT_DP)).coerceAtLeast(minRow)
        val otherAvail = (pagerHeight - pageTopPad).coerceAtLeast(minRow)
        val firstRows = (firstAvail / minRow).coerceAtLeast(1)
        val otherRows = (otherAvail / minRow).coerceAtLeast(1)
        rowPxFirst = minOf(firstAvail / firstRows, maxRow)
        rowPxOther = minOf(otherAvail / otherRows, maxRow)
        grid = GridSpec(HOME_COLUMNS, firstRows, otherRows)

        loadOrCreateState()
        ops = HomeOps(state, grid)
        val installed = LinkedHashSet<String>()
        for (app in allApps) installed.add(app.packageName)
        ops.reconcile(installed, prefs.widgetTypes, prefs.searchWidget, HomeOps.MAX_DOCK)
        persistHome()
        viewFactory.retain(state.entries.map { it.id }.toSet())

        val existing = pagerAdapter
        if (existing == null) {
            val adapter = HomePagerAdapter(this)
            pagerAdapter = adapter
            homePager.adapter = adapter
        } else {
            existing.notifyDataSetChanged()
        }
        val current = if (pendingPage >= 0) pendingPage else homePager.currentItem
        pendingPage = -1
        val target = current.coerceIn(0, state.pageCount - 1)
        if (homePager.currentItem != target) homePager.setCurrentItem(target, false)

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
                        // Binder call hai: bahut chhote badlav pe nahi bhejte; ruke hue (offset 0) par aakhri value zaroor
                        if (abs(x - lastWallpaperX) >= 0.004f || (positionOffset == 0f && x != lastWallpaperX)) {
                            lastWallpaperX = x
                            homePager.windowToken?.let { wallpaperManager.setWallpaperOffsets(it, x, 0.5f) }
                        }
                    }
                }

                override fun onPageSelected(position: Int) {
                    updateIndicator(position)
                    homePager.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    homeStore.currentPage = position
                    // Selected widget ka frame naye page pe (agar wahi page hai)
                    selectedId?.let { selectEntry(it) }
                }
            })
        }
        DepthCurveTransformer.setEnabled(homePager, prefs.transition3d)

        buildIndicator(state.pageCount, homePager.currentItem)
        buildDock()
    }

    /** Saved layout lo; nahi hai (ya "Reset home layout" dabaya gaya) to purane flow jaisa default banao. */
    private fun loadOrCreateState() {
        val token = prefs.homeResetToken
        if (::state.isInitialized && state.resetToken == token) return
        if (!::state.isInitialized) {
            val loaded = homeStore.load()
            if (loaded != null && loaded.resetToken == token) {
                state = loaded
                return
            }
        }
        if (::state.isInitialized) {
            // Reset: purane system widgets ke host ids chhodo
            for (e in state.entries) if (e.kind == EntryKind.WIDGET) widgetHost.deleteId(e.widgetId)
            selectedId = null
        }
        state = createDefaultState()
        state.resetToken = token
    }

    /** Pehli baar (ya reset ke baad): wahi order jo pehle automatic tha, ab cells mein: widgets, search, folders, apps. */
    private fun createDefaultState(): HomeState {
        val st = HomeState()
        val o = HomeOps(st, grid)
        for (app in resolveDockApps()) st.dock.add(app.packageName)
        for (t in prefs.widgetTypes) o.placeFlow(o.builtin(t))
        val dockSet = st.dock.toSet()
        val homeApps = allApps.filter { it.packageName !in dockSet }
        for (item in buildHomeItems(homeApps)) {
            when (item) {
                is HomeItem.Search -> o.placeFlow(HomeEntry("search", EntryKind.SEARCH, 0, 0, 0, grid.cols, 1))
                is HomeItem.Folder -> {
                    val f = HomeEntry.newFolder(item.name, 0, 0, 0)
                    f.packages.addAll(item.apps.map { it.packageName })
                    o.placeFlow(f)
                }
                is HomeItem.App -> o.placeFlow(HomeEntry.app(item.app.packageName))
            }
        }
        return st
    }

    // ---------------------------------------------------------------------------------------
    // Unlock transition
    // ---------------------------------------------------------------------------------------
    /**
     * onResume har baar unlock nahi hota (app se wapas aana bhi onResume hai). Isliye unlock tab maana jata hai jab:
     *  - ACTION_USER_PRESENT aaya (launcher dikh raha ho to turant, nahi to 2.5 s ke andar onResume pe), ya
     *  - fallback: pause ke waqt keyguard locked tha aur ab nahi (kuch phones pe broadcast nahi aata).
     */
    private fun checkUnlockOnResume() {
        val now = SystemClock.elapsedRealtime()
        val viaBroadcast = userPresentAt != 0L && now - userPresentAt < 2500
        val keyguardNow = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val viaKeyguard = lockedAtPause && !keyguardNow
        lockedAtPause = false
        if (viaBroadcast || viaKeyguard) playUnlockAnimation()
    }

    /** Unlock pe: clock spring se bounce karke aata hai, icons halke stagger ke saath pop hote hain. */
    private fun playUnlockAnimation() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUnlockAnimAt < 2000) return // duplicate events se overlap nahi
        if (!resumed || editMode || drawerOpen || folderOpen || launchState != LaunchState.IDLE || drag.active) return
        lastUnlockAnimAt = now
        userPresentAt = 0L
        cancelUnlockAnimation()

        // Clock
        val page0 = pagerAdapter?.holderFor(0)
        val clockView: View? = if (page0 != null) {
            if (page0.clockCompact.visibility == View.VISIBLE) page0.clockCompactView else page0.clock
        } else {
            null
        }
        if (clockView != null && homePager.currentItem == 0) {
            clockView.alpha = 0f
            clockView.scaleX = 0.88f
            clockView.scaleY = 0.88f
            clockView.springAlpha(1f, Springs.STIFF_SOFT)
            clockView.springScale(1f, Springs.STIFF_SOFT, Springs.DAMPING_VERY_BOUNCY)
            unlockViews.add(clockView)
        }

        // Icons (current page + dock): scale + fade spring, chhota stagger
        var index = 0
        val cells = pagerAdapter?.cellsFor(homePager.currentItem)
        if (cells != null) {
            for (k in 0 until cells.childCount) {
                val v = cells.getChildAt(k)
                val tag = v.tag as? String ?: continue
                if (tag.startsWith("app:") || tag.startsWith("folder:")) animateIn(v, index++)
            }
        }
        for (k in 0 until dockContainer.childCount) animateIn(dockContainer.getChildAt(k), index++)
    }

    private fun animateIn(v: View, index: Int) {
        v.cancelSprings()
        v.alpha = 0f
        v.scaleX = 0.8f
        v.scaleY = 0.8f
        unlockViews.add(v)
        val start = Runnable {
            v.springAlpha(1f, Springs.STIFF_MEDIUM)
            v.springScale(1f, Springs.STIFF_SOFT, Springs.DAMPING_BOUNCY)
        }
        val delay = min(index * 16L, 260L)
        if (delay == 0L) {
            start.run()
        } else {
            unlockPending.add(v to start)
            v.postDelayed(start, delay)
        }
    }

    /** Chal rahi unlock animation rok kar sab views normal state mein (alpha / scale 1). */
    private fun cancelUnlockAnimation() {
        for ((v, r) in unlockPending) v.removeCallbacks(r)
        unlockPending.clear()
        for (v in unlockViews) {
            v.cancelSprings()
            v.alpha = 1f
            v.scaleX = 1f
            v.scaleY = 1f
        }
        unlockViews.clear()
    }

    internal fun persistHome() {
        if (::state.isInitialized) homeStore.save(state)
    }

    /** "Suggested apps" widget ke liye: dock ke bahar ke sabse zyada khole gaye apps. */
    internal fun suggestedApps(): List<AppInfo> {
        val dockSet = state.dock.toSet()
        return stats.top(allApps.filter { it.packageName !in dockSet }, 4)
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

    /** Dock ab saved layout (state.dock) se banta hai: apps dock mein aa / ja sakte hain, order badal sakta hai. */
    internal fun buildDock() {
        dockContainer.removeAllViews()
        for (pkg in state.dock) {
            val app = appByPkg[pkg] ?: continue
            val item = layoutInflater.inflate(R.layout.item_dock, dockContainer, false)
            val icon = item.findViewById<ImageView>(R.id.dockIcon)
            icon.setImageDrawable(iconStyle.drawableFor(app))
            iconStyle.styleIcon(icon)
            item.tag = pkg
            item.setOnClickListener { launchApp(app, icon) }
            item.applyPressEffect()
            dockContainer.addView(item)
        }
    }

    // ---- Dock drag-drop ke helpers (DragController use karta hai) ----
    internal fun dockRect(): Rect {
        if (editMode) return Rect() // edit mode mein dock chhupa hai: drop / long-press ke liye nahi
        val loc = IntArray(2)
        dockContainer.getLocationOnScreen(loc)
        return Rect(0, loc[1] - dp(12), resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
    }

    /** Dock mein kis position pe insert hoga (dragged app ko ginti se bahar rakhkar). */
    internal fun dockIndexAt(rawX: Float, excludePkg: String?): Int {
        var idx = 0
        val loc = IntArray(2)
        for (i in 0 until dockContainer.childCount) {
            val c = dockContainer.getChildAt(i)
            if (excludePkg != null && c.tag == excludePkg) continue
            c.getLocationOnScreen(loc)
            if (rawX > loc[0] + c.width / 2f) idx++
        }
        return idx
    }

    internal fun dockSlotRect(index: Int, count: Int): RectF {
        val loc = IntArray(2)
        dockContainer.getLocationOnScreen(loc)
        val slotW = dockContainer.width / max(count, 1).toFloat()
        val cx = loc[0] + (index + 0.5f) * slotW
        val cy = loc[1] + dockContainer.height / 2f
        val half = iconStyle.sizePx / 2f
        return RectF(cx - half, cy - half, cx + half, cy + half)
    }

    internal fun dockChildAt(index: Int): View? = dockContainer.getChildAt(index)

    internal fun setDockHighlight(on: Boolean) {
        if (on) {
            if (dockHighlight == null) {
                dockHighlight = GradientDrawable().apply {
                    setColor(Color.argb(46, 255, 255, 255))
                    cornerRadius = dp(24).toFloat()
                }
            }
            dockContainer.foreground = dockHighlight
        } else if (dockContainer.foreground != null) {
            dockContainer.foreground = null
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
            val dot = pageIndicator.getChildAt(i)
            dot.springAlpha(if (active) 1f else 0.45f, Springs.STIFF_MEDIUM)
            dot.springScale(if (active) 1.35f else 1f, Springs.STIFF_MEDIUM, Springs.DAMPING_VERY_BOUNCY)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Folder popup (liquid glass panel + peeche home blur)
    // ---------------------------------------------------------------------------------------
    private fun setupFolderPopup() {
        folderRecycler.layoutManager = GridLayoutManager(this, HOME_COLUMNS)
        folderOverlay.setOnClickListener { closeFolder() }
        folderTitle.setOnClickListener { renameOpenFolder() } // naam pe tap = rename
    }

    /** Folder ka naam badlo (home pe folder ke neeche ka label bhi). */
    private fun renameOpenFolder() {
        val entry = openFolderId?.let { state.find(it) } ?: return
        val input = EditText(this)
        input.setText(entry.name)
        input.setSelection(input.text.length)
        input.setSingleLine()
        AlertDialog.Builder(this)
            .setTitle("Folder ka naam")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Folder" }.take(24)
                entry.name = name
                folderTitle.text = name
                persistHome()
                pagerAdapter?.syncAll(false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Folder popup khula ho to uska screen rect (drag ke liye). */
    internal fun folderPanelRect(): Rect? {
        if (!folderOpen) return null
        val loc = IntArray(2)
        folderPanel.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + folderPanel.width, loc[1] + folderPanel.height)
    }

    /** Folder ke andar pointer kaun si position pe hai. */
    internal fun folderIndexAt(rawX: Float, rawY: Float): Int {
        val entry = openFolderId?.let { state.find(it) } ?: return 0
        val loc = IntArray(2)
        folderRecycler.getLocationOnScreen(loc)
        val cellW = (folderRecycler.width / HOME_COLUMNS).coerceAtLeast(1)
        val cellH = dp(ROW_HEIGHT_DP)
        val col = ((rawX - loc[0]) / cellW).toInt().coerceIn(0, HOME_COLUMNS - 1)
        val row = ((rawY - loc[1]) / cellH).toInt().coerceAtLeast(0)
        return (row * HOME_COLUMNS + col).coerceIn(0, (entry.packages.size - 1).coerceAtLeast(0))
    }

    /** Folder ka content badla (reorder / hatana): popup ki grid dobara. */
    internal fun refreshOpenFolder() {
        if (!folderOpen) return
        val entry = openFolderId?.let { state.find(it) }
        if (entry == null) {
            closeFolder()
            return
        }
        folderTitle.text = entry.name
        val apps = entry.packages.mapNotNull { appByPkg[it] }
        folderRecycler.adapter = HomeGridAdapter(
            ArrayList<HomeItem>(apps.map { HomeItem.App(it) }),
            nameTint, false, iconStyle, 0, homeActions
        )
    }

    /** Folder ke andar app ko long-press: drag shuru (folder ke andar reorder ya bahar nikalna / doosre folder mein). */
    private fun startFolderDrag(app: AppInfo, view: View) {
        val folderId = openFolderId ?: return
        val origin = (view.parent as? View) ?: view
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        window.superDispatchTouchEvent(cancel)
        cancel.recycle()
        ignoreTouches = true
        drag.start(HomeEntry.app(app.packageName), DragController.Src.FOLDER, origin, lastRawX, lastRawY, folderId)
    }

    /** App ko folder se hatao: wo folder ki jagah (ya paas ki khaali jagah) pe home pe aa jata hai. */
    private fun removeFromFolder(folderId: String, pkg: String) {
        val f = state.find(folderId) ?: return
        val page = f.page
        val x = f.cx
        val y = f.cy
        f.packages.remove(pkg)
        ops.cleanFolders()
        val e = HomeEntry.app(pkg)
        if (!ops.placeNearest(e, page, x, y)) ops.placeFlow(e, page)
        closeFolder(false)
        afterModelChange(true)
    }

    internal fun openFolderEntry(id: String, tile: View) {
        if (folderOpen || drawerOpen || launchState != LaunchState.IDLE) return
        val entry = state.find(id) ?: return
        openFolderId = id
        folderOpen = true

        val apps = entry.packages.mapNotNull { appByPkg[it] }
        folderTitle.text = entry.name
        folderRecycler.adapter = HomeGridAdapter(
            ArrayList<HomeItem>(apps.map { HomeItem.App(it) }),
            nameTint, false, iconStyle, 0, homeActions
        )
        val rows = ((apps.size + HOME_COLUMNS - 1) / HOME_COLUMNS).coerceIn(1, 4)
        val lp = folderRecycler.layoutParams
        lp.height = rows * dp(ROW_HEIGHT_DP)
        folderRecycler.layoutParams = lp

        folderPanel.background = LiquidGlassDrawable(dp(28).toFloat(), glassStrength().coerceAtLeast(0.3f))

        folderOverlay.cancelSprings()
        folderOverlay.alpha = 0f
        folderOverlay.visibility = View.VISIBLE
        folderOverlay.springAlpha(1f, Springs.STIFF_SOFT)

        // Panel folder icon ke center se phoolta hai: spring scale, halka bounce, pivot icon pe
        val tileLoc = IntArray(2)
        tile.getLocationOnScreen(tileLoc)
        folderPanel.cancelSprings()
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
            folderPanel.springAlpha(1f, Springs.STIFF_MEDIUM)
            folderPanel.springScale(1f, 380f, 0.62f)
        }
    }

    internal fun closeFolder(animated: Boolean = true) {
        if (!folderOpen) return
        folderOpen = false

        folderPanel.cancelSprings()
        folderOverlay.cancelSprings()
        if (animated) {
            folderPanel.springScale(0.2f, 620f, Springs.DAMPING_SMOOTH)
            folderPanel.springAlpha(0f, Springs.STIFF_HIGH)
            folderOverlay.springAlpha(0f, Springs.STIFF_MEDIUM) {
                if (!folderOpen) folderOverlay.visibility = View.GONE
            }
        } else {
            folderOverlay.visibility = View.GONE
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
        // Smooth scroll: size fixed, zyada views cache, search / filter pe list-change animation nahi (tez + halka)
        drawerRecycler.setHasFixedSize(true)
        drawerRecycler.setItemViewCacheSize(20)
        drawerRecycler.itemAnimator = null

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

    internal fun openDrawer(focusSearch: Boolean = false) {
        if (drawerOpen || editMode || folderOpen || launchState != LaunchState.IDLE) return
        drawerOpen = true

        // Drawer spring se upar aata hai (halka settle); home peeche chhota hoke fade hota hai
        drawerContainer.cancelSprings()
        drawerContainer.translationY = screenHeight()
        drawerContainer.visibility = View.VISIBLE
        // Sirf translation animate hoti hai: layer ki zaroorat nahi (layer banana pehle frame mein hitch deta tha)
        drawerContainer.springTranslationY(0f, 420f, 0.88f)

        homeContainer.cancelSprings()
        homeContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        homeContainer.springAlpha(0f, Springs.STIFF_MEDIUM)
        homeContainer.springScale(0.92f, 420f, 0.8f) {
            homeContainer.setLayerType(View.LAYER_TYPE_NONE, null)
        }

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

        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(searchBar.windowToken, 0)
        searchBar.clearFocus()
        // Search text / list reset yahan nahi: setText("") list rebuild + scrollToPosition(0) karta tha, yani
        // slide-down ke dauran list achanak upar kood jati thi (glitch). Drawer chhup jaye tab reset hota hai.

        drawerContainer.cancelSprings()
        homeContainer.cancelSprings()
        if (animated) {
            // Drawer neeche jata hai (bina bounce, bina layer), home bouncy spring se wapas apni jagah
            drawerContainer.springTranslationY(screenHeight(), 520f, Springs.DAMPING_SMOOTH) {
                if (!drawerOpen) {
                    drawerContainer.visibility = View.GONE
                    resetDrawerContent()
                }
            }
            homeContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            homeContainer.springAlpha(1f, Springs.STIFF_MEDIUM)
            homeContainer.springScale(1f, 420f, 0.5f) {
                homeContainer.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        } else {
            homeContainer.setLayerType(View.LAYER_TYPE_NONE, null)
            drawerContainer.visibility = View.GONE
            homeContainer.alpha = 1f
            homeContainer.scaleX = 1f
            homeContainer.scaleY = 1f
            resetDrawerContent()
        }
    }

    /** Drawer chhupne ke baad: search text saaf + list top pe (screen pe nahi dikhta, isliye glitch nahi). */
    private fun resetDrawerContent() {
        if (searchBar.text.isNotEmpty()) {
            searchBar.setText("") // TextWatcher refreshDrawerList() bulata hai (scroll bhi top pe)
        } else {
            drawerRecycler.scrollToPosition(0)
        }
    }

    /** Drawer ka background: wallpaper ke color wala dark gradient. */
    private fun updateDrawerBackground() {
        if (!::drawerContainer.isInitialized || !::prefs.isInitialized) return
        val t = wallpaperTheme
        val colors = if (t != null) {
            intArrayOf(t.drawerTop, t.drawerBottom)
        } else {
            intArrayOf(Color.parseColor("#1A1A24"), Color.parseColor("#101018"))
        }
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
        // Blur nahi hai: gehra tint taaki icons padhe ja sakein
        bg.alpha = 215
        drawerContainer.background = bg
    }

    // ---------------------------------------------------------------------------------------
    // Wallpaper theme sync
    // ---------------------------------------------------------------------------------------
    private fun applyTheme(t: ThemeColors) {
        GlassTheme.tint = t.glassTint // glass surfaces ka wallpaper-based tint
        wallpaperTheme = t
        nameTint = t.appNameTint
        accentColor = t.accent
        wallpaperLight = t.isLight
        viewFactory.invalidateStyle()
        pagerAdapter?.refreshAll()
        drawerAdapter.setNameTint(t.appNameTint)
        alphabetIndex.tint = t.appNameTint

        // Search bar: border + text + hint
        (searchBar.background.mutate() as GradientDrawable).setStroke(dp(2), t.searchBorder)
        searchBar.setTextColor(t.appNameTint)
        searchBar.setHintTextColor(ColorUtils.setAlphaComponent(t.appNameTint, 0x99))

        updateDrawerBackground()
    }

    // ---------------------------------------------------------------------------------------
    // Touch: long-press (edit mode), swipe-up / swipe-down (drawer)
    // ---------------------------------------------------------------------------------------
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastRawX = ev.rawX
        lastRawY = ev.rawY
        // Drag chal raha ho to saare touches drag controller ke (pager / icons ko kuch nahi milta)
        if (drag.active) {
            drag.onTouch(ev)
            return true
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoreTouches = false
            downX = ev.x
            downY = ev.y
            drawerStartedAtTop = !drawerRecycler.canScrollVertically(-1) &&
                ev.x < rootLayout.width - dp(40)

            longPressHandler.removeCallbacks(longPressRunnable)
            // Home / dock ke upar long-press: item uthao ya (khaali jagah pe) edit mode
            if (!drawerOpen && !folderOpen && launchState == LaunchState.IDLE && sheet == null) {
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
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
                    if (!drawerOpen && !editMode && !folderOpen && sheet == null && dy < -dp(140)) {
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
    // Edit mode: pages chhote, upar-right Done, neeche (dock ki jagah) 4 gol buttons:
    // Widgets / Wallpapers & style / Layout / Home screen settings. Pages manager = page dots pe tap.
    // Items long-press karke drag hote hain.
    // ---------------------------------------------------------------------------------------
    private class Hit(val entry: HomeEntry, val src: DragController.Src, val view: View?)

    private fun onHomeLongPress() {
        if (drawerOpen || folderOpen || launchState != LaunchState.IDLE || sheet != null) return
        val hit = hitTestEntry(lastRawX, lastRawY)
        // Neeche ke views ko CANCEL bhejo taaki pressed state / click na bane
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        window.superDispatchTouchEvent(cancel)
        cancel.recycle()
        ignoreTouches = true
        if (hit == null) {
            if (!editMode) enterEditMode()
            return
        }
        drag.start(hit.entry, hit.src, hit.view, lastRawX, lastRawY)
    }

    /** Screen point pe kaun sa item hai (dock ya current home page). */
    private fun hitTestEntry(rawX: Float, rawY: Float): Hit? {
        if (editMode) {
            val tb = findViewById<View>(R.id.editToolbar)
            val l = IntArray(2)
            tb.getLocationOnScreen(l)
            if (rawY >= l[1]) return null // neeche ke buttons pe long-press = item nahi
        }
        if (dockRect().contains(rawX.toInt(), rawY.toInt())) {
            val loc = IntArray(2)
            for (i in 0 until dockContainer.childCount) {
                val c = dockContainer.getChildAt(i)
                c.getLocationOnScreen(loc)
                if (rawX >= loc[0] && rawX <= loc[0] + c.width) {
                    val pkg = c.tag as? String ?: return null
                    return Hit(HomeEntry.app(pkg), DragController.Src.DOCK, c)
                }
            }
            return null
        }
        if (homePager.scrollState != ViewPager2.SCROLL_STATE_IDLE) return null
        val page = homePager.currentItem
        val cells = pagerAdapter?.cellsFor(page) ?: return null
        val local = cells.toLocal(rawX, rawY, homePager.scaleX)
        if (local[0] < 0f || local[1] < 0f || local[0] > cells.width ||
            local[1] > cells.paddingTop + cells.rows * cells.cellH
        ) return null
        val cell = cells.cellAt(local[0], local[1])
        val e = ops.occupantAt(page, cell[0], cell[1]) ?: return null
        return Hit(e, DragController.Src.HOME, cells.childForId(e.id))
    }

    /** Drag shuru hone se pehle: edit mode on, selection hatao. */
    internal fun prepareForDrag() {
        selectEntry(null)
        if (!editMode && !folderOpen) enterEditMode()
    }

    internal fun enterEditMode() {
        if (editMode || drawerOpen || folderOpen) return
        editMode = true
        homePager.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        pagerAdapter?.setEditing(true)

        // Pager spring se chhota hota hai (halka bounce). Animation ke dauran hardware layer = ek texture, smooth scale.
        homePager.cancelSprings()
        homePager.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        homePager.springScale(EDIT_SCALE, 380f, 0.6f) { homePager.setLayerType(View.LAYER_TYPE_NONE, null) }
        homePager.springTranslationY(0f, 380f, 0.8f)

        // Dock neeche jaake chhup jata hai: uski jagah 4 buttons aate hain
        dockContainer.cancelSprings()
        dockContainer.springAlpha(0f, Springs.STIFF_MEDIUM)
        dockContainer.springTranslationY(dp(48).toFloat(), 420f, Springs.DAMPING_SMOOTH) {
            if (editMode) dockContainer.visibility = View.INVISIBLE // touch bhi na le
        }

        pageIndicator.background = LiquidGlassDrawable(dp(10).toFloat(), 0.5f)
        pageIndicator.setPadding(dp(10), 0, dp(10), 0)
        pageIndicator.springTranslationY(-(homePager.height * (1f - EDIT_SCALE) / 2f), 380f, 0.7f)

        // Done + buttons: stagger ke saath bouncy pop-in
        editOverlay.cancelSprings()
        editOverlay.alpha = 1f
        editOverlay.visibility = View.VISIBLE
        for ((v, r) in editPending) v.removeCallbacks(r)
        editPending.clear()
        editChrome.forEachIndexed { i, v -> popIn(v, i) }
        showEditHint()
    }

    private fun popIn(v: View, index: Int) {
        v.cancelSprings()
        v.alpha = 0f
        v.scaleX = 0.5f
        v.scaleY = 0.5f
        v.translationY = if (v.id == R.id.editDone) -dp(12).toFloat() else dp(28).toFloat()
        val start = Runnable {
            v.springAlpha(1f, Springs.STIFF_MEDIUM)
            v.springScale(1f, Springs.STIFF_SOFT, Springs.DAMPING_VERY_BOUNCY)
            v.springTranslationY(0f, Springs.STIFF_SOFT, Springs.DAMPING_BOUNCY)
        }
        editPending.add(v to start)
        v.postDelayed(start, 40L * index)
    }

    /** Hint kuch second dikhta hai, phir fade (system Toast nahi: wo page dots / buttons ko dhak deta tha). */
    private fun showEditHint() {
        editHint.removeCallbacks(editHintHide)
        editHint.cancelSprings()
        editHint.alpha = 0f
        editHint.springAlpha(1f, Springs.STIFF_SOFT)
        editHint.postDelayed(editHintHide, 2800L)
    }

    internal fun exitEditMode() {
        if (!editMode) return
        drag.cancel()
        dismissSheet(false)
        selectEntry(null)
        editMode = false
        pagerAdapter?.setEditing(false)
        persistHome()

        homePager.cancelSprings()
        homePager.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        homePager.springScale(1f, 380f, 0.55f) { homePager.setLayerType(View.LAYER_TYPE_NONE, null) } // bouncy wapas
        homePager.springTranslationY(0f, 380f, 0.8f)

        // Dock bouncy wapas upar aata hai
        dockContainer.visibility = View.VISIBLE
        dockContainer.cancelSprings()
        dockContainer.springAlpha(1f, Springs.STIFF_MEDIUM)
        dockContainer.springTranslationY(0f, 420f, 0.6f)

        pageIndicator.background = null
        pageIndicator.setPadding(0, 0, 0, 0)
        pageIndicator.springTranslationY(0f, 380f, 0.7f)

        for ((v, r) in editPending) v.removeCallbacks(r)
        editPending.clear()
        editHint.removeCallbacks(editHintHide)
        editHint.springAlpha(0f, Springs.STIFF_HIGH)
        editChrome.forEach { v ->
            v.cancelSprings()
            v.springScale(0.8f, Springs.STIFF_HIGH, Springs.DAMPING_SMOOTH)
        }
        editChrome.forEachIndexed { i, v ->
            if (i == 0) {
                v.springAlpha(0f, Springs.STIFF_HIGH) { if (!editMode) editOverlay.visibility = View.GONE }
            } else {
                v.springAlpha(0f, Springs.STIFF_HIGH)
            }
        }
    }

    // ---- Selection (widget resize) ----
    /** Edit mode mein page pe tap: widget pe = select (resize handles), baaki jagah = deselect, folder = khulta hai. */
    internal fun onEditTap(page: Int, x: Float, y: Float) {
        val cells = pagerAdapter?.cellsFor(page) ?: return
        val c = cells.cellAt(x, y)
        val e = ops.occupantAt(page, c[0], c[1])
        when {
            e != null && (e.kind == EntryKind.WIDGET || e.kind == EntryKind.BUILTIN) -> selectEntry(e.id)
            e != null && e.kind == EntryKind.FOLDER -> {
                selectEntry(null)
                cells.childForId(e.id)?.let { v -> openFolderEntry(e.id, v.findViewById<View>(R.id.folderTile) ?: v) }
            }
            else -> selectEntry(null)
        }
    }

    internal fun selectEntry(id: String?) {
        val adapter = pagerAdapter ?: return
        for (page in 0 until state.pageCount) {
            val cells = adapter.cellsFor(page) ?: continue
            cells.resizeFrame?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                cells.resizeFrame = null
            }
        }
        selectedId = null
        if (id == null) return
        val e = state.find(id) ?: return
        val cells = adapter.cellsFor(e.page) ?: run {
            selectedId = id // page abhi bound nahi: page aate hi frame lagega
            return
        }
        val rows = grid.rows(e.page)
        var minSx = 1
        var minSy = 1
        var maxSx = grid.cols
        var maxSy = rows
        var canH = true
        var canV = true
        when (e.kind) {
            EntryKind.WIDGET -> {
                val info = widgetHost.infoFor(e.widgetId)
                if (info != null) {
                    val sp = widgetHost.spansFor(info, cells.cellW, cells.cellH, grid.cols, rows)
                    minSx = sp.minSx
                    minSy = sp.minSy
                    maxSx = sp.maxSx
                    maxSy = sp.maxSy
                    canH = sp.maxSx > sp.minSx
                    canV = sp.maxSy > sp.minSy
                } else {
                    canH = false
                    canV = false
                }
            }
            EntryKind.BUILTIN -> {
                minSx = 2
                minSy = 1
                maxSy = minOf(4, rows)
            }
            else -> return
        }
        val frame = ResizeFrame(this, e, minSx, minSy, maxSx, maxSy, canH, canV)
        frame.alpha = 0f
        cells.addView(frame, CellLayout.LP(e.cx, e.cy, e.sx, e.sy))
        cells.resizeFrame = frame
        frame.scaleX = 0.96f
        frame.scaleY = 0.96f
        frame.springAlpha(1f, Springs.STIFF_MEDIUM)
        frame.springScale(1f, Springs.STIFF_SOFT, Springs.DAMPING_BOUNCY)
        selectedId = id
    }

    /** Widget / built-in widget hatao (app uninstall nahi hota). */
    internal fun removeEntry(e: HomeEntry) {
        if (e.kind == EntryKind.WIDGET) widgetHost.deleteId(e.widgetId)
        if (e.kind == EntryKind.BUILTIN) {
            prefs.widgetTypes = prefs.widgetTypes - e.type
            lastPrefsSignature = prefs.signature()
        }
        state.entries.remove(e)
        selectedId = null
        afterModelChange(true)
    }

    /** Model badla: save, views milao (position badalne ka animation), dock + selection dobara. */
    internal fun afterModelChange(animate: Boolean) {
        persistHome()
        viewFactory.retain(state.entries.map { it.id }.toSet())
        pagerAdapter?.syncAll(animate)
        buildDock()
        val sid = selectedId
        if (sid != null) selectEntry(sid)
    }

    /** Pages ki ginti / order badla. */
    internal fun onPagesChanged() {
        pagerAdapter?.notifyDataSetChanged()
        if (homePager.currentItem >= state.pageCount) homePager.setCurrentItem(state.pageCount - 1, false)
        buildIndicator(state.pageCount, homePager.currentItem)
        persistHome()
    }

    internal fun dismissSheet(animated: Boolean = true) {
        val sh = sheet ?: return
        sheet = null
        val panel = (sh as? ViewGroup)?.getChildAt(0)
        if (animated && panel != null) {
            panel.springTranslationY(panel.height.toFloat(), 600f, Springs.DAMPING_SMOOTH) {
                panel.cancelSprings()
                sh.cancelSprings()
                (sh.parent as? ViewGroup)?.removeView(sh)
            }
            sh.springAlpha(0f, Springs.STIFF_MEDIUM)
        } else {
            (sh.parent as? ViewGroup)?.removeView(sh)
        }
    }

    @Deprecated("Widget bind / configure results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!handleWidgetResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /** Edit mode ke buttons + search pill ka mic. */
    private fun setupExtras() {
        findViewById<View>(R.id.editDone).setOnClickListener { exitEditMode() }
        findViewById<View>(R.id.editWidgets).setOnClickListener { showWidgetPicker() }
        // Pages manager: edit mode mein page dots pe tap
        pageIndicator.setOnClickListener { if (editMode) showPageManager() }
        for (id in listOf(R.id.editDone, R.id.editWidgets, R.id.editWallpaper, R.id.editLayout, R.id.editSettings)) {
            findViewById<View>(id).applyPressEffect()
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

    internal fun safeStart(intent: Intent) {
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
                    sheet != null -> dismissSheet()
                    drag.active -> drag.cancel()
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
    /**
     * Installed apps (labels + icons). SIRF background thread pe chalao: 100+ apps ke icons load karna bhari hai.
     * Hidden filter yahan nahi lagta (cache poori list rakhta hai); main thread pe [withoutHidden] se lagta hai.
     */
    private fun queryInstalledApps(): List<AppInfo> {
        val pm = applicationContext.packageManager // Activity ka pm nahi: cache mein Activity ka reference na rahe
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        installedCount = resolveInfos.size
        val self = applicationContext.packageName
        return resolveInfos
            .filter { it.activityInfo.packageName != self } // khud ko hatao
            .map { info ->
                AppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    icon = info.loadIcon(pm),
                    category = info.activityInfo.applicationInfo.category
                )
            }
            .sortedBy { it.labelLower }
    }

    /** Hide kiye hue apps nikaal do (main thread pe chalta hai, sasta). */
    private fun withoutHidden(apps: List<AppInfo>): List<AppInfo> {
        val hidden = prefs.hiddenApps
        return if (hidden.isEmpty()) apps else apps.filter { it.packageName !in hidden }
    }

    /** Apps background thread pe load karo; UI nahi rukta. Nateeja aane par drawer + home update. */
    private fun reloadAppsAsync() {
        val generation = ++loadGeneration
        Background.io.execute {
            val apps = try {
                queryInstalledApps()
            } catch (e: Exception) {
                return@execute // kuch phones pe PackageManager kabhi fail hota hai: crash nahi, agle resume pe phir koshish
            }
            AppCache.set(LoadedApps(apps, emptyList(), false))
            AppCache.installedCount = installedCount
            uiHandler.post {
                if (isDestroyed || generation != loadGeneration) return@post
                allApps = withoutHidden(apps)
                appsLoaded = true
                refreshDrawerList()
                if (lastBuiltHeight > 0) buildHome(lastBuiltHeight)
            }
        }
    }

    /** Naya app install / purana uninstall hua ho to app list dobara banao (sirf ginti background mein check hoti hai). */
    private fun reloadAppsIfChanged() {
        if (!appsLoaded) return // pehli load abhi chal rahi hai
        val app = applicationContext
        Background.io.execute {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val count = try {
                app.packageManager.queryIntentActivities(intent, 0).size
            } catch (e: Exception) {
                return@execute
            }
            if (count != installedCount) {
                uiHandler.post { if (!isDestroyed) reloadAppsAsync() }
            }
        }
    }

    /** Hide / unhide ke baad: icons dobara load kiye bina cache wali list ko dobara filter karo. */
    private fun reloadApps() {
        val full = AppCache.get()?.apps ?: allApps
        allApps = withoutHidden(full)
        refreshDrawerList()
        if (lastBuiltHeight > 0) buildHome(lastBuiltHeight)
    }

    /** App icon pe long-press: App info / Uninstall / Hide (ColorOS jaisa menu). */
    /** Drag bina hile chhoda (ya folder se): app ka menu. */
    internal fun showAppMenuFor(pkg: String, anchor: View?, folderId: String?) {
        val app = appByPkg[pkg] ?: return
        showAppMenu(app, anchor ?: dockContainer, folderId)
    }

    internal fun showAppMenu(app: AppInfo, anchor: View, folderId: String? = null) {
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
        if (folderId != null) popup.menu.add(0, 4, 3, "Folder se hatao")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> safeStart(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                )
                2 -> safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                3 -> hideApp(app)
                4 -> folderId?.let { removeFromFolder(it, app.packageName) }
            }
            true
        }
        popup.show()
    }

    internal fun hideApp(app: AppInfo) {
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
    internal fun launchApp(app: AppInfo, iconView: View) {
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
            cell.cancelSprings()
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
    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun screenHeight(): Float = resources.displayMetrics.heightPixels.toFloat()
}
