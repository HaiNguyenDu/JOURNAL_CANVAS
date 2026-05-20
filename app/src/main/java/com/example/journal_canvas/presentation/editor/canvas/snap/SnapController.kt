package com.example.journal_canvas.presentation.editor.canvas.snap

import android.graphics.RectF
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasTransformUtils
import com.example.journal_canvas.presentation.editor.canvas.viewport.PageConfig
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import kotlin.math.abs

class SnapController {
    private val movingBounds = RectF()
    val gridGuides: List<SnapGuide> = listOf(
        SnapGuide(SnapAxis.X, PageConfig.PAGE_WIDTH * 0.25f),
        SnapGuide(SnapAxis.X, PageConfig.PAGE_WIDTH * 0.5f),
        SnapGuide(SnapAxis.X, PageConfig.PAGE_WIDTH * 0.75f),
        SnapGuide(SnapAxis.Y, PageConfig.PAGE_HEIGHT * 0.25f),
        SnapGuide(SnapAxis.Y, PageConfig.PAGE_HEIGHT * 0.5f),
        SnapGuide(SnapAxis.Y, PageConfig.PAGE_HEIGHT * 0.75f),
    )
    private val targetXLines = listOf(
        PageConfig.PAGE_WIDTH * 0.25f,
        PageConfig.PAGE_WIDTH * 0.5f,
        PageConfig.PAGE_WIDTH * 0.75f,
    )
    private val targetYLines = listOf(
        PageConfig.PAGE_HEIGHT * 0.25f,
        PageConfig.PAGE_HEIGHT * 0.5f,
        PageConfig.PAGE_HEIGHT * 0.75f,
    )

    fun snapObject(
        obj: CanvasObjectUiModel,
        proposedX: Float,
        proposedY: Float,
        scale: Float,
        rotation: Float,
        canvasScale: Float,
    ): SnapResult {
        val threshold = SNAP_THRESHOLD_PX / canvasScale.coerceAtLeast(0.001f)
        CanvasTransformUtils.setTransformedObjectBounds(
            x = proposedX,
            y = proposedY,
            width = obj.width,
            height = obj.height,
            scale = scale,
            rotation = rotation,
            out = movingBounds,
        )

        val xMatch = findBestMatch(
            movingStart = movingBounds.left,
            movingCenter = movingBounds.centerX(),
            movingEnd = movingBounds.right,
            targets = targetXLines,
            threshold = threshold,
        )
        val yMatch = findBestMatch(
            movingStart = movingBounds.top,
            movingCenter = movingBounds.centerY(),
            movingEnd = movingBounds.bottom,
            targets = targetYLines,
            threshold = threshold,
        )

        val snappedX = xMatch?.let { proposedX + it.delta } ?: proposedX
        val snappedY = yMatch?.let { proposedY + it.delta } ?: proposedY

        return SnapResult(snappedX, snappedY, emptyList())
    }

    private fun findBestMatch(
        movingStart: Float,
        movingCenter: Float,
        movingEnd: Float,
        targets: List<Float>,
        threshold: Float,
    ): SnapMatch? {
        var best: SnapMatch? = null
        targets.forEach { target ->
            best = chooseBetter(best, SnapMatch(target, target - movingStart), threshold)
            best = chooseBetter(best, SnapMatch(target, target - movingCenter), threshold)
            best = chooseBetter(best, SnapMatch(target, target - movingEnd), threshold)
        }
        return best
    }

    private fun chooseBetter(current: SnapMatch?, candidate: SnapMatch, threshold: Float): SnapMatch? {
        val distance = abs(candidate.delta)
        if (distance > threshold) return current
        val currentDistance = current?.let { abs(it.delta) } ?: Float.MAX_VALUE
        return if (distance < currentDistance) candidate else current
    }

    private data class SnapMatch(
        val target: Float,
        val delta: Float,
    )

    private companion object {
        const val SNAP_THRESHOLD_PX = 6f
    }
}
