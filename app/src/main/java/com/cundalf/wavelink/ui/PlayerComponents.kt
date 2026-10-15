package com.cundalf.wavelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.playback.PlaybackError
import java.util.Locale

// ---------------------------------------------------------------------------
// Top bar (botón de cast persistente arriba a la derecha, per design system)
// ---------------------------------------------------------------------------

@Composable
internal fun WaveTopBar(
    castEnabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.ajustes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (castEnabled) {
            CastButton()
        }
    }
}

// ---------------------------------------------------------------------------
// Mini player persistente (Bottom Sheet colapsado sobre la barra del sistema)
// ---------------------------------------------------------------------------

@Composable
internal fun MiniPlayer(
    player: PlaybackUiState,
    nowPlaying: NowPlaying?,
    error: PlaybackError?,
    castActive: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onResync: () -> Unit = {},
) {
    val (statusText, statusColor) = miniStatus(player, nowPlaying, error, castActive)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (player.isPlaying) {
                    EqualizerBars(animating = true)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(
                            onClickLabel = stringResource(R.string.ampliar_reproductor),
                            onClick = onExpand,
                        ),
            ) {
                Text(
                    text = player.subtitle ?: player.title ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiniPlayerControls(
                playWhenReady = player.playWhenReady,
                onResync = onResync,
                onPlayPause = onPlayPause,
                onExpand = onExpand,
            )
        }
    }
}

@Composable
private fun MiniPlayerControls(
    playWhenReady: Boolean,
    onResync: () -> Unit,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
) {
    Row {
        IconButton(onClick = onResync, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_sync_24),
                contentDescription = stringResource(R.string.sincronizar_desc),
            )
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
            Icon(
                painter =
                    painterResource(
                        if (playWhenReady) R.drawable.ic_pause_24 else R.drawable.ic_play_24,
                    ),
                contentDescription =
                    stringResource(
                        if (playWhenReady) R.string.pause else R.string.play,
                    ),
            )
        }
        IconButton(onClick = onExpand, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.ampliar_reproductor),
            )
        }
    }
}

@Composable
private fun miniStatus(
    player: PlaybackUiState,
    nowPlaying: NowPlaying?,
    error: PlaybackError?,
    castActive: Boolean,
): Pair<String, Color> {
    val errorText = errorPresentation(error)
    return when {
        errorText != null -> errorText

        castActive -> stringResource(R.string.cast_activo) to MaterialTheme.colorScheme.primary

        player.playWhenReady && player.playbackState == Player.STATE_BUFFERING ->
            stringResource(R.string.conectando) to MaterialTheme.colorScheme.primary

        player.isPlaying -> {
            val track = nowPlaying?.title
            val text =
                if (track.isNullOrBlank()) {
                    stringResource(R.string.reproduciendo)
                } else {
                    stringResource(R.string.reproduciendo_track, track)
                }
            text to MaterialTheme.colorScheme.primary
        }

        else -> stringResource(R.string.pausado) to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// ---------------------------------------------------------------------------
// Reproductor expandido (Bottom Sheet a pantalla completa)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullPlayerSheet(
    player: PlaybackUiState,
    nowPlaying: NowPlaying?,
    error: PlaybackError?,
    castEnabled: Boolean,
    canRequest: Boolean,
    onOpenRequest: () -> Unit,
    onPlayPause: () -> Unit,
    onResync: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        FullPlayerContent(
            player = player,
            nowPlaying = nowPlaying,
            error = error,
            castEnabled = castEnabled,
            castActive = rememberCastActive(castEnabled),
            canRequest = canRequest,
            onOpenRequest = onOpenRequest,
            onPlayPause = onPlayPause,
            onResync = onResync,
        )
    }
}

@Composable
internal fun FullPlayerContent(
    player: PlaybackUiState,
    nowPlaying: NowPlaying?,
    error: PlaybackError?,
    castEnabled: Boolean,
    castActive: Boolean,
    canRequest: Boolean,
    onOpenRequest: () -> Unit,
    onPlayPause: () -> Unit,
    onResync: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = player.subtitle ?: player.title ?: stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))
        TrackSection(nowPlaying)

        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BigPlayButton(
                playWhenReady = player.playWhenReady,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                onClick = onPlayPause,
            )
            IconButton(onClick = onResync, modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_sync_24),
                    contentDescription = stringResource(R.string.sincronizar_desc),
                    modifier = Modifier.size(28.dp),
                )
            }
            if (castEnabled) {
                CastButton()
            }
        }

        Spacer(Modifier.height(20.dp))
        PlayerStatusLine(player = player, error = error, castActive = castActive)

        if (canRequest) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenRequest) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pedir_tema))
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(error = error)
        }
    }
}

@Composable
private fun TrackSection(nowPlaying: NowPlaying?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CoverArt(nowPlaying = nowPlaying, size = 220.dp)

        Spacer(Modifier.height(16.dp))
        nowPlaying?.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            val artist = nowPlaying.artist ?: ""
            val album = nowPlaying.album ?: ""
            val subtitle = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        TechnicalLine(nowPlaying)

        nowPlaying?.djName?.let { dj ->
            Spacer(Modifier.height(12.dp))
            val tagline = nowPlaying.djTagline?.let { ", $it" } ?: ""
            Text(
                text =
                    stringResource(
                        R.string.en_el_aire_dj,
                        stringResource(R.string.en_el_aire),
                        dj + tagline,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        nowPlaying?.upNext?.let { upNext ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.a_continuacion, upNext),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TechnicalLine(nowPlaying: NowPlaying?) {
    val parts = mutableListOf<String>()
    nowPlaying?.let { np ->
        np.genre?.takeIf { it.isNotBlank() }?.let { parts += it }
        np.listeners?.takeIf { it > 0 }?.let { listeners ->
            parts += pluralStringResource(R.plurals.listeners_count, listeners, listeners)
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PlayerStatusLine(
    player: PlaybackUiState,
    error: PlaybackError?,
    castActive: Boolean,
) {
    val errorText = errorPresentation(error)
    val (text, color) =
        when {
            errorText != null -> errorText

            castActive ->
                stringResource(
                    R.string.cast_activo_detalle,
                    stringResource(R.string.cast_activo),
                    stringResource(R.string.control_remoto),
                ) to MaterialTheme.colorScheme.primary

            player.playWhenReady && player.playbackState == Player.STATE_BUFFERING ->
                stringResource(R.string.conectando) to MaterialTheme.colorScheme.primary

            player.isPlaying -> stringResource(R.string.reproduciendo) to MaterialTheme.colorScheme.primary

            else -> stringResource(R.string.pausado) to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulsingDot(color = color, pulsing = player.isPlaying || castActive)
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
internal fun errorPresentation(error: PlaybackError?): Pair<String, Color>? {
    if (error == null) return null
    return when (error) {
        PlaybackError.AUTH -> stringResource(R.string.error_401) to MaterialTheme.colorScheme.error

        PlaybackError.NETWORK -> stringResource(R.string.error_red) to ReconnectColor

        PlaybackError.RECONNECTING -> stringResource(R.string.reconectando) to ReconnectColor

        PlaybackError.STREAM -> stringResource(R.string.error_stream) to ReconnectColor
    }
}

@Composable
private fun ErrorBanner(error: PlaybackError) {
    val (message, color) = errorPresentation(error)!!
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun BigPlayButton(
    playWhenReady: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 3.dp,
            )
        } else {
            Icon(
                painter = painterResource(if (playWhenReady) R.drawable.ic_pause_24 else R.drawable.ic_play_24),
                contentDescription = stringResource(if (playWhenReady) R.string.pause else R.string.play),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}
