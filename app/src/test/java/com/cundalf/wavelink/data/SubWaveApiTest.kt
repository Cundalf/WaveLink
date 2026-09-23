package com.cundalf.wavelink.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubWaveApiTest {
    private class FakeTransport(
        private val statuses: Map<String, Int?> = emptyMap(),
        private val responses: Map<String, HttpResult> = emptyMap(),
    ) : HttpTransport {
        var lastPostUrl: String? = null
        var lastPostBody: String? = null

        override fun execute(
            url: String,
            method: String,
            body: String?,
            contentType: String?,
        ): HttpResult {
            if (method == "POST") {
                lastPostUrl = url
                lastPostBody = body
            }
            return responses[url] ?: HttpResult(404, null)
        }

        override fun status(url: String): Int? = statuses[url]
    }

    private val base = "https://radio.example.com"
    private val streamUrl = "https://radio.example.com/stream.mp3"

    @After
    fun tearDown() {
        SubWaveHttp.transport = UrlConnectionTransport
    }

    @Test
    fun apiBaseDerivesOriginFromStreamUrl() {
        assertEquals(
            "https://radio.example.com",
            SubWaveApi.apiBase("https://radio.example.com/stream.mp3"),
        )
        assertEquals(
            "http://192.168.1.10:8080",
            SubWaveApi.apiBase("http://192.168.1.10:8080/stream.mp3?auth=secreto"),
        )
    }

    @Test
    fun apiBaseRejectsInvalidUrls() {
        assertNull(SubWaveApi.apiBase("ftp://example.com/stream.mp3"))
        assertNull(SubWaveApi.apiBase("no es una url"))
        assertNull(SubWaveApi.apiBase(""))
    }

    @Test
    fun parseNowPlayingMapsAllFields() {
        val json =
            JSONObject(
                """
                {
                  "nowPlaying": {
                    "title": "Midnight City",
                    "artist": "M83",
                    "album": "Hurry Up, We're Dreaming",
                    "subsonic_id": "a1b2c3",
                    "genre": "Synthpop",
                    "bpm": 105,
                    "musicalKey": "F#m"
                  },
                  "dj": { "name": "Frequency", "tagline": "after-dark selector", "station": "SUB/WAVE" },
                  "listeners": 3,
                  "stream": { "mount": "/stream.mp3", "format": "mp3", "bitrate": 128, "sampleRate": 44100 }
                }
                """.trimIndent(),
            )
        val np = SubWaveApi.parseNowPlaying(json, "https://radio.example.com", "Nightcall — Kavinsky")
        assertEquals("Midnight City", np.title)
        assertEquals("M83", np.artist)
        assertEquals("Hurry Up, We're Dreaming", np.album)
        assertEquals("https://radio.example.com/api/cover/a1b2c3", np.coverUrl)
        assertEquals("Synthpop", np.genre)
        assertEquals(105, np.bpm)
        assertEquals("F#m", np.musicalKey)
        assertEquals("Frequency", np.djName)
        assertEquals("after-dark selector", np.djTagline)
        assertEquals("SUB/WAVE", np.stationName)
        assertEquals(3, np.listeners)
        assertEquals(128, np.bitrate)
        assertEquals(44100, np.sampleRate)
        assertEquals("mp3", np.format)
        assertEquals("Nightcall — Kavinsky", np.upNext)
    }

    @Test
    fun parseNowPlayingToleratesMissingTrack() {
        val json = JSONObject("""{ "listeners": 0, "streamOnline": true }""")
        val np = SubWaveApi.parseNowPlaying(json, "https://radio.example.com", null)
        assertNull(np.title)
        assertNull(np.coverUrl)
        assertNull(np.bitrate)
        assertEquals(0, np.listeners)
    }

    @Test
    fun testStationRejectsWrongPasswordFromStream() =
        runBlocking {
            SubWaveHttp.transport =
                FakeTransport(statuses = mapOf("$streamUrl?auth=mala" to 401))
            assertEquals(StationTest.Unauthorized, SubWaveApi.testStation(streamUrl, "mala"))
        }

    @Test
    fun testStationFailsWhenStreamIsUnreachable() =
        runBlocking {
            SubWaveHttp.transport = FakeTransport(statuses = mapOf(streamUrl to null))
            assertEquals(StationTest.Failed, SubWaveApi.testStation(streamUrl, null))
        }

    @Test
    fun testStationReportsOkForPlainStreamWithoutApi() =
        runBlocking {
            SubWaveHttp.transport =
                FakeTransport(
                    statuses = mapOf(streamUrl to 200),
                    responses =
                        mapOf(
                            "$base/api/now-playing" to HttpResult(404, "<html>not found</html>"),
                            "$base/api/request/00000000-0000-0000-0000-000000000000" to
                                HttpResult(404, "<html>not found</html>"),
                        ),
                )
            val result = SubWaveApi.testStation(streamUrl, null)
            assertTrue(result is StationTest.Ok)
            val ok = result as StationTest.Ok
            assertNull(ok.stationName)
            assertEquals(StationCapabilities(nowPlaying = false, requests = false), ok.capabilities)
        }

    @Test
    fun testStationDetectsSubWaveCapabilities() =
        runBlocking {
            SubWaveHttp.transport =
                FakeTransport(
                    statuses = mapOf("$streamUrl?auth=bien" to 200),
                    responses =
                        mapOf(
                            "$base/api/now-playing" to
                                HttpResult(
                                    200,
                                    """
                                    {
                                      "nowPlaying": { "title": "Nightcall" },
                                      "dj": { "station": "SUB/WAVE" },
                                      "listeners": 7,
                                      "stream": { "bitrate": 192 }
                                    }
                                    """.trimIndent(),
                                ),
                            "$base/api/request/00000000-0000-0000-0000-000000000000" to
                                HttpResult(404, """{"status":"unknown"}"""),
                        ),
                )
            val ok = SubWaveApi.testStation(streamUrl, "bien") as StationTest.Ok
            assertEquals("SUB/WAVE", ok.stationName)
            assertEquals(7, ok.listeners)
            assertEquals(192, ok.bitrate)
            assertTrue(ok.capabilities.nowPlaying)
            assertTrue(ok.capabilities.requests)
        }

    @Test
    fun nowPlayingCapabilityRequiresExpectedJson() {
        val html = HttpResult(200, "<html>hello</html>")
        assertNull(SubWaveApi.parseNowPlayingResponse(base, html))

        val proxyJson = HttpResult(200, """{"ok":true}""")
        assertNull(SubWaveApi.parseNowPlayingResponse(base, proxyJson))
    }

    @Test
    fun requestsProbeOnlyAcceptsJsonUnknown() {
        assertTrue(SubWaveRequests.parseRequestsProbe(HttpResult(404, """{"status":"unknown"}""")))
        assertFalse(SubWaveRequests.parseRequestsProbe(HttpResult(404, "<html>Cannot GET</html>")))
        assertFalse(SubWaveRequests.parseRequestsProbe(HttpResult(200, """{"status":"unknown"}""")))
        assertFalse(SubWaveRequests.parseRequestsProbe(HttpResult(404, null)))
    }

    @Test
    fun sendRequestParsesAcceptedId() =
        runBlocking {
            val transport =
                FakeTransport(
                    responses =
                        mapOf(
                            "$base/api/request" to
                                HttpResult(202, """{"success":true,"requestId":"abc-123","status":"pending"}"""),
                        ),
                )
            SubWaveHttp.transport = transport
            val submission = SubWaveRequests.sendRequest(base, "Algo de M83", "Clara")
            assertEquals(RequestSubmission.Accepted("abc-123"), submission)
            val body = JSONObject(transport.lastPostBody!!)
            assertEquals("Algo de M83", body.getString("text"))
            assertEquals("Clara", body.getString("name"))
            assertEquals("$base/api/request", transport.lastPostUrl)
        }

    @Test
    fun sendRequestReportsClosedBooth() =
        runBlocking {
            SubWaveHttp.transport =
                FakeTransport(
                    responses =
                        mapOf(
                            "$base/api/request" to
                                HttpResult(503, """{"success":false,"message":"Requests are temporarily closed."}"""),
                        ),
                )
            val submission = SubWaveRequests.sendRequest(base, "Algo", "")
            assertEquals(RequestSubmission.Closed("Requests are temporarily closed."), submission)
        }

    @Test
    fun sendRequestReportsValidationError() =
        runBlocking {
            SubWaveHttp.transport =
                FakeTransport(
                    responses =
                        mapOf(
                            "$base/api/request" to HttpResult(429, """{"success":false,"message":"Easy there"}"""),
                        ),
                )
            val submission = SubWaveRequests.sendRequest(base, "Algo", "")
            assertEquals(RequestSubmission.Rejected("Easy there"), submission)
        }

    @Test
    fun pollRequestParsesResolvedStatus() =
        runBlocking {
            SubWaveHttp.transport =
                FakeTransport(
                    responses =
                        mapOf(
                            "$base/api/request/abc-123" to
                                HttpResult(
                                    200,
                                    """
                                    {
                                      "status": "resolved",
                                      "ack": "Queued",
                                      "track": { "title": "Midnight City", "artist": "M83" },
                                      "queuePosition": 2
                                    }
                                    """.trimIndent(),
                                ),
                        ),
                )
            val status = SubWaveRequests.pollRequest(base, "abc-123")!!
            assertTrue(status.isResolved)
            assertEquals("Queued", status.ack)
            assertEquals("Midnight City", status.trackTitle)
            assertEquals("M83", status.trackArtist)
        }

    @Test
    fun pollRequestParsesUnknownStatus() =
        runBlocking {
            SubWaveHttp.transport =
                FakeTransport(
                    responses = mapOf("$base/api/request/x" to HttpResult(404, """{"status":"unknown"}""")),
                )
            val status = SubWaveRequests.pollRequest(base, "x")!!
            assertTrue(status.isUnknown)
            assertFalse(status.isResolved)
        }

    @Test
    fun apiMessagePrefersMessageOverError() {
        assertEquals("boom", SubWaveRequests.apiMessage("""{"message":"boom","error":"nope"}"""))
        assertEquals("nope", SubWaveRequests.apiMessage("""{"error":"nope"}"""))
        assertNull(SubWaveRequests.apiMessage("<html>oops</html>"))
        assertNull(SubWaveRequests.apiMessage(null))
    }
}
