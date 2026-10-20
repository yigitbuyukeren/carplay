package com.ridedeck.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One thing the rider said and whether it was understood. */
data class VoiceHistoryEntry(val heard: String, val understood: Boolean, val atMs: Long)

/** The last few spoken commands as text (never audio), newest first; pure list handling. */
object VoiceHistory {
    const val MAX = 10

    fun add(list: List<VoiceHistoryEntry>, entry: VoiceHistoryEntry): List<VoiceHistoryEntry> =
        (listOf(entry) + list).take(MAX)

    fun toJson(list: List<VoiceHistoryEntry>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("h", it.heard).put("u", it.understood).put("t", it.atMs)) }
        return arr.toString()
    }

    fun fromJson(json: String?): List<VoiceHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VoiceHistoryEntry(o.getString("h"), o.optBoolean("u", true), o.optLong("t"))
            }.take(MAX)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
