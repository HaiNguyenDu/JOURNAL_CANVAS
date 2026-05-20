package com.example.journal_canvas.presentation.editor.canvas.render

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.journal_canvas.presentation.editor.CanvasUiState
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import kotlin.math.abs
import kotlin.math.max

class TextObjectRenderer {
    var onTextMeasured: ((id: String, height: Float) -> Unit)? = null

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textLayoutCache = mutableMapOf<String, TextLayoutCacheEntry>()

    fun draw(canvas: Canvas, obj: CanvasObjectUiModel.Text, state: CanvasUiState) {
        val widthInt = obj.width.toInt().coerceAtLeast(1)
        val layout = getOrBuildLayout(obj, widthInt)
        layout.draw(canvas)

        val measuredHeight = max(layout.height.toFloat(), obj.textSize * MIN_LINE_HEIGHT_RATIO)
        if (abs(obj.height - measuredHeight) > HEIGHT_EPSILON) {
            onTextMeasured?.invoke(obj.id, measuredHeight)
        }

        if (obj.id == state.editingTextObjectId && state.cursorVisible) {
            drawCursor(canvas, obj, layout, state.canvasScale)
        }
    }

    private fun drawCursor(
        canvas: Canvas,
        obj: CanvasObjectUiModel.Text,
        layout: StaticLayout,
        canvasScale: Float,
    ) {
        val charCount = obj.text.length
        val line = layout.getLineForOffset(charCount)
        val cursorX = layout.getPrimaryHorizontal(charCount)
        val top = layout.getLineTop(line).toFloat()
        val rawBottom = layout.getLineBottom(line).toFloat()
        val bottom = if (rawBottom > top) rawBottom else top + obj.textSize * MIN_LINE_HEIGHT_RATIO

        cursorPaint.color = obj.color
        cursorPaint.strokeWidth = CURSOR_WIDTH_PX / max(canvasScale * obj.scale, 0.01f)
        canvas.drawLine(cursorX, top, cursorX, bottom, cursorPaint)
    }

    private fun getOrBuildLayout(obj: CanvasObjectUiModel.Text, widthInt: Int): StaticLayout {
        val cached = textLayoutCache[obj.id]
        if (cached != null &&
            cached.text == obj.text &&
            cached.textSize == obj.textSize &&
            cached.width == widthInt &&
            cached.color == obj.color
        ) {
            return cached.layout
        }
        textPaint.color = obj.color
        textPaint.textSize = obj.textSize
        val layout = StaticLayout.Builder
            .obtain(obj.text, 0, obj.text.length, textPaint, widthInt)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()
        textLayoutCache[obj.id] = TextLayoutCacheEntry(
            text = obj.text,
            textSize = obj.textSize,
            width = widthInt,
            color = obj.color,
            layout = layout,
        )
        return layout
    }

    private data class TextLayoutCacheEntry(
        val text: String,
        val textSize: Float,
        val width: Int,
        val color: Int,
        val layout: StaticLayout,
    )

    private companion object {
        const val MIN_LINE_HEIGHT_RATIO = 1.2f
        const val HEIGHT_EPSILON = 1f
        const val CURSOR_WIDTH_PX = 2f
    }
}
