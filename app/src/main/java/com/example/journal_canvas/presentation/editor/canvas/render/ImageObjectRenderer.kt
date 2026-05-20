package com.example.journal_canvas.presentation.editor.canvas.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasTransformUtils
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import com.example.journal_canvas.util.BitmapMemoryCache

class ImageObjectRenderer {
    var bitmapCache: BitmapMemoryCache? = null

    private val imagePlaceholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0DDD5.toInt()
        style = Paint.Style.FILL
    }
    private val bitmapSrcRect = Rect()
    private val bitmapDstRect = RectF()
    private val placeholderBounds = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, obj: CanvasObjectUiModel.Image, canvasScale: Float) {
        val targetMaxDimensionPx = CanvasTransformUtils.estimateImageTargetMaxDimensionPx(
            obj = obj,
            canvasScale = canvasScale,
        )
        val bitmap = bitmapCache?.get(obj.imageUri, targetMaxDimensionPx)
        if (bitmap != null && !bitmap.isRecycled) {
            bitmapSrcRect.set(0, 0, bitmap.width, bitmap.height)
            bitmapDstRect.set(0f, 0f, obj.width, obj.height)
            canvas.drawBitmap(bitmap, bitmapSrcRect, bitmapDstRect, bitmapPaint)
        } else {
            placeholderBounds.set(0f, 0f, obj.width, obj.height)
            canvas.drawRect(placeholderBounds, imagePlaceholderPaint)
        }
    }
}
