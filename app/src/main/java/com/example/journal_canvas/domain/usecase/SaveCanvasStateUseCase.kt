package com.example.journal_canvas.domain.usecase

import com.example.journal_canvas.domain.model.CanvasState
import com.example.journal_canvas.domain.repository.CanvasRepository
import javax.inject.Inject

class SaveCanvasStateUseCase @Inject constructor(
    private val repository: CanvasRepository,
) {
    suspend operator fun invoke(state: CanvasState) {
        repository.saveCanvasState(state)
    }
}
