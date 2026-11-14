package com.example.mylauncher

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Home screen pages ke liye 3D curved depth + zoom-out transition.
 *
 * Effect (position: 0 = current page, -1 = left page, +1 = right page):
 *  - Scale    : swipe karte waqt page 100% -> 88% tak depth mein jata hai
 *  - Alpha    : page halka fade hota hai (100% -> 45%)
 *  - RotationY: pages halka sa mudte hain (max 12 degree) -> curved look
 *  - Parallax : page apni normal speed se thoda dheema chalta hai -> depth ka ehsaas
 *
 * Performance (120Hz ke liye):
 *  - transformPage() mein koi object allocation, layout ya findViewById nahi hai;
 *    sirf GPU-composited properties (scale/alpha/rotation/translation) badalti hain.
 *  - Screen ke bahar wale pages (|position| >= 1) alpha 0 pe set hote hain, to draw skip hota hai.
 *  - attach() swipe ke dauran pages ko hardware layer pe daalta hai (GPU texture cache),
 *    aur idle hote hi hata deta hai taaki memory na khaye.
 *
 * Use:
 *   val pager = findViewById<ViewPager2>(R.id.homePager)
 *   pager.adapter = HomePagerAdapter(...)
 *   DepthCurveTransformer.attach(pager)
 *   DepthCurveTransformer.requestHighRefreshRate(this)   // optional
 */
class DepthCurveTransformer(
    /** true = pages andar ki taraf mudte hain (concave), false = bahar ki taraf (convex). */
    private val concave: Boolean = true
) : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val a = abs(position)

        // Poori tarah screen ke bahar: draw hi mat karo
        if (a >= 1f) {
            page.alpha = 0f
            return
        }

        // Smoothstep easing: shuru aur ant mein dheema, beech mein tez -> "soft" feel
        val e = a * a * (3f - 2f * a)

        val scale = 1f - MAX_SCALE_DOWN * e
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - MAX_FADE * e

        // Pivot us edge pe jo screen ke center ki taraf hai -> door/curve jaisa mudna
        page.pivotX = if (position < 0f) page.width.toFloat() else 0f
        page.pivotY = page.height * 0.5f

        val direction = if (concave) -1f else 1f
        page.rotationY = direction * position * MAX_ROTATION

        // Parallax: page ko thoda peeche khincho -> depth
        page.translationX = -position * page.width * PARALLAX
    }

    companion object {
        private const val MAX_SCALE_DOWN = 0.12f   // 0.88x tak chhota
        private const val MAX_FADE = 0.55f         // alpha 0.45 tak
        private const val MAX_ROTATION = 12f       // degrees
        private const val PARALLAX = 0.10f         // page width ka 10%
        private const val CAMERA_DISTANCE_DP = 8000f // bada = halka perspective (subtle)

        /**
         * Transformer lagata hai aur smoothness ke liye ViewPager2 ko tune karta hai.
         * Adapter set karne ke BAAD call karo.
         */
        fun attach(pager: ViewPager2, concave: Boolean = true) {
            pager.setPageTransformer(DepthCurveTransformer(concave))
            pager.offscreenPageLimit = 1 // sirf aas-paas ke pages ready rakho

            val rv = pager.getChildAt(0) as RecyclerView
            rv.overScrollMode = View.OVER_SCROLL_NEVER // edge glow band, transform saaf dikhe

            // cameraDistance ek baar set karo (har frame nahi, warna extra invalidate hota hai)
            val cameraDistancePx = CAMERA_DISTANCE_DP * pager.resources.displayMetrics.density
            for (i in 0 until rv.childCount) rv.getChildAt(i).cameraDistance = cameraDistancePx

            rv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    view.cameraDistance = cameraDistancePx
                    // Naya page swipe ke beech mein aaye to usse bhi layer do
                    if (pager.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
                        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    }
                }
                override fun onChildViewDetachedFromWindow(view: View) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })

            // Swipe ke dauran hardware layer ON, idle hote hi OFF
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    val type = if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        View.LAYER_TYPE_NONE
                    } else {
                        View.LAYER_TYPE_HARDWARE
                    }
                    for (i in 0 until rv.childCount) rv.getChildAt(i).setLayerType(type, null)
                }
            })
        }

        /** Settings se 3D transition on/off. */
        fun setEnabled(pager: ViewPager2, enabled: Boolean, concave: Boolean = true) {
            pager.setPageTransformer(if (enabled) DepthCurveTransformer(concave) else null)
        }

        /**
         * Window ko 120Hz ka hint deta hai. Ye sirf request hai: final refresh rate
         * device, panel (LTPO), battery saver aur system policy decide karte hain.
         */
        fun requestHighRefreshRate(activity: Activity) {
            val attrs = activity.window.attributes
            attrs.preferredRefreshRate = 120f
            activity.window.attributes = attrs
        }
    }
}
