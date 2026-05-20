package com.example.journal_canvas.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.journal_canvas.data.mapper.CanvasStateDto
import com.example.journal_canvas.data.mapper.toDomain
import com.example.journal_canvas.data.mapper.toDto
import com.example.journal_canvas.domain.model.CanvasState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.canvasDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "canvas_state",
)

@Singleton
class CanvasLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    fun observeCanvasState(): Flow<CanvasState> {
        return context.canvasDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { preferences ->
                decodeCanvasState(preferences[CANVAS_JSON_KEY])
            }
    }

    suspend fun saveCanvasState(state: CanvasState) {
        val canvasJson = json.encodeToString(state.toDto())
        context.canvasDataStore.edit { preferences ->
            preferences[CANVAS_JSON_KEY] = canvasJson
        }
    }

    private fun decodeCanvasState(canvasJson: String?): CanvasState {
        if (canvasJson.isNullOrBlank()) return CanvasState()

        return try {
            json.decodeFromString<CanvasStateDto>(canvasJson).toDomain()
        } catch (_: IllegalArgumentException) {
            CanvasState()
        } catch (_: SerializationException) {
            CanvasState()
        }
    }

    private companion object {
        val CANVAS_JSON_KEY = stringPreferencesKey("canvas_json")
    }
}
