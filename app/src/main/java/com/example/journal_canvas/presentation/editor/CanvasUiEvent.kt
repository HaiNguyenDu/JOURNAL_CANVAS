package com.example.journal_canvas.presentation.editor

sealed interface CanvasUiEvent {
    data object AddTextClicked : CanvasUiEvent
    data object AddImageClicked : CanvasUiEvent
    data class AddImageSelected(val uri: String) : CanvasUiEvent
    data class ObjectSelected(val id: String?) : CanvasUiEvent
    data class ObjectTransformChanged(
        val id: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val rotation: Float,
    ) : CanvasUiEvent
    data class CanvasTransformChanged(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
    ) : CanvasUiEvent
    data class ObjectResized(
        val id: String,
        val x: Float,
        val y: Float,
        val width: Float,
    ) : CanvasUiEvent
    data object GestureStarted : CanvasUiEvent
    data object GestureCommitted : CanvasUiEvent
    data object GestureCancelled : CanvasUiEvent
    data class TextHeightChanged(val id: String, val height: Float) : CanvasUiEvent
    data class TextEditRequested(val id: String) : CanvasUiEvent
    data class TextChanged(val id: String, val text: String) : CanvasUiEvent
    data class TextEditCommitted(val id: String) : CanvasUiEvent
    data object TextEditCancelled : CanvasUiEvent
    data object CursorBlinkToggled : CanvasUiEvent
    data class CanvasTapped(val canvasX: Float, val canvasY: Float) : CanvasUiEvent
    data object PlacementCancelled : CanvasUiEvent
    data object UndoClicked : CanvasUiEvent
    data object RedoClicked : CanvasUiEvent
}
