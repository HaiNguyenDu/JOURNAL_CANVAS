package com.example.journal_canvas.presentation.editor

sealed interface CanvasUiEffect {
    data object OpenImagePicker : CanvasUiEffect
    data class ShowMessage(val message: String) : CanvasUiEffect
    data class StartTextEditing(val id: String, val text: String) : CanvasUiEffect
    data object StopTextEditing : CanvasUiEffect
}
