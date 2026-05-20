package com.example.journal_canvas.presentation.editor.canvas.gesture

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.example.journal_canvas.presentation.editor.CanvasUiEvent
import com.example.journal_canvas.presentation.editor.CanvasUiState
import com.example.journal_canvas.presentation.editor.canvas.hit.CanvasHitTester
import com.example.journal_canvas.presentation.editor.canvas.render.SelectionRenderer
import com.example.journal_canvas.presentation.editor.canvas.snap.SnapController
import com.example.journal_canvas.presentation.editor.canvas.snap.SnapGuide
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasObjectPageMath
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasViewportController
import com.example.journal_canvas.presentation.editor.canvas.viewport.CanvasViewportMath
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel

class CanvasGestureController(
    context: Context,
    private val stateProvider: () -> CanvasUiState,
    private val eventSink: (CanvasUiEvent) -> Unit,
    private val invalidateView: () -> Unit,
    private val snapGuideSink: (List<SnapGuide>) -> Unit,
    private val viewportController: CanvasViewportController,
) {
    private val hitTester = CanvasHitTester()
    private val snapController = SnapController()
    private val selectionHandleHitTester = SelectionHandleHitTester()
    private val objectMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val mappingPoints = FloatArray(2)
    private val tempPoints = FloatArray(2)
    private val deltaPoints = FloatArray(2)
    private val objectClampBounds = RectF()

    private var touchState: TouchState = TouchState.None

    private val touchSlop: Float by lazy {
        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }
    private val density: Float = context.resources.displayMetrics.density
    private fun handleTouchRadius(canvasScale: Float): Float =
        (SelectionRenderer.HANDLE_SIZE_DP / 2f) * density * canvasScale
    private val doubleTapTimeout: Long = ViewConfiguration.getDoubleTapTimeout().toLong()
    private var lastTapTime: Long = 0L
    private var lastTapObjectId: String? = null

    private val uiState: CanvasUiState
        get() = stateProvider()

    fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> {
                emitGestureCancelledIfNeeded(touchState)
                resetTouchState()
                true
            }
            else -> false
        }
    }

    fun shouldHideSelectionHandles(): Boolean {
        return touchState is TouchState.DragObject ||
            touchState is TouchState.TransformObject
    }

    private fun handleDown(event: MotionEvent): Boolean {
        val placementActive = uiState.pendingPlacement != null
        if (!placementActive) {
            val selectedId = uiState.selectedObjectId
            if (selectedId != null) {
                val selectedObj = uiState.objectsById[selectedId]
                if (selectedObj != null) {
                    val handle = selectionHandleHitTester.findHandleAt(
                        screenX = event.x,
                        screenY = event.y,
                        obj = selectedObj,
                        canvasScale = uiState.canvasScale,
                        canvasOffsetX = uiState.canvasOffsetX,
                        canvasOffsetY = uiState.canvasOffsetY,
                        handleTouchRadius = handleTouchRadius(uiState.canvasScale),
                    )
                    if (handle != null && startHandleGesture(event, selectedObj, handle)) {
                        return true
                    }
                }
            }
        }

        val hitId = if (placementActive) {
            null
        } else {
            screenToCanvas(event.x, event.y, mappingPoints)
            if (CanvasViewportMath.isPointInsidePage(mappingPoints[0], mappingPoints[1])) {
                hitTester.findObjectAt(uiState.objects, mappingPoints[0], mappingPoints[1])
            } else {
                null
            }
        }
        touchState = TouchState.TapCandidate(
            downX = event.x,
            downY = event.y,
            objectId = hitId,
        )
        return true
    }

    private fun startHandleGesture(
        event: MotionEvent,
        obj: CanvasObjectUiModel,
        handle: SelectionHandle,
    ): Boolean = when (handle) {
        SelectionHandle.TOP_RIGHT_ROTATE -> startRotateHandle(event, obj)
        SelectionHandle.LEFT_EDGE, SelectionHandle.RIGHT_EDGE -> startEdgeResizeHandle(event, obj, handle)
        SelectionHandle.TOP_LEFT,
        SelectionHandle.BOTTOM_LEFT,
        SelectionHandle.BOTTOM_RIGHT -> startScaleHandle(event, obj, handle)
    }

    private fun startEdgeResizeHandle(
        event: MotionEvent,
        obj: CanvasObjectUiModel,
        handle: SelectionHandle,
    ): Boolean {
        if (obj !is CanvasObjectUiModel.Text) return false
        emitGestureStarted()
        touchState = if (handle == SelectionHandle.LEFT_EDGE) {
            TouchState.ResizeLeft(
                objectId = obj.id,
                downX = event.x,
                downY = event.y,
                initialObjX = obj.x,
                initialObjY = obj.y,
                initialWidth = obj.width,
            )
        } else {
            TouchState.ResizeRight(
                objectId = obj.id,
                downX = event.x,
                downY = event.y,
                initialObjX = obj.x,
                initialObjY = obj.y,
                initialWidth = obj.width,
            )
        }
        return true
    }

    private fun startRotateHandle(event: MotionEvent, obj: CanvasObjectUiModel): Boolean {
        CanvasGestureMath.getObjectCenterCanvas(obj, mappingPoints)
        val centerCanvasX = mappingPoints[0]
        val centerCanvasY = mappingPoints[1]
        screenToCanvas(event.x, event.y, mappingPoints)
        val downCanvasX = mappingPoints[0]
        val downCanvasY = mappingPoints[1]
        val initialDistance = CanvasGestureMath.distanceTo(centerCanvasX, centerCanvasY, downCanvasX, downCanvasY)
        if (initialDistance <= 0f) return false

        emitGestureStarted()
        touchState = TouchState.RotateObject(
            objectId = obj.id,
            centerCanvasX = centerCanvasX,
            centerCanvasY = centerCanvasY,
            initialScale = obj.scale,
            initialRotation = obj.rotation,
            initialDistance = initialDistance,
            initialAngle = CanvasGestureMath.angleTo(downCanvasX, downCanvasY, centerCanvasX, centerCanvasY),
        )
        return true
    }

    private fun startScaleHandle(
        event: MotionEvent,
        obj: CanvasObjectUiModel,
        handle: SelectionHandle,
    ): Boolean {
        selectionHandleHitTester.getOppositeLocalPoint(handle, obj, mappingPoints)
        val pivotLocalX = mappingPoints[0]
        val pivotLocalY = mappingPoints[1]
        CanvasGestureMath.localPointToCanvas(obj, pivotLocalX, pivotLocalY, mappingPoints)
        val pivotCanvasX = mappingPoints[0]
        val pivotCanvasY = mappingPoints[1]
        screenToCanvas(event.x, event.y, mappingPoints)
        val downCanvasX = mappingPoints[0]
        val downCanvasY = mappingPoints[1]
        val initialDistance = CanvasGestureMath.distanceTo(downCanvasX, downCanvasY, pivotCanvasX, pivotCanvasY)
        if (initialDistance <= 0f) return false

        emitGestureStarted()
        touchState = TouchState.ScaleObject(
            objectId = obj.id,
            handle = handle,
            pivotCanvasX = pivotCanvasX,
            pivotCanvasY = pivotCanvasY,
            pivotLocalX = pivotLocalX,
            pivotLocalY = pivotLocalY,
            initialScale = obj.scale,
            initialRotation = obj.rotation,
            initialDistance = initialDistance,
        )
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        when (val state = touchState) {
            is TouchState.ScaleObject -> handleScaleObjectMove(event, state)
            is TouchState.RotateObject -> handleRotateObjectMove(event, state)
            is TouchState.ResizeRight -> handleResizeRightMove(event, state)
            is TouchState.ResizeLeft -> handleResizeLeftMove(event, state)
            is TouchState.TapCandidate -> handleTapCandidateMove(event, state)
            is TouchState.DragObject -> handleDragObjectMove(event, state)
            is TouchState.DragCanvas -> handleDragCanvasMove(event, state)
            is TouchState.TransformObject -> handleTransformObjectMove(event, state)
            is TouchState.ZoomCanvas -> handleZoomCanvasMove(event, state)
            TouchState.None -> Unit
        }
        return true
    }

    private fun handleScaleObjectMove(event: MotionEvent, state: TouchState.ScaleObject) {
        val obj = uiState.objectsById[state.objectId] ?: return
        if (state.initialDistance <= 0f) return
        screenToCanvas(event.x, event.y, mappingPoints)
        val currentDistance = CanvasGestureMath.distanceTo(
            mappingPoints[0],
            mappingPoints[1],
            state.pivotCanvasX,
            state.pivotCanvasY,
        )
        val newScale = (state.initialScale * (currentDistance / state.initialDistance))
            .coerceIn(MIN_OBJECT_SCALE, MAX_OBJECT_SCALE)
        CanvasGestureMath.localToCanvasOffset(
            state.pivotLocalX,
            state.pivotLocalY,
            newScale,
            state.initialRotation,
            tempPoints,
        )
        val newX = state.pivotCanvasX - tempPoints[0]
        val newY = state.pivotCanvasY - tempPoints[1]
        emitClampedObjectTransform(obj, newX, newY, newScale, obj.rotation)
    }

    private fun handleRotateObjectMove(event: MotionEvent, state: TouchState.RotateObject) {
        val obj = uiState.objectsById[state.objectId] ?: return
        screenToCanvas(event.x, event.y, mappingPoints)
        val currentCanvasX = mappingPoints[0]
        val currentCanvasY = mappingPoints[1]
        val currentDistance = CanvasGestureMath.distanceTo(
            state.centerCanvasX,
            state.centerCanvasY,
            currentCanvasX,
            currentCanvasY,
        )
        val currentAngle = CanvasGestureMath.angleTo(
            currentCanvasX,
            currentCanvasY,
            state.centerCanvasX,
            state.centerCanvasY,
        )
        val newScale = if (state.initialDistance > 0f) {
            (state.initialScale * (currentDistance / state.initialDistance))
                .coerceIn(MIN_OBJECT_SCALE, MAX_OBJECT_SCALE)
        } else {
            obj.scale
        }
        val newRotation = state.initialRotation + (currentAngle - state.initialAngle)
        emitClampedObjectTransform(obj, obj.x, obj.y, newScale, newRotation)
    }

    private fun handleTapCandidateMove(event: MotionEvent, state: TouchState.TapCandidate) {
        if (hasMovedBeyondTouchSlop(event.x, event.y, state.downX, state.downY)) {
            transitionTapCandidateToDrag(event, state)
        }
    }

    private fun transitionTapCandidateToDrag(event: MotionEvent, state: TouchState.TapCandidate) {
        emitGestureStarted()
        if (state.objectId != null) {
            val obj = uiState.objectsById[state.objectId]
            if (obj == null) {
                resetTouchState()
                return
            }
            if (uiState.selectedObjectId != state.objectId) {
                eventSink(CanvasUiEvent.ObjectSelected(state.objectId))
            }
            touchState = TouchState.DragObject(
                objectId = state.objectId,
                downX = event.x,
                downY = event.y,
                initialObjX = obj.x,
                initialObjY = obj.y,
            )
            snapGuideSink(snapController.gridGuides)
            invalidateView()
        } else {
            touchState = TouchState.DragCanvas(
                lastX = event.x,
                lastY = event.y,
            )
        }
    }

    private fun handleDragObjectMove(event: MotionEvent, state: TouchState.DragObject) {
        val obj = uiState.objectsById[state.objectId]
        if (obj == null) {
            resetTouchState()
            return
        }
        val canvasDx = (event.x - state.downX) / uiState.canvasScale
        val canvasDy = (event.y - state.downY) / uiState.canvasScale
        val proposedX = state.initialObjX + canvasDx
        val proposedY = state.initialObjY + canvasDy
        val snapped = snapController.snapObject(
            obj = obj,
            proposedX = proposedX,
            proposedY = proposedY,
            scale = obj.scale,
            rotation = obj.rotation,
            canvasScale = uiState.canvasScale,
        )
        emitClampedObjectTransform(
            obj = obj,
            x = snapped.x,
            y = snapped.y,
            scale = obj.scale,
            rotation = obj.rotation,
        )
        snapGuideSink(snapController.gridGuides)
    }

    private fun handleDragCanvasMove(event: MotionEvent, state: TouchState.DragCanvas) {
        val dx = event.x - state.lastX
        val dy = event.y - state.lastY
        viewportController.clampOffset(
            scale = uiState.canvasScale,
            offsetX = uiState.canvasOffsetX + dx,
            offsetY = uiState.canvasOffsetY + dy,
            out = tempPoints,
        )
        eventSink(
            CanvasUiEvent.CanvasTransformChanged(
                scale = uiState.canvasScale,
                offsetX = tempPoints[0],
                offsetY = tempPoints[1],
            ),
        )
        touchState = state.copy(lastX = event.x, lastY = event.y)
    }

    private fun emitClampedObjectTransform(
        obj: CanvasObjectUiModel,
        x: Float,
        y: Float,
        scale: Float,
        rotation: Float,
    ) {
        clampObjectPositionToPage(
            x = x,
            y = y,
            scale = scale,
            rotation = rotation,
            width = obj.width,
            height = obj.height,
            out = tempPoints,
        )
        eventSink(
            CanvasUiEvent.ObjectTransformChanged(
                id = obj.id,
                x = tempPoints[0],
                y = tempPoints[1],
                scale = scale,
                rotation = rotation,
            ),
        )
    }

    private fun clampObjectPositionToPage(
        x: Float,
        y: Float,
        scale: Float,
        rotation: Float,
        width: Float,
        height: Float,
        out: FloatArray,
    ) {
        CanvasObjectPageMath.clampPositionToPage(
            x = x,
            y = y,
            width = width,
            height = height,
            scale = scale,
            rotation = rotation,
            outBounds = objectClampBounds,
            outPosition = out,
        )
    }

    private fun hasMovedBeyondTouchSlop(x: Float, y: Float, downX: Float, downY: Float): Boolean {
        val dx = x - downX
        val dy = y - downY
        return dx * dx + dy * dy > touchSlop * touchSlop
    }

    private fun handleResizeRightMove(event: MotionEvent, state: TouchState.ResizeRight) {
        val obj = uiState.objectsById[state.objectId]
                as? CanvasObjectUiModel.Text ?: return
        val localDx = CanvasGestureMath.screenToLocalDx(
            screenDx = event.x - state.downX,
            screenDy = event.y - state.downY,
            canvasScale = uiState.canvasScale,
            obj = obj,
            objectMatrix = objectMatrix,
            inverseMatrix = inverseMatrix,
            out = deltaPoints,
        )
        val newWidth = (state.initialWidth + localDx).coerceAtLeast(MIN_OBJECT_WIDTH)
        emitClampedObjectResize(
            obj = obj,
            x = state.initialObjX,
            y = state.initialObjY,
            width = newWidth,
        )
    }

    private fun emitClampedObjectResize(
        obj: CanvasObjectUiModel,
        x: Float,
        y: Float,
        width: Float,
    ) {
        clampObjectPositionToPage(
            x = x,
            y = y,
            scale = obj.scale,
            rotation = obj.rotation,
            width = width,
            height = obj.height,
            out = tempPoints,
        )
        eventSink(
            CanvasUiEvent.ObjectResized(
                id = obj.id,
                x = tempPoints[0],
                y = tempPoints[1],
                width = width,
            ),
        )
    }

    private fun handleResizeLeftMove(event: MotionEvent, state: TouchState.ResizeLeft) {
        val obj = uiState.objectsById[state.objectId]
                as? CanvasObjectUiModel.Text ?: return
        val localDx = CanvasGestureMath.screenToLocalDx(
            screenDx = event.x - state.downX,
            screenDy = event.y - state.downY,
            canvasScale = uiState.canvasScale,
            obj = obj,
            objectMatrix = objectMatrix,
            inverseMatrix = inverseMatrix,
            out = deltaPoints,
        )
        val newWidth = (state.initialWidth - localDx).coerceAtLeast(MIN_OBJECT_WIDTH)
        val actualLocalDx = state.initialWidth - newWidth
        val originDelta = CanvasGestureMath.localDxToCanvasDelta(
            localDx = actualLocalDx,
            obj = obj,
            objectMatrix = objectMatrix,
            out = deltaPoints,
        )
        emitClampedObjectResize(
            obj = obj,
            x = state.initialObjX + originDelta[0],
            y = state.initialObjY + originDelta[1],
            width = newWidth,
        )
    }

    private fun handleUp(event: MotionEvent): Boolean {
        val state = touchState
        when {
            state is TouchState.TapCandidate -> handleTapUp(event, state)
            isModifyingGesture(state) -> eventSink(CanvasUiEvent.GestureCommitted)
        }
        resetTouchState()
        return true
    }

    private fun handleTapUp(event: MotionEvent, state: TouchState.TapCandidate) {
        if (uiState.pendingPlacement != null) {
            screenToCanvas(event.x, event.y, mappingPoints)
            if (CanvasViewportMath.isPointInsidePage(mappingPoints[0], mappingPoints[1])) {
                eventSink(CanvasUiEvent.CanvasTapped(mappingPoints[0], mappingPoints[1]))
            }
            lastTapObjectId = null
            lastTapTime = 0L
            return
        }

        val tappedId = state.objectId
        val now = event.eventTime
        val tappedObj = tappedId?.let { id -> uiState.objectsById[id] }
        val isDoubleTapOnText = tappedObj is CanvasObjectUiModel.Text &&
            lastTapObjectId == tappedId &&
            now - lastTapTime <= doubleTapTimeout

        if (isDoubleTapOnText) {
            eventSink(CanvasUiEvent.TextEditRequested(requireNotNull(tappedId)))
            lastTapObjectId = null
            lastTapTime = 0L
        } else {
            eventSink(CanvasUiEvent.ObjectSelected(tappedId))
            if (tappedId != null) {
                lastTapObjectId = tappedId
                lastTapTime = now
            } else {
                lastTapObjectId = null
                lastTapTime = 0L
            }
        }
    }

    private fun resetTouchState() {
        val wasHidingSelectionHandles = shouldHideSelectionHandles()
        touchState = TouchState.None
        snapGuideSink(emptyList())
        if (wasHidingSelectionHandles) invalidateView()
    }

    private fun screenToCanvas(screenX: Float, screenY: Float, out: FloatArray) {
        CanvasGestureMath.screenToCanvas(
            screenX = screenX,
            screenY = screenY,
            canvasScale = uiState.canvasScale,
            canvasOffsetX = uiState.canvasOffsetX,
            canvasOffsetY = uiState.canvasOffsetY,
            out = out,
        )
    }

    private fun handlePointerDown(event: MotionEvent): Boolean {
        if (event.pointerCount < 2) return true
        val dist = CanvasGestureMath.pointerDistance(event)
        if (dist <= 0f) return true

        val state = touchState
        val selectedId = uiState.selectedObjectId

        if (state is TouchState.TapCandidate &&
            state.objectId != null &&
            state.objectId != selectedId
        ) {
            eventSink(CanvasUiEvent.ObjectSelected(state.objectId))
            resetTouchState()
            return true
        }

        if (selectedId != null && isPinchOnSelectedObject(state, selectedId)) {
            val obj = uiState.objectsById[selectedId] ?: return true
            if (isModifyingGesture(state)) eventSink(CanvasUiEvent.GestureCommitted)
            snapGuideSink(emptyList())
            screenToCanvas(CanvasGestureMath.pointerMidX(event), CanvasGestureMath.pointerMidY(event), mappingPoints)
            CanvasGestureMath.canvasPointToObjectLocal(obj, mappingPoints[0], mappingPoints[1], tempPoints)
            emitGestureStarted()
            touchState = TouchState.TransformObject(
                objectId = selectedId,
                initialScale = obj.scale,
                initialRotation = obj.rotation,
                initialDistance = dist,
                initialAngle = CanvasGestureMath.pointerAngle(event),
                anchorLocalX = tempPoints[0],
                anchorLocalY = tempPoints[1],
            )
            invalidateView()
            return true
        }

        if (canStartCanvasZoomFromState(state)) {
            emitGestureStarted()
            touchState = TouchState.ZoomCanvas(
                initialCanvasScale = uiState.canvasScale,
                initialCanvasOffsetX = uiState.canvasOffsetX,
                initialCanvasOffsetY = uiState.canvasOffsetY,
                initialDistance = dist,
                initialMidX = CanvasGestureMath.pointerMidX(event),
                initialMidY = CanvasGestureMath.pointerMidY(event),
            )
        }
        return true
    }

    private fun isPinchOnSelectedObject(state: TouchState, selectedId: String): Boolean = when (state) {
        is TouchState.TapCandidate -> state.objectId == selectedId
        is TouchState.DragObject -> state.objectId == selectedId
        else -> false
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        when (touchState) {
            is TouchState.ZoomCanvas,
            is TouchState.TransformObject -> {
                eventSink(CanvasUiEvent.GestureCommitted)
                resetTouchState()
            }
            else -> Unit
        }
        return true
    }

    private fun handleTransformObjectMove(event: MotionEvent, state: TouchState.TransformObject) {
        if (event.pointerCount < 2) return
        val obj = uiState.objectsById[state.objectId]
        if (obj == null) {
            resetTouchState()
            return
        }
        if (state.initialDistance <= 0f) return
        val currentDistance = CanvasGestureMath.pointerDistance(event)
        val currentAngle = CanvasGestureMath.pointerAngle(event)
        val scaleFactor = currentDistance / state.initialDistance
        val newScale = (state.initialScale * scaleFactor).coerceIn(MIN_OBJECT_SCALE, MAX_OBJECT_SCALE)
        val newRotation = state.initialRotation + (currentAngle - state.initialAngle)
        screenToCanvas(CanvasGestureMath.pointerMidX(event), CanvasGestureMath.pointerMidY(event), mappingPoints)
        CanvasGestureMath.localToCanvasOffset(state.anchorLocalX, state.anchorLocalY, newScale, newRotation, tempPoints)
        val newX = mappingPoints[0] - tempPoints[0]
        val newY = mappingPoints[1] - tempPoints[1]
        emitClampedObjectTransform(obj, newX, newY, newScale, newRotation)
    }

    private fun handleZoomCanvasMove(event: MotionEvent, state: TouchState.ZoomCanvas) {
        if (event.pointerCount < 2) return
        if (state.initialDistance <= 0f || state.initialCanvasScale <= 0f) return
        val currentDist = CanvasGestureMath.pointerDistance(event)
        val currentMidX = CanvasGestureMath.pointerMidX(event)
        val currentMidY = CanvasGestureMath.pointerMidY(event)
        val scaleFactor = currentDist / state.initialDistance
        val newScale = (state.initialCanvasScale * scaleFactor).coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
        val scaleRatio = newScale / state.initialCanvasScale
        val newOffsetX = currentMidX - (state.initialMidX - state.initialCanvasOffsetX) * scaleRatio
        val newOffsetY = currentMidY - (state.initialMidY - state.initialCanvasOffsetY) * scaleRatio
        viewportController.clampOffset(
            scale = newScale,
            offsetX = newOffsetX,
            offsetY = newOffsetY,
            out = tempPoints,
        )
        eventSink(
            CanvasUiEvent.CanvasTransformChanged(
                scale = newScale,
                offsetX = tempPoints[0],
                offsetY = tempPoints[1],
            ),
        )
    }

    private fun canStartCanvasZoomFromState(state: TouchState): Boolean = when (state) {
        TouchState.None -> true
        is TouchState.TapCandidate -> state.objectId == null
        is TouchState.DragCanvas -> true
        else -> false
    }

    private fun isModifyingGesture(state: TouchState): Boolean =
        state is TouchState.ScaleObject ||
        state is TouchState.RotateObject ||
        state is TouchState.ResizeLeft ||
        state is TouchState.ResizeRight ||
        state is TouchState.DragObject ||
        state is TouchState.DragCanvas ||
        state is TouchState.TransformObject ||
        state is TouchState.ZoomCanvas

    private fun emitGestureStarted() {
        eventSink(CanvasUiEvent.GestureStarted)
    }

    private fun emitGestureCancelledIfNeeded(state: TouchState) {
        if (isModifyingGesture(state)) eventSink(CanvasUiEvent.GestureCancelled)
    }

    private companion object {
        const val MIN_OBJECT_WIDTH = 60f
        const val MIN_OBJECT_SCALE = 0.2f
        const val MAX_OBJECT_SCALE = 5f
        const val MIN_CANVAS_SCALE = 0.3f
        const val MAX_CANVAS_SCALE = 5f
    }
}
