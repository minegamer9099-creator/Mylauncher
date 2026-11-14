package com.example.mylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

/** Glass sticky note: tap karke likho, launcher ke andar save ho jata hai. */
class NotesWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private val store = context.getSharedPreferences("launcher_data", Context.MODE_PRIVATE)
    private var note: String = store.getString(KEY, "") ?: ""

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    }

    init {
        setOnClickListener { editNote() }
    }

    private fun editNote() {
        val input = EditText(context)
        input.setText(note)
        input.setSelection(input.text.length)
        input.hint = "Yahan likho..."
        AlertDialog.Builder(context)
            .setTitle("Quick note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                note = input.text.toString()
                store.edit().putString(KEY, note).apply()
                invalidate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        titlePaint.color = accent
        bodyPaint.color = primaryColor

        val pad = 16f * density
        canvas.drawText("NOTE", pad, pad + titlePaint.textSize, titlePaint)

        val text = if (note.isEmpty()) "Tap karke note likho" else note
        if (note.isEmpty()) bodyPaint.color = secondaryColor
        val textWidth = (width - 2 * pad).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, bodyPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(5)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(pad, pad + titlePaint.textSize + 10f * density)
        layout.draw(canvas)
        canvas.restore()
    }

    private companion object {
        const val KEY = "note"
    }
}
