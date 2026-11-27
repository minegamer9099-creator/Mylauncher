# Smoother + more optimised (round 3)
- Baseline profile (`baseline-prof.txt` + `profileinstaller`): app code install ke baad AOT-compile, pehle use mein jank kam.
  (Release build isse fail ho to is file ko delete karo.)
- `GlassClockView`: bade text + shadow ek hardware-layer texture mein, `render()` cache (same time/color pe relayout nahi).
- `FolderTileView`: 3x3 mini icons ek bitmap mein cache (pehle har frame 9 clipPath).
- `DrawerAdapter`: click / long-press / press-effect listeners ek baar `onCreateViewHolder` mein (scroll mein GC kam).
- Wallpaper parallax: `setWallpaperOffsets` binder call sirf tab jab offset sach mein badla.

# Edit screen redesign + more optimisation

## Edit screen (screenshot ke hisaab se)
- Upar-right: **Done** glass pill. Neeche (dock ki jagah): 4 gol buttons: Widgets, Wallpapers & style, Layout, Home screen settings
  (alag vector icons, 52dp circles). Dock edit mode mein neeche slide hoke chhup jata hai (bouncy wapas aata hai).
- Pages manager: edit mode mein page dots pe tap (alag "Pages" button hata).
- System Toast ki jagah in-screen hint pill (kuch second baad fade): ab page dots / buttons nahi dhakta.
- Entry: pager 0.8x tak bouncy shrink, Done + buttons stagger ke saath bouncy pop-in; exit: bouncy wapas.
- Edit mode perf: pager transition ke dauran hardware layer (baad mein hat jata hai), dock hidden hone se kam views draw,
  drag-move mein per-event array allocation nahi.

## Build-level optimisation
- `resourceConfigurations` en + hi, `isShrinkResources = true`, Kotlin runtime null-check assertions off,
  R8 `-allowaccessmodification` / `-repackageclasses`.

# Changes: heavy effects removed + spring (bouncy) animations + perf

## Removed (lag ki wajah se)
- 3D depth wallpaper: `DepthLayerView`, `DepthEditorActivity`, `DepthStore`, `SubjectSegmenter`, prefs, settings button, manifest entry.
- All blur: window blur (drawer / app launch), folder home blur (RenderEffect), "Visual blur" / "Animation blur" switches,
  settings-screen blur. Drawer / settings use a darker solid tint instead.
- Touch glow: switch removed, `Prefs.touchGlow` is now always `false`.

## Animations -> spring physics (`Springs.kt`, `androidx.dynamicanimation:1.0.0`)
- Press effect: fast press-in, bouncy release.
- Drawer open/close, folder open/close, edit mode in/out, bottom sheets, page dots, widget resize frame.
- Drag: bouncy pickup "pop", spring drop / return; grid reorder slides with a soft bounce.
- Unlock: bouncy clock + staggered icon pop. App-open overlay: spring-shaped interpolator (480 ms).
- Hardware layer only while drawer / home animate, removed afterwards.

## Perf
- Drawer RecyclerView: fixed size, bigger view cache, no item animator.
- `PaletteEngine` uses shared `Background.io` (extra thread removed); `bgExecutor` removed.
- Earlier fixes (async app loading, MusicWidget reconnect, release build) stay.

Not compiled / device-tested in this environment: build via GitHub Actions and send errors if any.

# Changes: fix freeze / lag when returning to the launcher (minimize app)

## Root causes found
- `MainActivity.onCreate` loaded labels + icons of ALL installed apps on the main thread
  (`loadInstalledApps()`). When Android recreates the launcher (low RAM / process killed while another
  app is open) the screen froze for a few seconds. (`AppLoader` / `AppCache` existed but were unused.)
- `MusicWidgetView.onWindowFocusChanged` did disconnect + connect on EVERY focus gain, i.e. a synchronous
  `MediaSessionManager.getActiveSessions()` binder call on the main thread each time you returned from an app.
- `onTrimMemory(TRIM_MEMORY_BACKGROUND)` dropped the 3D depth wallpaper bitmaps too early, so coming back
  re-decoded two 2400px images (wallpaper flicker + jank).
- `DepthLayerView`: `applyConfig` / `setAccent` / `setBlur` re-did layout / text render / RenderEffect on every
  `onResume` / `onStop` even when nothing changed.
- Only a debuggable, non-R8 APK was built (slower runtime).

## Fixes
- `MainActivity.kt`: apps load on `Background.io` + process-level `AppCache`; `buildHome` waits until the
  first load finishes (otherwise `reconcile()` would wipe the saved layout); hide / unhide re-filters the cached
  list instead of reloading all icons; `reloadAppsIfChanged` only counts launcher activities in the background.
- `AppLoader.kt`: `AppCache.installedCount`.
- `MusicWidgetView.kt`: reconnect on focus only if access was not granted yet.
- `DepthLayerView.kt`: skip unchanged config / accent / blur.
- `MainActivity.onTrimMemory`: depth bitmaps are dropped only at `TRIM_MEMORY_MODERATE`.
- `app/build.gradle.kts`, `proguard-rules.pro`, `.github/workflows/build.yml`: release APK (R8, not debuggable,
  signed with the debug key) is built as `MyLauncher-release-apk`; debug APK still built.

Build / device testing is not possible in this environment.

# Changes: premium opening animation, default system closing, no duplicate launch

## Behaviour
- Tap on icon -> custom opening animation starts immediately from the tapped icon (320 ms, one animator,
  one draw call per frame). `startActivity()` is called once, right away (no artificial delay).
- Closing / returning from an app -> Android's own default transition. The launcher has no return animation,
  no window-animation override and no overlay on return.
- The selected home page is kept (also across process death via `onSaveInstanceState`).

## Modified files
- `MainActivity.kt`: launch state machine (IDLE/LAUNCHING), single `startActivity` call site, removed return
  animation (collapse / settle / watchdog / pendingReturn), page save/restore, `onNewIntent` no longer resets
  the page when coming back from an app, origin rect with fallback, safety timeout.
- `LaunchOverlayView.kt`: opening only. Removed reverse/collapse logic. `start()` cancels the previous animator,
  `reset()` releases everything.
- `SuggestedAppsWidgetView.kt`: exposes the tapped icon bounds so the animation starts from that icon.
- `IconStyle.kt`: `cornerRadiusFor(size)`.
- `HomePagerAdapter.kt`, `DrawerAdapter.kt`, `AlphabetIndexView.kt`: skip refresh when nothing changed.
- `Prefs.kt`, `SettingsActivity.kt`: removed the close-animation setting.
- `themes.xml`, `AndroidManifest.xml`: removed the launcher window-animation style override
  (`Theme.MyLauncher.Home`), removed `stateNotNeeded` (needed to keep the page across process death).
- `res/anim/none.xml`: deleted.
