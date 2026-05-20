package com.example.journal_canvas.presentation.editor.canvas.gesture

sealed interface TouchState {

    data object None : TouchState

    data class TapCandidate(
        val downX: Float,
        val downY: Float,
        val objectId: String?,
        val hasMovedBeyondSlop: Boolean = false,
    ) : TouchState

    data class DragObject(
        val objectId: String,
        val downX: Float,
        val downY: Float,
        val initialObjX: Float,
        val initialObjY: Float,
    ) : TouchState

    data class DragCanvas(
        val lastX: Float,
        val lastY: Float,
    ) : TouchState

    data class ResizeRight(
        val objectId: String,
        val downX: Float,
        val downY: Float,
        val initialObjX: Float,
        val initialObjY: Float,
        val initialWidth: Float,
    ) : TouchState

    data class ResizeLeft(
        val objectId: String,
        val downX: Float,
        val downY: Float,
        val initialObjX: Float,
        val initialObjY: Float,
        val initialWidth: Float,
    ) : TouchState

    data class TransformObject(
        val objectId: String,
        val initialScale: Float,
        val initialRotation: Float,
        val initialDistance: Float,
        val initialAngle: Float,
        val anchorLocalX: Float,
        val anchorLocalY: Float,
    ) : TouchState

    data class ScaleObject(
        val objectId: String,
        val handle: SelectionHandle,
        val pivotCanvasX: Float,
        val pivotCanvasY: Float,
        val pivotLocalX: Float,
        val pivotLocalY: Float,
        val initialScale: Float,
        val initialRotation: Float,
        val initialDistance: Float,
    ) : TouchState

    data class RotateObject(
        val objectId: String,
        val centerCanvasX: Float,
        val centerCanvasY: Float,
        val initialScale: Float,
        val initialRotation: Float,
        val initialDistance: Float,
        val initialAngle: Float,
    ) : TouchState

    data class ZoomCanvas(
        val initialCanvasScale: Float,
        val initialCanvasOffsetX: Float,
        val initialCanvasOffsetY: Float,
        val initialDistance: Float,
        val initialMidX: Float,
        val initialMidY: Float,
    ) : TouchState
}
