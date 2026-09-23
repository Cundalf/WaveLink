package com.cundalf.wavelink.playback

import android.content.Context
import com.cundalf.wavelink.BuildConfig
import com.cundalf.wavelink.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tipo de error publicado por el servicio; la UI lo traduce en su idioma actual. */
enum class PlaybackError {
    AUTH,
    NETWORK,
    RECONNECTING,
    STREAM,
}

object PlaybackEvents {
    private val _error = MutableStateFlow<PlaybackError?>(null)
    val error: StateFlow<PlaybackError?> = _error.asStateFlow()

    fun publishError(error: PlaybackError?) {
        _error.value = error
    }
}

object CastConfig {
    /**
     * El botón de cast aparece si hay un receiver app ID propio o si el build se
     * generó con -Psubwave.castAllowDefault=true (receiver por defecto de Google,
     * solo para pruebas; Play exige un ID propio registrado).
     */
    fun hasAppId(context: Context): Boolean =
        context.getString(R.string.cast_receiver_id).isNotBlank() ||
            BuildConfig.CAST_ALLOW_DEFAULT
}
