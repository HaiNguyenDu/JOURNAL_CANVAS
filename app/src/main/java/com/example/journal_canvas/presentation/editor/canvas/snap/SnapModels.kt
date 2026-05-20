package com.example.journal_canvas.presentation.editor.canvas.snap

enum class SnapAxis {
    X,
    Y,
}

data class SnapGuide(
    val axis: SnapAxis,
    val value: Float,
)

data class SnapResult(
    val x: Float,
    val y: Float,
    val guides: List<SnapGuide>,
)
