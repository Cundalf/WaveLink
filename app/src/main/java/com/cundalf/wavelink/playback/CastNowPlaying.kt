package com.cundalf.wavelink.playback

import android.graphics.Bitmap
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Serializa el "ahora suena" para el canal custom de Cast.
 *
 * CastPlayer no puede actualizar la metadata de un ítem ya cargado sin
 * recargarlo, y el receiver (HTTPS) no siempre puede consultar la API de la
 * radio por CORS/contenido mixto. El teléfono, que ya tiene los datos, se los
 * empuja por `urn:x-cast:` como mucho una vez por tema (el canal admite 64 KB).
 */
internal object CastNowPlaying {
    const val NAMESPACE = "urn:x-cast:com.cundalf.wavelink"
    const val TYPE_NOW_PLAYING = "now-playing"

    const val FIELD_TYPE = "type"
    const val FIELD_STATION = "station"
    const val FIELD_TITLE = "title"
    const val FIELD_ARTIST = "artist"
    const val FIELD_COVER = "cover"

    private const val COVER_PREFIX = "data:image/jpeg;base64,"
    private const val COVER_MAX_DIM_PX = 480
    private const val COVER_MIN_DIM_PX = 240
    private const val JPEG_QUALITY = 75

    // Deja margen bajo el límite de 64 KB del bus de mensajes de Cast al pasar
    // a base64 (~33% más) y sumar el resto del JSON.
    private const val MAX_COVER_BYTES = 32 * 1024

    fun toJson(
        radio: Radio,
        nowPlaying: NowPlaying,
        coverDataUrl: String?,
    ): String =
        JSONObject()
            .put(FIELD_TYPE, TYPE_NOW_PLAYING)
            .put(FIELD_STATION, radio.nombre)
            .put(FIELD_TITLE, nowPlaying.title ?: JSONObject.NULL)
            .put(FIELD_ARTIST, nowPlaying.artist ?: JSONObject.NULL)
            .put(FIELD_COVER, coverDataUrl ?: JSONObject.NULL)
            .toString()

    /**
     * Portada como data URI para esquivar CORS/contenido mixto en el receiver.
     * Devuelve `null` si aún reducida al mínimo supera el presupuesto del canal.
     */
    fun coverDataUrl(bitmap: Bitmap): String? {
        var maxDim = COVER_MAX_DIM_PX
        while (maxDim >= COVER_MIN_DIM_PX) {
            val bytes = bitmap.scaledTo(maxDim).jpegBytes()
            if (bytes.size <= MAX_COVER_BYTES) {
                return COVER_PREFIX + Base64.getEncoder().encodeToString(bytes)
            }
            maxDim /= 2
        }
        return null
    }

    private fun Bitmap.scaledTo(maxDim: Int): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= maxDim) return this
        val ratio = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            this,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun Bitmap.jpegBytes(): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
}
