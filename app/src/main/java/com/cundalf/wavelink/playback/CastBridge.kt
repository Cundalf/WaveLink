package com.cundalf.wavelink.playback

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener

/**
 * Alterna la sesión de medios entre el player local ([Player]) y un [CastPlayer].
 * Al conectar un dispositivo Cast, el [MediaLibrarySession] pasa a exponer el
 * [CastPlayer]; al desconectarse vuelve al player local y restaura el estado previo.
 */
@UnstableApi
@Suppress("DEPRECATION")
class CastBridge(
    private val context: Context,
    private val session: MediaLibrarySession,
) {
    private val castContext: CastContext? =
        runCatching {
            if (CastConfig.hasAppId(context)) CastContext.getSharedInstance(context) else null
        }.getOrNull()

    private val sessionManager: SessionManager? = castContext?.sessionManager

    private var localPlayer: Player? = null
    private var castPlayer: CastPlayer? = null
    private var wasPlayingBeforeCast = false

    /** Avisa a [PlaybackService] para re-publicar el now-playing al cambiar de player. */
    var onCastStateChanged: (() -> Unit)? = null

    val isCasting: Boolean
        get() = castPlayer != null

    // El service escucha solo al ExoPlayer local; play/pause remoto del TV
    // también tiene que despertar/detener el polling de now-playing.
    private val castPlayerListener =
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                onCastStateChanged?.invoke()
            }
        }

    private val listener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(
                session: CastSession,
                sessionId: String,
            ) = startCast()

            override fun onSessionResumed(
                session: CastSession,
                wasSuspended: Boolean,
            ) = startCast()

            override fun onSessionEnded(
                session: CastSession,
                error: Int,
            ) = stopCast()

            override fun onSessionEnding(session: CastSession) = Unit

            override fun onSessionStartFailed(
                session: CastSession,
                error: Int,
            ) = Unit

            override fun onSessionResumeFailed(
                session: CastSession,
                error: Int,
            ) = Unit

            override fun onSessionResuming(
                session: CastSession,
                sessionId: String,
            ) = Unit

            override fun onSessionStarting(session: CastSession) = Unit

            override fun onSessionSuspended(
                session: CastSession,
                reason: Int,
            ) = Unit
        }

    fun register() {
        localPlayer = session.player
        sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        onCastStateChanged = null
        stopCast()
    }

    /**
     * Empuja el "ahora suena" al receiver por canal custom: `CastPlayer` no
     * permite actualizar metadata sin recargar el ítem, y el receiver no
     * siempre puede consultar la API por CORS/contenido mixto.
     */
    fun sendNowPlaying(
        radio: Radio,
        nowPlaying: NowPlaying,
        coverDataUrl: String?,
    ) {
        val castSession = castContext?.sessionManager?.currentCastSession ?: return
        if (!castSession.isConnected) return
        runCatching {
            castSession.sendMessage(
                CastNowPlaying.NAMESPACE,
                CastNowPlaying.toJson(radio, nowPlaying, coverDataUrl),
            )
        }
    }

    private fun startCast() {
        if (castPlayer != null) return
        val castContext = castContext ?: return

        val local = session.player
        localPlayer = local
        wasPlayingBeforeCast = local.isPlaying
        if (local.isPlaying) local.pause()

        val player =
            CastPlayer(
                context,
                castContext,
                DefaultMediaItemConverter(),
                C.DEFAULT_SEEK_BACK_INCREMENT_MS,
                C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
                C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS,
            )
        val current = session.player.currentMediaItem
        if (current != null) {
            player.setMediaItems(listOf(current))
            player.prepare()
        }
        player.addListener(castPlayerListener)
        player.play()
        castPlayer = player
        session.setPlayer(player)
        onCastStateChanged?.invoke()
    }

    private fun stopCast() {
        val player = castPlayer ?: return
        castPlayer = null
        val local = localPlayer
        if (local != null) {
            session.setPlayer(local)
        }
        player.removeListener(castPlayerListener)
        player.release()
        if (local != null && wasPlayingBeforeCast) {
            local.prepare()
            local.play()
        }
        onCastStateChanged?.invoke()
    }
}
