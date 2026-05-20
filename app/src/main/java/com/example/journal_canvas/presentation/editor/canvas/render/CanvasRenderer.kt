package com.example.journal_canvas.presentation.editor.canvas.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.journal_canvas.R
import com.example.journal_canvas.presentation.editor.CanvasUiState
import com.example.journal_canvas.presentation.editor.canvas.snap.SnapGuide
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasTransformUtils
import com.example.journal_canvas.presentation.editor.canvas.viewport.PageConfig
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import com.example.journal_canvas.util.BitmapMemoryCache

class CanvasRenderer(context: Context) {
    private val textRenderer = TextObjectRenderer()
    private val imageRenderer = ImageObjectRenderer()
    private val selectionRenderer = SelectionRenderer(context)
    private val snapGuideRenderer = SnapGuideRenderer()

    var onTextMeasured: ((id: String, height: Float) -> Unit)?
        get() = textRenderer.onTextMeasured
        set(value) {
            textRenderer.onTextMeasured = value
        }

    var bitmapCache: BitmapMemoryCache?
        get() = imageRenderer.bitmapCache
        set(value) {
            imageRenderer.bitmapCache = value
        }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.back_ground)
        style = Paint.Style.FILL
    }
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val pageShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1F000000
        style = Paint.Style.FILL
    }

    private val objectBounds = RectF()
    private val pageBounds = RectF()
    private val pageShadowBounds = RectF()
    private val viewportBounds = RectF()

    fun draw(
        canvas: Canvas,
        state: CanvasUiState,
        viewWidth: Int,
        viewHeight: Int,
        hideSelectionHandles: Boolean,
        snapGuides: List<SnapGuide>,
    ) {
        drawBackground(canvas, viewWidth, viewHeight)
        setViewportBounds(state, viewWidth, viewHeight)

        canvas.save()
        canvas.translate(state.canvasOffsetX, state.canvasOffsetY)
        canvas.scale(state.canvasScale, state.canvasScale)
        drawPage(canvas, state.canvasScale)

        val objects = objectsInDrawOrder(state.objects)
        canvas.save()
        canvas.clipRect(0f, 0f, PageConfig.PAGE_WIDTH, PageConfig.PAGE_HEIGHT)
        objects.forEach { obj ->
            if (shouldDrawObject(obj)) {
                drawObject(canvas, obj, state)
            }
        }
        canvas.restore()

        snapGuideRenderer.draw(canvas, snapGuides, state.canvasScale)
        val selected = state.selectedObject
        if (selected != null && shouldDrawObject(selected)) {
            selectionRenderer.draw(canvas, selected, hideSelectionHandles)
        }

        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), backgroundPaint)
    }

    private fun setViewportBounds(state: CanvasUiState, viewWidth: Int, viewHeight: Int) {
        CanvasTransformUtils.setViewportCanvasBounds(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            canvasOffsetX = state.canvasOffsetX,
            canvasOffsetY = state.canvasOffsetY,
            canvasScale = state.canvasScale,
            paddingScreenPx = DRAW_CULL_PADDING_PX,
            out = viewportBounds,
        )
    }

    private fun drawPage(canvas: Canvas, canvasScale: Float) {
        val safeScale = canvasScale.coerceAtLeast(0.001f)
        val shadowOffset = PAGE_SHADOW_OFFSET_PX / safeScale
        val shadowSpread = PAGE_SHADOW_SPREAD_PX / safeScale
        pageShadowBounds.set(
            -shadowSpread,
            shadowOffset,
            PageConfig.PAGE_WIDTH + shadowSpread,
            PageConfig.PAGE_HEIGHT + shadowOffset + shadowSpread,
        )
        pageBounds.set(0f, 0f, PageConfig.PAGE_WIDTH, PageConfig.PAGE_HEIGHT)
        canvas.drawRoundRect(
            pageShadowBounds,
            PageConfig.PAGE_CORNER_RADIUS,
            PageConfig.PAGE_CORNER_RADIUS,
            pageShadowPaint,
        )
        canvas.drawRoundRect(
            pageBounds,
            PageConfig.PAGE_CORNER_RADIUS,
            PageConfig.PAGE_CORNER_RADIUS,
            pagePaint,
        )
    }

    private fun drawObject(canvas: Canvas, obj: CanvasObjectUiModel, state: CanvasUiState) {
        canvas.save()
        canvas.translate(obj.x, obj.y)
        canvas.rotate(obj.rotation)
        canvas.scale(obj.scale, obj.scale)

        when (obj) {
            is CanvasObjectUiModel.Text -> textRenderer.draw(canvas, obj, state)
            is CanvasObjectUiModel.Image -> imageRenderer.draw(canvas, obj, state.canvasScale)
        }

        canvas.restore()
    }

    private fun objectsInDrawOrder(objects: List<CanvasObjectUiModel>): List<CanvasObjectUiModel> {
        if (objects.size < 2) return objects

        var previousZ = objects.first().zIndex
        for (index in 1 until objects.size) {
            val zIndex = objects[index].zIndex
            if (zIndex < previousZ) return objects.sortedBy { it.zIndex }
            previousZ = zIndex
        }
        return objects
    }

    private fun shouldDrawObject(obj: CanvasObjectUiModel): Boolean {
        if (obj.width <= 0f || obj.height <= 0f) return false
        CanvasTransformUtils.setTransformedObjectBounds(obj, objectBounds)
        return CanvasTransformUtils.intersects(objectBounds, pageBounds) &&
            CanvasTransformUtils.intersects(objectBounds, viewportBounds)
    }

    private companion object {
        const val DRAW_CULL_PADDING_PX = 96f
        const val PAGE_SHADOW_OFFSET_PX = 4f
        const val PAGE_SHADOW_SPREAD_PX = 2f
    }
}
