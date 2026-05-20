package com.example.journal_canvas.presentation.editor.canvas.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.journal_canvas.R
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel

class SelectionRenderer(context: Context) {
    private val icPoint: Drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_point))
    private val icRotate: Drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_rotate))

    private val handleSizePx: Float = HANDLE_SIZE_DP * context.resources.displayMetrics.density
    private val halfHandlePx: Float = handleSizePx / 2f

    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val selectionBounds = RectF()

    fun draw(
        canvas: Canvas,
        obj: CanvasObjectUiModel,
        hideHandles: Boolean,
    ) {
        canvas.save()
        canvas.translate(obj.x, obj.y)
        canvas.rotate(obj.rotation)
        canvas.scale(obj.scale, obj.scale)

        selectionBounds.set(0f, 0f, obj.width, obj.height)
        canvas.drawRect(selectionBounds, selectionBorderPaint)

        if (!hideHandles) {
            val h = halfHandlePx / obj.scale
            drawHandleIcon(canvas, icPoint, 0f, 0f, h)
            drawHandleIcon(canvas, icRotate, obj.width, 0f, h)
            drawHandleIcon(canvas, icPoint, 0f, obj.height, h)
            drawHandleIcon(canvas, icPoint, obj.width, obj.height, h)

            if (obj is CanvasObjectUiModel.Text) {
                val midY = obj.height / 2f
                drawHandleIcon(canvas, icPoint, 0f, midY, h)
                drawHandleIcon(canvas, icPoint, obj.width, midY, h)
            }
        }

        canvas.restore()
    }

    private fun drawHandleIcon(
        canvas: Canvas,
        drawable: Drawable,
        centerX: Float,
        centerY: Float,
        halfSize: Float,
    ) {
        val left = (centerX - halfSize).toInt()
        val top = (centerY - halfSize).toInt()
        val right = (centerX + halfSize).toInt()
        val bottom = (centerY + halfSize).toInt()
        drawable.setBounds(left, top, right, bottom)
        drawable.draw(canvas)
    }

    companion object {
        const val HANDLE_SIZE_DP = 12f
    }
}
