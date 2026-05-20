package com.example.journal_canvas.presentation.editor.canvas

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.journal_canvas.presentation.editor.CanvasUiEvent
import com.example.journal_canvas.presentation.editor.CanvasUiState
import com.example.journal_canvas.presentation.editor.canvas.gesture.CanvasGestureController
import com.example.journal_canvas.presentation.editor.canvas.image.VisibleImageRequestCollector
import com.example.journal_canvas.presentation.editor.canvas.render.CanvasRenderer
import com.example.journal_canvas.presentation.editor.canvas.snap.SnapGuide
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasViewportController
import com.example.journal_canvas.util.BitmapMemoryCache

class JournalCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onEvent: ((CanvasUiEvent) -> Unit)? = null

    private var uiState = CanvasUiState()
    private var snapGuides: List<SnapGuide> = emptyList()

    private val renderer = CanvasRenderer(context).apply {
        onTextMeasured = { id, height ->
            onEvent?.invoke(CanvasUiEvent.TextHeightChanged(id, height))
        }
    }
    private val viewportController = CanvasViewportController(
        viewWidth = { width },
        viewHeight = { height },
        eventSink = { event -> onEvent?.invoke(event) },
    )
    private val gestureController = CanvasGestureController(
        context = context,
        stateProvider = { uiState },
        eventSink = { event -> onEvent?.invoke(event) },
        invalidateView = { invalidate() },
        snapGuideSink = { guides -> updateSnapGuides(guides) },
        viewportController = viewportController,
    )
    private val visibleImageRequestCollector = VisibleImageRequestCollector()

    var bitmapCache: BitmapMemoryCache? = null
        set(value) {
            field = value
            renderer.bitmapCache = value
        }

    fun submitState(state: CanvasUiState) {
        val update = viewportController.submitState(uiState, state)
        uiState = update.state
        if (update.shouldInvalidate) invalidate()
    }

    fun collectVisibleImageRequests(out: MutableMap<String, Int>) {
        visibleImageRequestCollector.collect(
            state = uiState,
            viewWidth = width,
            viewHeight = height,
            out = out,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(
            canvas = canvas,
            state = uiState,
            viewWidth = width,
            viewHeight = height,
            hideSelectionHandles = gestureController.shouldHideSelectionHandles(),
            snapGuides = snapGuides,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val update = viewportController.onSizeChanged(uiState)
        uiState = update.state
        if (update.shouldInvalidate) invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureController.onTouchEvent(event)
    }

    private fun updateSnapGuides(guides: List<SnapGuide>) {
        if (snapGuides == guides) return
        snapGuides = guides
        invalidate()
    }
}
