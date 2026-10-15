package com.cundalf.wavelink.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.cundalf.wavelink.MainActivity
import com.cundalf.wavelink.R
import com.cundalf.wavelink.SubWaveCastApplication
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import com.cundalf.wavelink.data.RadioRepository
import com.cundalf.wavelink.data.SubWaveApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        exoPlayer.addListener(playerListener)

        session =
            MediaLibrarySession
                .Builder(this, exoPlayer, sessionCallback)
                // Botón "Sincronizar" en Android Auto, notificación y controles del sistema.
                .setMediaButtonPreferences(listOf(resyncButton()))
                // La notificación y el "abrir app" del sistema llevan a MainActivity.
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build()
        // Avisa a Android Auto cuando cambian las radios (alta, edición o baja en el teléfono).
        serviceScope.launch {
            repository.state
                // Antes de la primera lectura de DataStore la lista está vacía: no
                // notificar ese estado transitorio (el auto vería una carpeta vacía).
                .filter { repository.isLoaded }
                .map { it.radios }
                .distinctUntilChanged()
                .collect { radios ->
                    session.notifyChildrenChanged(LibraryTree.STATIONS_ID, radios.size, null)
                }
        }
        // En el arranque en frío las radios aún no están leídas: precargar antes de
        // tiempo dejaba la radio por defecto sin cargar.
        serviceScope.launch {
            repository.awaitLoaded(REPOSITORY_LOAD_TIMEOUT_MS)
            preloadDefaultRadio(session)
        }
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

    /**
     * Responde [block] cuando el repositorio ya leyó DataStore. Antes de eso la lista
     * está vacía: Android Auto mostraría una carpeta sin radios, o un mediaId válido
     * se descartaría como inexistente. La espera tiene tope para no colgar el callback.
     */
    private fun <T> whenLoaded(block: () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val complete = {
            if (!future.isDone) {
                runCatching(block).fold(future::set, future::setException)
            }
        }
        if (repository.isLoaded) {
            complete()
        } else {
            val job =
                serviceScope.launch {
                    repository.awaitLoaded(REPOSITORY_LOAD_TIMEOUT_MS)
                    complete()
                }
            job.invokeOnCompletion { cause ->
                // Scope cancelado (onDestroy): Media3 no debe quedar esperando un
                // callback que ya nunca va a llegar.
                if (cause != null && !future.isDone) future.cancel(false)
            }
        }
        return future
    }

    override fun onGetSession(controller: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        serviceScope.cancel()
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
        CommandButton
            .Builder(CommandButton.ICON_UNDEFINED)
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
                // AcceptedResultBuilder(session, controller) parte de los comandos que
                // corresponden al controlador (propio: todos; ajeno: solo lectura) y se le
                // suma el botón Sincronizar. No copiar los de super.onConnect(): devuelve un
                // marcador "no implementado" con comandos VACÍOS, y el controlador quedaba
                // sin setMediaItem/prepare/play (tocar Play no hacía nada).
                val trusted = controller.isTrusted
                val auto =
                    session.isAutoCompanionController(controller) ||
                        session.isAutomotiveController(controller)
                // Una vez por conexión, sin datos sensibles: diagnóstico de campo.
                Log.i(TAG, "onConnect pkg=${controller.packageName} trusted=$trusted auto=$auto")
                val defaults =
                    MediaSession.ConnectionResult
                        .AcceptedResultBuilder(session, controller)
                        .build()
                // En dispositivos donde gearhead no es privilegiado, Android Auto
                // llega como controlador no confiable y quedaría solo-lectura (mismo
                // síntoma del rechazo de Play de 0.3.0): se le dan los comandos completos.
                val sessionCommands =
                    if (auto && !trusted) {
                        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    } else {
                        defaults.availableSessionCommands
                    }
                val playerCommands =
                    if (auto && !trusted) {
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    } else {
                        defaults.availablePlayerCommands
                    }
                return MediaSession.ConnectionResult
                    .AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(
                        // Resync solo para trusted o Auto: no se expone a un ajeno no confiable.
                        if (trusted || auto) {
                            sessionCommands.buildUpon().add(SESSION_COMMAND_RESYNC).build()
                        } else {
                            sessionCommands
                        },
                    ).setAvailablePlayerCommands(playerCommands)
                    .build()
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
            ): ListenableFuture<LibraryResult<MediaItem>> =
                whenLoaded {
                    // El default de onSubscribe pide el ítem para validar que sea
                    // navegable; sin esto, Android Auto rechaza la suscripción.
                    val item =
                        when (mediaId) {
                            LibraryTree.ROOT_ID -> LibraryTree.root(getString(R.string.app_name))
                            LibraryTree.STATIONS_ID -> LibraryTree.stationsFolder(this@PlaybackService)
                            else ->
                                repository
                                    .snapshot()
                                    .firstOrNull { it.id == mediaId }
                                    ?.let { LibraryTree.itemFor(this@PlaybackService, it) }
                        }
                    item?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                whenLoaded {
                    val all = LibraryTree.children(this@PlaybackService, parentId, repository.snapshot())
                    // Media3 valida que no se devuelvan más ítems que pageSize. Auto no
                    // pagina: pide la lista completa (pageSize = Int.MAX_VALUE).
                    val pageItems = LibraryTree.page(all, page, pageSize)
                    LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
                }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<Void>> =
                whenLoaded {
                    val count = LibraryTree.search(query, repository.snapshot()).size
                    session.notifySearchResultChanged(browser, query, count, params)
                    LibraryResult.ofVoid(params)
                }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                whenLoaded {
                    val matches = LibraryTree.search(query, repository.snapshot())
                    val items =
                        LibraryTree.page(matches, page, pageSize).map {
                            RadioMedia.toMediaItem(it, this@PlaybackService)
                        }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                }

            override fun onAddMediaItems(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
            ): ListenableFuture<List<MediaItem>> {
                if (mediaItems.all { it.localConfiguration != null }) {
                    return Futures.immediateFuture(mediaItems)
                }
                return whenLoaded {
                    LibraryTree.resolve(
                        this@PlaybackService,
                        mediaItems,
                        repository.snapshot(),
                        repository.snapshotDefault(),
                    )
                }
            }

            /**
             * "Play" sin ítem cargado (botón del auto, notificación, auriculares):
             * reproduce la radio por defecto. Lo pide el usuario, así que MA-1 se cumple.
             */
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                isForPlayback: Boolean,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
                whenLoaded {
                    val default = repository.snapshotDefault()
                    val items =
                        listOfNotNull(default?.let { RadioMedia.toMediaItem(it, this@PlaybackService) })
                    MediaSession.MediaItemsWithStartPosition(items, C.INDEX_UNSET, C.TIME_UNSET)
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
                    pollNowPlayingSafely()
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

    /** La metadata en vivo es accesoria: un fallo no debe tumbar el proceso (scope sin handler). */
    private suspend fun pollNowPlayingSafely() {
        try {
            pollNowPlaying()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo actualizar el ahora suena", e)
        }
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

    /** Mapea un error de reproducción a la categoría que ve la UI. `internal` para tests. */
    internal fun classify(error: PlaybackException): PlaybackError {
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
        private const val TAG = "PlaybackService"
        private const val REPOSITORY_LOAD_TIMEOUT_MS = 5_000L
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
