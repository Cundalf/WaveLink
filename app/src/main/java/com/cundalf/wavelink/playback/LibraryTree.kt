package com.cundalf.wavelink.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.cundalf.wavelink.data.Radio

/**
 * Árbol de biblioteca que se expone a Android Auto vía MediaLibraryService.
 *
 * Media3 exige que TODOS los ítems devueltos (raíz y hojas) declaren
 * `isBrowsable` e `isPlayable`: sin esos flags el cliente no puede navegar y
 * Android Auto queda en un spinner infinito.
 */
internal object LibraryTree {
    const val ROOT_ID = "root"

    fun root(title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

    fun children(
        context: Context,
        radios: List<Radio>,
    ): List<MediaItem> = radios.map { RadioMedia.toMediaItem(it, context) }

    fun itemFor(
        context: Context,
        radio: Radio,
    ): MediaItem = RadioMedia.toMediaItem(radio, context)

    /**
     * Recorte por página que exige Media3 (no devolver más ítems que pageSize).
     * Aritmética en Long para tolerar pageSize = Int.MAX_VALUE sin overflow.
     */
    fun <T> page(
        items: List<T>,
        page: Int,
        pageSize: Int,
    ): List<T> {
        if (pageSize <= 0 || page < 0) return emptyList()
        val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(from, to)
    }
}
