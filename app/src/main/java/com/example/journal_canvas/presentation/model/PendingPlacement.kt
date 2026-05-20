package com.example.journal_canvas.presentation.model

sealed interface PendingPlacement {
    data object Text : PendingPlacement

    data class Image(
        val uri: String,
        val width: Float,
        val height: Float,
    ) : PendingPlacement
}
