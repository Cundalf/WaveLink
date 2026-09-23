package com.cundalf.wavelink.playback

import androidx.media3.common.MediaMetadata
import com.cundalf.wavelink.data.Radio
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryTreeTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun radio(id: String = "radio-1") =
        Radio(
            id = id,
            nombre = "Mi Radio",
            streamUrl = "https://radio.example.com/stream.mp3",
            password = null,
            esDefault = true,
        )

    @Test
    fun rootIsBrowsableAndNotPlayable() {
        val root = LibraryTree.root("WaveLink")
        assertEquals(LibraryTree.ROOT_ID, root.mediaId)
        assertEquals("WaveLink", root.mediaMetadata.title?.toString())
        assertEquals(true, root.mediaMetadata.isBrowsable)
        assertEquals(false, root.mediaMetadata.isPlayable)
    }

    @Test
    fun childrenArePlayableLeaves() {
        val children = LibraryTree.children(context, listOf(radio("a"), radio("b")))
        assertEquals(2, children.size)
        children.forEach { item ->
            assertEquals(true, item.mediaMetadata.isPlayable)
            assertEquals(false, item.mediaMetadata.isBrowsable)
            assertEquals("Mi Radio", item.mediaMetadata.title?.toString())
        }
        assertEquals(listOf("a", "b"), children.map { it.mediaId })
    }

    @Test
    fun itemForResolvesRadioById() {
        val item = LibraryTree.itemFor(context, radio("radio-9"))
        assertEquals("radio-9", item.mediaId)
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, item.mediaMetadata.mediaType)
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
