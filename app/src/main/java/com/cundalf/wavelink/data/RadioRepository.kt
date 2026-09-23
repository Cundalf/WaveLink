package com.cundalf.wavelink.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

private val Context.radioDataStore by preferencesDataStore(name = "radios")

class RadioRepository(private val context: Context) {
    private object Keys {
        val RADIOS = stringPreferencesKey("radios_json")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: StateFlow<RadioState> =
        context.radioDataStore.data
            .map { prefs -> RadioJson.decode(prefs[Keys.RADIOS]) }
            .map { list -> RadioState(list, list.firstOrNull { it.esDefault }) }
            .stateIn(scope, SharingStarted.Eagerly, RadioState(emptyList(), null))

    fun snapshot(): List<Radio> = state.value.radios

    fun snapshotDefault(): Radio? = state.value.default

    suspend fun add(
        nombre: String,
        streamUrl: String,
        password: String?,
        showNowPlaying: Boolean,
        allowRequests: Boolean,
    ) {
        context.radioDataStore.edit { prefs ->
            val current = RadioJson.decode(prefs[Keys.RADIOS]).toMutableList()
            val makeDefault = current.isEmpty()
            current.add(
                Radio(
                    id = UUID.randomUUID().toString(),
                    nombre = nombre,
                    streamUrl = streamUrl,
                    password = password?.takeIf { it.isNotBlank() },
                    esDefault = makeDefault,
                    showNowPlaying = showNowPlaying,
                    allowRequests = allowRequests,
                ),
            )
            prefs[Keys.RADIOS] = RadioJson.encode(normalize(current))
        }
    }

    suspend fun update(
        id: String,
        nombre: String,
        streamUrl: String,
        password: String?,
        showNowPlaying: Boolean,
        allowRequests: Boolean,
    ) {
        context.radioDataStore.edit { prefs ->
            val current =
                RadioJson.decode(prefs[Keys.RADIOS]).map { r ->
                    if (r.id == id) {
                        r.copy(
                            nombre = nombre,
                            streamUrl = streamUrl,
                            password = password?.takeIf { it.isNotBlank() },
                            showNowPlaying = showNowPlaying,
                            allowRequests = allowRequests,
                        )
                    } else {
                        r
                    }
                }
            prefs[Keys.RADIOS] = RadioJson.encode(normalize(current))
        }
    }

    suspend fun delete(id: String) {
        context.radioDataStore.edit { prefs ->
            val current = RadioJson.decode(prefs[Keys.RADIOS]).filterNot { it.id == id }
            prefs[Keys.RADIOS] = RadioJson.encode(normalize(current))
        }
    }

    suspend fun setDefault(id: String) {
        context.radioDataStore.edit { prefs ->
            val current = RadioJson.decode(prefs[Keys.RADIOS]).map { it.copy(esDefault = it.id == id) }
            prefs[Keys.RADIOS] = RadioJson.encode(normalize(current))
        }
    }

    private fun normalize(list: List<Radio>): List<Radio> {
        if (list.isEmpty()) return list
        val withDefault = list.filter { it.esDefault }
        return when {
            withDefault.size == 1 -> list
            withDefault.isEmpty() ->
                list.mapIndexed {
                        index,
                        radio,
                    ->
                    if (index == 0) radio.copy(esDefault = true) else radio.copy(esDefault = false)
                }
            else -> {
                val keepFirst = withDefault.first().id
                list.map { if (it.id == keepFirst) it.copy(esDefault = true) else it.copy(esDefault = false) }
            }
        }
    }
}
