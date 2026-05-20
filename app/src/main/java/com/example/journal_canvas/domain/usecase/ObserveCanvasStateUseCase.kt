package com.example.journal_canvas.domain.usecase

import com.example.journal_canvas.domain.repository.CanvasRepository
import javax.inject.Inject

class ObserveCanvasStateUseCase @Inject constructor(
    private val repository: CanvasRepository,
) {
    operator fun invoke() = repository.observeCanvasState()
}
