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

    fun decode(json: String?): List<Radio> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Radio(
                    id = obj.optString("id"),
                    nombre = obj.optString("nombre"),
                    streamUrl = obj.optString("url"),
                    password = obj.optString("password").ifBlank { null },
                    esDefault = obj.optBoolean("default"),
                    showNowPlaying = obj.optBoolean("showNowPlaying", true),
                    allowRequests = obj.optBoolean("allowRequests", false),
                )
            }
        }.getOrDefault(emptyList())
    }
}
