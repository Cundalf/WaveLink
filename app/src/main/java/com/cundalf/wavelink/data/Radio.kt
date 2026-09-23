package com.cundalf.wavelink.data

data class Radio(
    val id: String,
    val nombre: String,
    val streamUrl: String,
    val password: String?,
    val esDefault: Boolean,
    /** Muestra la metadata en vivo de la API SUB/WAVE (título, tapa, DJ…). */
    val showNowPlaying: Boolean = true,
    /** Muestra la UI para enviar pedidos a la cabina (si la estación los admite). */
    val allowRequests: Boolean = false,
)

data class RadioState(
    val radios: List<Radio>,
    val default: Radio?,
)
