package com.example.journal_canvas.presentation.editor.canvas.gesture

import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import kotlin.math.cos
import kotlin.math.sin

class SelectionHandleHitTester {
    private val point = FloatArray(2)

    fun findHandleAt(
        screenX: Float,
        screenY: Float,
        obj: CanvasObjectUiModel,
        canvasScale: Float,
        canvasOffsetX: Float,
        canvasOffsetY: Float,
        handleTouchRadius: Float,
    ): SelectionHandle? {
        if (obj is CanvasObjectUiModel.Text) {
            val midY = obj.height / 2f
            getHandleScreenPos(obj, 0f, midY, canvasScale, canvasOffsetX, canvasOffsetY, point)
            if (isNear(screenX, screenY, point, handleTouchRadius)) return SelectionHandle.LEFT_EDGE
            getHandleScreenPos(obj, obj.width, midY, canvasScale, canvasOffsetX, canvasOffsetY, point)
            if (isNear(screenX, screenY, point, handleTouchRadius)) return SelectionHandle.RIGHT_EDGE
        }

        getHandleScreenPos(obj, obj.width, 0f, canvasScale, canvasOffsetX, canvasOffsetY, point)
        if (isNear(screenX, screenY, point, handleTouchRadius)) return SelectionHandle.TOP_RIGHT_ROTATE
        getHandleScreenPos(obj, 0f, 0f, canvasScale, canvasOffsetX, canvasOffsetY, point)
        if (isNear(screenX, screenY, point, handleTouchRadius)) return SelectionHandle.TOP_LEFT
        getHandleScreenPos(obj, 0f, obj.height, canvasScale, canvasOffsetX, canvasOffsetY, point)
        if (isNear(screenX, screenY, point, handleTouchRadius)) return SelectionHandle.BOTTOM_LEFT
        getHandleScreenPos(obj, obj.width, obj.height, canvasScale, canvasOffsetX, canvasOffsetY, point)
        if (isNear(screenX, screenY, point, handleTouchRadius)) return SelectionHandle.BOTTOM_RIGHT
        return null
    }

    fun getOppositeLocalPoint(
        handle: SelectionHandle,
        obj: CanvasObjectUiModel,
        out: FloatArray,
    ) {
        when (handle) {
            SelectionHandle.TOP_LEFT -> { out[0] = obj.width; out[1] = obj.height }
            SelectionHandle.BOTTOM_RIGHT -> { out[0] = 0f; out[1] = 0f }
            SelectionHandle.BOTTOM_LEFT -> { out[0] = obj.width; out[1] = 0f }
            SelectionHandle.TOP_RIGHT_ROTATE -> { out[0] = 0f; out[1] = obj.height }
            SelectionHandle.LEFT_EDGE -> { out[0] = obj.width; out[1] = obj.height / 2f }
            SelectionHandle.RIGHT_EDGE -> { out[0] = 0f; out[1] = obj.height / 2f }
        }
    }

    private fun getHandleScreenPos(
        obj: CanvasObjectUiModel,
        localX: Float,
        localY: Float,
        canvasScale: Float,
        canvasOffsetX: Float,
        canvasOffsetY: Float,
        out: FloatArray,
    ) {
        val scaledX = localX * obj.scale
        val scaledY = localY * obj.scale
        val radians = Math.toRadians(obj.rotation.toDouble())
        val cosR = cos(radians).toFloat()
        val sinR = sin(radians).toFloat()
        val rotatedX = scaledX * cosR - scaledY * sinR
        val rotatedY = scaledX * sinR + scaledY * cosR
        val canvasX = obj.x + rotatedX
        val canvasY = obj.y + rotatedY
        out[0] = canvasX * canvasScale + canvasOffsetX
        out[1] = canvasY * canvasScale + canvasOffsetY
    }

    private fun isNear(
        x: Float,
        y: Float,
        point: FloatArray,
        handleTouchRadius: Float,
    ): Boolean {
        val dx = x - point[0]
        val dy = y - point[1]
        return dx * dx + dy * dy <= handleTouchRadius * handleTouchRadius
    }
}
