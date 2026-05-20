package com.example.journal_canvas.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.journal_canvas.domain.model.CanvasObject
import com.example.journal_canvas.domain.model.CanvasState
import com.example.journal_canvas.domain.model.ImageCanvasObject
import com.example.journal_canvas.domain.model.TextCanvasObject
import com.example.journal_canvas.domain.usecase.ObserveCanvasStateUseCase
import com.example.journal_canvas.domain.usecase.SaveCanvasStateUseCase
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import com.example.journal_canvas.presentation.model.PendingPlacement
import com.example.journal_canvas.presentation.model.copyTransform
import com.example.journal_canvas.util.BitmapLoader
import com.example.journal_canvas.util.ImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class CanvasEditorViewModel @Inject constructor(
    private val observeCanvasStateUseCase: ObserveCanvasStateUseCase,
    private val saveCanvasStateUseCase: SaveCanvasStateUseCase,
    private val bitmapLoader: BitmapLoader,
    private val imageStore: ImageStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CanvasUiState(isLoading = true))
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<CanvasUiEffect>()
    val effect: SharedFlow<CanvasUiEffect> = _effect.asSharedFlow()
    private val undoStack = ArrayDeque<CanvasHistorySnapshot>()
    private val redoStack = ArrayDeque<CanvasHistorySnapshot>()
    private var gestureStartSnapshot: CanvasHistorySnapshot? = null
    private var textEditStartSnapshot: CanvasHistorySnapshot? = null
    private val transientImageUris = mutableSetOf<String>()
    private var hasSweptImagesAfterInitialLoad = false

    init {
        viewModelScope.launch {
            observeCanvasStateUseCase()
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Unable to load canvas",
                        )
                    }
                }
                .collect { state ->
                    _uiState.update { current ->
                        state.toUiState(
                            selectedObjectId = current.selectedObjectId,
                            editingTextObjectId = current.editingTextObjectId,
                            cursorVisible = current.cursorVisible,
                            pendingPlacement = current.pendingPlacement,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                        )
                    }
                    if (!hasSweptImagesAfterInitialLoad) {
                        hasSweptImagesAfterInitialLoad = true
                        cleanupUnusedImportedImages()
                    }
                }
        }
    }

    fun onEvent(event: CanvasUiEvent) {
        when (event) {
            CanvasUiEvent.AddTextClicked -> addText()
            CanvasUiEvent.AddImageClicked -> onAddImageClicked()
            is CanvasUiEvent.AddImageSelected -> addImage(event.uri)
            is CanvasUiEvent.ObjectSelected -> selectObject(event.id)
            is CanvasUiEvent.ObjectTransformChanged -> updateObjectTransform(
                event.id, event.x, event.y, event.scale, event.rotation,
            )
            is CanvasUiEvent.CanvasTransformChanged -> updateCanvasTransform(
                event.scale, event.offsetX, event.offsetY,
            )
            is CanvasUiEvent.ObjectResized -> updateObjectResize(event.id, event.x, event.y, event.width)
            CanvasUiEvent.GestureStarted -> onGestureStarted()
            CanvasUiEvent.GestureCommitted -> onGestureCommitted()
            CanvasUiEvent.GestureCancelled -> onGestureCancelled()
            is CanvasUiEvent.TextHeightChanged -> onTextHeightChanged(event.id, event.height)
            is CanvasUiEvent.TextEditRequested -> requestTextEdit(event.id)
            is CanvasUiEvent.TextChanged -> onTextChanged(event.id, event.text)
            is CanvasUiEvent.TextEditCommitted -> commitTextEdit(event.id)
            CanvasUiEvent.TextEditCancelled -> cancelTextEdit()
            CanvasUiEvent.CursorBlinkToggled -> onCursorBlinkToggled()
            is CanvasUiEvent.CanvasTapped -> onCanvasTapped(event.canvasX, event.canvasY)
            CanvasUiEvent.PlacementCancelled -> cancelPlacement()
            CanvasUiEvent.UndoClicked -> undo()
            CanvasUiEvent.RedoClicked -> redo()
        }
    }

    private fun addText() {
        commitActiveTextEditBeforeNewAction()
        val current = _uiState.value
        if (current.pendingPlacement != null) return
        cleanupPendingPlacement(current.pendingPlacement)
        _uiState.value = withHistoryFlags(current.copy(pendingPlacement = PendingPlacement.Text))
    }

    private fun addImage(uri: String) {
        viewModelScope.launch {
            val persistentUri = imageStore.importToInternalStorage(uri) ?: uri
            transientImageUris.add(persistentUri)
            try {
                val size = bitmapLoader.loadDimensions(persistentUri)
                val (w, h) = if (size != null) {
                    computeFitDimensions(size.width, size.height)
                } else {
                    DEFAULT_IMAGE_DIMENSION to DEFAULT_IMAGE_DIMENSION
                }
                bitmapLoader.load(persistentUri, maxOf(w, h).toInt())

                val current = _uiState.value
                cleanupPendingPlacement(current.pendingPlacement)
                _uiState.value = withHistoryFlags(current.copy(
                    pendingPlacement = PendingPlacement.Image(persistentUri, w, h),
                ))
            } finally {
                transientImageUris.remove(persistentUri)
                cleanupUnusedImportedImages()
            }
        }
    }

    private fun onCanvasTapped(canvasX: Float, canvasY: Float) {
        when (val pending = _uiState.value.pendingPlacement) {
            null -> Unit
            PendingPlacement.Text -> placeText(canvasX, canvasY)
            is PendingPlacement.Image -> placeImage(pending, canvasX, canvasY)
        }
    }

    private fun placeText(canvasX: Float, canvasY: Float) {
        val textSize = 36f
        val width = 240f
        val height = textSize * 1.2f
        val obj = CanvasObjectUiModel.Text(
            id = UUID.randomUUID().toString(),
            x = canvasX - width / 2f,
            y = canvasY - height / 2f,
            scale = 1f,
            rotation = 0f,
            zIndex = nextZIndex(),
            width = width,
            height = height,
            text = "",
            textSize = textSize,
            color = 0xFF222222.toInt(),
        )
        updateAndSave {
            it.copy(
                objects = it.objects + obj,
                selectedObjectId = obj.id,
                editingTextObjectId = obj.id,
                cursorVisible = true,
                pendingPlacement = null,
                errorMessage = null,
            )
        }
        textEditStartSnapshot = _uiState.value.toHistorySnapshot()
        viewModelScope.launch {
            _effect.emit(CanvasUiEffect.StartTextEditing(id = obj.id, text = ""))
        }
    }

    private fun placeImage(pending: PendingPlacement.Image, canvasX: Float, canvasY: Float) {
        val obj = CanvasObjectUiModel.Image(
            id = UUID.randomUUID().toString(),
            x = canvasX - pending.width / 2f,
            y = canvasY - pending.height / 2f,
            scale = 1f,
            rotation = 0f,
            zIndex = nextZIndex(),
            width = pending.width,
            height = pending.height,
            imageUri = pending.uri,
        )
        updateAndSave {
            it.copy(
                objects = it.objects + obj,
                selectedObjectId = obj.id,
                pendingPlacement = null,
                errorMessage = null,
            )
        }
    }

    private fun cancelPlacement() {
        val current = _uiState.value
        if (current.pendingPlacement == null) return
        cleanupPendingPlacement(current.pendingPlacement)
        _uiState.value = withHistoryFlags(current.copy(pendingPlacement = null))
        cleanupUnusedImportedImages()
    }

    private fun cleanupPendingPlacement(pending: PendingPlacement?) {
        if (pending is PendingPlacement.Image) {
            cleanupImageIfUnreferenced(pending.uri, includePending = false)
        }
    }

    private fun computeFitDimensions(srcWidth: Int, srcHeight: Int): Pair<Float, Float> {
        if (srcWidth <= 0 || srcHeight <= 0) return DEFAULT_IMAGE_DIMENSION to DEFAULT_IMAGE_DIMENSION
        val aspect = srcWidth.toFloat() / srcHeight.toFloat()
        return if (aspect >= 1f) {
            DEFAULT_IMAGE_DIMENSION to (DEFAULT_IMAGE_DIMENSION / aspect)
        } else {
            (DEFAULT_IMAGE_DIMENSION * aspect) to DEFAULT_IMAGE_DIMENSION
        }
    }

    private fun selectObject(id: String?) {
        val current = _uiState.value
        val editingId = current.editingTextObjectId
        if (editingId != null && editingId != id) {
            recordTextEditIfChanged(current)
            _uiState.value = withHistoryFlags(_uiState.value.copy(
                selectedObjectId = id,
                editingTextObjectId = null,
                cursorVisible = false,
            ))
            saveCurrentState()
            viewModelScope.launch { _effect.emit(CanvasUiEffect.StopTextEditing) }
        } else {
            _uiState.update { withHistoryFlags(it.copy(selectedObjectId = id)) }
        }
    }

    private fun updateObjectTransform(id: String, x: Float, y: Float, scale: Float, rotation: Float) {
        updateLive { state ->
            state.copy(
                objects = state.objects.map { obj ->
                    if (obj.id == id) obj.copyTransform(
                        x = x,
                        y = y,
                        scale = scale.coerceIn(MIN_OBJECT_SCALE, MAX_OBJECT_SCALE),
                        rotation = rotation,
                    ) else obj
                },
            )
        }
    }

    private fun updateObjectResize(id: String, x: Float, y: Float, width: Float) {
        updateLive { state ->
            state.copy(
                objects = state.objects.map { obj ->
                    if (obj.id == id) obj.copyTransform(x = x, y = y, width = width.coerceAtLeast(MIN_OBJECT_WIDTH))
                    else obj
                },
            )
        }
    }

    private fun updateCanvasTransform(scale: Float, offsetX: Float, offsetY: Float) {
        updateLive {
            it.copy(
                canvasScale = scale.coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE),
                canvasOffsetX = offsetX,
                canvasOffsetY = offsetY,
            )
        }
    }

    private fun onTextHeightChanged(id: String, height: Float) {
        val current = _uiState.value
        val updatedObjects = current.objects.map { obj ->
            if (obj.id == id && obj is CanvasObjectUiModel.Text &&
                abs(obj.height - height) > TEXT_HEIGHT_EPSILON
            ) {
                obj.copyTransform(height = height)
            } else {
                obj
            }
        }
        if (updatedObjects !== current.objects) {
            updateLive { it.copy(objects = updatedObjects) }
        }
    }

    private fun requestTextEdit(id: String) {
        val current = _uiState.value
        val text = (current.objectsById[id] as? CanvasObjectUiModel.Text)?.text ?: return
        val priorEditingId = current.editingTextObjectId
        if (priorEditingId != null && priorEditingId != id) {
            recordTextEditIfChanged(current)
            saveCurrentState()
        }
        textEditStartSnapshot = if (priorEditingId == id && textEditStartSnapshot != null) {
            textEditStartSnapshot
        } else {
            _uiState.value.toHistorySnapshot()
        }
        _uiState.value = withHistoryFlags(_uiState.value.copy(
            selectedObjectId = id,
            editingTextObjectId = id,
            cursorVisible = true,
        ))
        viewModelScope.launch {
            _effect.emit(CanvasUiEffect.StartTextEditing(id = id, text = text))
        }
    }

    private fun onTextChanged(id: String, newText: String) {
        val current = _uiState.value
        val updatedObjects = current.objects.map { obj ->
            if (obj.id == id && obj is CanvasObjectUiModel.Text && obj.text != newText) {
                obj.copy(text = newText)
            } else {
                obj
            }
        }
        if (updatedObjects !== current.objects) {
            updateLive {
                it.copy(
                    objects = updatedObjects,
                    cursorVisible = if (it.editingTextObjectId == id) true else it.cursorVisible,
                )
            }
        }
    }

    private fun commitTextEdit(id: String) {
        val current = _uiState.value
        if (current.editingTextObjectId != id) return
        recordTextEditIfChanged(current)
        _uiState.value = withHistoryFlags(_uiState.value.copy(
            editingTextObjectId = null,
            cursorVisible = false,
        ))
        saveCurrentState()
        viewModelScope.launch { _effect.emit(CanvasUiEffect.StopTextEditing) }
    }

    private fun cancelTextEdit() {
        val current = _uiState.value
        if (current.editingTextObjectId == null) return
        recordTextEditIfChanged(current)
        _uiState.value = withHistoryFlags(_uiState.value.copy(
            editingTextObjectId = null,
            cursorVisible = false,
        ))
        viewModelScope.launch { _effect.emit(CanvasUiEffect.StopTextEditing) }
    }

    private fun onCursorBlinkToggled() {
        val current = _uiState.value
        when {
            current.editingTextObjectId != null ->
                _uiState.value = current.copy(cursorVisible = !current.cursorVisible)
            current.cursorVisible ->
                _uiState.value = current.copy(cursorVisible = false)
        }
    }

    private fun onGestureStarted() {
        gestureStartSnapshot = _uiState.value.toHistorySnapshot()
    }

    private fun onGestureCommitted() {
        val startSnapshot = gestureStartSnapshot
        if (startSnapshot != null && startSnapshot != _uiState.value.toHistorySnapshot()) {
            recordUndoSnapshot(startSnapshot)
            saveCurrentState()
        }
        gestureStartSnapshot = null
    }

    private fun onGestureCancelled() {
        gestureStartSnapshot = null
    }

    private fun onAddImageClicked() {
        commitActiveTextEditBeforeNewAction()
        if (_uiState.value.pendingPlacement != null) return
        viewModelScope.launch { _effect.emit(CanvasUiEffect.OpenImagePicker) }
    }

    private fun undo() {
        recordTextEditIfChanged(_uiState.value)
        val target = undoStack.removeLastOrNull() ?: run {
            syncHistoryFlags()
            return
        }

        val currentSnapshot = _uiState.value.toHistorySnapshot()
        pushRedoSnapshot(currentSnapshot)
        restoreSnapshot(target)
    }

    private fun redo() {
        val activeEditBeforeRedo = _uiState.value.editingTextObjectId
        recordTextEditIfChanged(_uiState.value)
        if (activeEditBeforeRedo != null && redoStack.isEmpty()) {
            _uiState.value = withHistoryFlags(_uiState.value.copy(
                editingTextObjectId = null,
                cursorVisible = false,
            ))
            saveCurrentState()
            syncHistoryFlags()
            viewModelScope.launch { _effect.emit(CanvasUiEffect.StopTextEditing) }
            return
        }

        val target = redoStack.removeLastOrNull() ?: run {
            syncHistoryFlags()
            return
        }

        val currentSnapshot = _uiState.value.toHistorySnapshot()
        pushUndoSnapshot(currentSnapshot)
        restoreSnapshot(target)
    }

    private fun updateLive(reducer: (CanvasUiState) -> CanvasUiState) {
        _uiState.value = withHistoryFlags(reducer(_uiState.value).copy(isLoading = false))
    }

    private fun saveCurrentState() {
        val current = _uiState.value
        viewModelScope.launch {
            saveCanvasStateUseCase(current.toDomain())
        }
    }

    private fun updateAndSave(reducer: (CanvasUiState) -> CanvasUiState) {
        val before = _uiState.value.toHistorySnapshot()
        updateLive(reducer)
        if (before != _uiState.value.toHistorySnapshot()) {
            recordUndoSnapshot(before)
        }
        saveCurrentState()
    }

    private fun commitActiveTextEditBeforeNewAction() {
        val current = _uiState.value
        if (current.editingTextObjectId == null) return

        recordTextEditIfChanged(current)
        _uiState.value = withHistoryFlags(_uiState.value.copy(
            editingTextObjectId = null,
            cursorVisible = false,
        ))
        saveCurrentState()
        viewModelScope.launch { _effect.emit(CanvasUiEffect.StopTextEditing) }
    }

    private fun recordTextEditIfChanged(state: CanvasUiState) {
        val startSnapshot = textEditStartSnapshot ?: return
        if (startSnapshot != state.toHistorySnapshot()) {
            recordUndoSnapshot(startSnapshot)
        }
        textEditStartSnapshot = null
    }

    private fun recordUndoSnapshot(snapshot: CanvasHistorySnapshot) {
        if (undoStack.lastOrNull() == snapshot) {
            syncHistoryFlags()
            return
        }
        undoStack.addLast(snapshot)
        val droppedOldUndo = trimOldest(undoStack)
        val droppedRedo = redoStack.isNotEmpty()
        redoStack.clear()
        syncHistoryFlags()
        if (droppedOldUndo || droppedRedo) {
            cleanupUnusedImportedImages()
        }
    }

    private fun pushUndoSnapshot(snapshot: CanvasHistorySnapshot) {
        if (undoStack.lastOrNull() == snapshot) return
        undoStack.addLast(snapshot)
        if (trimOldest(undoStack)) {
            cleanupUnusedImportedImages()
        }
    }

    private fun pushRedoSnapshot(snapshot: CanvasHistorySnapshot) {
        if (redoStack.lastOrNull() == snapshot) return
        redoStack.addLast(snapshot)
        if (trimOldest(redoStack)) {
            cleanupUnusedImportedImages()
        }
    }

    private fun restoreSnapshot(snapshot: CanvasHistorySnapshot) {
        cleanupPendingPlacement(_uiState.value.pendingPlacement)
        gestureStartSnapshot = null
        textEditStartSnapshot = null

        _uiState.value = withHistoryFlags(snapshot.toUiState(_uiState.value))
        saveCurrentState()
        cleanupUnusedImportedImages()
        viewModelScope.launch { _effect.emit(CanvasUiEffect.StopTextEditing) }
    }

    private fun syncHistoryFlags() {
        val current = _uiState.value
        val updated = withHistoryFlags(current)
        if (updated != current) {
            _uiState.value = updated
        }
    }

    private fun withHistoryFlags(state: CanvasUiState): CanvasUiState {
        return state.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    private fun trimOldest(stack: ArrayDeque<CanvasHistorySnapshot>): Boolean {
        var removed = false
        while (stack.size > MAX_HISTORY_STEPS) {
            stack.removeFirst()
            removed = true
        }
        return removed
    }

    private fun cleanupUnusedImportedImages() {
        val referencedUris = collectReferencedImageUris(includePending = true)
        viewModelScope.launch {
            imageStore.deleteUnreferencedImages(referencedUris)
        }
    }

    private fun cleanupImageIfUnreferenced(uri: String, includePending: Boolean) {
        if (uri in collectReferencedImageUris(includePending = includePending)) return
        viewModelScope.launch {
            imageStore.delete(uri)
        }
    }

    private fun collectReferencedImageUris(includePending: Boolean): Set<String> {
        val refs = mutableSetOf<String>()

        refs.addImageUris(_uiState.value.objects)
        if (includePending) {
            val pending = _uiState.value.pendingPlacement
            if (pending is PendingPlacement.Image) refs.add(pending.uri)
        }
        undoStack.forEach { refs.addImageUris(it.objects) }
        redoStack.forEach { refs.addImageUris(it.objects) }
        gestureStartSnapshot?.let { refs.addImageUris(it.objects) }
        textEditStartSnapshot?.let { refs.addImageUris(it.objects) }
        refs.addAll(transientImageUris)

        return refs
    }

    private fun MutableSet<String>.addImageUris(objects: List<CanvasObjectUiModel>) {
        objects.forEach { obj ->
            if (obj is CanvasObjectUiModel.Image) add(obj.imageUri)
        }
    }

    private fun nextZIndex(): Int {
        return (_uiState.value.objects.maxOfOrNull { it.zIndex } ?: -1) + 1
    }

    private companion object {
        const val MIN_CANVAS_SCALE = 0.3f
        const val MAX_CANVAS_SCALE = 5f
        const val MIN_OBJECT_SCALE = 0.2f
        const val MAX_OBJECT_SCALE = 5f
        const val MIN_OBJECT_WIDTH = 60f
        const val TEXT_HEIGHT_EPSILON = 1f
        const val DEFAULT_IMAGE_DIMENSION = 280f
        const val MAX_HISTORY_STEPS = 30
    }
}

private fun CanvasState.toUiState(
    selectedObjectId: String?,
    editingTextObjectId: String?,
    cursorVisible: Boolean,
    pendingPlacement: com.example.journal_canvas.presentation.model.PendingPlacement?,
    canUndo: Boolean,
    canRedo: Boolean,
): CanvasUiState {
    return CanvasUiState(
        objects = objects.map { it.toUiModel() },
        selectedObjectId = selectedObjectId,
        editingTextObjectId = editingTextObjectId,
        cursorVisible = cursorVisible,
        pendingPlacement = pendingPlacement,
        canvasScale = canvasScale,
        canvasOffsetX = canvasOffsetX,
        canvasOffsetY = canvasOffsetY,
        isLoading = false,
        canUndo = canUndo,
        canRedo = canRedo,
    )
}

private data class CanvasHistorySnapshot(
    val objects: List<CanvasObjectUiModel>,
    val selectedObjectId: String?,
    val canvasScale: Float,
    val canvasOffsetX: Float,
    val canvasOffsetY: Float,
)

private fun CanvasUiState.toHistorySnapshot(): CanvasHistorySnapshot {
    return CanvasHistorySnapshot(
        objects = objects,
        selectedObjectId = selectedObjectId,
        canvasScale = canvasScale,
        canvasOffsetX = canvasOffsetX,
        canvasOffsetY = canvasOffsetY,
    )
}

private fun CanvasHistorySnapshot.toUiState(current: CanvasUiState): CanvasUiState {
    val restoredSelectedId = selectedObjectId?.takeIf { selectedId ->
        objects.any { it.id == selectedId }
    }
    return current.copy(
        objects = objects,
        selectedObjectId = restoredSelectedId,
        editingTextObjectId = null,
        cursorVisible = false,
        pendingPlacement = null,
        canvasScale = canvasScale,
        canvasOffsetX = canvasOffsetX,
        canvasOffsetY = canvasOffsetY,
        isLoading = false,
        errorMessage = null,
    )
}

private fun CanvasObject.toUiModel(): CanvasObjectUiModel = when (this) {
    is TextCanvasObject -> CanvasObjectUiModel.Text(
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
    is ImageCanvasObject -> CanvasObjectUiModel.Image(
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

private fun CanvasUiState.toDomain(): CanvasState {
    return CanvasState(
        objects = objects.map { it.toDomain() },
        canvasScale = canvasScale,
        canvasOffsetX = canvasOffsetX,
        canvasOffsetY = canvasOffsetY,
    )
}

private fun CanvasObjectUiModel.toDomain(): CanvasObject = when (this) {
    is CanvasObjectUiModel.Text -> TextCanvasObject(
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
    is CanvasObjectUiModel.Image -> ImageCanvasObject(
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
