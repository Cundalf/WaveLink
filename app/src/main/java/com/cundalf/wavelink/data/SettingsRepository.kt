package com.cundalf.wavelink.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val WELCOME_SHOWN = booleanPreferencesKey("welcome_shown")
    }

    val welcomeShown: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.WELCOME_SHOWN] ?: false }

    suspend fun markWelcomeShown() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WELCOME_SHOWN] = true
        }
    }
}
