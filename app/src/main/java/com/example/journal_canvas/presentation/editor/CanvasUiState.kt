package com.example.journal_canvas.presentation.editor

import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import com.example.journal_canvas.presentation.model.PendingPlacement

data class CanvasUiState(
    val objects: List<CanvasObjectUiModel> = emptyList(),
    val selectedObjectId: String? = null,
    val editingTextObjectId: String? = null,
    val cursorVisible: Boolean = false,
    val pendingPlacement: PendingPlacement? = null,
    val canvasScale: Float = 1f,
    val canvasOffsetX: Float = 0f,
    val canvasOffsetY: Float = 0f,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val objectsById: Map<String, CanvasObjectUiModel> by lazy(LazyThreadSafetyMode.NONE) {
        if (objects.isEmpty()) emptyMap() else objects.associateBy { it.id }
    }

    val selectedObject: CanvasObjectUiModel?
        get() = selectedObjectId?.let { objectsById[it] }
}
