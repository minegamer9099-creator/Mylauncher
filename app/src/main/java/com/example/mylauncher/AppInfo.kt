package com.example.mylauncher

import android.graphics.drawable.Drawable

/**
 * Ek installed app ki zaroori info.
 * category = Android ki app category (Categories tab ke liye), -1 = undefined
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val category: Int = -1
)
