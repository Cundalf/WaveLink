package com.cundalf.wavelink.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import java.io.ByteArrayOutputStream

object RadioMedia {
    // Cache del PNG del ícono: se embebe como artwork en cada MediaItem
    // (Android Auto exige título + miniatura por ítem — criterio MC-1).
    private var artworkBytes: ByteArray? = null

    fun authenticatedUrl(radio: Radio): String = authenticatedUrl(radio.streamUrl, radio.password)

    fun authenticatedUrl(
        streamUrl: String,
        password: String?,
    ): String {
        val base = streamUrl.trim()
        if (password.isNullOrBlank()) return base
        val separator = if (base.contains('?')) "&" else "?"
        return base + separator + "auth=" + Uri.encode(password)
    }

    fun toMediaItem(
        radio: Radio,
        context: Context,
    ): MediaItem {
        val artwork = artworkPng(context)
        return MediaItem.Builder()
            .setMediaId(radio.id)
            .setUri(authenticatedUrl(radio))
            // Sin MIME/tipo, DefaultMediaItemConverter asume MEDIA_TYPE_MOVIE y
            // el receiver muestra un "video" negro con audio. Declarar la radio
            // como música/estación evita la pantalla negra.
            .setMimeType(MIME_AUDIO)
            // Marca el stream como vivo para Cast (streamType=LIVE) y fija la
            // velocidad a 1x: la radio nunca "acelera" para alcanzar el vivo.
            .setLiveConfiguration(LIVE_CONFIGURATION)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(radio.nombre)
                    .setArtist(BRAND)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    // Hoja reproducible del árbol de biblioteca (requisito de Auto).
                    .setIsBrowsable(false)
                    // Android Auto (bridge Media1) decide si el ítem es una hoja
                    // reproducible leyendo isPlayable de MediaMetadata.
                    .setIsPlayable(true)
                    .apply {
                        if (artwork.isNotEmpty()) {
                            setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                    .build(),
            )
            .build()
    }

    /**
     * Reconstruye el ítem con la metadata en vivo (título/artista/portada) para
     * que Android Auto, la notificación y la sesión de medios la muestren. El
     * nombre de la radio viaja en `subtitle` para no perder el contexto de la
     * estación, y se conserva el artwork embebido como fallback del ícono.
     */
    fun withNowPlaying(
        item: MediaItem,
        radio: Radio,
        nowPlaying: NowPlaying,
    ): MediaItem {
        val track = nowPlaying.title?.takeIf { it.isNotBlank() }
        val metadata =
            item.mediaMetadata
                .buildUpon()
                .setTitle(track ?: radio.nombre)
                .setArtist(nowPlaying.artist?.takeIf { it.isNotBlank() } ?: BRAND)
                .setSubtitle(track?.let { radio.nombre })
                .setArtworkUri(nowPlaying.coverUrl?.toUri())
                .build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    private fun artworkPng(context: Context): ByteArray {
        artworkBytes?.let { return it }
        val bytes =
            runCatching {
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
                val bitmap = drawable?.toBitmap(ARTWORK_SIZE_PX, ARTWORK_SIZE_PX) ?: return@runCatching ByteArray(0)
                bitmap.toPngBytes()
            }.getOrDefault(ByteArray(0))
        artworkBytes = bytes
        return bytes
    }

    private fun Bitmap.toPngBytes(): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            out.toByteArray()
        }

    private const val BRAND = "WaveLink"
    private const val MIME_AUDIO = MimeTypes.AUDIO_MPEG
    private const val ARTWORK_SIZE_PX = 512
    private const val PNG_QUALITY = 100
    private const val LIVE_PLAYBACK_SPEED = 1f

    private val LIVE_CONFIGURATION =
        MediaItem.LiveConfiguration.Builder()
            .setMinPlaybackSpeed(LIVE_PLAYBACK_SPEED)
            .setMaxPlaybackSpeed(LIVE_PLAYBACK_SPEED)
            .build()
}
