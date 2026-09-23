package com.cundalf.wavelink.playback

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.cundalf.wavelink.R
import com.cundalf.wavelink.SubWaveCastApplication
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import com.cundalf.wavelink.data.RadioRepository
import com.cundalf.wavelink.data.SubWaveApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de medios (Media3) que expone el árbol de biblioteca a Android Auto
 * (una entrada FLAG_PLAYABLE por radio) y mantiene viva la reproducción en segundo
 * plano. La UI y Android Auto hablan con él solo a través de un [MediaController].
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var repository: RadioRepository
    private lateinit var session: MediaLibrarySession
    private var castBridge: CastBridge? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val retryPolicy = PlaybackRetryPolicy(MAX_RETRIES)
    private var retryJob: Job? = null
    private var nowPlayingJob: Job? = null
    private var lastNowPlayingKey: String? = null
    private var lastNowPlayingRadioId: String? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as SubWaveCastApplication).radioRepository

        exoPlayer =
            ExoPlayer.Builder(this)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        exoPlayer.addListener(playerListener)

        session =
            MediaLibrarySession.Builder(this, exoPlayer, sessionCallback)
                // Botón "Sincronizar" en Android Auto, notificación y controles del sistema.
                .setMediaButtonPreferences(listOf(resyncButton()))
                .build()
        preloadDefaultRadio(session)
        castBridge =
            CastBridge(this, session).also {
                it.onCastStateChanged = { syncNowPlaying(forceRestart = true) }
                it.register()
            }
    }

    /**
     * Carga la radio por defecto en la cola SIN reproducirla. Así Android Auto
     * muestra controles funcionales (play sin selección) y se cumple MA-1:
     * la app nunca inicia reproducción por sí sola al conectar el auto.
     */
    private fun preloadDefaultRadio(session: MediaLibrarySession) {
        val default = repository.snapshotDefault() ?: return
        if (session.player.currentMediaItem == null) {
            session.player.setMediaItems(listOf(RadioMedia.toMediaItem(default, this)), 0, 0)
        }
    }

    override fun onGetSession(controller: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        retryJob?.cancel()
        nowPlayingJob?.cancel()
        castBridge?.release()
        castBridge = null
        session.release()
        exoPlayer.release()
        super.onDestroy()
    }

    /**
     * Botón "Sincronizar" que aparece en Android Auto, la notificación y los
     * controles de medios del sistema (media button preferences).
     */
    private fun resyncButton(): CommandButton =
        CommandButton.Builder()
            .setSessionCommand(SESSION_COMMAND_RESYNC)
            .setDisplayName(getString(R.string.sincronizar))
            .setCustomIconResId(R.drawable.ic_sync_24)
            .setEnabled(true)
            .build()

    /**
     * Vuelve al vivo: los streams MP3 progresivos no permiten hacer seek al live
     * edge, así que se recarga la conexión (en ExoPlayer re-prepara; en
     * CastPlayer re-emite el LOAD al receiver, que arranca en la posición live).
     */
    private fun resyncToLive() {
        val player = session.player
        val current = player.currentMediaItem ?: return
        player.setMediaItems(listOf(current))
        player.prepare()
        player.play()
    }

    private val sessionCallback =
        object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                // Sin esto el botón se filtra del layout y el controller no
                // puede enviar el comando.
                val result = super.onConnect(session, controller)
                return if (result.isAccepted) {
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                        .setAvailableSessionCommands(
                            result.availableSessionCommands.buildUpon().add(SESSION_COMMAND_RESYNC).build(),
                        )
                        .setAvailablePlayerCommands(result.availablePlayerCommands)
                        .build()
                } else {
                    result
                }
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customSessionCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (customSessionCommand.customAction == SESSION_COMMAND_RESYNC.customAction) {
                    resyncToLive()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customSessionCommand, args)
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> =
                Futures.immediateFuture(
                    LibraryResult.ofItem(LibraryTree.root(getString(R.string.app_name)), params),
                )

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String,
            ): ListenableFuture<LibraryResult<MediaItem>> {
                // El default de onSubscribe pide el ítem para validar que sea
                // navegable; sin esto, Android Auto rechaza la suscripción.
                val item =
                    when (mediaId) {
                        LibraryTree.ROOT_ID -> LibraryTree.root(getString(R.string.app_name))
                        else ->
                            repository.snapshot().firstOrNull { it.id == mediaId }
                                ?.let { LibraryTree.itemFor(this@PlaybackService, it) }
                    }
                return Futures.immediateFuture(
                    item?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
                )
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val all =
                    if (parentId == LibraryTree.ROOT_ID) {
                        LibraryTree.children(this@PlaybackService, repository.snapshot())
                    } else {
                        emptyList()
                    }
                // Media3 valida que no se devuelvan más ítems que pageSize.
                val pageItems = LibraryTree.page(all, page, pageSize)
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params),
                )
            }

            override fun onAddMediaItems(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
            ): ListenableFuture<List<MediaItem>> {
                // Android Auto (bridge Media1) envía solo el mediaId del ítem
                // seleccionado; hay que resolverlo al MediaItem completo con URI.
                val radios = repository.snapshot()
                val resolved =
                    mediaItems.map { item ->
                        radios.firstOrNull { it.id == item.mediaId }
                            ?.let { RadioMedia.toMediaItem(it, this@PlaybackService) }
                            ?: item
                    }
                return Futures.immediateFuture(resolved)
            }
        }

    private val playerListener =
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                // Android Auto puede mandar "play" sobre la radio precargada en
                // IDLE: play() no hace prepare(), así que lo hacemos acá. Solo
                // pasa con una orden de reproducción del usuario (MA-1 intacto).
                if (playWhenReady &&
                    exoPlayer.playbackState == Player.STATE_IDLE &&
                    exoPlayer.currentMediaItem != null
                ) {
                    exoPlayer.prepare()
                }
                syncNowPlaying()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    // Única señal de conexión exitosa: recién acá se puede
                    // resetear el contador de reintentos.
                    Player.STATE_READY -> {
                        retryPolicy.onConnected()
                        PlaybackEvents.publishError(null)
                        syncNowPlaying()
                    }

                    Player.STATE_BUFFERING -> PlaybackEvents.publishError(null)
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                // Nueva radio elegida: reintentos desde cero y sin error previo.
                retryPolicy.onMediaItemChanged()
                retryJob?.cancel()
                PlaybackEvents.publishError(null)
                syncNowPlaying(forceRestart = true)
            }

            override fun onPlayerError(error: PlaybackException) {
                handleError(error)
            }
        }

    /**
     * Mantiene el "ahora suena" publicado para la UI (única fuente de polling),
     * la sesión (Android Auto, notificación) y el receiver de Cast. Corre
     * mientras haya una radio cargada con la info en vivo habilitada: a ritmo
     * de 5 s con reproducción y a 30 s en pausa (la radio sigue girando).
     */
    private fun syncNowPlaying(forceRestart: Boolean = false) {
        val radio = currentRadio()
        if (radio == null || !radio.showNowPlaying) {
            nowPlayingJob?.cancel()
            nowPlayingJob = null
            NowPlayingEvents.publish(null)
            return
        }
        if (forceRestart) {
            lastNowPlayingKey = null
            nowPlayingJob?.cancel()
            nowPlayingJob = null
        }
        if (nowPlayingJob?.isActive == true) return
        nowPlayingJob =
            serviceScope.launch {
                while (isActive && session.player.currentMediaItem != null) {
                    pollNowPlaying()
                    delay(
                        if (session.player.playWhenReady) {
                            NOW_PLAYING_POLL_MS
                        } else {
                            NOW_PLAYING_POLL_IDLE_MS
                        },
                    )
                }
            }
    }

    private fun currentRadio(): Radio? {
        val mediaId = session.player.currentMediaItem?.mediaId ?: return null
        return repository.snapshot().firstOrNull { it.id == mediaId }
    }

    private suspend fun pollNowPlaying() {
        val radio = currentRadio()
        if (radio == null || !radio.showNowPlaying) {
            NowPlayingEvents.publish(null)
            return
        }
        val base = SubWaveApi.apiBase(radio.streamUrl) ?: return
        val nowPlaying = SubWaveApi.fetchNowPlaying(base) ?: return
        // La UI consume esta misma instantánea (sin su propio polling).
        NowPlayingEvents.publish(NowPlayingEvents.Update(radio.id, nowPlaying))
        if (nowPlaying.title.isNullOrBlank() &&
            nowPlaying.artist.isNullOrBlank() &&
            nowPlaying.coverUrl == null
        ) {
            return
        }
        val key = listOf(nowPlaying.title, nowPlaying.artist, nowPlaying.coverUrl).joinToString(KEY_SEPARATOR)
        if (radio.id == lastNowPlayingRadioId && key == lastNowPlayingKey) return
        lastNowPlayingRadioId = radio.id
        lastNowPlayingKey = key
        publishNowPlaying(radio, nowPlaying)
    }

    private suspend fun publishNowPlaying(
        radio: Radio,
        nowPlaying: NowPlaying,
    ) {
        val bridge = castBridge
        if (bridge?.isCasting == true) {
            // CastPlayer no puede actualizar metadata sin recargar el ítem: el
            // receiver la recibe por canal custom (con portada embebida).
            val cover =
                nowPlaying.coverUrl?.let { url ->
                    val bitmap = SubWaveApi.loadCover(url) ?: return@let null
                    // Escalar/comprimir fuera del hilo principal.
                    withContext(Dispatchers.Default) { CastNowPlaying.coverDataUrl(bitmap) }
                }
            bridge.sendNowPlaying(radio, nowPlaying, cover)
            return
        }
        val player = session.player
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        if (item.mediaId != radio.id || index == C.INDEX_UNSET) return
        val updated = RadioMedia.withNowPlaying(item, radio, nowPlaying)
        if (updated.mediaMetadata == item.mediaMetadata) return
        // ExoPlayer reemplaza la metadata sin cortar el audio (misma URL).
        player.replaceMediaItem(index, updated)
    }

    private fun handleError(error: PlaybackException) {
        val kind = classify(error)
        // 401 no se reintenta (contraseña/URL mal): es un error definitivo.
        val canRetry = kind != PlaybackError.AUTH && retryPolicy.nextRetry()
        if (!canRetry) {
            retryPolicy.reset()
            retryJob?.cancel()
            // Deja playWhenReady=false para que un nuevo play del usuario (o del
            // auto) dispare el hook de prepare de onPlayWhenReadyChanged.
            exoPlayer.pause()
            PlaybackEvents.publishError(kind)
            return
        }
        PlaybackEvents.publishError(PlaybackError.RECONNECTING)
        retryJob?.cancel()
        retryJob =
            serviceScope.launch {
                delay(RETRY_DELAY_MS)
                if (exoPlayer.playWhenReady) {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
    }

    private fun classify(error: PlaybackException): PlaybackError {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return if (cause.responseCode == HTTP_UNAUTHORIZED) {
                    PlaybackError.AUTH
                } else {
                    PlaybackError.NETWORK
                }
            }
            cause = cause.cause
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            -> PlaybackError.NETWORK

            else -> PlaybackError.STREAM
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5_000L
        private const val HTTP_UNAUTHORIZED = 401
        private const val NOW_PLAYING_POLL_MS = 5_000L
        private const val NOW_PLAYING_POLL_IDLE_MS = 30_000L
        private const val KEY_SEPARATOR = "|"

        const val ACTION_RESYNC = "com.cundalf.wavelink.action.RESYNC"
        val SESSION_COMMAND_RESYNC = SessionCommand(ACTION_RESYNC, Bundle.EMPTY)
    }
}
