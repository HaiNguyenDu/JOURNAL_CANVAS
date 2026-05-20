package com.example.journal_canvas.presentation.editor.canvas.viewport

import com.example.journal_canvas.presentation.editor.CanvasUiEvent
import com.example.journal_canvas.presentation.editor.CanvasUiState

class CanvasViewportController(
    private val viewWidth: () -> Int,
    private val viewHeight: () -> Int,
    private val eventSink: (CanvasUiEvent) -> Unit,
) {
    private val tempPoints = FloatArray(2)
    private val resultPoints = FloatArray(2)

    private var hasSubmittedState = false
    private var hasAppliedInitialViewport = false

    fun submitState(current: CanvasUiState, next: CanvasUiState): CanvasViewportUpdate {
        hasSubmittedState = true
        val initialFitState = applyInitialViewportIfNeeded(next)
        if (initialFitState != null) {
            return CanvasViewportUpdate(initialFitState, shouldInvalidate = true)
        }
        if (current == next) return CanvasViewportUpdate(next, shouldInvalidate = false)
        return CanvasViewportUpdate(next, shouldInvalidate = shouldInvalidateForState(current, next))
    }

    fun onSizeChanged(current: CanvasUiState): CanvasViewportUpdate {
        val initialFitState = applyInitialViewportIfNeeded(current)
        if (initialFitState != null) {
            return CanvasViewportUpdate(initialFitState, shouldInvalidate = true)
        }
        return clampCurrentViewportIfNeeded(current)
    }

    fun clampOffset(
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        out: FloatArray,
    ) {
        CanvasViewportMath.clampOffset(
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = viewWidth(),
            viewHeight = viewHeight(),
            out = out,
        )
    }

    private fun applyInitialViewportIfNeeded(state: CanvasUiState): CanvasUiState? {
        if (!hasSubmittedState ||
            hasAppliedInitialViewport ||
            viewWidth() <= 0 ||
            viewHeight() <= 0 ||
            state.isLoading
        ) {
            return null
        }

        val isDefaultTransform = CanvasViewportMath.isDefaultTransform(
            scale = state.canvasScale,
            offsetX = state.canvasOffsetX,
            offsetY = state.canvasOffsetY,
        )
        val targetScale = if (isDefaultTransform) {
            CanvasViewportMath.calculateFitScale(viewWidth(), viewHeight())
                .coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
        } else {
            state.canvasScale.coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
        }

        if (isDefaultTransform) {
            CanvasViewportMath.calculateCenteredOffset(
                viewWidth = viewWidth(),
                viewHeight = viewHeight(),
                scale = targetScale,
                out = tempPoints,
            )
        } else {
            tempPoints[0] = state.canvasOffsetX
            tempPoints[1] = state.canvasOffsetY
        }

        clampOffset(
            scale = targetScale,
            offsetX = tempPoints[0],
            offsetY = tempPoints[1],
            out = resultPoints,
        )
        hasAppliedInitialViewport = true

        if (state.canvasScale == targetScale &&
            state.canvasOffsetX == resultPoints[0] &&
            state.canvasOffsetY == resultPoints[1]
        ) {
            return null
        }

        eventSink(
            CanvasUiEvent.CanvasTransformChanged(
                scale = targetScale,
                offsetX = resultPoints[0],
                offsetY = resultPoints[1],
            ),
        )
        return state.copy(
            canvasScale = targetScale,
            canvasOffsetX = resultPoints[0],
            canvasOffsetY = resultPoints[1],
        )
    }

    private fun clampCurrentViewportIfNeeded(state: CanvasUiState): CanvasViewportUpdate {
        if (!hasSubmittedState || viewWidth() <= 0 || viewHeight() <= 0 || state.isLoading) {
            return CanvasViewportUpdate(state, shouldInvalidate = false)
        }

        val targetScale = state.canvasScale.coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
        clampOffset(
            scale = targetScale,
            offsetX = state.canvasOffsetX,
            offsetY = state.canvasOffsetY,
            out = tempPoints,
        )

        if (state.canvasScale == targetScale &&
            state.canvasOffsetX == tempPoints[0] &&
            state.canvasOffsetY == tempPoints[1]
        ) {
            return CanvasViewportUpdate(state, shouldInvalidate = false)
        }

        eventSink(
            CanvasUiEvent.CanvasTransformChanged(
                scale = targetScale,
                offsetX = tempPoints[0],
                offsetY = tempPoints[1],
            ),
        )
        return CanvasViewportUpdate(
            state = state.copy(
                canvasScale = targetScale,
                canvasOffsetX = tempPoints[0],
                canvasOffsetY = tempPoints[1],
            ),
            shouldInvalidate = true,
        )
    }

    private fun shouldInvalidateForState(old: CanvasUiState, new: CanvasUiState): Boolean {
        return old.objects != new.objects ||
            old.selectedObjectId != new.selectedObjectId ||
            old.editingTextObjectId != new.editingTextObjectId ||
            old.cursorVisible != new.cursorVisible ||
            old.canvasScale != new.canvasScale ||
            old.canvasOffsetX != new.canvasOffsetX ||
            old.canvasOffsetY != new.canvasOffsetY
    }

    private companion object {
        const val MIN_CANVAS_SCALE = 0.3f
        const val MAX_CANVAS_SCALE = 5f
    }
}

data class CanvasViewportUpdate(
    val state: CanvasUiState,
    val shouldInvalidate: Boolean,
)
