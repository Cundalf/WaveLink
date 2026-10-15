package com.cundalf.wavelink.data

import org.json.JSONObject

/** Resultado de enviar un pedido a la cabina (POST /api/request). */
sealed interface RequestSubmission {
    data class Accepted(
        val requestId: String,
    ) : RequestSubmission

    data class Rejected(
        val message: String?,
    ) : RequestSubmission

    data class Closed(
        val message: String?,
    ) : RequestSubmission

    data object Failed : RequestSubmission
}

/** Estado del pedido consultado con GET /api/request/{id}. */
data class RequestStatus(
    val status: String,
    val ack: String?,
    val trackTitle: String?,
    val trackArtist: String?,
    val message: String?,
) {
    val isResolved: Boolean get() = status == STATUS_RESOLVED
    val isFailed: Boolean get() = status == STATUS_FAILED
    val isUnknown: Boolean get() = status == STATUS_UNKNOWN

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_RESOLVED = "resolved"
        const val STATUS_FAILED = "failed"
        const val STATUS_UNKNOWN = "unknown"
    }
}

/**
 * Endpoints de pedidos a la cabina de SUB/WAVE. No hay endpoint público de
 * capacidades, así que la existencia de la ruta se detecta con un id imposible:
 * si la ruta existe responde 404 JSON `status:unknown`; si no, el 404 es HTML.
 */
object SubWaveRequests {
    private const val API_REQUEST = "/api/request"
    private const val REQUESTS_PROBE_ID = "00000000-0000-0000-0000-000000000000"

    suspend fun sendRequest(
        base: String,
        text: String,
        name: String,
    ): RequestSubmission =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val payload =
                JSONObject()
                    .put("text", text)
                    .put("name", name)
                    .toString()
            val result =
                SubWaveHttp.transport.execute("$base$API_REQUEST", "POST", payload, "application/json")
            when {
                result.code == HTTP_ACCEPTED || result.code in HTTP_OK_RANGE -> {
                    val requestId =
                        parseJson(result.body)?.optString("requestId")?.takeIf { it.isNotBlank() }
                    if (requestId != null) {
                        RequestSubmission.Accepted(requestId)
                    } else {
                        RequestSubmission.Failed
                    }
                }

                result.code == HTTP_SERVICE_UNAVAILABLE ->
                    RequestSubmission.Closed(apiMessage(result.body))

                else -> RequestSubmission.Rejected(apiMessage(result.body))
            }
        }

    suspend fun pollRequest(
        base: String,
        requestId: String,
    ): RequestStatus? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result =
                SubWaveHttp.transport.execute(
                    "$base$API_REQUEST/${android.net.Uri.encode(requestId)}",
                    "GET",
                )
            val json = parseJson(result.body) ?: return@withContext null
            val status = json.optString("status")
            if (status.isBlank()) return@withContext null
            val track = json.optJSONObject("track")
            RequestStatus(
                status = status,
                ack = json.optString("ack").takeIf { it.isNotBlank() },
                trackTitle = track?.optString("title")?.takeIf { it.isNotBlank() },
                trackArtist = track?.optString("artist")?.takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() },
            )
        }

    /**
     * Sondea la ruta de pedidos sin enviar nada: el endpoint existe si un id
     * imposible responde 404 JSON `status:unknown` (en vez del 404 HTML del
     * router).
     */
    internal fun isAvailable(base: String): Boolean =
        parseRequestsProbe(
            SubWaveHttp.transport.execute("$base$API_REQUEST/$REQUESTS_PROBE_ID", "GET"),
        )

    /** El servidor usa `message` para errores de la API y `error` para validación. */
    internal fun apiMessage(body: String?): String? {
        val json = parseJson(body) ?: return null
        return json.optString("message").takeIf { it.isNotBlank() }
            ?: json.optString("error").takeIf { it.isNotBlank() }
    }

    internal fun parseRequestsProbe(result: HttpResult): Boolean {
        if (result.code != HTTP_NOT_FOUND) return false
        val json = parseJson(result.body) ?: return false
        return json.optString("status") == RequestStatus.STATUS_UNKNOWN
    }

    private fun parseJson(body: String?): JSONObject? = body?.let { runCatching { JSONObject(it) }.getOrNull() }

    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299
    private val HTTP_OK_RANGE = HTTP_OK_MIN..HTTP_OK_MAX
    private const val HTTP_ACCEPTED = 202
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_SERVICE_UNAVAILABLE = 503
}
