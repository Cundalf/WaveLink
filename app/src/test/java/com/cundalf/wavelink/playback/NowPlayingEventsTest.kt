package com.cundalf.wavelink.playback

import com.cundalf.wavelink.data.NowPlaying
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingEventsTest {
    @After
    fun tearDown() {
        NowPlayingEvents.publish(null)
    }

    @Test
    fun forRadioReturnsNowPlayingOnlyForTheSameRadio() {
        val update = NowPlayingEvents.Update("radio-1", NowPlaying(title = "Midnight City"))
        assertEquals("Midnight City", update.forRadio("radio-1")?.title)
        assertNull(update.forRadio("radio-2"))
        assertNull(update.forRadio(null))
    }

    @Test
    fun forRadioPropagatesNullNowPlaying() {
        val update = NowPlayingEvents.Update("radio-1", null)
        assertNull(update.forRadio("radio-1"))
    }

    @Test
    fun publishReplacesTheSnapshot() {
        NowPlayingEvents.publish(NowPlayingEvents.Update("radio-1", NowPlaying(title = "A")))
        assertEquals(
            "A",
            NowPlayingEvents.update.value
                ?.nowPlaying
                ?.title,
        )
        NowPlayingEvents.publish(NowPlayingEvents.Update("radio-2", NowPlaying(title = "B")))
        assertEquals(
            "B",
            NowPlayingEvents.update.value
                ?.nowPlaying
                ?.title,
        )
    }
}
