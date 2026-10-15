package com.cundalf.wavelink.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.StationCapabilities
import com.cundalf.wavelink.data.StationTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private fun str(id: Int): String = context.getString(id)

    // -----------------------------------------------------------------------
    // "Ahora suena"
    // -----------------------------------------------------------------------

    @Test
    fun nowPlayingCardShowsTrackArtistAlbumAndListeners() {
        compose.setContent {
            SubWaveCastTheme {
                NowPlayingCard(
                    nowPlaying =
                        NowPlaying(
                            title = "Midnight City",
                            artist = "M83",
                            album = "Hurry Up",
                            listeners = 3,
                        ),
                    hasRadio = true,
                )
            }
        }
        compose.onNodeWithText("Midnight City").assertIsDisplayed()
        compose.onNodeWithText("M83 · Hurry Up").assertIsDisplayed()
        compose
            .onNodeWithText(context.resources.getQuantityString(R.plurals.listeners_count, 3, 3))
            .assertIsDisplayed()
    }

    @Test
    fun nowPlayingCardShowsEmptyStateWhenStationHasNoInfo() {
        compose.setContent {
            SubWaveCastTheme {
                NowPlayingCard(nowPlaying = null, hasRadio = true)
            }
        }
        compose.onNodeWithText(str(R.string.sin_track_info)).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Editor de radio
    // -----------------------------------------------------------------------

    @Test
    fun radioEditorShowsTestButtonNextToUrlAndFlagSwitches() {
        compose.setContent {
            SubWaveCastTheme {
                RadioEditorContent(
                    radio = null,
                    testStation = { _, _ -> StationTest.Failed },
                    onSave = { _, _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText(str(R.string.url_stream)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.probar)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.datos_en_vivo)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.pedidos_cabina)).performScrollTo().assertIsDisplayed()
        // Sin URL no se puede probar.
        compose.onNodeWithText(str(R.string.probar)).assertIsNotEnabled()
    }

    @Test
    fun radioEditorShowsDetectedCapabilitiesAndSavesFlags() {
        var saved: List<Any?>? = null
        compose.setContent {
            SubWaveCastTheme {
                RadioEditorContent(
                    radio = null,
                    testStation = { _, _ ->
                        StationTest.Ok(
                            stationName = "SUB/WAVE",
                            listeners = 3,
                            bitrate = 192,
                            capabilities = StationCapabilities(nowPlaying = true, requests = true),
                        )
                    },
                    onSave = { nombre, url, password, showNowPlaying, allowRequests ->
                        saved = listOf(nombre, url, password, showNowPlaying, allowRequests)
                    },
                )
            }
        }
        compose.onNodeWithText(str(R.string.nombre)).performTextInput("Mi Radio")
        compose
            .onNodeWithText(str(R.string.url_stream))
            .performTextInput("https://radio.example.com/stream.mp3")
        compose.onNodeWithText(str(R.string.probar)).assertIsEnabled()
        compose.onNodeWithText(str(R.string.probar)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.conexion_ok)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.conexion_datos_si)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.conexion_pedidos_si)).performScrollTo().assertIsDisplayed()

        // El switch de pedidos viene apagado; se activa a mano.
        compose.onNodeWithText(str(R.string.pedidos_cabina)).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.guardar)).performScrollTo().performClick()

        assertTrue(saved != null)
        assertEquals("Mi Radio", saved!![0])
        assertEquals("https://radio.example.com/stream.mp3", saved!![1])
        assertEquals(true, saved!![3])
        assertEquals(true, saved!![4])
    }

    @Test
    fun radioEditorReportsWrongPassword() {
        compose.setContent {
            SubWaveCastTheme {
                RadioEditorContent(
                    radio = null,
                    testStation = { _, _ -> StationTest.Unauthorized },
                    onSave = { _, _, _, _, _ -> },
                )
            }
        }
        compose
            .onNodeWithText(str(R.string.url_stream))
            .performTextInput("https://radio.example.com/stream.mp3")
        compose.onNodeWithText(str(R.string.probar)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.conexion_401)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun radioEditorRejectsUrlWithoutHost() {
        compose.setContent {
            SubWaveCastTheme {
                RadioEditorContent(
                    radio = null,
                    testStation = { _, _ -> StationTest.Failed },
                    onSave = { _, _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText(str(R.string.url_stream)).performTextInput("http://")
        compose.onNodeWithText(str(R.string.error_url_host)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.probar)).assertIsNotEnabled()
    }

    @Test
    fun radioEditorWarnsAboutUnencryptedHttpUrl() {
        compose.setContent {
            SubWaveCastTheme {
                RadioEditorContent(
                    radio = null,
                    testStation = { _, _ -> StationTest.Failed },
                    onSave = { _, _, _, _, _ -> },
                )
            }
        }
        compose
            .onNodeWithText(str(R.string.url_stream))
            .performTextInput("http://radio.example.com/stream.mp3")
        compose.onNodeWithText(str(R.string.conexion_sin_cifrado)).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Hoja de pedido a la cabina
    // -----------------------------------------------------------------------

    @Test
    fun requestSheetDisablesSendUntilTextIsEntered() {
        compose.setContent {
            SubWaveCastTheme {
                RequestSheetContent(
                    state = RequestUiState.Idle,
                    onSend = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.pedido_enviar)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.pedido_texto)).performTextInput("Algo de M83")
        compose.onNodeWithText(str(R.string.pedido_enviar)).assertIsEnabled()
    }

    @Test
    fun requestSheetShowsSentState() {
        compose.setContent {
            SubWaveCastTheme {
                RequestSheetContent(
                    state = RequestUiState.Sent(),
                    onSend = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.pedido_enviado)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.cerrar)).assertIsDisplayed()
    }

    @Test
    fun requestSheetShowsResolvedAndServerErrorMessage() {
        compose.setContent {
            SubWaveCastTheme {
                RequestSheetContent(
                    state = RequestUiState.Error(serverMessage = "The request line is busy"),
                    onSend = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.pedido_error)).assertIsDisplayed()
        compose.onNodeWithText("The request line is busy").assertIsDisplayed()
    }

    @Test
    fun requestSheetShowsClosedBoothState() {
        compose.setContent {
            SubWaveCastTheme {
                RequestSheetContent(
                    state = RequestUiState.Closed(serverMessage = null),
                    onSend = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.pedido_cerrado)).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Lista de radios
    // -----------------------------------------------------------------------

    @Test
    fun radioListHidesEmptyStateWhileDataStoreHasNotLoaded() {
        compose.setContent {
            SubWaveCastTheme {
                RadioList(
                    radios = emptyList(),
                    radiosLoaded = false,
                    player = idlePlayer(),
                    nowPlaying = null,
                    showNowPlaying = false,
                    actions = emptyActions(),
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
        compose.onNodeWithText(str(R.string.sin_radios)).assertDoesNotExist()
    }

    @Test
    fun radioListShowsEmptyStateOnceLoaded() {
        compose.setContent {
            SubWaveCastTheme {
                RadioList(
                    radios = emptyList(),
                    radiosLoaded = true,
                    player = idlePlayer(),
                    nowPlaying = null,
                    showNowPlaying = false,
                    actions = emptyActions(),
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
        compose.onNodeWithText(str(R.string.sin_radios)).performScrollTo().assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Reproductor completo
    // -----------------------------------------------------------------------

    @Test
    fun fullPlayerShowsRequestButtonOnlyWhenStationAllowsIt() {
        compose.setContent {
            SubWaveCastTheme {
                FullPlayerContent(
                    player = idlePlayer(),
                    nowPlaying = null,
                    error = null,
                    castEnabled = false,
                    castActive = false,
                    canRequest = true,
                    onOpenRequest = {},
                    onPlayPause = {},
                    onResync = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.pedir_tema)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fullPlayerHidesRequestButtonWhenStationDoesNotAllowIt() {
        compose.setContent {
            SubWaveCastTheme {
                FullPlayerContent(
                    player = idlePlayer(),
                    nowPlaying = null,
                    error = null,
                    castEnabled = false,
                    castActive = false,
                    canRequest = false,
                    onOpenRequest = {},
                    onPlayPause = {},
                    onResync = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.pedir_tema)).assertDoesNotExist()
    }

    private fun idlePlayer() =
        PlaybackUiState(
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            mediaId = "radio-1",
            title = "Mi Radio",
        )

    private fun emptyActions() =
        HomeActions(
            onPlayRadio = {},
            onAddRadio = {},
            onEditRadio = {},
            onDeleteRadio = {},
            onSetDefault = {},
            onExpandPlayer = {},
            onPlayPause = {},
            onResync = {},
            onOpenSettings = {},
        )
}
