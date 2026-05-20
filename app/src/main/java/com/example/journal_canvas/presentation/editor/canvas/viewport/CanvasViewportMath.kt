package com.example.journal_canvas.presentation.editor.canvas.viewport

import kotlin.math.abs
import kotlin.math.min

object CanvasViewportMath {

    fun calculateFitScale(
        viewWidth: Int,
        viewHeight: Int,
        padding: Float = PageConfig.PAGE_PADDING,
    ): Float {
        if (viewWidth <= 0 || viewHeight <= 0) return 1f
        val availableWidth = (viewWidth.toFloat() - padding * 2f).coerceAtLeast(1f)
        val availableHeight = (viewHeight.toFloat() - padding * 2f).coerceAtLeast(1f)
        return min(
            availableWidth / PageConfig.PAGE_WIDTH,
            availableHeight / PageConfig.PAGE_HEIGHT,
        ).coerceAtLeast(MIN_SAFE_SCALE)
    }

    fun calculateCenteredOffset(
        viewWidth: Int,
        viewHeight: Int,
        scale: Float,
        out: FloatArray,
    ) {
        out[0] = (viewWidth.toFloat() - PageConfig.PAGE_WIDTH * scale) / 2f
        out[1] = (viewHeight.toFloat() - PageConfig.PAGE_HEIGHT * scale) / 2f
    }

    fun clampOffset(
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        viewWidth: Int,
        viewHeight: Int,
        out: FloatArray,
    ) {
        val pageScreenWidth = PageConfig.PAGE_WIDTH * scale
        val pageScreenHeight = PageConfig.PAGE_HEIGHT * scale
        val viewWidthF = viewWidth.toFloat()
        val viewHeightF = viewHeight.toFloat()

        out[0] = if (pageScreenWidth <= viewWidthF) {
            (viewWidthF - pageScreenWidth) / 2f
        } else {
            offsetX.coerceIn(viewWidthF - pageScreenWidth, 0f)
        }

        out[1] = if (pageScreenHeight <= viewHeightF) {
            (viewHeightF - pageScreenHeight) / 2f
        } else {
            offsetY.coerceIn(viewHeightF - pageScreenHeight, 0f)
        }
    }

    fun screenToPage(
        screenX: Float,
        screenY: Float,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        out: FloatArray,
    ) {
        val safeScale = scale.coerceAtLeast(MIN_SAFE_SCALE)
        out[0] = (screenX - offsetX) / safeScale
        out[1] = (screenY - offsetY) / safeScale
    }

    fun isPointInsidePage(pageX: Float, pageY: Float): Boolean {
        return pageX >= 0f &&
            pageX <= PageConfig.PAGE_WIDTH &&
            pageY >= 0f &&
            pageY <= PageConfig.PAGE_HEIGHT
    }

    fun isDefaultTransform(scale: Float, offsetX: Float, offsetY: Float): Boolean {
        return abs(scale - 1f) <= DEFAULT_TRANSFORM_EPSILON &&
            abs(offsetX) <= DEFAULT_TRANSFORM_EPSILON &&
            abs(offsetY) <= DEFAULT_TRANSFORM_EPSILON
    }

    private const val MIN_SAFE_SCALE = 0.001f
    private const val DEFAULT_TRANSFORM_EPSILON = 0.0001f
}
