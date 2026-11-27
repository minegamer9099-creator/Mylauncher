package com.example.mylauncher

import android.content.pm.ApplicationInfo

/** Drawer list ka ek item: ya to section header ya ek app. */
sealed class DrawerItem {
    data class Header(val title: String) : DrawerItem()
    data class App(val app: AppInfo) : DrawerItem()
}

/** Android category -> Categories tab ka section naam. */
fun categoryName(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_AUDIO,
    ApplicationInfo.CATEGORY_VIDEO,
    ApplicationInfo.CATEGORY_IMAGE -> "Media"
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_MAPS -> "Maps & Travel"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    else -> "Others"
}
