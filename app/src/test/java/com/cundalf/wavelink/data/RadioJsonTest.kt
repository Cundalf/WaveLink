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

    @Test
    fun entriesWithoutIdAreDiscarded() {
        val json =
            """
            [
              {"id":"","nombre":"Sin id","url":"https://a.example/stream.mp3","default":false},
              {"nombre":"Sin campo id","url":"https://b.example/stream.mp3","default":false},
              {"id":"   ","nombre":"Id en blanco","url":"https://c.example/stream.mp3","default":false},
              {"id":"ok","nombre":"Válida","url":"https://d.example/stream.mp3","password":"p",
               "default":true,"showNowPlaying":false,"allowRequests":true}
            ]
            """.trimIndent()
        val decoded = RadioJson.decode(json)
        assertEquals(
            listOf(Radio("ok", "Válida", "https://d.example/stream.mp3", "p", true, false, true)),
            decoded,
        )
    }

    @Test
    fun entriesWithoutUrlAreDiscarded() {
        val json =
            """
            [
              {"id":"a","nombre":"Sin url","url":"","default":false},
              {"id":"b","nombre":"Sin campo url","default":false},
              {"id":"c","nombre":"Url en blanco","url":"   ","default":false},
              {"id":"ok","nombre":"Válida","url":"https://d.example/stream.mp3","password":"p",
               "default":false,"showNowPlaying":false,"allowRequests":true}
            ]
            """.trimIndent()
        val decoded = RadioJson.decode(json)
        assertEquals(
            listOf(Radio("ok", "Válida", "https://d.example/stream.mp3", "p", false, false, true)),
            decoded,
        )
    }
}
