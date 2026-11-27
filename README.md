# My Launcher (ColorOS-style)

## Features
- Home pages (swipe) + 3D page transition, page indicator, wallpaper parallax
- Clock: ColorOS compact (left clock + right date) ya bada centered glass clock
- Liquid glass widgets (light/dark auto, ): Analog clock, Battery, Suggested apps,
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

## Spring animations, unlock transition, dynamic glass colors

- **Spring physics** (`Springs.kt`, `androidx.dynamicanimation`): press effect, drawer, folder, edit mode, bottom sheet, page dots, drag ghost aur grid reorder spring se chalte hain (interrupt hone par velocity bani rehti hai, release pe halka bounce). App-open overlay ek spring-shape interpolator use karta hai.
- **Unlock animation** (`MainActivity`): `ACTION_USER_PRESENT` + keyguard-state fallback; 2 s dedupe. Clock bouncy spring se aata hai, icons chhote stagger ke saath pop hote hain.
- **Dynamic colors**: `PaletteEngine` system wallpaper se `ThemeColors` (+ `glassTint`); `GlassTheme.tint` se saare `LiquidGlassDrawable` halka rangte hain.
- **Hata diya (lag ki wajah se)**: 3D depth wallpaper (layers + editor), saare blur effects (drawer / folder / app-launch), touch glow.
