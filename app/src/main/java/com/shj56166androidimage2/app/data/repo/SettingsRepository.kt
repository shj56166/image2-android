package com.shj56166androidimage2.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shj56166androidimage2.app.data.model.AppSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "image_playground_settings")
private val SETTINGS_JSON = stringPreferencesKey("settings_json")

internal fun createDefaultSettingsState(): AppSettingsState =
    AppSettingsState(
        activeProfileId = "",
        profiles = emptyList(),
    )

class SettingsRepository(private val context: Context) {
    val defaultState = createDefaultSettingsState()

    val settings: Flow<AppSettingsState> = context.settingsDataStore.data.map { prefs ->
        prefs[SETTINGS_JSON]?.let {
            runCatching { AppJson.decodeFromString(AppSettingsState.serializer(), it) }.getOrNull()
        } ?: defaultState
    }

    suspend fun save(state: AppSettingsState) {
        context.settingsDataStore.edit { prefs ->
            prefs[SETTINGS_JSON] = AppJson.encodeToString(AppSettingsState.serializer(), state)
        }
    }

    suspend fun reset() {
        save(defaultState)
    }
}
