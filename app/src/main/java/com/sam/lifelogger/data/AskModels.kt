package com.sam.lifelogger.data

import org.json.JSONArray
import org.json.JSONObject

data class AskScope(
    val startDate: String? = null,
    val endDate: String? = null,
    val sources: List<String>? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        startDate?.let { put("start_date", it) }
        endDate?.let { put("end_date", it) }
        sources?.let { put("sources", JSONArray(it)) }
    }
}

data class Citation(
    val source: String,
    val date: String?,
    val title: String?,
    val snippet: String?
) {
    companion object {
        fun fromJson(obj: JSONObject): Citation = Citation(
            source = obj.optString("source", ""),
            date = if (obj.has("date") && !obj.isNull("date")) obj.optString("date") else null,
            title = if (obj.has("title") && !obj.isNull("title")) obj.optString("title") else null,
            snippet = if (obj.has("snippet") && !obj.isNull("snippet")) obj.optString("snippet") else null
        )
    }
}

data class AskSearchInfo(
    val startDate: String?,
    val endDate: String?,
    val sources: List<String>?,
    val resultCount: Int?
) {
    companion object {
        fun fromJson(obj: JSONObject?): AskSearchInfo? {
            if (obj == null) return null
            return AskSearchInfo(
                startDate = takeIf { obj.has("start_date") && !obj.isNull("start_date") }?.let { obj.optString("start_date") },
                endDate = takeIf { obj.has("end_date") && !obj.isNull("end_date") }?.let { obj.optString("end_date") },
                sources = obj.optJSONArray("sources")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                },
                resultCount = if (obj.has("result_count")) obj.optInt("result_count") else null
            )
        }
    }
}

data class AskResponse(
    val question: String,
    val inputType: String,
    val answer: String,
    val confidence: String?,
    val citations: List<Citation>?,
    val searched: AskSearchInfo?,
    val transcript: String?,
    val audioDurationSeconds: Double?
) {
    companion object {
        fun fromJson(json: String): AskResponse {
            val obj = JSONObject(json)
            val citationsArr = obj.optJSONArray("citations")
            val citations = citationsArr?.let { arr ->
                (0 until arr.length()).map { Citation.fromJson(arr.getJSONObject(it)) }
            }
            return AskResponse(
                question = obj.optString("question", ""),
                inputType = obj.optString("input_type", "text"),
                answer = obj.optString("answer", ""),
                confidence = obj.optString("confidence", null),
                citations = citations,
                searched = AskSearchInfo.fromJson(obj.optJSONObject("searched")),
                transcript = obj.optString("transcript", null),
                audioDurationSeconds = if (obj.has("audio_duration_seconds")) obj.optDouble("audio_duration_seconds") else null
            )
        }
    }
}
