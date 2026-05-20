package com.example.journal_canvas.domain.model

data class CanvasState(
    val objects: List<CanvasObject> = emptyList(),
    val canvasScale: Float = 1f,
    val canvasOffsetX: Float = 0f,
    val canvasOffsetY: Float = 0f,
)
