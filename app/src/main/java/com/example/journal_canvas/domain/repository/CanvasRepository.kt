package com.example.journal_canvas.domain.repository

import com.example.journal_canvas.domain.model.CanvasState
import kotlinx.coroutines.flow.Flow

interface CanvasRepository {
    fun observeCanvasState(): Flow<CanvasState>
    suspend fun saveCanvasState(state: CanvasState)
}
