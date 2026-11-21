package com.example.mylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * Drawer ke right edge pe A-Z (aur #) ki vertical list.
 * Ungli rakho ya upar-neeche ghumao -> onLetterSelected ko letter milta hai.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val letters: List<Char> = ('A'..'Z').toList() + '#'
    private var activeIndex = -1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 9f, resources.displayMetrics
        )
        color = Color.WHITE
    }

    /** Letters ka color (wallpaper palette se set hota hai). */
    var tint: Int = Color.WHITE
        set(value) {
            if (field == value) return
            field = value
            paint.color = value
            invalidate()
        }

    var onLetterSelected: ((Char) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellHeight = height.toFloat() / letters.size
        val centerX = width / 2f
        for (i in letters.indices) {
            paint.alpha = if (i == activeIndex) 255 else 150
            val baseline = cellHeight * i + cellHeight / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(letters[i].toString(), centerX, baseline, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val index = (event.y / height * letters.size).toInt()
                    .coerceIn(0, letters.size - 1)
                if (index != activeIndex) {
                    activeIndex = index
                    invalidate()
                    onLetterSelected?.invoke(letters[index])
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
