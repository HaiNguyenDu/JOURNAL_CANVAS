package com.example.journal_canvas.presentation.editor.canvas.gesture

import android.graphics.Matrix
import android.view.MotionEvent
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasViewportMath
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CanvasGestureMath {

    fun distanceTo(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun angleTo(x: Float, y: Float, cx: Float, cy: Float): Float {
        return Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
    }

    fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return sqrt(dx * dx + dy * dy)
    }

    fun pointerMidX(event: MotionEvent): Float {
        if (event.pointerCount < 2) return event.x
        return (event.getX(0) + event.getX(1)) / 2f
    }

    fun pointerMidY(event: MotionEvent): Float {
        if (event.pointerCount < 2) return event.y
        return (event.getY(0) + event.getY(1)) / 2f
    }

    fun pointerAngle(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    fun localToCanvasOffset(
        localX: Float,
        localY: Float,
        scale: Float,
        rotation: Float,
        out: FloatArray,
    ) {
        val scaledX = localX * scale
        val scaledY = localY * scale
        val radians = Math.toRadians(rotation.toDouble())
        val cosR = cos(radians).toFloat()
        val sinR = sin(radians).toFloat()
        out[0] = scaledX * cosR - scaledY * sinR
        out[1] = scaledX * sinR + scaledY * cosR
    }

    fun localPointToCanvas(
        obj: CanvasObjectUiModel,
        localX: Float,
        localY: Float,
        out: FloatArray,
    ) {
        localToCanvasOffset(localX, localY, obj.scale, obj.rotation, out)
        out[0] += obj.x
        out[1] += obj.y
    }

    fun getObjectCenterCanvas(obj: CanvasObjectUiModel, out: FloatArray) {
        localPointToCanvas(obj, obj.width / 2f, obj.height / 2f, out)
    }

    fun screenToCanvas(
        screenX: Float,
        screenY: Float,
        canvasScale: Float,
        canvasOffsetX: Float,
        canvasOffsetY: Float,
        out: FloatArray,
    ) {
        CanvasViewportMath.screenToPage(
            screenX = screenX,
            screenY = screenY,
            offsetX = canvasOffsetX,
            offsetY = canvasOffsetY,
            scale = canvasScale,
            out = out,
        )
    }

    fun canvasPointToObjectLocal(
        obj: CanvasObjectUiModel,
        canvasX: Float,
        canvasY: Float,
        out: FloatArray,
    ) {
        val translatedX = canvasX - obj.x
        val translatedY = canvasY - obj.y
        val radians = Math.toRadians((-obj.rotation).toDouble())
        val cosR = cos(radians).toFloat()
        val sinR = sin(radians).toFloat()
        val rotatedX = translatedX * cosR - translatedY * sinR
        val rotatedY = translatedX * sinR + translatedY * cosR
        val safeScale = obj.scale.coerceAtLeast(0.001f)
        out[0] = rotatedX / safeScale
        out[1] = rotatedY / safeScale
    }

    fun screenToLocalDx(
        screenDx: Float,
        screenDy: Float,
        canvasScale: Float,
        obj: CanvasObjectUiModel,
        objectMatrix: Matrix,
        inverseMatrix: Matrix,
        out: FloatArray,
    ): Float {
        val canvasDx = screenDx / canvasScale
        val canvasDy = screenDy / canvasScale
        objectMatrix.reset()
        objectMatrix.postScale(obj.scale, obj.scale)
        objectMatrix.postRotate(obj.rotation)
        return if (objectMatrix.invert(inverseMatrix)) {
            out[0] = canvasDx
            out[1] = canvasDy
            inverseMatrix.mapVectors(out)
            out[0]
        } else {
            canvasDx / obj.scale.coerceAtLeast(0.001f)
        }
    }

    fun localDxToCanvasDelta(
        localDx: Float,
        obj: CanvasObjectUiModel,
        objectMatrix: Matrix,
        out: FloatArray,
    ): FloatArray {
        out[0] = localDx
        out[1] = 0f
        objectMatrix.reset()
        objectMatrix.postScale(obj.scale, obj.scale)
        objectMatrix.postRotate(obj.rotation)
        objectMatrix.mapVectors(out)
        return out
    }
}
