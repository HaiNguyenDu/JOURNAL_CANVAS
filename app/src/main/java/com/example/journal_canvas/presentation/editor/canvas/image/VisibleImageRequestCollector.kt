package com.example.journal_canvas.presentation.editor.canvas.image

import android.graphics.RectF
import com.example.journal_canvas.presentation.editor.CanvasUiState
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasTransformUtils
import com.example.journal_canvas.presentation.editor.canvas.viewport.PageConfig
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel

class VisibleImageRequestCollector {
    private val visibleViewportBounds = RectF()
    private val visibleObjectBounds = RectF()
    private val visiblePageBounds = RectF()

    fun collect(
        state: CanvasUiState,
        viewWidth: Int,
        viewHeight: Int,
        out: MutableMap<String, Int>,
    ) {
        out.clear()
        if (viewWidth <= 0 || viewHeight <= 0) return

        CanvasTransformUtils.setViewportCanvasBounds(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            canvasOffsetX = state.canvasOffsetX,
            canvasOffsetY = state.canvasOffsetY,
            canvasScale = state.canvasScale,
            paddingScreenPx = IMAGE_LOAD_PRELOAD_PADDING_PX,
            out = visibleViewportBounds,
        )
        visiblePageBounds.set(0f, 0f, PageConfig.PAGE_WIDTH, PageConfig.PAGE_HEIGHT)

        state.objects.forEach { obj ->
            if (obj !is CanvasObjectUiModel.Image) return@forEach
            CanvasTransformUtils.setTransformedObjectBounds(obj, visibleObjectBounds)
            if (!CanvasTransformUtils.intersects(visibleObjectBounds, visiblePageBounds)) {
                return@forEach
            }
            if (!CanvasTransformUtils.intersects(visibleObjectBounds, visibleViewportBounds)) {
                return@forEach
            }

            val targetMaxDimensionPx = CanvasTransformUtils.estimateImageTargetMaxDimensionPx(
                obj = obj,
                canvasScale = state.canvasScale,
            )
            val existing = out[obj.imageUri]
            if (existing == null || targetMaxDimensionPx > existing) {
                out[obj.imageUri] = targetMaxDimensionPx
            }
        }
    }

    private companion object {
        const val IMAGE_LOAD_PRELOAD_PADDING_PX = 512f
    }
}
