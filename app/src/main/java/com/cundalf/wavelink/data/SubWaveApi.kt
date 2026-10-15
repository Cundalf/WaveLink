package com.cundalf.wavelink.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.net.toUri
import com.cundalf.wavelink.playback.RadioMedia
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverUrl: String? = null,
    val genre: String? = null,
    val bpm: Int? = null,
    val musicalKey: String? = null,
    val djName: String? = null,
    val djTagline: String? = null,
    val listeners: Int? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val format: String? = null,
    val stationName: String? = null,
    val upNext: String? = null,
)

/** Funciones que la estación expone en su API pública ([base]/api/…). */
data class StationCapabilities(
    val nowPlaying: Boolean = false,
    val requests: Boolean = false,
    val stationName: String? = null,
    val listeners: Int? = null,
    val bitrate: Int? = null,
)

sealed interface StationTest {
    data object Checking : StationTest

    data class Ok(
        val stationName: String?,
        val listeners: Int?,
        val bitrate: Int?,
        val capabilities: StationCapabilities,
    ) : StationTest

    data object Unauthorized : StationTest

    data object Failed : StationTest
}

object SubWaveApi {
    private const val TEST_TIMEOUT_MS = 20_000L
    private const val COVER_TIMEOUT_MS = 5_000
    private const val COVER_CACHE_BYTES = 8 * 1024 * 1024
    private const val API_NOW_PLAYING = "/api/now-playing"

    private val coverCache =
        object : LruCache<String, Bitmap>(COVER_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    fun apiBase(streamUrl: String): String? {
        val uri = runCatching { streamUrl.trim().toUri() }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    suspend fun fetchNowPlaying(base: String): NowPlaying? =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = SubWaveHttp.transport.execute("$base$API_NOW_PLAYING", "GET")
            if (!isOk(result.code)) return@withContext null
            val json =
                result.body?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?: return@withContext null
            parseNowPlaying(json, base, fetchUpNext(base))
        }

    internal fun parseNowPlaying(
        json: JSONObject,
        base: String,
        upNext: String?,
    ): NowPlaying {
        val track = json.optJSONObject("nowPlaying")
        val dj = json.optJSONObject("dj")
        val stream = json.optJSONObject("stream")
        val coverId = track?.optString("subsonic_id").takeIf { !it.isNullOrBlank() }
        return NowPlaying(
            title = track?.optString("title").takeIf { !it.isNullOrBlank() },
            artist = track?.optString("artist").takeIf { !it.isNullOrBlank() },
            album = track?.optString("album").takeIf { !it.isNullOrBlank() },
            coverUrl = coverId?.let { "$base/api/cover/$it" },
            genre = track?.optString("genre").takeIf { !it.isNullOrBlank() },
            bpm = if (track?.has("bpm") == true) track.optInt("bpm") else null,
            musicalKey = track?.optString("musicalKey").takeIf { !it.isNullOrBlank() },
            djName = dj?.optString("name").takeIf { !it.isNullOrBlank() },
            djTagline = dj?.optString("tagline").takeIf { !it.isNullOrBlank() },
            listeners = if (json.has("listeners")) json.optInt("listeners") else null,
            bitrate = if (stream?.has("bitrate") == true) stream.optInt("bitrate") else null,
            sampleRate = if (stream?.has("sampleRate") == true) stream.optInt("sampleRate") else null,
            format = stream?.optString("format").takeIf { !it.isNullOrBlank() },
            stationName = dj?.optString("station").takeIf { !it.isNullOrBlank() },
            upNext = upNext,
        )
    }

    /**
     * Prueba la estación validando la URL y la contraseña contra el stream real
     * (ground truth: un `?auth=` incorrecto responde 401) y, si hay API pública,
     * detecta qué funciones expone.
     */
    suspend fun testStation(
        url: String,
        password: String?,
    ): StationTest =
        withTimeoutOrNull(TEST_TIMEOUT_MS) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val streamCode = SubWaveHttp.transport.status(RadioMedia.authenticatedUrl(url, password))
                when {
                    streamCode == null -> StationTest.Failed
                    streamCode == HTTP_UNAUTHORIZED || streamCode == HTTP_FORBIDDEN ->
                        StationTest.Unauthorized
                    streamCode in HTTP_OK_RANGE -> {
                        val capabilities = apiBase(url)?.let { probeCapabilities(it) }
                        StationTest.Ok(
                            stationName = capabilities?.stationName,
                            listeners = capabilities?.listeners,
                            bitrate = capabilities?.bitrate,
                            capabilities = capabilities ?: StationCapabilities(),
                        )
                    }
                    else -> StationTest.Failed
                }
            }
        } ?: StationTest.Failed

    private fun probeCapabilities(base: String): StationCapabilities {
        val nowPlayingResult = SubWaveHttp.transport.execute("$base$API_NOW_PLAYING", "GET")
        val nowPlaying = parseNowPlayingResponse(base, nowPlayingResult)
        return StationCapabilities(
            nowPlaying = nowPlaying != null,
            requests = SubWaveRequests.isAvailable(base),
            stationName = nowPlaying?.stationName,
            listeners = nowPlaying?.listeners,
            bitrate = nowPlaying?.bitrate,
        )
    }

    internal fun parseNowPlayingResponse(
        base: String,
        result: HttpResult,
    ): NowPlaying? {
        if (!isOk(result.code)) return null
        val json = result.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val looksLikeSubWave =
            json.has("nowPlaying") || json.has("stream") || json.has("dj") || json.has("listeners")
        if (!looksLikeSubWave) return null
        return parseNowPlaying(json, base, upNext = null)
    }

    suspend fun loadCover(url: String): Bitmap? =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            coverCache.get(url)?.let { return@withContext it }
            val bitmap =
                runCatching {
                    val conn =
                        (URL(url).openConnection() as HttpURLConnection).apply {
                            connectTimeout = COVER_TIMEOUT_MS
                            readTimeout = COVER_TIMEOUT_MS
                            setRequestProperty("User-Agent", USER_AGENT)
                        }
                    try {
                        if (!isOk(conn.responseCode)) {
                            null
                        } else {
                            conn.inputStream.use { decodeCover(it) }
                        }
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()
            bitmap?.let { coverCache.put(url, it) }
            bitmap
        }

    private fun fetchUpNext(base: String): String? {
        val result = SubWaveHttp.transport.execute("$base/api/state", "GET")
        if (!isOk(result.code) || result.body == null) return null
        val json = runCatching { JSONObject(result.body) }.getOrNull() ?: return null
        val upcoming = json.optJSONArray("upcoming") ?: return null
        if (upcoming.length() == 0) return null
        val track = upcoming.optJSONObject(0) ?: return null
        val title = track.optString("title")
        val artist = track.optString("artist")
        return when {
            title.isBlank() -> null
            artist.isBlank() -> title
            else -> "$title — $artist"
        }
    }

    private fun isOk(code: Int): Boolean = code in HTTP_OK_RANGE

    private const val USER_AGENT = "SUB/WAVE Cast/0.1"
    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299
    private val HTTP_OK_RANGE = HTTP_OK_MIN..HTTP_OK_MAX
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
}

private const val COVER_MAX_EDGE_PX = 512

private fun decodeCover(stream: InputStream): Bitmap? {
    val bytes = stream.readBytes()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = coverSampleSize(bounds.outWidth, bounds.outHeight)
        }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun coverSampleSize(
    width: Int,
    height: Int,
): Int {
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    while (width / (sampleSize * 2) >= COVER_MAX_EDGE_PX || height / (sampleSize * 2) >= COVER_MAX_EDGE_PX) {
        sampleSize *= 2
    }
    return sampleSize
}
