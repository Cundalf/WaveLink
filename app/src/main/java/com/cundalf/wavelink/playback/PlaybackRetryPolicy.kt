package com.cundalf.wavelink.playback

/**
 * Cuenta los reintentos automáticos de la radio actual.
 *
 * Importante: un reintento interno vuelve a pasar por BUFFERING antes de fallar,
 * y `play()` puede re-disparar `onPlayWhenReadyChanged`. Resetear el contador en
 * esos eventos producía un loop infinito de "Reconectando…": la única señal de
 * éxito es STATE_READY (o el cambio de radio, que arranca de cero).
 */
internal class PlaybackRetryPolicy(
    private val maxRetries: Int,
) {
    var retries = 0
        private set

    /** La conexión llegó a STATE_READY: la recuperación fue exitosa. */
    fun onConnected() {
        retries = 0
    }

    /** El usuario eligió otra radio: los reintentos arrancan de cero. */
    fun onMediaItemChanged() {
        retries = 0
    }

    fun reset() {
        retries = 0
    }

    /** Consume un reintento. Devuelve false cuando ya no quedan (y reinicia). */
    fun nextRetry(): Boolean {
        if (retries >= maxRetries) {
            retries = 0
            return false
        }
        retries++
        return true
    }
}
