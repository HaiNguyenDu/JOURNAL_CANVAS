package com.example.journal_canvas.di

import com.example.journal_canvas.data.repository.CanvasRepositoryImpl
import com.example.journal_canvas.domain.repository.CanvasRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCanvasRepository(
        impl: CanvasRepositoryImpl,
    ): CanvasRepository
}
