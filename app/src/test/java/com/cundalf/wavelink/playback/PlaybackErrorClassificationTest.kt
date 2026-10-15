package com.cundalf.wavelink.playback

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P5: clasificación de errores de reproducción (regresión: 401 se reintentaba
 * como error de red; ver `handleError`, que no reintenta AUTH).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackErrorClassificationTest {
    private val service = PlaybackService()

    @Test
    fun unauthorizedResponseIsAuth() {
        val error = ioError(invalidResponseCode(HTTP_UNAUTHORIZED))
        assertEquals(PlaybackError.AUTH, service.classify(error))
    }

    @Test
    fun otherHttpErrorsAreNetwork() {
        val error = ioError(invalidResponseCode(HTTP_SERVER_ERROR))
        assertEquals(PlaybackError.NETWORK, service.classify(error))
    }

    @Test
    fun dataSourceFailureIsNetwork() {
        val error =
            PlaybackException(
                "sin conexión",
                DataSourceException(REASON_POSITION_OUT_OF_RANGE),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )
        assertEquals(PlaybackError.NETWORK, service.classify(error))
    }

    @Test
    fun parsingFailureIsStream() {
        val error =
            PlaybackException(
                "contenedor roto",
                null,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            )
        assertEquals(PlaybackError.STREAM, service.classify(error))
    }

    private fun ioError(cause: Throwable): PlaybackException =
        PlaybackException("io", cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

    private fun invalidResponseCode(code: Int): HttpDataSource.InvalidResponseCodeException =
        HttpDataSource.InvalidResponseCodeException(
            code,
            "respuesta $code",
            null,
            emptyMap(),
            DataSpec(Uri.parse("https://radio.example.com/stream.mp3")),
            ByteArray(0),
        )

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_SERVER_ERROR = 500

        /** Valor de DataSourceException.POSITION_OUT_OF_RANGE (deprecado en Media3 1.11). */
        const val REASON_POSITION_OUT_OF_RANGE = 0
    }
}
