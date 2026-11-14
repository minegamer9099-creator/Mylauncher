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
