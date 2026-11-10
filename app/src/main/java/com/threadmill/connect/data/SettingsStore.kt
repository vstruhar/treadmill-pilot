package com.threadmill.connect.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val treadmillAddress: String? = null,
    val treadmillName: String? = null,
    val voiceEnabled: Boolean = true,
    val accent: String = "Lime",
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            treadmillAddress = prefs[ADDRESS],
            treadmillName = prefs[NAME],
            voiceEnabled = prefs[VOICE] ?: true,
            accent = prefs[ACCENT] ?: "Lime",
        )
    }

    suspend fun rememberTreadmill(address: String, name: String) {
        store.edit { prefs ->
            prefs[ADDRESS] = address
            prefs[NAME] = name
        }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[VOICE] = enabled }
    }

    suspend fun setAccent(name: String) {
        store.edit { prefs -> prefs[ACCENT] = name }
    }

    private companion object {
        val ADDRESS = stringPreferencesKey("treadmill_address")
        val NAME = stringPreferencesKey("treadmill_name")
        val VOICE = booleanPreferencesKey("voice")
        val ACCENT = stringPreferencesKey("accent")
    }
}
