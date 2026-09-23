package com.cundalf.wavelink.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioJsonTest {
    private fun sampleRadios() =
        listOf(
            Radio(
                id = "a",
                nombre = "Radio A",
                streamUrl = "https://a.example/stream.mp3",
                password = null,
                esDefault = true,
                showNowPlaying = true,
                allowRequests = true,
            ),
            Radio(
                id = "b",
                nombre = "Radio B",
                streamUrl = "http://b.example/stream.mp3",
                password = "p@ss",
                esDefault = false,
                showNowPlaying = false,
                allowRequests = false,
            ),
        )

    @Test
    fun roundTripPreservesAllFields() {
        val decoded = RadioJson.decode(RadioJson.encode(sampleRadios()))
        assertEquals(sampleRadios(), decoded)
    }

    @Test
    fun jsonWithoutNewFlagsUsesCompatDefaults() {
        val json =
            """[{"id":"x","nombre":"R","url":"https://r.example/stream.mp3","password":"","default":false}]"""
        val decoded = RadioJson.decode(json).single()
        assertTrue(decoded.showNowPlaying)
        assertEquals(false, decoded.allowRequests)
    }

    @Test
    fun nullOrBlankJsonReturnsEmptyList() {
        assertTrue(RadioJson.decode(null).isEmpty())
        assertTrue(RadioJson.decode("").isEmpty())
        assertTrue(RadioJson.decode("   ").isEmpty())
    }

    @Test
    fun malformedJsonReturnsEmptyList() {
        assertTrue(RadioJson.decode("{not valid json").isEmpty())
        assertTrue(RadioJson.decode("[1,2,3]").isEmpty())
    }

    @Test
    fun blankPasswordDecodesToNull() {
        val json =
            """[{"id":"x","nombre":"R","url":"https://r.example/stream.mp3","password":"","default":false}]"""
        val decoded = RadioJson.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(null, decoded.first().password)
    }
}
