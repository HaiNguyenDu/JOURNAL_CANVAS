package com.example.journal_canvas.presentation.editor.canvas.viewport

import android.graphics.RectF
import kotlin.math.min

object CanvasObjectPageMath {

    fun clampPositionToPage(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        scale: Float,
        rotation: Float,
        outBounds: RectF,
        outPosition: FloatArray,
    ) {
        CanvasTransformUtils.setTransformedObjectBounds(
            x = x,
            y = y,
            width = width,
            height = height,
            scale = scale,
            rotation = rotation,
            out = outBounds,
        )

        val boundsWidth = outBounds.width().coerceAtLeast(0f)
        val boundsHeight = outBounds.height().coerceAtLeast(0f)
        if (boundsWidth <= 0f || boundsHeight <= 0f) {
            outPosition[0] = x
            outPosition[1] = y
            return
        }

        val minVisibleX = min(
            min(PageConfig.MIN_VISIBLE_OBJECT_PAGE_SIZE, boundsWidth),
            PageConfig.PAGE_WIDTH,
        )
        val minVisibleY = min(
            min(PageConfig.MIN_VISIBLE_OBJECT_PAGE_SIZE, boundsHeight),
            PageConfig.PAGE_HEIGHT,
        )
        val dx = calculateAxisShift(
            start = outBounds.left,
            end = outBounds.right,
            pageSize = PageConfig.PAGE_WIDTH,
            minVisible = minVisibleX,
        )
        val dy = calculateAxisShift(
            start = outBounds.top,
            end = outBounds.bottom,
            pageSize = PageConfig.PAGE_HEIGHT,
            minVisible = minVisibleY,
        )

        outPosition[0] = x + dx
        outPosition[1] = y + dy
    }

    private fun calculateAxisShift(
        start: Float,
        end: Float,
        pageSize: Float,
        minVisible: Float,
    ): Float {
        return when {
            end < minVisible -> minVisible - end
            start > pageSize - minVisible -> pageSize - minVisible - start
            else -> 0f
        }
    }
}
