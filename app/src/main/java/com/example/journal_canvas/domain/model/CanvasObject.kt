package com.example.journal_canvas.domain.model

sealed class CanvasObject {
    abstract val id: String
    abstract val x: Float
    abstract val y: Float
    abstract val scale: Float
    abstract val rotation: Float
    abstract val zIndex: Int
    abstract val width: Float
    abstract val height: Float
}
