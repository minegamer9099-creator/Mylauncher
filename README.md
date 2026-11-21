# My Launcher (ColorOS-style)

## Features
- Home pages (swipe) + 3D depth transition, page indicator, wallpaper parallax
- Clock: ColorOS compact (left clock + right date) ya bada centered glass clock
- Liquid glass widgets (light/dark auto, touch pe glow): Analog clock, Battery, Suggested apps,
  Music player, Date, Storage, Quick note, Quick actions (torch / Wi-Fi / Bluetooth / Settings)
- Search widget home ke beech mein; edit mode mein icons / folders / search drag-drop (order save hota hai)
- Glass folders (auto: Google + categories), glass app drawer (All / Categories, A-Z, search)
- App icon long-press menu: App info, Uninstall, Hide app
- Dark icons / system dark mode icons
- Settings (liquid glass): Wallpapers & style, Widgets, Layout, Home screen settings
- Wallpaper presets + gallery se wallpaper, wallpaper se colors

- App close animation: Smooth (default) ya Zoom to icon (Settings > Home screen settings). Smooth app open/close: icon se phailta surface (aur wapas icon mein simatta), folders bhi icon se phoolte hain

## GitHub se APK banane ke steps
1. GitHub pe naya repo banao.
2. Is poore folder ka content (`.github` folder samet) repo mein upload karo.
3. Repo ke **Actions** tab mein "Build APK" chalega (5-10 min).
4. Run pe click -> neeche **Artifacts** se `MyLauncher-debug-apk` download karo.
5. Zip kholo, `app-debug.apk` install karo.
6. Home button -> "My Launcher" -> "Always".
7. Music widget ke liye: Settings > Widgets > Notification access allow karo.
8. Home pe khaali jagah long-press -> edit mode -> neeche ke buttons se settings ke pages.

## Build fail ho to
Actions mein failed run kholo, error ka text copy karke Claude ko bhejo.

## Home screen editing system (cell-based layout)

Home ab ek persistent **cell grid** hai (4 columns x rows), purana "sab apps automatic flow" nahi.

- **Model / storage**: `HomeModel.kt` (`HomeEntry`, `HomeState`, `HomeOps`, `HomeStore`). Layout JSON ke roop mein alag SharedPreferences file `launcher_layout` mein save hota hai (pages, items ki page/x/y/span, folders + unke apps + naam, dock, widget ids, current page). Pehli baar purana order (`homeOrder`, auto folders, widgets, search) cells mein convert hota hai; naye install apps aakhir mein jode jaate hain, uninstall / hide hue apps layout se nikal jaate hain.
- **Grid**: `CellLayout.kt` (spans, drop preview, FLIP slide animation). Koi hardcoded coordinates nahi.
- **Views**: `HomeViewFactory.kt` (cached views), `HomePagerAdapter.kt` (page = clock header + CellLayout).
- **Drag & drop**: `DragController.kt` (home, dock, folder popup; edge pe 0.6 s ruko = page badlo / naya page; app pe app = naya folder, folder pe app = folder mein; push / reorder; drop pe save).
- **Widgets**: `WidgetHostManager.kt` (AppWidgetHost: bind permission dialog, configure activity, cached views), `ResizeFrame.kt` (4 handles + remove), `HomePanels.kt` (widget picker, page manager).
- **Edit mode**: khaali jagah / item long-press. Toolbar upar (Widgets, Pages, Wallpaper, Layout, Settings, Done); dock neeche dikhta rehta hai.
- **Reset**: Settings > Home layout reset ab poora arrangement default karta hai.

Build / device testing is environment mein nahi ho saka.

## 3D depth wallpaper, unlock transition, dynamic glass colors

- **Depth layers** (`DepthLayerView`): background wallpaper -> bada `GlassClockView` (+ date) -> transparent foreground subject; launcher UI (pager, dock) in sab ke upar. Background aur foreground ek hi centerCrop matrix se draw hote hain (aligned rehte hain); foreground ke liye alag x / y / scale adjust. Bitmaps HARDWARE config mein (GPU memory).
- **Editor** (`DepthEditorActivity`, Settings > Wallpaper > "3D depth wallpaper"): background aur foreground PNG alag-alag chuno / badlo / hatao, live preview, clock x / y / size, subject x / y / scale, date on/off, depth on/off. "Apply" par hi save hota hai.
- **Subject cutout** (`SubjectSegmenter`): classical on-device algorithm (AI model nahi): upar ke kinaron ke colors ko background maanke region grow, phir morphology + feather. Simple backgrounds (aasmaan, plain deewar) pe theek, busy pe galat ho sakta hai: brush se theek karo. Brush editor mask ko add / erase karta hai.
- **Storage** (`DepthStore`): sirf `filesDir/depth/` (bg.jpg, fg.png), import par sampled decode + validate + EXIF rotation, atomic write.
- **Unlock animation** (`MainActivity`): `ACTION_USER_PRESENT` (ek hi dynamic receiver) + keyguard-state fallback; onResume har baar unlock nahi maana jata; 2 s dedupe. Depth wallpaper pe zoom-out (+ API 31+ pe blur-to-clear, low-RAM pe blur nahi), clock ka halka overshoot scale-in, icons ka chhota stagger.
- **Dynamic colors**: `PaletteEngine` depth background (ya system wallpaper) se `ThemeColors`, ab `glassTint` bhi; `GlassTheme.tint` se saare `LiquidGlassDrawable` (cards, search pill, folder, panels) halka rangte hain.
- Weather: implement nahi kiya (project mein koi weather source nahi, aur naya permission / network nahi jodna tha).

Build / device testing is environment mein nahi ho saka.
