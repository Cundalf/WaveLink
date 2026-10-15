package com.cundalf.wavelink.data

import org.json.JSONArray
import org.json.JSONObject

internal object RadioJson {
    fun encode(list: List<Radio>): String {
        val array = JSONArray()
        list.forEach { radio ->
            array.put(
                JSONObject().apply {
                    put("id", radio.id)
                    put("nombre", radio.nombre)
                    put("url", radio.streamUrl)
                    put("password", radio.password ?: "")
                    put("default", radio.esDefault)
                    put("showNowPlaying", radio.showNowPlaying)
                    put("allowRequests", radio.allowRequests)
                },
            )
        }
        return array.toString()
    }

    /**
     * Decodifica la lista guardada. Parseo tolerante: JSON ausente o inválido devuelve
     * lista vacía. Las entradas sin `id` o sin `url` (o con solo espacios) se descartan:
     * una radio sin URL no se puede reproducir y rompería el árbol de Android Auto.
     * El resto de los campos usa los defaults de compatibilidad de [Radio].
     */
    fun decode(json: String?): List<Radio> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                val streamUrl = obj.optString("url")
                if (id.isBlank() || streamUrl.isBlank()) {
                    null
                } else {
                    Radio(
                        id = id,
                        nombre = obj.optString("nombre"),
                        streamUrl = streamUrl,
                        password = obj.optString("password").ifBlank { null },
                        esDefault = obj.optBoolean("default"),
                        showNowPlaying = obj.optBoolean("showNowPlaying", true),
                        allowRequests = obj.optBoolean("allowRequests", false),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
