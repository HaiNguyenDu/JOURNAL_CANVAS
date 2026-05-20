package com.example.journal_canvas.presentation.editor

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.journal_canvas.R
import com.example.journal_canvas.databinding.ActivityCanvasEditorBinding
import com.example.journal_canvas.presentation.model.CanvasObjectUiModel
import com.example.journal_canvas.presentation.model.PendingPlacement
import com.example.journal_canvas.util.BitmapLoader
import com.example.journal_canvas.util.BitmapMemoryCache
import com.hainguyenduy.stepguideview.model.GuideStep
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@AndroidEntryPoint
class CanvasEditorActivity : AppCompatActivity() {
    private val binding by lazy { ActivityCanvasEditorBinding.inflate(layoutInflater) }
    private val viewModel: CanvasEditorViewModel by viewModels()

    @Inject lateinit var bitmapLoader: BitmapLoader
    @Inject lateinit var bitmapCache: BitmapMemoryCache

    private var activeEditingId: String? = null

    private var isProgrammaticTextChange = false

    private var cursorBlinkJob: Job? = null

    private val imageLoadJobs = mutableMapOf<String, Job>()
    private val imageLoadFailures = mutableMapOf<String, Long>()
    private val visibleImageRequests = mutableMapOf<String, Int>()
    private val visibleImageUris = mutableSetOf<String>()
    private val imageLoadSemaphore = Semaphore(IMAGE_LOAD_PARALLELISM)

    private var lastPendingPlacement: PendingPlacement? = null
    private var placementBannerHeight: Int = 0
    private var bannerAnimator: ViewPropertyAnimator? = null

    private val backCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            viewModel.onEvent(CanvasUiEvent.PlacementCancelled)
        }
    }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.onEvent(CanvasUiEvent.AddImageSelected(it.toString())) }
    }
    protected fun setIsLightTheme(value: Boolean = true) {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            value
    }

    private fun handleInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBarInsets.top,
                bottom = if (imeVisible) imeInsets.bottom else systemBarInsets.bottom
            )

            windowInsets
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ), navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        handleInsets()
        onBackPressedDispatcher.addCallback(this, backCallback)
        setupToolbar()
        setupCanvasCallbacks()
        setupHiddenEditor()
        setupPlacementBanner()
        observeViewModel()
        setIsLightTheme()
        maybeShowOnboarding()
    }

    private fun maybeShowOnboarding() {
        val prefs = getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_GUIDE_SHOWN, false)) return
        binding.guideView.post {
            binding.guideView.show(
                buttonStep(binding.addTextButton, R.string.guide_add_text_title, R.string.guide_add_text_description),
                buttonStep(binding.addImageButton, R.string.guide_add_image_title, R.string.guide_add_image_description),
                buttonStep(binding.undoButton, R.string.guide_undo_title, R.string.guide_undo_description),
                buttonStep(binding.redoButton, R.string.guide_redo_title, R.string.guide_redo_description),
            )
            prefs.edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
        }
    }

    private fun buttonStep(target: View, titleRes: Int, descriptionRes: Int): GuideStep =
        GuideStep(targetView = target, titleRes = titleRes, descriptionRes = descriptionRes)

    private fun setupPlacementBanner() {
        binding.cancelButton.setOnClickListener {
            viewModel.onEvent(CanvasUiEvent.PlacementCancelled)
        }
        binding.placementBanner.post {
            placementBannerHeight = binding.placementBanner.height
            binding.placementBanner.translationY = -placementBannerHeight.toFloat()
        }
    }

    private fun setupToolbar() {
        binding.addTextButton.setOnClickListener { viewModel.onEvent(CanvasUiEvent.AddTextClicked) }
        binding.addImageButton.setOnClickListener { viewModel.onEvent(CanvasUiEvent.AddImageClicked) }
        binding.undoButton.setOnClickListener { viewModel.onEvent(CanvasUiEvent.UndoClicked) }
        binding.redoButton.setOnClickListener { viewModel.onEvent(CanvasUiEvent.RedoClicked) }
    }

    private fun setupCanvasCallbacks() {
        binding.journalCanvasView.onEvent = viewModel::onEvent
        binding.journalCanvasView.bitmapCache = bitmapCache
    }

    private fun setupHiddenEditor() {
        binding.hiddenTextEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticTextChange) return
                val id = activeEditingId ?: return
                viewModel.onEvent(CanvasUiEvent.TextChanged(id, s?.toString().orEmpty()))
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.journalCanvasView.submitState(state)
                        updateUndoRedoButtons(state)
                        if (binding.journalCanvasView.width > 0 && binding.journalCanvasView.height > 0) {
                            triggerBitmapLoadsFor(state)
                        } else {
                            binding.journalCanvasView.post {
                                triggerBitmapLoadsFor(viewModel.uiState.value)
                            }
                        }
                        updatePlacementBanner(state.pendingPlacement)
                    }
                }
                launch {
                    viewModel.effect.collect { effect ->
                        handleEffect(effect)
                    }
                }
            }
        }
    }

    private fun handleEffect(effect: CanvasUiEffect) {
        when (effect) {
            CanvasUiEffect.OpenImagePicker -> imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            is CanvasUiEffect.ShowMessage -> Toast.makeText(
                this,
                effect.message,
                Toast.LENGTH_SHORT,
            ).show()
            is CanvasUiEffect.StartTextEditing -> startInlineTextEditing(effect.id, effect.text)
            CanvasUiEffect.StopTextEditing -> stopInlineTextEditing()
        }
    }

    private fun startInlineTextEditing(id: String, text: String) {
        val editor = binding.hiddenTextEditor
        val wasEditing = activeEditingId != null
        activeEditingId = id

        isProgrammaticTextChange = true
        editor.setText(text)
        editor.setSelection(text.length)
        isProgrammaticTextChange = false

        editor.isFocusableInTouchMode = true
        editor.requestFocus()
        showKeyboard()

        if (!wasEditing) startCursorBlink()
    }

    private fun stopInlineTextEditing() {
        activeEditingId = null
        stopCursorBlink()
        hideKeyboard()
        binding.hiddenTextEditor.clearFocus()
    }

    private fun startCursorBlink() {
        cursorBlinkJob?.cancel()
        cursorBlinkJob = lifecycleScope.launch {
            while (isActive) {
                delay(CURSOR_BLINK_INTERVAL_MS)
                viewModel.onEvent(CanvasUiEvent.CursorBlinkToggled)
            }
        }
    }

    private fun stopCursorBlink() {
        cursorBlinkJob?.cancel()
        cursorBlinkJob = null
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.hiddenTextEditor, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.hiddenTextEditor.windowToken, 0)
    }

    private fun triggerBitmapLoadsFor(state: CanvasUiState) {
        if (state.objects.none { it is CanvasObjectUiModel.Image }) {
            visibleImageRequests.clear()
            visibleImageUris.clear()
            bitmapCache.clearPins()
            cancelOffscreenImageLoads(visibleImageUris)
            return
        }

        binding.journalCanvasView.collectVisibleImageRequests(visibleImageRequests)
        visibleImageUris.clear()
        visibleImageUris.addAll(visibleImageRequests.keys)
        bitmapCache.pinUris(visibleImageUris)
        cancelOffscreenImageLoads(visibleImageUris)

        val now = SystemClock.elapsedRealtime()
        visibleImageRequests.forEach { (uri, targetMaxDimensionPx) ->
            if (bitmapCache.hasAtLeast(uri, targetMaxDimensionPx)) return@forEach

            val bucket = BitmapMemoryCache.bucketFor(targetMaxDimensionPx)
            val loadKey = imageLoadKey(uri, bucket)
            if (imageLoadJobs.containsKey(loadKey)) return@forEach

            val lastFailureAt = imageLoadFailures[loadKey]
            if (lastFailureAt != null && now - lastFailureAt < IMAGE_LOAD_RETRY_BACKOFF_MS) {
                return@forEach
            }

            imageLoadJobs[loadKey] = lifecycleScope.launch {
                var shouldInvalidate = false
                try {
                    val bitmap = imageLoadSemaphore.withPermit {
                        bitmapLoader.load(uri, targetMaxDimensionPx)
                    }
                    if (bitmap == null) {
                        imageLoadFailures[loadKey] = SystemClock.elapsedRealtime()
                    } else {
                        imageLoadFailures.remove(loadKey)
                        shouldInvalidate = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    imageLoadJobs.remove(loadKey)
                    if (shouldInvalidate && uri in visibleImageUris) {
                        bitmapCache.pinUris(visibleImageUris)
                        binding.journalCanvasView.invalidate()
                    }
                }
            }
        }
    }

    private fun updateUndoRedoButtons(state: CanvasUiState) {
        setHistoryButtonState(binding.undoButton, state.canUndo)
        setHistoryButtonState(binding.redoButton, state.canRedo)
    }

    private fun setHistoryButtonState(button: ImageView, enabled: Boolean) {
        button.isEnabled = enabled
        button.isClickable = enabled
        button.alpha = 1f
        val colorRes = if (enabled) R.color.black else R.color.back_ground
        button.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, colorRes),
        )
    }

    private fun cancelOffscreenImageLoads(visibleUris: Set<String>) {
        val iterator = imageLoadJobs.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (imageLoadKeyUri(entry.key) !in visibleUris) {
                entry.value.cancel()
                iterator.remove()
            }
        }
    }

    private fun imageLoadKey(uri: String, bucket: Int): String = "$bucket|$uri"

    private fun imageLoadKeyUri(key: String): String = key.substringAfter('|')

    private fun updatePlacementBanner(pending: PendingPlacement?) {
        val previous = lastPendingPlacement
        lastPendingPlacement = pending
        backCallback.isEnabled = pending != null
        updatePlacementToolbarVisibility(pending != null)

        if (pending != null) {
            binding.placementBannerText.text = getString(
                when (pending) {
                    PendingPlacement.Text -> R.string.placement_tap_text
                    is PendingPlacement.Image -> R.string.placement_tap_image
                }
            )
            if (previous == null) {
                slideBannerDown()
            }
        } else if (previous != null) {
            slideBannerUp()
        }
    }

    private fun updatePlacementToolbarVisibility(isPlacementActive: Boolean) {
        val toolbarVisibility = if (isPlacementActive) View.GONE else View.VISIBLE
        binding.layoutButtonAdd.visibility = toolbarVisibility
        binding.layoutUndoRedo.visibility = toolbarVisibility
    }

    private fun slideBannerDown() {
        val banner = binding.placementBanner
        bannerAnimator?.cancel()
        if (placementBannerHeight == 0) {
            banner.measure(
                View.MeasureSpec.makeMeasureSpec(binding.root.width.coerceAtLeast(1), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            placementBannerHeight = banner.measuredHeight
        }
        banner.translationY = -placementBannerHeight.toFloat()
        banner.visibility = View.VISIBLE
        bannerAnimator = banner.animate()
            .translationY(0f)
            .setDuration(BANNER_ANIM_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { bannerAnimator = null }
            .apply { start() }
    }

    private fun slideBannerUp() {
        val banner = binding.placementBanner
        bannerAnimator?.cancel()
        bannerAnimator = banner.animate()
            .translationY(-placementBannerHeight.toFloat())
            .setDuration(BANNER_ANIM_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                banner.visibility = View.GONE
                bannerAnimator = null
            }
            .apply { start() }
    }

    override fun onStop() {
        activeEditingId?.let { id ->
            viewModel.onEvent(CanvasUiEvent.TextEditCommitted(id))
        }
        stopCursorBlink()
        super.onStop()
    }

    private companion object {
        const val CURSOR_BLINK_INTERVAL_MS = 500L
        const val BANNER_ANIM_MS = 220L
        const val IMAGE_LOAD_PARALLELISM = 3
        const val IMAGE_LOAD_RETRY_BACKOFF_MS = 3_000L
        const val PREFS_ONBOARDING = "canvas_editor_onboarding"
        const val KEY_GUIDE_SHOWN = "guide_shown"
    }
}
