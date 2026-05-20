package com.example.journal_canvas.presentation.editor.canvas.hit

import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import kotlin.math.cos
import kotlin.math.sin

class CanvasHitTester {

    fun findObjectAt(
        objects: List<CanvasObjectUiModel>,
        canvasX: Float,
        canvasY: Float
    ): String? {
        var hitId: String? = null
        var hitZIndex = Int.MIN_VALUE
        objects.forEach { obj ->
            if (obj.zIndex >= hitZIndex && isHit(obj, canvasX, canvasY)) {
                hitId = obj.id
                hitZIndex = obj.zIndex
            }
        }
        return hitId
    }

    private fun isHit(
        obj: CanvasObjectUiModel,
        canvasX: Float,
        canvasY: Float
    ): Boolean {
        if (obj.width <= 0f || obj.height <= 0f) return false
        if (obj.scale == 0f) return false

        val translatedX = canvasX - obj.x
        val translatedY = canvasY - obj.y

        val radians = Math.toRadians((-obj.rotation).toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()

        val rotatedX = translatedX * cos - translatedY * sin
        val rotatedY = translatedX * sin + translatedY * cos

        val localX = rotatedX / obj.scale
        val localY = rotatedY / obj.scale

        return localX >= 0f &&
                localX <= obj.width &&
                localY >= 0f &&
                localY <= obj.height
    }
}
