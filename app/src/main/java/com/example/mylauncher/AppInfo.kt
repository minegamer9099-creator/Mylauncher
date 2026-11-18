package com.example.mylauncher

import android.graphics.drawable.Drawable

/**
 * Ek installed app ki zaroori info.
 * icon = pehle se chhote bitmap mein badla hua (aur theme laga hua) icon, category = Android category (-1 = undefined).
 * labelLower: search / sort ke liye ek baar bana hua lowercase naam (har keystroke pe dobara nahi banta).
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val category: Int = -1
) {
    val labelLower: String = label.lowercase()
}
