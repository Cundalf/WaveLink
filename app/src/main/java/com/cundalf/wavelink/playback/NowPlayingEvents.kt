package com.cundalf.wavelink.playback

import com.cundalf.wavelink.data.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Publicación única del "ahora suena": el [PlaybackService] consulta la API de
 * la estación y la UI lee de acá (mismo proceso). Sin esto, pantalla y servicio
 * hacían polling duplicado contra la estación cada 5 s.
 */
object NowPlayingEvents {
    /** Instantánea del now-playing de la radio que corresponde a [radioId]. */
    data class Update(
        val radioId: String,
        val nowPlaying: NowPlaying?,
    ) {
        /** Devuelve el now-playing solo si pertenece a la radio [mediaId]. */
        fun forRadio(mediaId: String?): NowPlaying? = nowPlaying.takeIf { radioId == mediaId }
    }

    private val _update = MutableStateFlow<Update?>(null)
    val update: StateFlow<Update?> = _update.asStateFlow()

    fun publish(update: Update?) {
        _update.value = update
    }
}
