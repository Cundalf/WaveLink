package com.cundalf.wavelink.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.cundalf.wavelink.R
import com.cundalf.wavelink.data.Radio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryTreeTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun radio(
        id: String = "radio-1",
        nombre: String = "Mi Radio",
    ) = Radio(
        id = id,
        nombre = nombre,
        streamUrl = "https://radio.example.com/stream.mp3",
        password = null,
        esDefault = true,
    )

    /** Ítem tal como lo envía Android Auto: solo mediaId, sin URI. */
    private fun requested(mediaId: String): MediaItem = MediaItem.Builder().setMediaId(mediaId).build()

    /** Pedido de búsqueda por voz: mediaId por defecto + searchQuery, sin URI. */
    private fun search(query: String?): MediaItem =
        MediaItem
            .Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata
                    .Builder()
                    .setSearchQuery(query)
                    .build(),
            ).build()

    @Test
    fun rootIsBrowsableAndNotPlayable() {
        val root = LibraryTree.root("WaveLink")
        assertEquals(LibraryTree.ROOT_ID, root.mediaId)
        assertEquals("WaveLink", root.mediaMetadata.title?.toString())
        assertEquals(true, root.mediaMetadata.isBrowsable)
        assertEquals(false, root.mediaMetadata.isPlayable)
    }

    @Test
    fun rootChildrenAreOnlyBrowsableFolders() {
        // Auto descarta las hojas reproducibles bajo la raíz: deben colgar de una carpeta.
        val children = LibraryTree.children(context, LibraryTree.ROOT_ID, listOf(radio("a")))
        val folder = children.single()
        assertEquals(LibraryTree.STATIONS_ID, folder.mediaId)
        assertEquals(context.getString(R.string.radios), folder.mediaMetadata.title?.toString())
        assertEquals(true, folder.mediaMetadata.isBrowsable)
        assertEquals(false, folder.mediaMetadata.isPlayable)
        // Criterio MC-1: título e imagen en cada ítem.
        assertTrue(folder.mediaMetadata.artworkData != null)
    }

    @Test
    fun stationsFolderListsPlayableLeaves() {
        val children =
            LibraryTree.children(context, LibraryTree.STATIONS_ID, listOf(radio("a"), radio("b")))
        assertEquals(listOf("a", "b"), children.map { it.mediaId })
        children.forEach { item ->
            assertEquals(true, item.mediaMetadata.isPlayable)
            assertEquals(false, item.mediaMetadata.isBrowsable)
            assertEquals("Mi Radio", item.mediaMetadata.title?.toString())
        }
    }

    @Test
    fun unknownParentHasNoChildren() {
        assertTrue(LibraryTree.children(context, "no-existe", listOf(radio("a"))).isEmpty())
    }

    @Test
    fun itemForResolvesRadioById() {
        val item = LibraryTree.itemFor(context, radio("radio-9"))
        assertEquals("radio-9", item.mediaId)
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, item.mediaMetadata.mediaType)
    }

    @Test
    fun resolveKeepsItemsThatAlreadyHaveUri() {
        val ready = RadioMedia.toMediaItem(radio("a"), context)
        val resolved = LibraryTree.resolve(context, listOf(ready), emptyList(), null)
        assertEquals(listOf("a"), resolved.map { it.mediaId })
        assertEquals(
            "https://radio.example.com/stream.mp3",
            resolved
                .single()
                .localConfiguration
                ?.uri
                ?.toString(),
        )
    }

    @Test
    fun resolveFillsUriForKnownMediaId() {
        val resolved =
            LibraryTree.resolve(context, listOf(requested("b")), listOf(radio("a"), radio("b")), null)
        assertEquals(listOf("b"), resolved.map { it.mediaId })
        assertEquals(
            "https://radio.example.com/stream.mp3",
            resolved
                .single()
                .localConfiguration
                ?.uri
                ?.toString(),
        )
    }

    /**
     * Regresión: un ítem sin URI llegaba a ExoPlayer (NullPointerException en
     * DefaultMediaSourceFactory) y la radio no sonaba. Debe descartarse.
     */
    @Test
    fun resolveDropsUnknownIdsInsteadOfReturningItemsWithoutUri() {
        val resolved =
            LibraryTree.resolve(context, listOf(requested("fantasma")), listOf(radio("a")), null)
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun resolveSearchQueryMatchesNameOrFallsBackToDefault() {
        val radios = listOf(radio("a", "Jazz FM"), radio("b", "Rock 98"))
        val default = radios.first()

        fun resolvedIds(query: String?): List<String> =
            LibraryTree.resolve(context, listOf(search(query)), radios, default).map { it.mediaId }

        assertEquals(listOf("b"), resolvedIds("rock"))
        assertEquals(listOf("a"), resolvedIds("JAZZ FM"))
        assertEquals(listOf("a"), resolvedIds("salsa"))
        assertEquals(listOf("a"), resolvedIds(""))
        // Sin searchQuery no es una búsqueda: ítem desconocido, se descarta.
        assertEquals(emptyList<String>(), resolvedIds(null))
    }

    @Test
    fun searchWithoutMatchOrDefaultDropsItem() {
        val radios = listOf(radio("a", "Jazz FM"))
        assertTrue(LibraryTree.resolve(context, listOf(search("salsa")), radios, null).isEmpty())
    }

    @Test
    fun searchReturnsExactMatchesBeforePartialOnes() {
        val radios =
            listOf(radio("a", "Rock 98"), radio("b", "Rock"), radio("c", "Hard Rock"), radio("d", "Jazz"))
        assertEquals(listOf("b", "a", "c"), LibraryTree.search("rock", radios).map { it.id })
        // Sin distinguir mayúsculas y con espacios sobrantes.
        assertEquals(listOf("b", "a", "c"), LibraryTree.search("  ROCK  ", radios).map { it.id })
        assertTrue(LibraryTree.search("salsa", radios).isEmpty())
        assertTrue(LibraryTree.search("", radios).isEmpty())
    }

    @Test
    fun selectForSearchFallsBackToDefault() {
        val radios = listOf(radio("a", "Jazz FM"), radio("b", "Rock 98"))
        val default = radios.first()
        assertEquals("b", LibraryTree.selectForSearch("rock", radios, default)?.id)
        assertEquals("a", LibraryTree.selectForSearch("salsa", radios, default)?.id)
        assertEquals("a", LibraryTree.selectForSearch(null, radios, default)?.id)
        // Sin radios no hay nada que reproducir: no-op.
        assertNull(LibraryTree.selectForSearch("rock", emptyList(), null))
    }

    @Test
    fun pageSlicesWithoutOverflowingOnMaxPageSize() {
        val items = (1..5).toList()
        assertEquals(items, LibraryTree.page(items, page = 0, pageSize = Int.MAX_VALUE))
        assertEquals(listOf(1, 2), LibraryTree.page(items, page = 0, pageSize = 2))
        assertEquals(listOf(3, 4), LibraryTree.page(items, page = 1, pageSize = 2))
        assertEquals(listOf(5), LibraryTree.page(items, page = 2, pageSize = 2))
        assertEquals(emptyList<Int>(), LibraryTree.page(items, page = 5, pageSize = 2))
        assertEquals(emptyList<Int>(), LibraryTree.page(items, page = 0, pageSize = 0))
        assertEquals(emptyList<Int>(), LibraryTree.page(items, page = -1, pageSize = 2))
    }
}
