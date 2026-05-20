package com.example.journal_canvas.presentation.editor.canvas.render

import android.graphics.Canvas
import android.graphics.Paint
import com.example.journal_canvas.presentation.editor.canvas.snap.SnapAxis
import com.example.journal_canvas.presentation.editor.canvas.snap.SnapGuide
import com.example.journal_canvas.presentation.editor.canvas.viewport.PageConfig

class SnapGuideRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0088FF.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun draw(canvas: Canvas, guides: List<SnapGuide>, canvasScale: Float) {
        if (guides.isEmpty()) return
        paint.strokeWidth = GUIDE_STROKE_PX / canvasScale.coerceAtLeast(0.001f)
        canvas.save()
        canvas.clipRect(0f, 0f, PageConfig.PAGE_WIDTH, PageConfig.PAGE_HEIGHT)
        guides.forEach { guide ->
            when (guide.axis) {
                SnapAxis.X -> canvas.drawLine(guide.value, 0f, guide.value, PageConfig.PAGE_HEIGHT, paint)
                SnapAxis.Y -> canvas.drawLine(0f, guide.value, PageConfig.PAGE_WIDTH, guide.value, paint)
            }
        }
        canvas.restore()
    }

    private companion object {
        const val GUIDE_STROKE_PX = 2f
    }
}
