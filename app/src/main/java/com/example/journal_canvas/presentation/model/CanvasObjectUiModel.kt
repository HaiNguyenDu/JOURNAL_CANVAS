package com.example.journal_canvas.presentation.model

sealed class CanvasObjectUiModel {
    abstract val id: String
    abstract val x: Float
    abstract val y: Float
    abstract val scale: Float
    abstract val rotation: Float
    abstract val zIndex: Int
    abstract val width: Float
    abstract val height: Float

    data class Text(
        override val id: String,
        override val x: Float,
        override val y: Float,
        override val scale: Float,
        override val rotation: Float,
        override val zIndex: Int,
        override val width: Float,
        override val height: Float,
        val text: String,
        val textSize: Float,
        val color: Int,
    ) : CanvasObjectUiModel()

    data class Image(
        override val id: String,
        override val x: Float,
        override val y: Float,
        override val scale: Float,
        override val rotation: Float,
        override val zIndex: Int,
        override val width: Float,
        override val height: Float,
        val imageUri: String,
    ) : CanvasObjectUiModel()
}

fun CanvasObjectUiModel.copyTransform(
    x: Float = this.x,
    y: Float = this.y,
    scale: Float = this.scale,
    rotation: Float = this.rotation,
    width: Float = this.width,
    height: Float = this.height,
): CanvasObjectUiModel {
    return when (this) {
        is CanvasObjectUiModel.Text -> copy(
            x = x, y = y, scale = scale, rotation = rotation, width = width, height = height,
        )
        is CanvasObjectUiModel.Image -> copy(
            x = x, y = y, scale = scale, rotation = rotation, width = width, height = height,
        )
    }
}
