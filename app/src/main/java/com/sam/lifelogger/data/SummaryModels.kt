package com.sam.lifelogger.data

import org.json.JSONObject

data class SummaryDetail(
    val summary: String,
    val summaryHtml: String?,
    val segmentCount: Int,
    val totalDurationSeconds: Double,
    val title: String?,
    val editedAt: String?,
    val isEdited: Boolean
) {
    companion object {
        fun fromJsonObject(root: JSONObject): SummaryDetail {
            val rawTitle = root.optString("title", "")
            val rawEditedAt = root.optString("edited_at", "")
            return SummaryDetail(
                summary = root.getString("summary"),
                summaryHtml = root.optString("summary_html", "").ifBlank { null },
                segmentCount = root.optInt("segment_count", 0),
                totalDurationSeconds = root.optDouble("total_duration_seconds", 0.0),
                title = if (root.has("title") && !root.isNull("title") && rawTitle.isNotBlank()) rawTitle else null,
                editedAt = if (root.has("edited_at") && !root.isNull("edited_at") && rawEditedAt.isNotBlank()) rawEditedAt else null,
                isEdited = root.optBoolean("is_edited", false)
            )
        }
    }
}
