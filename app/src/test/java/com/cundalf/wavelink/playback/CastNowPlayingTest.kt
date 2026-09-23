package com.cundalf.wavelink.playback

import android.graphics.Bitmap
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastNowPlayingTest {
    private val radio =
        Radio(
            id = "radio-1",
            nombre = "Mi Radio",
            streamUrl = "https://radio.example.com/stream.mp3",
            password = null,
            esDefault = true,
        )

    @Test
    fun namespaceIsAValidCustomCastChannel() {
        assertTrue(CastNowPlaying.NAMESPACE.startsWith("urn:x-cast:"))
    }

    @Test
    fun jsonCarriesTypeStationTrackArtistAndCover() {
        val json =
            JSONObject(
                CastNowPlaying.toJson(
                    radio,
                    NowPlaying(title = "Midnight City", artist = "M83"),
                    coverDataUrl = "data:image/jpeg;base64,AAA",
                ),
            )
        assertEquals(CastNowPlaying.TYPE_NOW_PLAYING, json.getString(CastNowPlaying.FIELD_TYPE))
        assertEquals("Mi Radio", json.getString(CastNowPlaying.FIELD_STATION))
        assertEquals("Midnight City", json.getString(CastNowPlaying.FIELD_TITLE))
        assertEquals("M83", json.getString(CastNowPlaying.FIELD_ARTIST))
        assertEquals("data:image/jpeg;base64,AAA", json.getString(CastNowPlaying.FIELD_COVER))
    }

    @Test
    fun jsonKeepsMissingFieldsAsNull() {
        val json =
            JSONObject(
                CastNowPlaying.toJson(radio, NowPlaying(), coverDataUrl = null),
            )
        assertTrue(json.isNull(CastNowPlaying.FIELD_TITLE))
        assertTrue(json.isNull(CastNowPlaying.FIELD_ARTIST))
        assertTrue(json.isNull(CastNowPlaying.FIELD_COVER))
    }

    @Test
    fun coverIsEncodedAsJpegDataUrl() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val dataUrl = CastNowPlaying.coverDataUrl(bitmap)
        assertNotNull(dataUrl)
        assertTrue(dataUrl!!.startsWith("data:image/jpeg;base64,"))
    }
}
