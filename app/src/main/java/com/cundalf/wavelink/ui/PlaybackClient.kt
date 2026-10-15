package com.cundalf.wavelink.ui

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cundalf.wavelink.data.Radio
import com.cundalf.wavelink.playback.PlaybackService
import com.cundalf.wavelink.playback.RadioMedia
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Instantánea del estado del player, actualizada por los eventos del [MediaController]. */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val mediaId: String? = null,
    val title: String? = null,
    /** Nombre de la radio cuando [title] trae el tema en vivo. */
    val subtitle: String? = null,
)

/**
 * Cliente de la UI hacia [PlaybackService] usando [MediaController], el mismo
 * componente que usa Android Auto. La pantalla nunca toca el Player directamente.
 */
@UnstableApi
class PlaybackClient(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _playerState = MutableStateFlow(PlaybackUiState())
    val playerState: StateFlow<PlaybackUiState> = _playerState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var listener: Player.Listener? = null

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture?.addListener(
            {
                val future = controllerFuture
                if (future == null) return@addListener
                runCatching {
                    val controller = future.get() ?: return@runCatching
                    _controller.value = controller
                    val playerListener =
                        object : Player.Listener {
                            override fun onEvents(
                                player: Player,
                                events: Player.Events,
                            ) {
                                publishState(controller)
                            }
                        }
                    listener = playerListener
                    controller.addListener(playerListener)
                    publishState(controller)
                }.onFailure {
                    controllerFuture = null
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun disconnect() {
        val controller = _controller.value
        listener?.let { controller?.removeListener(it) }
        listener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _controller.value = null
        _playerState.value = PlaybackUiState()
    }

    private fun publishState(controller: MediaController) {
        _playerState.value =
            PlaybackUiState(
                isPlaying = controller.isPlaying,
                playWhenReady = controller.playWhenReady,
                playbackState = controller.playbackState,
                mediaId = controller.currentMediaItem?.mediaId,
                title = controller.mediaMetadata.title?.toString(),
                subtitle = controller.mediaMetadata.subtitle?.toString(),
            )
    }

    fun playRadio(radio: Radio) {
        val controller = _controller.value ?: return
        controller.setMediaItem(RadioMedia.toMediaItem(radio, appContext))
        controller.prepare()
        controller.play()
    }

    fun togglePlay() {
        val controller = _controller.value ?: return
        when {
            controller.isPlaying -> controller.pause()
            // Tras agotar los reintentos el player queda en STATE_IDLE;
            // un play() sin prepare() no haría nada.
            controller.playbackState == Player.STATE_IDLE -> {
                controller.prepare()
                controller.play()
            }
            else -> controller.play()
        }
    }

    fun stop() {
        val controller = _controller.value ?: return
        controller.stop()
        controller.clearMediaItems()
    }

    /** Vuelve a sintonizar el vivo (reconexión del stream) sin cambiar de radio. */
    fun resync() {
        val controller = _controller.value ?: return
        runCatching { controller.sendCustomCommand(PlaybackService.SESSION_COMMAND_RESYNC, Bundle.EMPTY) }
    }
}
