package com.example.journal_canvas.domain.model

data class ImageCanvasObject(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val scale: Float,
    override val rotation: Float,
    override val zIndex: Int,
    override val width: Float,
    override val height: Float,
    val imageUri: String,
) : CanvasObject()
