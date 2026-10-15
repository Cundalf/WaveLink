package com.cundalf.wavelink.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.Language
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import com.cundalf.wavelink.data.SubWaveApi
import com.cundalf.wavelink.playback.PlaybackError

@Composable
fun SubWaveCastApp(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val requestState by viewModel.requestState.collectAsStateWithLifecycle()
    val welcomeShown by viewModel.welcomeShown.collectAsStateWithLifecycle(initialValue = null)
    val radiosLoaded by viewModel.radiosLoaded.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val dialogs = remember { DialogStates() }
    val currentRadio = state.radios.firstOrNull { it.id == player.mediaId }
    val activity = LocalActivity.current as? Activity

    HomeScreen(
        radios = state.radios,
        radiosLoaded = radiosLoaded,
        player = player,
        nowPlaying = nowPlaying,
        showNowPlaying = currentRadio?.showNowPlaying != false,
        error = error,
        castEnabled = viewModel.castEnabled,
        actions =
            HomeActions(
                onPlayRadio = { viewModel.playRadio(it) },
                onAddRadio = {
                    dialogs.editingRadio = null
                    dialogs.showEditor = true
                },
                onEditRadio = {
                    dialogs.editingRadio = it
                    dialogs.showEditor = true
                },
                onDeleteRadio = { dialogs.pendingDelete = it },
                onSetDefault = { viewModel.setDefault(it) },
                onExpandPlayer = { dialogs.showPlayer = true },
                onPlayPause = { viewModel.togglePlay() },
                onResync = { viewModel.resync() },
                onOpenSettings = { dialogs.showLanguage = true },
            ),
        modifier = modifier,
    )

    AppOverlays(
        dialogs = dialogs,
        state =
            OverlayState(
                player = player,
                nowPlaying = nowPlaying,
                error = error,
                currentRadio = currentRadio,
                requestState = requestState,
                language = language,
                welcomeShown = welcomeShown,
                castEnabled = viewModel.castEnabled,
            ),
        actions = overlayActions(viewModel, dialogs, onRecreate = { activity?.recreate() }),
    )
}

private fun overlayActions(
    viewModel: PlayerViewModel,
    dialogs: DialogStates,
    onRecreate: () -> Unit,
): OverlayActions =
    OverlayActions(
        onPlayPause = { viewModel.togglePlay() },
        onResync = { viewModel.resync() },
        onOpenRequest = {
            viewModel.resetRequest()
            dialogs.showRequest = true
        },
        onSendRequest = { text, name -> viewModel.sendRequest(text, name) },
        onDismissRequest = {
            dialogs.showRequest = false
            viewModel.resetRequest()
        },
        onSaveRadio = { radio, nombre, url, password, showNowPlaying, allowRequests ->
            viewModel.save(radio, nombre, url, password, showNowPlaying, allowRequests)
            dialogs.showEditor = false
            dialogs.editingRadio = null
        },
        onDismissEditor = {
            dialogs.showEditor = false
            dialogs.editingRadio = null
        },
        onConfirmDelete = { radio ->
            viewModel.delete(radio)
            dialogs.pendingDelete = null
        },
        onDismissDelete = { dialogs.pendingDelete = null },
        onSelectLanguage = { selected ->
            dialogs.showLanguage = false
            viewModel.setLanguage(selected)
            onRecreate()
        },
        onDismissLanguage = { dialogs.showLanguage = false },
        onStartWelcome = viewModel::markWelcomeShown,
    )

// ---------------------------------------------------------------------------
// Overlays (hojas/diálogos por encima del Home)
// ---------------------------------------------------------------------------

private class DialogStates {
    var showEditor by mutableStateOf(false)
    var showLanguage by mutableStateOf(false)
    var editingRadio by mutableStateOf<Radio?>(null)
    var pendingDelete by mutableStateOf<Radio?>(null)
    var showPlayer by mutableStateOf(false)
    var showRequest by mutableStateOf(false)
}

private class OverlayState(
    val player: PlaybackUiState,
    val nowPlaying: NowPlaying?,
    val error: PlaybackError?,
    val currentRadio: Radio?,
    val requestState: RequestUiState,
    val language: Language,
    val welcomeShown: Boolean?,
    val castEnabled: Boolean,
)

private class OverlayActions(
    val onPlayPause: () -> Unit,
    val onResync: () -> Unit = {},
    val onOpenRequest: () -> Unit,
    val onSendRequest: (String, String) -> Unit,
    val onDismissRequest: () -> Unit,
    val onSaveRadio: (Radio?, String, String, String?, Boolean, Boolean) -> Unit,
    val onDismissEditor: () -> Unit,
    val onConfirmDelete: (Radio) -> Unit,
    val onDismissDelete: () -> Unit,
    val onSelectLanguage: (Language) -> Unit,
    val onDismissLanguage: () -> Unit,
    val onStartWelcome: () -> Unit,
)

@Composable
private fun AppOverlays(
    dialogs: DialogStates,
    state: OverlayState,
    actions: OverlayActions,
) {
    if (dialogs.showPlayer) {
        FullPlayerSheet(
            player = state.player,
            nowPlaying = state.nowPlaying,
            error = state.error,
            castEnabled = state.castEnabled,
            canRequest = state.currentRadio?.allowRequests == true,
            onOpenRequest = actions.onOpenRequest,
            onPlayPause = actions.onPlayPause,
            onResync = actions.onResync,
            onDismiss = { dialogs.showPlayer = false },
        )
    }
    if (dialogs.showRequest) {
        RequestSheet(
            state = state.requestState,
            onSend = actions.onSendRequest,
            onDismiss = actions.onDismissRequest,
        )
    }
    if (dialogs.showEditor) {
        RadioEditorSheet(
            radio = dialogs.editingRadio,
            testStation = SubWaveApi::testStation,
            onDismiss = actions.onDismissEditor,
            onSave = { nombre, url, password, showNowPlaying, allowRequests ->
                actions.onSaveRadio(
                    dialogs.editingRadio,
                    nombre,
                    url,
                    password,
                    showNowPlaying,
                    allowRequests,
                )
            },
        )
    }
    dialogs.pendingDelete?.let { radio ->
        DeleteRadioDialog(
            radioName = radio.nombre,
            onConfirm = { actions.onConfirmDelete(radio) },
            onDismiss = actions.onDismissDelete,
        )
    }
    if (dialogs.showLanguage) {
        LanguageSheet(
            current = state.language,
            onSelect = actions.onSelectLanguage,
            onDismiss = actions.onDismissLanguage,
        )
    }
    if (state.welcomeShown == false) {
        WelcomeScreen(onStart = actions.onStartWelcome)
    }
}

// ---------------------------------------------------------------------------
// Pantalla principal
// ---------------------------------------------------------------------------

internal class HomeActions(
    val onPlayRadio: (Radio) -> Unit,
    val onAddRadio: () -> Unit,
    val onEditRadio: (Radio) -> Unit,
    val onDeleteRadio: (Radio) -> Unit,
    val onSetDefault: (Radio) -> Unit,
    val onExpandPlayer: () -> Unit,
    val onPlayPause: () -> Unit,
    val onResync: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
private fun HomeScreen(
    radios: List<Radio>,
    radiosLoaded: Boolean,
    player: PlaybackUiState,
    nowPlaying: NowPlaying?,
    showNowPlaying: Boolean,
    error: PlaybackError?,
    castEnabled: Boolean,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WaveTopBar(castEnabled = castEnabled, onOpenSettings = actions.onOpenSettings) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAddRadio,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.agregar_radio)) },
            )
        },
        bottomBar = {
            if (player.mediaId != null) {
                MiniPlayer(
                    player = player,
                    nowPlaying = nowPlaying,
                    error = error,
                    castActive = rememberCastActive(castEnabled),
                    onExpand = actions.onExpandPlayer,
                    onPlayPause = actions.onPlayPause,
                    onResync = actions.onResync,
                )
            }
        },
    ) { padding ->
        RadioList(
            radios = radios,
            radiosLoaded = radiosLoaded,
            player = player,
            nowPlaying = nowPlaying,
            showNowPlaying = showNowPlaying,
            actions = actions,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun RadioList(
    radios: List<Radio>,
    radiosLoaded: Boolean,
    player: PlaybackUiState,
    nowPlaying: NowPlaying?,
    showNowPlaying: Boolean,
    actions: HomeActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = contentPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showNowPlaying) {
            item(contentType = "header_now_playing") {
                Text(
                    text = stringResource(R.string.ahora_suena),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(contentType = "now_playing_card") {
                NowPlayingCard(nowPlaying = nowPlaying, hasRadio = player.mediaId != null)
            }
        }
        item(contentType = "header_radios") {
            Text(
                text = stringResource(R.string.radios),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (radios.isEmpty()) {
            if (radiosLoaded) {
                item(contentType = "empty_radios") { EmptyRadiosState(onAddRadio = actions.onAddRadio) }
            }
        } else {
            items(radios, key = { it.id }, contentType = { "radio" }) { radio ->
                RadioCard(
                    radio = radio,
                    isCurrent = player.mediaId == radio.id,
                    isPlaying = player.isPlaying,
                    onPlay = { actions.onPlayRadio(radio) },
                    onEdit = { actions.onEditRadio(radio) },
                    onDelete = { actions.onDeleteRadio(radio) },
                    onSetDefault = { actions.onSetDefault(radio) },
                )
            }
        }
    }
}
