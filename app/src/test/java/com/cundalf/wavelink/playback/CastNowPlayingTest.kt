package com.cundalf.wavelink.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import java.util.Base64
import kotlin.random.Random

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

    /**
     * P15: una portada grande e incompresible (ruido) debe caber en el
     * presupuesto del canal custom de Cast (64 KB) o descartarse; nunca emitir
     * un data URL que sature el bus de mensajes del receiver.
     */
    @Test
    fun largeNoisyCoverFitsBudgetOrIsDropped() {
        val size = 480
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random(SEED)
        val pixels = IntArray(size * size) { random.nextInt() }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

        val dataUrl = CastNowPlaying.coverDataUrl(bitmap)

        if (dataUrl != null) {
            assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"))
            val encoded = dataUrl.substringAfter(',')
            assertTrue(encoded.length <= MAX_COVER_BASE64_CHARS)
            val decoded = Base64.getDecoder().decode(encoded)
            assertTrue(decoded.size <= MAX_COVER_BYTES)
            assertEquals(0xFF.toByte(), decoded[0])
            assertEquals(0xD8.toByte(), decoded[1])
        }
    }

    /** P15: una portada chica debe ser un JPEG válido que decodifica. */
    @Test
    fun smallCoverIsAValidJpegThatDecodes() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)

        val dataUrl = CastNowPlaying.coverDataUrl(bitmap)
        assertNotNull(dataUrl)
        assertTrue(dataUrl!!.startsWith("data:image/jpeg;base64,"))

        val decoded = Base64.getDecoder().decode(dataUrl.substringAfter(','))
        assertTrue(decoded.size <= MAX_COVER_BYTES)
        val restored = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        assertNotNull(restored)
        assertEquals(64, restored.width)
        assertEquals(64, restored.height)
    }

    private companion object {
        const val SEED = 42
        const val MAX_COVER_BYTES = 32 * 1024
        const val MAX_COVER_BASE64_CHARS = 44 * 1024
    }
}
