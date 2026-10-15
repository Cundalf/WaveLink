package com.cundalf.wavelink.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import com.cundalf.wavelink.data.SubWaveApi

@Composable
internal fun NowPlayingCard(
    nowPlaying: NowPlaying?,
    hasRadio: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (nowPlaying == null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(if (hasRadio) R.string.sin_track_info else R.string.sintoniza_para_ver),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(nowPlaying = nowPlaying, size = 72.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    nowPlaying.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val artist = nowPlaying.artist ?: ""
                    val album = nowPlaying.album ?: ""
                    val subtitle = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                nowPlaying.listeners?.takeIf { it > 0 }?.let { listeners ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pluralStringResource(R.plurals.listeners_count, listeners, listeners),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CoverArt(
    nowPlaying: NowPlaying?,
    size: Dp,
) {
    val url = nowPlaying?.coverUrl
    val bitmap by
        produceState<Bitmap?>(initialValue = null, key1 = url) {
            value = url?.let { SubWaveApi.loadCover(it) }
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(size)
                    .clip(CoverShape),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CoverShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * COVER_PLACEHOLDER_SCALE),
            )
        }
    }
}

@Composable
internal fun RadioCard(
    radio: Radio,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioCardLead(
                radio = radio,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.opciones),
                    )
                }
                RadioActionsMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onEdit = {
                        menuOpen = false
                        onEdit()
                    },
                    onSetDefault = {
                        menuOpen = false
                        onSetDefault()
                    },
                    onDelete = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun RadioCardLead(
    radio: Radio,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.clickable(
                onClickLabel = stringResource(R.string.reproducir_radio, radio.nombre),
                onClick = onPlay,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent && isPlaying) {
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
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = radio.nombre,
                    style = MaterialTheme.typography.titleLarge,
                    color =
                        if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (radio.esDefault) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.por_defecto),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = sanitizedStreamUrl(radio.streamUrl),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RadioActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.editar)) },
            onClick = onEdit,
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.marcar_por_defecto)) },
            onClick = onSetDefault,
            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.eliminar)) },
            onClick = onDelete,
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        )
    }
}

@Composable
internal fun EmptyRadiosState(onAddRadio: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = stringResource(R.string.sin_radios),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddRadio) {
            Text(stringResource(R.string.agregar_radio))
        }
    }
}

/** URL sin credenciales embebidas (`user:pass@host`), para mostrar en la tarjeta. */
internal fun sanitizedStreamUrl(url: String): String {
    val trimmed = url.trim()
    val uri = runCatching { trimmed.toUri() }.getOrNull() ?: return trimmed
    val encodedUserInfo = uri.encodedUserInfo ?: return trimmed
    val encodedAuthority = uri.encodedAuthority ?: return trimmed
    val sanitizedAuthority = encodedAuthority.removePrefix("$encodedUserInfo@")
    if (sanitizedAuthority == encodedAuthority) return trimmed
    return uri.buildUpon().encodedAuthority(sanitizedAuthority).build().toString()
}

private val CoverShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(16.dp)
private const val COVER_PLACEHOLDER_SCALE = 0.45f
