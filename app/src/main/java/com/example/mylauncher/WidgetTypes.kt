package com.example.mylauncher

import android.content.Context
import android.view.View

/** Saare home widgets: naam, description aur banane ka tareeka. */
object WidgetTypes {

    val ALL: List<String> = listOf("clock", "battery", "suggested", "music", "date", "storage", "notes", "quick")

    fun title(type: String): String = when (type) {
        "clock" -> "Analog clock"
        "battery" -> "Battery"
        "suggested" -> "Suggested apps"
        "music" -> "Music player"
        "date" -> "Date card"
        "storage" -> "Storage"
        "notes" -> "Quick note"
        "quick" -> "Quick actions"
        else -> type
    }

    fun description(type: String): String = when (type) {
        "clock" -> "Glass card mein analog clock aur date"
        "battery" -> "Battery percent ring"
        "suggested" -> "Sabse zyada use hone wale 4 apps"
        "music" -> "Chal rahe gaane ke controls (notification access chahiye)"
        "date" -> "Bada date number aur din"
        "storage" -> "Phone ki storage kitni bhari hai"
        "notes" -> "Tap karke chhota note likho"
        "quick" -> "Torch, Wi-Fi, Bluetooth, Settings"
        else -> ""
    }

    fun create(context: Context, type: String): View = when (type) {
        "clock" -> AnalogClockWidgetView(context)
        "battery" -> BatteryWidgetView(context)
        "suggested" -> SuggestedAppsWidgetView(context)
        "music" -> MusicWidgetView(context)
        "storage" -> StorageWidgetView(context)
        "notes" -> NotesWidgetView(context)
        "quick" -> QuickActionsWidgetView(context)
        else -> DateWidgetView(context)
    }
}
