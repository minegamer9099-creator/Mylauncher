package com.example.mylauncher

/** Home screen grid ka ek item: app, folder, ya search widget. */
sealed class HomeItem {

    /** Order save karne ke liye unique naam. */
    abstract val key: String

    data class App(val app: AppInfo) : HomeItem() {
        override val key: String get() = "app:" + app.packageName
    }

    data class Folder(val name: String, val apps: List<AppInfo>) : HomeItem() {
        override val key: String get() = "folder:" + name
    }

    /** Search bar (widget jaisa): poori row leta hai aur drag karke kahin bhi rakh sakte ho. */
    object Search : HomeItem() {
        override val key: String get() = "search"
    }
}
