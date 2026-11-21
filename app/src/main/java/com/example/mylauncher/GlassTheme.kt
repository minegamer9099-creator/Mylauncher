package com.example.mylauncher

import android.graphics.Color

/**
 * Wallpaper se nikla halka glass tint. [LiquidGlassDrawable] banate waqt yahi tint leta hai, isliye cards, search bar,
 * folder, settings panels sab ek hi jagah se wallpaper ke hisaab se halke rangte hain (glass ka design wahi rehta hai).
 */
object GlassTheme {
    @Volatile
    var tint: Int = Color.WHITE
}
