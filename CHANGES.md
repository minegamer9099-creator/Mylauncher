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
