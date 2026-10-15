package com.cundalf.wavelink.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

private const val TAG = "RadioRepository"

private val Context.radioDataStore by preferencesDataStore(
    name = "radios",
    // Un archivo corrupto se reemplaza por preferencias vacías dentro de DataStore. Sin
    // esto, la excepción llega al `catch` de `state` y completa el upstream: el flujo
    // queda muerto y no llegan más updates en todo el proceso.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Fuente única de las radios guardadas.
 *
 * El constructor con [DataStore] es `internal`: es el seam que usan los tests para
 * aislar cada caso con su propio archivo. Producción usa [Context.radioDataStore].
 */
class RadioRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.radioDataStore)

    private object Keys {
        val RADIOS = stringPreferencesKey("radios_json")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Se completa con la primera lectura de DataStore. Hasta entonces snapshot() está
    // vacío: los callbacks de Android Auto deben esperar en vez de responder con una
    // biblioteca vacía.
    private val loaded = CompletableDeferred<Unit>()

    val state: StateFlow<RadioState> =
        dataStore.data
            // Un archivo de preferencias ilegible no debe tumbar el proceso: lista vacía.
            .catch { e ->
                if (e is IOException) {
                    Log.w(TAG, "No se pudo leer el DataStore de radios; se emite lista vacía", e)
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }.map { prefs -> RadioJson.decode(prefs[Keys.RADIOS]) }
            .map { list -> RadioState(list, list.firstOrNull { it.esDefault }) }
            .onEach { loaded.complete(Unit) }
            .stateIn(scope, SharingStarted.Eagerly, RadioState(emptyList(), null))

    val isLoaded: Boolean
        get() = loaded.isCompleted

    /** Suspende hasta la primera lectura de DataStore, o hasta [timeoutMs] si nunca responde. */
    suspend fun awaitLoaded(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { loaded.await() }
    }

    fun snapshot(): List<Radio> = state.value.radios

    fun snapshotDefault(): Radio? = state.value.default

    suspend fun add(
        nombre: String,
        streamUrl: String,
        password: String?,
        showNowPlaying: Boolean,
        allowRequests: Boolean,
    ) {
        dataStore.edit { prefs ->
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
        dataStore.edit { prefs ->
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
        dataStore.edit { prefs ->
            val current = RadioJson.decode(prefs[Keys.RADIOS]).filterNot { it.id == id }
            prefs[Keys.RADIOS] = RadioJson.encode(normalize(current))
        }
    }

    suspend fun setDefault(id: String) {
        dataStore.edit { prefs ->
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
