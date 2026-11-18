package com.example.mylauncher

import android.service.notification.NotificationListenerService

/**
 * Khaali NotificationListenerService.
 * Android media sessions (Spotify, YouTube Music, etc.) ko padhne ke liye
 * app ke paas "Notification access" hona zaroori hai, aur uske liye ye service chahiye.
 * Settings > Notification access mein is app ko allow karna hoga.
 */
class MediaListener : NotificationListenerService()
