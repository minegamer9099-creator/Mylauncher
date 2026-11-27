package com.example.mylauncher

import android.view.View
import android.view.animation.Interpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spring (physics) animations. Fixed-duration animations se alag: spring beech mein interrupt ho to
 * velocity bani rehti hai, isliye tap / drag / dobara tap pe bhi smooth + natural lagta hai.
 *
 * Damping ratio: 1 = bina bounce ke, 0.7 = halka bounce, 0.55 = bouncy, 0.4 = bahut bouncy.
 * Stiffness: bada = tez.
 */
object Springs {
    const val DAMPING_SMOOTH = 1f
    const val DAMPING_SOFT = 0.78f
    const val DAMPING_BOUNCY = 0.55f
    const val DAMPING_VERY_BOUNCY = 0.42f

    const val STIFF_LOW = 180f
    const val STIFF_SOFT = 320f
    const val STIFF_MEDIUM = 700f
    const val STIFF_HIGH = 1500f
}

private class SpringHolder(val anim: SpringAnimation) {
    var listener: DynamicAnimation.OnAnimationEndListener? = null
}

@Suppress("UNCHECKED_CAST")
private fun View.springMapOrNull(): HashMap<DynamicAnimation.ViewProperty, SpringHolder>? =
    getTag(R.id.tag_springs) as? HashMap<DynamicAnimation.ViewProperty, SpringHolder>

private fun View.springMap(): HashMap<DynamicAnimation.ViewProperty, SpringHolder> {
    springMapOrNull()?.let { return it }
    val map = HashMap<DynamicAnimation.ViewProperty, SpringHolder>()
    setTag(R.id.tag_springs, map)
    return map
}

/**
 * [property] ko spring se [target] tak le jao. Pehle se chal rahi ho to wahi spring naye target ki taraf
 * mud jati hai (velocity ke saath). onEnd tab chalta hai jab spring theek ho jaye (cancel hone pe nahi).
 * Sirf main thread se.
 */
fun View.spring(
    property: DynamicAnimation.ViewProperty,
    target: Float,
    stiffness: Float = Springs.STIFF_MEDIUM,
    damping: Float = Springs.DAMPING_BOUNCY,
    onEnd: (() -> Unit)? = null
) {
    val map = springMap()
    val holder = map.getOrPut(property) { SpringHolder(SpringAnimation(this, property, target)) }
    val anim = holder.anim
    // Purana end-callback hatao: sirf sabse naye call ka onEnd chale
    holder.listener?.let { anim.removeEndListener(it) }
    holder.listener = null
    // SpringForce ke setters 'this' return karte hain: Kotlin property-assignment syntax yahan nahi chalta
    anim.spring.setStiffness(stiffness)
    anim.spring.setDampingRatio(damping)
    if (onEnd != null) {
        val l = object : DynamicAnimation.OnAnimationEndListener {
            override fun onAnimationEnd(animation: DynamicAnimation<*>?, canceled: Boolean, value: Float, velocity: Float) {
                animation?.removeEndListener(this)
                if (holder.listener === this) holder.listener = null
                if (!canceled) onEnd()
            }
        }
        holder.listener = l
        anim.addEndListener(l)
    }
    anim.animateToFinalPosition(target)
}

fun View.springScale(
    target: Float,
    stiffness: Float = Springs.STIFF_MEDIUM,
    damping: Float = Springs.DAMPING_BOUNCY,
    onEnd: (() -> Unit)? = null
) {
    spring(DynamicAnimation.SCALE_X, target, stiffness, damping)
    spring(DynamicAnimation.SCALE_Y, target, stiffness, damping, onEnd)
}

/** Alpha pe bounce nahi hota (damping 1): sirf smooth fade. */
fun View.springAlpha(target: Float, stiffness: Float = Springs.STIFF_SOFT, onEnd: (() -> Unit)? = null) {
    spring(DynamicAnimation.ALPHA, target, stiffness, Springs.DAMPING_SMOOTH, onEnd)
}

fun View.springTranslationX(target: Float, stiffness: Float = Springs.STIFF_MEDIUM, damping: Float = Springs.DAMPING_BOUNCY, onEnd: (() -> Unit)? = null) {
    spring(DynamicAnimation.TRANSLATION_X, target, stiffness, damping, onEnd)
}

fun View.springTranslationY(target: Float, stiffness: Float = Springs.STIFF_MEDIUM, damping: Float = Springs.DAMPING_BOUNCY, onEnd: (() -> Unit)? = null) {
    spring(DynamicAnimation.TRANSLATION_Y, target, stiffness, damping, onEnd)
}

/** Sirf ek property ka spring rok do. */
fun View.cancelSpring(property: DynamicAnimation.ViewProperty) {
    val holder = springMapOrNull()?.get(property) ?: return
    holder.listener?.let { holder.anim.removeEndListener(it) }
    holder.listener = null
    if (holder.anim.isRunning) holder.anim.cancel()
}

/** Is view ke saare chal rahe springs turant rok do (view jahan hai wahin rukta hai). */
fun View.cancelSprings() {
    val map = springMapOrNull() ?: return
    for (holder in map.values) {
        holder.listener?.let { holder.anim.removeEndListener(it) }
        holder.listener = null
        if (holder.anim.isRunning) holder.anim.cancel()
    }
}

/**
 * Damped-spring ki shakal ka interpolator (ValueAnimator ke saath): 0 se shuru, thoda overshoot, phir 1 pe settle.
 * Fixed duration chahiye (jaise LaunchOverlayView) to spring ki jagah ye use hota hai.
 * damping < 1 = bounce (chhota = zyada), omega = kitni tezi se settle.
 */
class SpringInterpolator(
    damping: Float = 0.78f,
    private val omega: Float = 22f
) : Interpolator {
    private val zeta = damping.coerceIn(0.05f, 0.99f)
    private val wd = omega * sqrt(1f - zeta * zeta)
    private val k = zeta * omega / wd

    override fun getInterpolation(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val decay = exp(-zeta * omega * t)
        return 1f - decay * (cos(wd * t) + k * sin(wd * t))
    }
}
