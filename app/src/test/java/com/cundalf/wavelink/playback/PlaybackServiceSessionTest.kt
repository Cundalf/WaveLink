package com.cundalf.wavelink.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.cundalf.wavelink.SubWaveCastApplication
import com.cundalf.wavelink.data.Radio
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Tests de regresión a nivel servicio (MediaBrowser real contra [PlaybackService]).
 *
 * Cubren las causas del rechazo de Google Play de la 0.3.0:
 * 1. `onConnect` devolvía comandos vacíos y el controlador no podía hacer nada.
 * 2. Ítems sin URI llegaban a ExoPlayer (la radio no sonaba).
 * 3. La raíz del browse tree debía exponer solo carpetas navegables.
 * 4. El auto en frío debía ver las radios cargadas (gate de DataStore) y MA-1:
 *    conectar nunca reproduce; el play del usuario arranca la radio por defecto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceSessionTest {
    private lateinit var app: Application
    private lateinit var serviceController: ServiceController<PlaybackService>
    private var browser: MediaBrowser? = null

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        // Estado global compartido entre tests del proceso de Robolectric.
        NowPlayingEvents.publish(null)
        PlaybackEvents.publishError(null)
    }

    @After
    fun tearDown() {
        browser?.release()
        browser = null
        shadowOf(Looper.getMainLooper()).idle()
        if (::serviceController.isInitialized) {
            serviceController.destroy()
        }
        NowPlayingEvents.publish(null)
        PlaybackEvents.publishError(null)
    }

    /**
     * Regresión 0.3.0 (causa 1): `onConnect` construido a mano devolvía comandos
     * vacíos. El controlador propio (trusted) debe poder setMediaItem/prepare/play
     * y ver la acción RESYNC del botón Sincronizar.
     */
    @Test
    fun onConnectGrantsPlayCommandsToController() {
        val mediaBrowser = connectBrowser()

        val playerCommands = mediaBrowser.availableCommands
        assertTrue(playerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertTrue(playerCommands.contains(Player.COMMAND_PREPARE))
        assertTrue(playerCommands.contains(Player.COMMAND_PLAY_PAUSE))

        assertTrue(
            mediaBrowser.availableSessionCommands.contains(
                SessionCommand(PlaybackService.ACTION_RESYNC, Bundle.EMPTY),
            ),
        )
    }

    /**
     * Regresión 0.3.0 (causa 4): un MediaItem del auto llega solo con mediaId, sin
     * URI. El servicio debe resolverlo vía LibraryTree; un mediaId desconocido se
     * descarta (nunca un ítem sin URI en la cola).
     */
    @Test
    fun resolveDropsUnknownAndKeepsResolvableMediaId() {
        val radio = seedRadio("Mi Radio", STREAM_URL)
        val mediaBrowser = connectBrowser()

        mediaBrowser.setMediaItem(MediaItem.Builder().setMediaId(radio.id).build())
        awaitCondition("el ítem conocido se resuelve y queda en la cola") {
            mediaBrowser.mediaItemCount == 1
        }
        val current = mediaBrowser.currentMediaItem
        assertNotNull(current)
        assertEquals(radio.id, current!!.mediaId)
        assertNotNull("el ítem resuelto debe tener URI", current.localConfiguration)
        assertEquals(STREAM_URL, current.localConfiguration?.uri?.toString())

        // `addMediaItem` (no `setMediaItem`) para que un ítem desconocido que no se
        // descarte quede en la cola y falle la aserción final.
        mediaBrowser.clearMediaItems()
        awaitCondition("la cola se vacía") { mediaBrowser.mediaItemCount == 0 }
        mediaBrowser.addMediaItem(MediaItem.Builder().setMediaId("radio-fantasma").build())
        mediaBrowser.addMediaItem(MediaItem.Builder().setMediaId(radio.id).build())
        awaitCondition("solo el ítem resoluble entra a la cola") {
            mediaBrowser.mediaItemCount == 1 &&
                mediaBrowser.getMediaItemAt(0).mediaId == radio.id
        }
        assertEquals(radio.id, mediaBrowser.currentMediaItem?.mediaId)
    }

    /**
     * Regresión 0.3.0 (causa 3): el auto en frío veía la carpeta vacía porque el
     * browse tree no esperaba la carga de DataStore. La raíz debe listar solo la
     * carpeta `stations` (navegable, no reproducible) y `stations` exactamente las
     * radios como hojas reproducibles. Un parent desconocido no aporta hijos.
     */
    @Test
    fun browseTreeListsStationsAfterLoad() {
        val radio = seedRadio("Mi Radio", STREAM_URL)
        val mediaBrowser = connectBrowser()

        val root = awaitFuture("getLibraryRoot", mediaBrowser.getLibraryRoot(null)).value
        assertNotNull(root)
        assertEquals(LibraryTree.ROOT_ID, root!!.mediaId)

        val rootChildren =
            awaitFuture(
                "getChildren(root)",
                mediaBrowser.getChildren(LibraryTree.ROOT_ID, 0, Int.MAX_VALUE, null),
            ).value
        assertNotNull(rootChildren)
        assertEquals(listOf(LibraryTree.STATIONS_ID), rootChildren!!.map { it.mediaId })
        assertEquals(false, rootChildren.single().mediaMetadata.isPlayable)

        awaitFuture(
            "subscribe(stations)",
            mediaBrowser.subscribe(LibraryTree.STATIONS_ID, null),
        )

        val stations =
            awaitFuture(
                "getChildren(stations)",
                mediaBrowser.getChildren(LibraryTree.STATIONS_ID, 0, Int.MAX_VALUE, null),
            ).value
        assertNotNull(stations)
        assertEquals(listOf(radio.id), stations!!.map { it.mediaId })
        val leaf = stations.single()
        assertEquals(true, leaf.mediaMetadata.isPlayable)
        assertEquals(false, leaf.mediaMetadata.isBrowsable)
        // Nota: Media3 no envía `localConfiguration` a los browsers (MediaItem.toBundle
        // omite la URI por diseño); la resolución a URI real se verifica en
        // resolveDropsUnknownAndKeepsResolvableMediaId vía setMediaItem.

        val unknown =
            awaitFuture(
                "getChildren(parent desconocido)",
                mediaBrowser.getChildren("parent-fantasma", 0, Int.MAX_VALUE, null),
            )
        assertTrue(
            "un parent desconocido no devuelve hijos",
            unknown.resultCode != LibraryResult.RESULT_SUCCESS || unknown.value.isNullOrEmpty(),
        )
    }

    /**
     * MA-1: al conectar el auto no suena nada (playWhenReady=false) y el botón
     * genérico de Play del auto arranca la radio por defecto ya precargada en la
     * cola sin reproducir. Sin red no hay audio: no se asserta STATE_READY.
     */
    @Test
    fun connectDoesNotAutoplayAndPlayLoadsDefault() {
        val radio = seedRadio("Mi Radio", STREAM_URL)
        val mediaBrowser = connectBrowser()

        assertFalse(mediaBrowser.playWhenReady)
        assertFalse(mediaBrowser.isPlaying)

        awaitCondition("la radio por defecto se precarga sin reproducir") {
            mediaBrowser.mediaItemCount == 1
        }
        assertEquals(radio.id, mediaBrowser.currentMediaItem?.mediaId)
        assertFalse(mediaBrowser.playWhenReady)
        assertFalse(mediaBrowser.isPlaying)

        mediaBrowser.play()
        awaitCondition("el play del usuario activa playWhenReady") {
            mediaBrowser.playWhenReady
        }
        assertEquals(radio.id, mediaBrowser.currentMediaItem?.mediaId)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Crea el servicio una sola vez por test, después de sembrar el repositorio. */
    private fun ensureServiceStarted(): PlaybackService {
        if (!::serviceController.isInitialized) {
            serviceController = Robolectric.buildService(PlaybackService::class.java).create()
        }
        return serviceController.get()
    }

    /**
     * Conecta un [MediaBrowser] real al servicio. Robolectric no resuelve
     * `bindService` solo: se registra el binder que devuelve `onBind()`.
     */
    private fun connectBrowser(): MediaBrowser {
        val service = ensureServiceStarted()
        val component = ComponentName(app, PlaybackService::class.java)
        val binder = service.onBind(Intent(MediaLibraryService.SERVICE_INTERFACE))
        requireNotNull(binder) { "PlaybackService.onBind devolvió null" }
        shadowOf(app).setComponentNameAndServiceForBindService(component, binder)
        val token = SessionToken(app, component)
        val future = MediaBrowser.Builder(app, token).buildAsync()
        return awaitFuture("conectar MediaBrowser", future).also { browser = it }
    }

    /** Siembra una radio en el repositorio real de la app y espera su estado. */
    private fun seedRadio(
        nombre: String,
        streamUrl: String,
    ): Radio {
        val repository = (app as SubWaveCastApplication).radioRepository
        return runBlocking {
            repository.awaitLoaded(STATE_TIMEOUT_MS)
            // El DataStore de producción es un singleton por proceso: los tests del
            // mismo sandbox de Robolectric comparten el archivo y las radios se
            // acumulan. Se parte siempre de una lista limpia.
            repository.snapshot().forEach { repository.delete(it.id) }
            repository.add(
                nombre = nombre,
                streamUrl = streamUrl,
                password = null,
                showNowPlaying = false,
                allowRequests = false,
            )
            val state =
                withTimeoutOrNull(STATE_TIMEOUT_MS) {
                    repository.state.first {
                        it.radios.size == 1 && it.radios.single().nombre == nombre
                    }
                } ?: throw AssertionError("La radio sembrada no llegó al estado del repositorio")
            state.radios.single()
        }
    }

    private fun <T> awaitFuture(
        description: String,
        future: ListenableFuture<T>,
    ): T {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (future.isDone) return future.get()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("No terminó en ${AWAIT_TIMEOUT_MS}ms: $description")
    }

    private fun awaitCondition(
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("No se cumplió en ${AWAIT_TIMEOUT_MS}ms: $description")
    }

    private companion object {
        const val STREAM_URL = "https://radio.example.com/stream.mp3"
        const val AWAIT_TIMEOUT_MS = 10_000L
        const val STATE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 5L
    }
}
