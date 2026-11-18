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

---

# Performance pass (code inspection based, not measured on a device)

## Bottlenecks found in the source (and where)
| # | Where | Problem | Fix |
|---|-------|---------|-----|
| 1 | `MainActivity.onCreate` | `loadInstalledApps()` ran on the UI thread: `loadLabel` + `loadIcon` for every app before the first frame | `AppLoader` on a background thread, result applied on the UI thread, `AppCache` survives Activity recreation |
| 2 | `MainActivity.hideApp` | hiding one app re-queried and reloaded ALL apps on the UI thread | hidden apps are filtered from the cached list, no PackageManager call |
| 3 | `MainActivity.onResume` | `queryIntentActivities()` binder call on every resume | one package add/remove/replace/change receiver, debounced reload |
| 4 | `MainActivity.buildHome` | 4x `resolveActivity()` binder calls on the UI thread per rebuild | dock packages resolved once during loading |
| 5 | App icons | each icon kept as `AdaptiveIconDrawable` (two layers, mask drawn every frame) | `IconRenderer`: one small bitmap per app, rendered once on the background thread; dark theme applied there too |
| 6 | `HomePagerAdapter.onBindViewHolder` | new `HomeGridAdapter` (so ~20 item inflations) and all widget views recreated on every bind, e.g. each time you swipe back to page 1 | first page has its own view type, grid adapter is created once and `update()`d, widgets only rebuilt when the widget list changes, shared `RecycledViewPool` |
| 7 | `GlassCardView.applyGlass`, search pill | a new `LiquidGlassDrawable` (3 shaders) on every bind | rebuilt only when strength actually changes |
| 8 | `AnalogClockWidgetView`, `DateWidgetView` | `Calendar` / `SimpleDateFormat` allocated in `onDraw` | values computed on tick, `onDraw` allocation free |
| 9 | `NotesWidgetView` | `StaticLayout` built in every `onDraw` | cached by text + width |
| 10 | `GlassClockView` | `SimpleDateFormat`, spans and `setText` (relayout) on every bind | cached formats, text only set when time/accent changed |
| 11 | 4-5 views | each registered its own `ACTION_TIME_TICK` receiver | one `TimeTicker` receiver |
| 12 | `MusicWidgetView` | session listener re-registered on every window focus change | only reconnects after a permission denial |
| 13 | `DrawerAdapter` | `notifyDataSetChanged()` on every keystroke / tab switch | `AsyncListDiffer` (diff off the UI thread), cached All / Category lists, precomputed lowercase labels, fixed-size recycler, no change animations |
| 14 | `PaletteEngine` | decoded the full wallpaper bitmap on every app start | theme cached by wallpaper id (applied instantly), decode only when the wallpaper changed |
| 15 | `onPageScrolled` | `setWallpaperOffsets` binder call every frame | only when the offset changed by > 0.002 |
| 16 | Executors | MainActivity, PaletteEngine and Settings each created their own thread | one shared pool (`Background`, max 2 threads, idle timeout) |
| 17 | `SettingsActivity` | 6 gradient bitmaps rendered on the UI thread | rendered on the background pool |
| 18 | `AlphabetIndexView.onDraw` | `toString()` per letter per frame | precomputed strings |
| 19 | Blur | real-time blur also on low-RAM devices | skipped when `isLowRamDevice` (glass tint fallback) |

## Not changed (checked, no real problem found)
- `LaunchOverlayView` (one draw call per frame, no allocations), `DepthCurveTransformer` (hardware layers only while swiping),
  `LiquidGlassDrawable` (shaders only built on bounds change), `FolderTileView` (draw allocation free).

## Not verified
No Gradle build, profiler run or device test was possible in this environment. All timings above are expectations from reading the code.
