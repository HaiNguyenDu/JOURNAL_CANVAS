package com.example.journal_canvas.data.mapper

import com.example.journal_canvas.domain.model.CanvasObject
import com.example.journal_canvas.domain.model.CanvasState
import com.example.journal_canvas.domain.model.ImageCanvasObject
import com.example.journal_canvas.domain.model.TextCanvasObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CanvasStateDto(
    val objects: List<CanvasObjectDto> = emptyList(),
    val canvasScale: Float = 1f,
    val canvasOffsetX: Float = 0f,
    val canvasOffsetY: Float = 0f,
)

@Serializable
sealed class CanvasObjectDto {
    abstract val id: String
    abstract val x: Float
    abstract val y: Float
    abstract val scale: Float
    abstract val rotation: Float
    abstract val zIndex: Int
    abstract val width: Float
    abstract val height: Float

    @Serializable
    @SerialName("text")
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
    ) : CanvasObjectDto()

    @Serializable
    @SerialName("image")
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
    ) : CanvasObjectDto()
}

fun CanvasState.toDto() = CanvasStateDto(
    objects = objects.map { it.toDto() },
    canvasScale = canvasScale,
    canvasOffsetX = canvasOffsetX,
    canvasOffsetY = canvasOffsetY,
)

fun CanvasStateDto.toDomain() = CanvasState(
    objects = objects.map { it.toDomain() },
    canvasScale = canvasScale,
    canvasOffsetX = canvasOffsetX,
    canvasOffsetY = canvasOffsetY,
)

private fun CanvasObject.toDto(): CanvasObjectDto = when (this) {
    is TextCanvasObject -> CanvasObjectDto.Text(
        id = id,
        x = x,
        y = y,
        scale = scale,
        rotation = rotation,
        zIndex = zIndex,
        width = width,
        height = height,
        text = text,
        textSize = textSize,
        color = color,
    )
    is ImageCanvasObject -> CanvasObjectDto.Image(
        id = id,
        x = x,
        y = y,
        scale = scale,
        rotation = rotation,
        zIndex = zIndex,
        width = width,
        height = height,
        imageUri = imageUri,
    )
}

private fun CanvasObjectDto.toDomain(): CanvasObject = when (this) {
    is CanvasObjectDto.Text -> TextCanvasObject(
        id = id,
        x = x,
        y = y,
        scale = scale,
        rotation = rotation,
        zIndex = zIndex,
        width = width,
        height = height,
        text = text,
        textSize = textSize,
        color = color,
    )
    is CanvasObjectDto.Image -> ImageCanvasObject(
        id = id,
        x = x,
        y = y,
        scale = scale,
        rotation = rotation,
        zIndex = zIndex,
        width = width,
        height = height,
        imageUri = imageUri,
    )
}
