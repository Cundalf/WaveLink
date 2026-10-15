package com.cundalf.wavelink.playback

import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.android.gms.cast.MediaInfo as CastMediaInfo
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioMediaTest {
    private fun radio(
        url: String,
        password: String? = null,
    ) = Radio(
        id = "radio-1",
        nombre = "Mi Radio",
        streamUrl = url,
        password = password,
        esDefault = true,
    )

    @Test
    fun noPasswordLeavesUrlUnchanged() {
        val url = "https://radio.example.com/stream.mp3"
        assertEquals(url, RadioMedia.authenticatedUrl(radio(url)))
    }

    @Test
    fun blankPasswordLeavesUrlUnchanged() {
        val url = "https://radio.example.com/stream.mp3"
        assertEquals(url, RadioMedia.authenticatedUrl(radio(url, password = "  ")))
    }

    @Test
    fun passwordAppendsEncodedAuthParam() {
        val url =
            RadioMedia.authenticatedUrl(
                radio("https://radio.example.com/stream.mp3", password = "p&ss +/word"),
            )
        assertEquals("https://radio.example.com/stream.mp3?auth=p%26ss%20%2B%2Fword", url)
    }

    @Test
    fun baseUrlWithExistingQueryUsesAmpersandSeparator() {
        val url =
            RadioMedia.authenticatedUrl(
                radio("https://radio.example.com/stream.mp3?foo=bar", password = "secreto"),
            )
        assertEquals("https://radio.example.com/stream.mp3?foo=bar&auth=secreto", url)
    }

    @Test
    fun mediaItemCarriesIdUriTitleAndNoPasswordInMetadata() {
        val item =
            RadioMedia.toMediaItem(
                radio("https://radio.example.com/stream.mp3", password = "secreto"),
                org.robolectric.RuntimeEnvironment.getApplication(),
            )
        assertEquals("radio-1", item.mediaId)
        assertEquals("https://radio.example.com/stream.mp3?auth=secreto", item.localConfiguration?.uri?.toString())
        assertEquals("Mi Radio", item.mediaMetadata.title?.toString())
        assertNull(item.mediaMetadata.subtitle)
    }

    @Test
    fun mediaItemDeclaresAudioRadioTypeForCastReceiver() {
        val item =
            RadioMedia.toMediaItem(
                radio("https://radio.example.com/stream.mp3"),
                org.robolectric.RuntimeEnvironment.getApplication(),
            )
        assertEquals("audio/mpeg", item.localConfiguration?.mimeType)
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, item.mediaMetadata.mediaType)
        assertTrue(item.mediaMetadata.isPlayable == true)
    }

    /**
     * Regresión: el MIME fijo `audio/mpeg` rompía HLS/DASH (ExoPlayer elegía
     * el extractor progresivo y la radio no sonaba) y el tipo del receiver Cast.
     */
    @Test
    fun mediaItemMimeTypeFollowsStreamExtension() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()

        fun mime(url: String) = RadioMedia.toMediaItem(radio(url), app).localConfiguration?.mimeType

        assertEquals(MimeTypes.APPLICATION_M3U8, mime("https://radio.example.com/live.m3u8"))
        assertEquals(MimeTypes.APPLICATION_MPD, mime("https://radio.example.com/live.mpd"))
        assertEquals(MimeTypes.AUDIO_AAC, mime("https://radio.example.com/live.aac"))
        assertEquals(MimeTypes.AUDIO_OGG, mime("https://radio.example.com/live.ogg"))
        assertEquals(MimeTypes.AUDIO_OPUS, mime("https://radio.example.com/live.opus"))
        assertEquals(MimeTypes.AUDIO_MPEG, mime("https://radio.example.com/live.mp3"))
        assertEquals(MimeTypes.AUDIO_MPEG, mime("https://radio.example.com/stream"))
    }

    @Test
    fun mimeTypeIsCaseInsensitiveAndIgnoresQueryString() {
        assertEquals(MimeTypes.APPLICATION_M3U8, RadioMedia.mimeTypeFor("https://radio.example.com/LIVE.M3U8?token=x"))
        assertEquals(MimeTypes.AUDIO_OGG, RadioMedia.mimeTypeFor("https://radio.example.com/live.ogg#frag"))
        assertEquals(MimeTypes.AUDIO_MPEG, RadioMedia.mimeTypeFor("  https://radio.example.com/stream  "))
    }

    @Test
    fun mediaItemEmbedsArtworkDataForNotificationsAndAuto() {
        val item =
            RadioMedia.toMediaItem(
                radio("https://radio.example.com/stream.mp3"),
                org.robolectric.RuntimeEnvironment.getApplication(),
            )
        assertTrue(item.mediaMetadata.artworkData != null)
        // La portada en vivo llega por canal custom de Cast (o polling del
        // receiver); embeberla como data URI en el LOAD arriesgaba el límite
        // de 64 KB del mensaje.
        assertNull(item.mediaMetadata.artworkUri)
    }

    @Test
    fun castConverterSendsMusicTrackInsteadOfBlackVideo() {
        val item =
            RadioMedia.toMediaItem(
                radio("https://radio.example.com/stream.mp3"),
                org.robolectric.RuntimeEnvironment.getApplication(),
            )
        val mediaInfo = DefaultMediaItemConverter().toMediaQueueItem(item).media!!
        val metadata = mediaInfo.metadata!!
        assertEquals("audio/mpeg", mediaInfo.contentType)
        // Sin este tipo el receiver interpreta el audio como video -> pantalla negra.
        assertEquals(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK, metadata.mediaType)
        assertEquals("Mi Radio", metadata.getString(CastMediaMetadata.KEY_TITLE))
        assertEquals("WaveLink", metadata.getString(CastMediaMetadata.KEY_ARTIST))
    }

    @Test
    fun mediaItemIsMarkedAsLiveForCastReceiver() {
        val item =
            RadioMedia.toMediaItem(
                radio("https://radio.example.com/stream.mp3"),
                org.robolectric.RuntimeEnvironment.getApplication(),
            )
        val mediaInfo = DefaultMediaItemConverter().toMediaQueueItem(item).media!!
        // LIVE evita que los receivers muestren barra de progreso/seek.
        assertEquals(CastMediaInfo.STREAM_TYPE_LIVE, mediaInfo.streamType)
        // Velocidad fija a 1x: la radio nunca acelera para alcanzar el vivo.
        assertEquals(1f, item.liveConfiguration.minPlaybackSpeed, 0f)
        assertEquals(1f, item.liveConfiguration.maxPlaybackSpeed, 0f)
    }

    @Test
    fun withNowPlayingKeepsLiveConfiguration() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val station = radio("https://radio.example.com/stream.mp3")
        val item = RadioMedia.toMediaItem(station, app)
        val updated = RadioMedia.withNowPlaying(item, station, NowPlaying(title = "Un tema"))
        assertEquals(item.liveConfiguration, updated.liveConfiguration)
    }

    @Test
    fun withNowPlayingReplacesTitleArtistSubtitleAndCover() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val station = radio("https://radio.example.com/stream.mp3")
        val item = RadioMedia.toMediaItem(station, app)
        val updated =
            RadioMedia.withNowPlaying(
                item,
                station,
                NowPlaying(
                    title = "Midnight City",
                    artist = "M83",
                    coverUrl = "https://radio.example.com/api/cover/a1b2",
                ),
            )
        assertEquals("Midnight City", updated.mediaMetadata.title?.toString())
        assertEquals("M83", updated.mediaMetadata.artist?.toString())
        assertEquals("Mi Radio", updated.mediaMetadata.subtitle?.toString())
        assertEquals(
            "https://radio.example.com/api/cover/a1b2",
            updated.mediaMetadata.artworkUri?.toString(),
        )
        // Identidad del ítem intacta: ExoPlayer reutiliza el media source.
        assertEquals(item.mediaId, updated.mediaId)
        assertEquals(item.localConfiguration?.uri, updated.localConfiguration?.uri)
        // Media3 prioriza artworkData sobre artworkUri: sin limpiar el ícono
        // embebido la portada en vivo nunca se mostraría.
        assertNull(updated.mediaMetadata.artworkData)
    }

    @Test
    fun withNowPlayingWithoutCoverKeepsEmbeddedArtwork() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val station = radio("https://radio.example.com/stream.mp3")
        val item = RadioMedia.toMediaItem(station, app)
        val updated = RadioMedia.withNowPlaying(item, station, NowPlaying(title = "Un tema"))
        assertTrue(updated.mediaMetadata.artworkData != null)
        assertNull(updated.mediaMetadata.artworkUri)
    }

    @Test
    fun withNowPlayingWithoutTrackKeepsStationAsTitle() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val station = radio("https://radio.example.com/stream.mp3")
        val item = RadioMedia.toMediaItem(station, app)
        val updated = RadioMedia.withNowPlaying(item, station, NowPlaying(artist = "M83"))
        assertEquals("Mi Radio", updated.mediaMetadata.title?.toString())
        assertEquals("M83", updated.mediaMetadata.artist?.toString())
        assertNull(updated.mediaMetadata.subtitle)
        assertNull(updated.mediaMetadata.artworkUri)
    }

    @Test
    fun withNowPlayingWithoutArtistFallsBackToBrand() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val station = radio("https://radio.example.com/stream.mp3")
        val item = RadioMedia.toMediaItem(station, app)
        val updated = RadioMedia.withNowPlaying(item, station, NowPlaying(title = "Solo un tema"))
        assertEquals("Solo un tema", updated.mediaMetadata.title?.toString())
        assertEquals("WaveLink", updated.mediaMetadata.artist?.toString())
        assertEquals("Mi Radio", updated.mediaMetadata.subtitle?.toString())
    }
}
