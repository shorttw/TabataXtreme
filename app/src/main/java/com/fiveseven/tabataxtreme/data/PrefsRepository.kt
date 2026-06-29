package com.fiveseven.tabataxtreme.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fiveseven.tabataxtreme.model.Preset
import com.fiveseven.tabataxtreme.model.WorkoutConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AppState(
    val presets: List<Preset> = emptyList(),
    val lastConfig: WorkoutConfig = WorkoutConfig()
)

private val Context.dataStore by preferencesDataStore(name = "app_state")

private object Keys {
    val STATE_JSON = stringPreferencesKey("state_json")
}

class PrefsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val stateFlow: Flow<AppState> = context.dataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE_JSON])
    }

    suspend fun upsertPreset(preset: Preset) {
        context.dataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE_JSON])
            val next = current.presets.toMutableList()
            val idx = next.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
            if (idx >= 0) next[idx] = preset else next.add(preset)
            val updated = current.copy(presets = next)
            prefs[Keys.STATE_JSON] = encodeState(updated)
        }
    }

    suspend fun deletePreset(name: String) {
        context.dataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE_JSON])
            val updated = current.copy(
                presets = current.presets.filterNot { it.name.equals(name, ignoreCase = true) }
            )
            prefs[Keys.STATE_JSON] = encodeState(updated)
        }
    }

    suspend fun setLastConfig(cfg: WorkoutConfig) {
        context.dataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE_JSON])
            val updated = current.copy(lastConfig = cfg)
            prefs[Keys.STATE_JSON] = encodeState(updated)
        }
    }

    private fun decodeState(raw: String?): AppState {
        if (raw.isNullOrBlank()) return AppState()
        return runCatching { json.decodeFromString(AppState.serializer(), raw) }
            .getOrElse { AppState() }
    }

    private fun encodeState(state: AppState): String =
        json.encodeToString(AppState.serializer(), state)
}
