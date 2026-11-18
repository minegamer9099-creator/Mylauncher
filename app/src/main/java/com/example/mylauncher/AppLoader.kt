package com.example.mylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.MediaStore

/** Ek baar ki loading ka nateeja: saare apps (hidden bhi), dock ke default apps, aur kaun sa icon theme laga tha. */
class LoadedApps(
    val apps: List<AppInfo>,
    val dockPackages: List<String>,
    val darkIcons: Boolean
)

/**
 * Installed apps ki list banata hai. SIRF background thread pe chalao (labels + icons load karna bhari hai).
 */
object AppLoader {

    fun load(context: Context, prefs: Prefs): LoadedApps {
        val app = context.applicationContext
        val pm = app.packageManager
        val renderer = IconRenderer(app, prefs)

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = pm.queryIntentActivities(intent, 0)

        val result = ArrayList<AppInfo>(infos.size)
        val seen = HashSet<String>()
        for (info in infos) {
            val pkg = info.activityInfo.packageName
            if (pkg == app.packageName) continue // khud ko list se hatao
            if (!seen.add(pkg)) continue          // ek package ke ek se zyada launcher activity: ek hi icon (launch to waise bhi ek hi hota hai)
            val raw: Drawable = try {
                info.loadIcon(pm)
            } catch (e: Exception) {
                continue
            }
            result.add(
                AppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = renderer.render(raw),
                    category = info.activityInfo.applicationInfo.category
                )
            )
        }
        result.sortBy { it.labelLower }
        return LoadedApps(result, resolveDockPackages(pm), renderer.dark)
    }

    /** Phone, Messages, Browser, Camera ke default apps (package names). Ek baar load ke saath nikalte hain. */
    private fun resolveDockPackages(pm: PackageManager): List<String> {
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val result = ArrayList<String>(4)
        for (probe in probes) {
            val pkg = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            if (pkg != null && pkg != "android" && pkg !in result) result.add(pkg)
        }
        return result
    }
}

/**
 * Process-level cache: Activity dobara bane (rotation, theme change) to apps phir se load nahi karne padte.
 * Sirf chhote bitmap icons rakhta hai, koi Activity / View reference nahi.
 */
object AppCache {
    @Volatile
    private var cached: LoadedApps? = null

    fun get(): LoadedApps? = cached

    fun set(value: LoadedApps) {
        cached = value
    }

    fun clear() {
        cached = null
    }
}
