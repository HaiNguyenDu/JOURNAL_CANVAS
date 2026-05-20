package com.example.journal_canvas.presentation.editor.canvas.viewport

import android.graphics.RectF
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import com.example.journal_canvas.util.BitmapMemoryCache
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object CanvasTransformUtils {
    fun screenToCanvasX(screenX: Float, canvasOffsetX: Float, canvasScale: Float): Float {
        return (screenX - canvasOffsetX) / canvasScale
    }

    fun screenToCanvasY(screenY: Float, canvasOffsetY: Float, canvasScale: Float): Float {
        return (screenY - canvasOffsetY) / canvasScale
    }

    fun setViewportCanvasBounds(
        viewWidth: Int,
        viewHeight: Int,
        canvasOffsetX: Float,
        canvasOffsetY: Float,
        canvasScale: Float,
        paddingScreenPx: Float,
        out: RectF,
    ) {
        val safeScale = canvasScale.coerceAtLeast(0.001f)
        out.set(
            screenToCanvasX(-paddingScreenPx, canvasOffsetX, safeScale),
            screenToCanvasY(-paddingScreenPx, canvasOffsetY, safeScale),
            screenToCanvasX(viewWidth + paddingScreenPx, canvasOffsetX, safeScale),
            screenToCanvasY(viewHeight + paddingScreenPx, canvasOffsetY, safeScale),
        )
        out.sort()
    }

    fun setTransformedObjectBounds(obj: CanvasObjectUiModel, out: RectF) {
        setTransformedObjectBounds(
            x = obj.x,
            y = obj.y,
            width = obj.width,
            height = obj.height,
            scale = obj.scale,
            rotation = obj.rotation,
            out = out,
        )
    }

    fun setTransformedObjectBounds(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        scale: Float,
        rotation: Float,
        out: RectF,
    ) {
        val safeWidth = width.coerceAtLeast(0f)
        val safeHeight = height.coerceAtLeast(0f)
        val radians = Math.toRadians(rotation.toDouble())
        val cosR = cos(radians).toFloat()
        val sinR = sin(radians).toFloat()

        val x0 = x
        val y0 = y
        val x1 = x + safeWidth * scale * cosR
        val y1 = y + safeWidth * scale * sinR
        val x2 = x - safeHeight * scale * sinR
        val y2 = y + safeHeight * scale * cosR
        val x3 = x1 + x2 - x
        val y3 = y1 + y2 - y

        out.set(
            min(min(x0, x1), min(x2, x3)),
            min(min(y0, y1), min(y2, y3)),
            max(max(x0, x1), max(x2, x3)),
            max(max(y0, y1), max(y2, y3)),
        )
    }

    fun intersects(a: RectF, b: RectF): Boolean {
        return a.left <= b.right &&
            a.right >= b.left &&
            a.top <= b.bottom &&
            a.bottom >= b.top
    }

    fun estimateImageTargetMaxDimensionPx(
        obj: CanvasObjectUiModel.Image,
        canvasScale: Float,
    ): Int {
        val screenWidth = obj.width * obj.scale * canvasScale
        val screenHeight = obj.height * obj.scale * canvasScale
        return ceil(max(screenWidth, screenHeight).toDouble()).toInt().coerceIn(
            BitmapMemoryCache.MIN_TARGET_DIMENSION_PX,
            BitmapMemoryCache.MAX_TARGET_DIMENSION_PX,
        )
    }
}
