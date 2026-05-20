package com.example.journal_canvas.data.repository

import com.example.journal_canvas.data.local.CanvasLocalDataSource
import com.example.journal_canvas.domain.model.CanvasState
import com.example.journal_canvas.domain.repository.CanvasRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanvasRepositoryImpl @Inject constructor(
    private val localDataSource: CanvasLocalDataSource,
) : CanvasRepository {
    override fun observeCanvasState(): Flow<CanvasState> {
        return localDataSource.observeCanvasState()
    }

    override suspend fun saveCanvasState(state: CanvasState) {
        localDataSource.saveCanvasState(state)
    }
}
