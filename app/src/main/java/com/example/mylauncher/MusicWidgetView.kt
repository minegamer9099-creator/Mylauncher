package com.example.mylauncher

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.MotionEvent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView

/**
 * Glass music card: jo bhi music chal raha hai uska naam + prev/play/next.
 * Iske liye "Notification access" allow karna padta hai (settings mein button hai).
 */
class MusicWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val title: TextView
    private val artist: TextView
    private val play: ImageButton

    private var controller: MediaController? = null
    private var sessionManager: MediaSessionManager? = null

    var glassStrength: Float = 0.5f
        set(value) {
            field = value
            applyGlass()
        }

    /** Doosre widgets ke saath match karne ke liye (abhi use nahi hota). */
    var accent: Int = 0

    /** Light glass: halka frosted card + gehre rang ka text. */
    var lightStyle: Boolean = false
        set(value) {
            field = value
            applyGlass()
            applyColors()
        }

    var touchGlow: Boolean = true
    private var glowAnimator: ValueAnimator? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateUi()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateUi()
        }
    }

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> bindController(list) }

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_music, this, true)
        title = findViewById(R.id.musicTitle)
        artist = findViewById(R.id.musicArtist)
        play = findViewById(R.id.musicPlay)
        applyGlass()
        clipToOutline = true

        findViewById<ImageButton>(R.id.musicPrev).setOnClickListener {
            controller?.transportControls?.skipToPrevious()
        }
        findViewById<ImageButton>(R.id.musicNext).setOnClickListener {
            controller?.transportControls?.skipToNext()
        }
        play.setOnClickListener {
            val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) controller?.transportControls?.pause()
            else controller?.transportControls?.play()
        }
    }

    private fun applyGlass() {
        background = LiquidGlassDrawable(24f * density, if (lightStyle) 0.55f else glassStrength)
    }

    private fun applyColors() {
        val primary = if (lightStyle) Color.parseColor("#1F2328") else Color.WHITE
        val secondary = if (lightStyle) Color.parseColor("#8A1F2328") else Color.parseColor("#B3FFFFFF")
        title.setTextColor(primary)
        artist.setTextColor(secondary)
        val tint = ColorStateList.valueOf(primary)
        findViewById<ImageButton>(R.id.musicPrev).imageTintList = tint
        findViewById<ImageButton>(R.id.musicNext).imageTintList = tint
        play.imageTintList = tint
    }

    /** Buttons touch consume karte hain, isliye glow dispatch level pe dekhte hain. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> animateGlow(1f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animateGlow(0f)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun animateGlow(target: Float) {
        val drawable = background as? LiquidGlassDrawable ?: return
        glowAnimator?.cancel()
        if (!touchGlow && target > 0f) return
        val animator = ValueAnimator.ofFloat(drawable.glow, target)
        animator.setDuration(if (target > 0f) 120L else 320L)
        animator.addUpdateListener { anim -> drawable.glow = anim.animatedValue as Float }
        animator.start()
        glowAnimator = animator
    }

    // ---------------------------------------------------------------------------------------
    private fun connect() {
        try {
            val msm = context.getSystemService(MediaSessionManager::class.java)
            val listenerComponent = ComponentName(context, MediaListener::class.java)
            sessionManager = msm
            msm.addOnActiveSessionsChangedListener(sessionListener, listenerComponent)
            bindController(msm.getActiveSessions(listenerComponent))
            setOnClickListener(null)
            isClickable = false
        } catch (e: SecurityException) {
            // Notification access abhi allow nahi hua
            title.text = "Music controls ke liye access allow karo"
            artist.text = "Yahan tap karo"
            setOnClickListener {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun disconnect() {
        controller?.unregisterCallback(callback)
        controller = null
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        sessionManager = null
    }

    private fun bindController(list: List<MediaController>?) {
        controller?.unregisterCallback(callback)
        controller = list?.firstOrNull()
        controller?.registerCallback(callback)
        updateUi()
    }

    private fun updateUi() {
        val c = controller
        title.text = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Kuch nahi chal raha"
        artist.text = c?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        play.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        connect()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disconnect()
    }

    /** Settings se wapas aane par (access allow karne ke baad) dobara jodo. */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && isAttachedToWindow) {
            disconnect()
            connect()
        }
    }
}
