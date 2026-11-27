package com.example.mylauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.Toast

/** Glass card ke andar 4 quick buttons: Torch, Wi-Fi, Bluetooth, Settings. */
class QuickActionsWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private val labels = listOf("Torch", "Wi-Fi", "Bluetooth", "Settings")
    private val chip = RectF()
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }

    private var torchOn = false
    private var torchId: String? = null
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private var downIndex = -1

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                invalidate()
            }
        }
    }

    init {
        torchId = findTorchId()
    }

    private fun findTorchId(): String? {
        val manager = cameraManager ?: return null
        return try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun chipBounds(index: Int, out: RectF) {
        val pad = 12f * density
        val gap = 8f * density
        val w = (width - 2 * pad - gap) / 2f
        val h = (height - 2 * pad - gap) / 2f
        val col = index % 2
        val row = index / 2
        val l = pad + col * (w + gap)
        val t = pad + row * (h + gap)
        out.set(l, t, l + w, t + h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in labels.indices) {
            chipBounds(i, chip)
            val active = i == 0 && torchOn
            chipPaint.color = when {
                active -> accent
                lightStyle -> Color.parseColor("#1A000000")
                else -> Color.parseColor("#26FFFFFF")
            }
            val radius = chip.height() * 0.35f
            canvas.drawRoundRect(chip, radius, radius, chipPaint)
            textPaint.color = if (active) Color.WHITE else primaryColor
            val baseline = chip.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(labels[i], chip.centerX(), baseline, textPaint)
        }
    }

    private fun hit(x: Float, y: Float): Int {
        for (i in labels.indices) {
            chipBounds(i, chip)
            if (chip.contains(x, y)) return i
        }
        return -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        handleGlow(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downIndex = hit(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val index = hit(event.x, event.y)
                if (index >= 0 && index == downIndex) perform(index)
                downIndex = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun perform(index: Int) {
        when (index) {
            0 -> toggleTorch()
            1 -> open(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
            )
            2 -> open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            3 -> open(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun toggleTorch() {
        val id = torchId
        val manager = cameraManager
        if (id == null || manager == null) {
            Toast.makeText(context, "Is phone mein torch nahi mili", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            manager.setTorchMode(id, !torchOn)
        } catch (e: CameraAccessException) {
            Toast.makeText(context, "Torch abhi use nahi ho sakti", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, "Torch abhi use nahi ho sakti", Toast.LENGTH_SHORT).show()
        }
    }

    private fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Ye option is phone pe available nahi hai", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cameraManager?.unregisterTorchCallback(torchCallback)
    }
}
