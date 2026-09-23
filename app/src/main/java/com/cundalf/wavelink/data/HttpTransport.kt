package com.cundalf.wavelink.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Resultado crudo de una petición HTTP. */
internal data class HttpResult(
    val code: Int,
    val body: String?,
)

/**
 * Capa HTTP inyectable: los tests la reemplazan por un fake para cubrir la
 * lógica de detección/validación sin abrir sockets reales.
 */
internal interface HttpTransport {
    fun execute(
        url: String,
        method: String,
        body: String? = null,
        contentType: String? = null,
    ): HttpResult

    /** Solo lee el status code (sin consumir el body: los streams son infinitos). */
    fun status(url: String): Int?
}

/** Punto único de inyección del transporte para la API de SUB/WAVE. */
internal object SubWaveHttp {
    var transport: HttpTransport = UrlConnectionTransport
}

internal object UrlConnectionTransport : HttpTransport {
    override fun execute(
        url: String,
        method: String,
        body: String?,
        contentType: String?,
    ): HttpResult {
        val conn = openForRequest(url, method, body, contentType) ?: return UNAVAILABLE
        return try {
            val code = conn.responseCode
            HttpResult(code, readBody(conn, code))
        } catch (
            @Suppress("SwallowedException") e: IOException,
        ) {
            // Degradación intencional: la UI maneja el fallo como "sin conexión".
            UNAVAILABLE
        } finally {
            conn.disconnect()
        }
    }

    override fun status(url: String): Int? =
        try {
            val conn = open(url, "GET")
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        } catch (
            @Suppress("SwallowedException") e: IOException,
        ) {
            null
        }

    private fun openForRequest(
        url: String,
        method: String,
        body: String?,
        contentType: String?,
    ): HttpURLConnection? =
        runCatching {
            val conn = open(url, method)
            contentType?.let { conn.setRequestProperty("Content-Type", it) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            conn
        }.getOrNull()

    private fun readBody(
        conn: HttpURLConnection,
        code: Int,
    ): String? =
        runCatching {
            // Los cuerpos de error importan (404 de /api/request, 503 "cerrado",
            // 429 con reintento): se leen también desde errorStream.
            val stream = if (code in HTTP_OK_RANGE) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }
        }.getOrNull()

    private fun open(
        url: String,
        method: String,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = method
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private val UNAVAILABLE = HttpResult(HTTP_UNAVAILABLE, null)

    private const val TIMEOUT_MS = 5_000
    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299
    private val HTTP_OK_RANGE = HTTP_OK_MIN..HTTP_OK_MAX
    private const val HTTP_UNAVAILABLE = 503
    private const val USER_AGENT = "SUB/WAVE Cast/0.1"
}
