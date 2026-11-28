package com.example.mylauncher

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * Window ko panel ka sabse tez display mode maango (jaise 120Hz), usi resolution pe.
 *
 * Sirf `preferredRefreshRate` kaafi nahi hota: kai phones (ColorOS / OneUI / HyperOS) ise ignore karke 60Hz pe
 * rakhte hain. `preferredDisplayModeId` ek exact mode maangta hai, jise zyada honor kiya jata hai.
 * Ye phir bhi sirf request hai: system "Smooth display" setting, battery saver ya thermal policy final decide karte hain.
 *
 * onResume / window-focus pe dobara lagta hai, kyunki app se wapas aane par system mode badal sakta hai.
 */
fun Activity.requestHighRefreshRate() {
    try {
        @Suppress("DEPRECATION")
        val disp: Display = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        val current = disp.mode
        var best = current
        for (m in disp.supportedModes) {
            if (m.physicalWidth == current.physicalWidth &&
                m.physicalHeight == current.physicalHeight &&
                m.refreshRate > best.refreshRate
            ) {
                best = m
            }
        }
        val lp = window.attributes
        if (lp.preferredDisplayModeId != best.modeId || lp.preferredRefreshRate != best.refreshRate) {
            lp.preferredDisplayModeId = best.modeId
            lp.preferredRefreshRate = best.refreshRate
            window.attributes = lp
        }
    } catch (e: Exception) {
        // Kuch devices pe Display / modes fail ho sakte hain: crash nahi, default rate chalta rahega
    }
}
