package com.cundalf.wavelink.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.Radio

/**
 * Árbol de biblioteca que se expone a Android Auto vía MediaLibraryService:
 *
 *     root              navegable, no reproducible (no se muestra como ítem)
 *     └── stations      carpeta "Radios": único tab de primer nivel
 *         └── <radio>   hojas reproducibles, una por estación
 *
 * Android Auto solo acepta como tabs de primer nivel los hijos navegables de la raíz
 * (root hint KEY_ROOT_CHILDREN_SUPPORTED_FLAGS = FLAG_BROWSABLE, y sin el hint asume
 * lo mismo). Si la raíz contiene radios reproducibles, Auto puede descartarlas y la
 * app aparece sin contenido.
 *
 * Media3 además exige que todo ítem declare `isBrowsable` o `isPlayable`.
 */
internal object LibraryTree {
    const val ROOT_ID = "root"
    const val STATIONS_ID = "stations"

    fun root(title: String): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            ).build()

    /** Carpeta con las radios; lleva miniatura (MC-1: título e imagen en cada ítem). */
    fun stationsFolder(context: Context): MediaItem {
        val artwork = RadioMedia.artworkPng(context)
        return MediaItem
            .Builder()
            .setMediaId(STATIONS_ID)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(context.getString(R.string.radios))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .apply {
                        if (artwork.isNotEmpty()) {
                            setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }.build(),
            ).build()
    }

    /** Hijos de [parentId]. Un nodo desconocido no tiene descendientes. */
    fun children(
        context: Context,
        parentId: String,
        radios: List<Radio>,
    ): List<MediaItem> =
        when (parentId) {
            ROOT_ID -> listOf(stationsFolder(context))
            STATIONS_ID -> radios.map { RadioMedia.toMediaItem(it, context) }
            else -> emptyList()
        }

    fun itemFor(
        context: Context,
        radio: Radio,
    ): MediaItem = RadioMedia.toMediaItem(radio, context)

    /**
     * Convierte los ítems pedidos por Android Auto en ítems reproducibles (con URI).
     *
     * Auto (bridge Media1) envía solo el mediaId elegido, o para la búsqueda por voz
     * un mediaId por defecto con `requestMetadata.searchQuery`. Lo que no se pueda
     * resolver se descarta: un ítem sin URI llega a ExoPlayer, que lanza
     * NullPointerException y la radio no suena.
     */
    fun resolve(
        context: Context,
        requested: List<MediaItem>,
        radios: List<Radio>,
        default: Radio?,
    ): List<MediaItem> =
        requested.mapNotNull { item ->
            if (item.localConfiguration != null) {
                item
            } else {
                resolveRadio(item, radios, default)?.let { RadioMedia.toMediaItem(it, context) }
            }
        }

    private fun resolveRadio(
        item: MediaItem,
        radios: List<Radio>,
        default: Radio?,
    ): Radio? {
        radios.firstOrNull { it.id == item.mediaId }?.let { return it }
        val query = item.requestMetadata.searchQuery ?: return null
        // Búsqueda por voz sin coincidencia (o sin texto): se reproduce la radio por defecto.
        return selectForSearch(query, radios, default)
    }

    /** Mismo criterio que la búsqueda del teléfono: nombre exacto y luego parcial. */
    fun matchSearch(
        query: String,
        radios: List<Radio>,
    ): Radio? = search(query, radios).firstOrNull()

    /**
     * Búsqueda por nombre: primero las coincidencias exactas y después las
     * parciales, respetando el orden de [radios] dentro de cada grupo.
     */
    fun search(
        query: String,
        radios: List<Radio>,
    ): List<Radio> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val exact = radios.filter { it.nombre.equals(text, ignoreCase = true) }
        val partial =
            radios.filter {
                !it.nombre.equals(text, ignoreCase = true) && it.nombre.contains(text, ignoreCase = true)
            }
        return exact + partial
    }

    /** Radio de un pedido de búsqueda: match por nombre o, si no hay, la por defecto. */
    fun selectForSearch(
        query: String?,
        radios: List<Radio>,
        default: Radio?,
    ): Radio? = query?.let { matchSearch(it, radios) } ?: default

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
