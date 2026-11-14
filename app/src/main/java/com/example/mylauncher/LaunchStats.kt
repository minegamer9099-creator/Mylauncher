package com.example.mylauncher

import android.content.Context

/**
 * Kaun sa app kitni baar khola gaya (launcher ke andar se). "Suggested" widget isse banta hai.
 * Alag file mein save hota hai taaki settings ka "badal gayi?" check na bigde.
 */
class LaunchStats(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("launcher_stats", Context.MODE_PRIVATE)

    fun record(packageName: String) {
        sp.edit().putInt(packageName, sp.getInt(packageName, 0) + 1).apply()
    }

    /** Sabse zyada khole gaye n apps; kam pade to baaki list ke pehle apps se bhar do. */
    fun top(candidates: List<AppInfo>, n: Int): List<AppInfo> {
        val ranked = candidates
            .filter { sp.getInt(it.packageName, 0) > 0 }
            .sortedByDescending { sp.getInt(it.packageName, 0) }
            .take(n)
            .toMutableList()
        for (app in candidates) {
            if (ranked.size >= n) break
            if (app !in ranked) ranked.add(app)
        }
        return ranked
    }
}
