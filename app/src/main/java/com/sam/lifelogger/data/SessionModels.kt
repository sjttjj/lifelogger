package com.sam.lifelogger.data

import org.json.JSONArray
import org.json.JSONObject

data class SessionNoteEntry(
    val filename: String,
    val type: String?,
    val sizeBytes: Long?,
    val path: String?
) {
    companion object {
        fun listFromJsonObject(root: JSONObject): List<SessionNoteEntry> {
            val arr = root.optJSONArray("sessions") ?: JSONArray()
            return (0 until arr.length()).map { index ->
                val obj = arr.getJSONObject(index)
                SessionNoteEntry(
                    filename = obj.getString("filename"),
                    type = obj.optString("type", "").ifBlank { null },
                    sizeBytes = if (obj.has("size_bytes") && !obj.isNull("size_bytes")) obj.optLong("size_bytes") else null,
                    path = obj.optString("path", "").ifBlank { null }
                )
            }
        }

        fun listFromJson(json: String): List<SessionNoteEntry> =
            listFromJsonObject(JSONObject(json))
    }
}

data class SessionNoteMetadata(
    val sessionId: String? = null,
    val chunks: Int? = null,
    val totalDuration: String? = null,
    val promptId: String? = null
)

data class SessionNoteDetail(
    val date: String,
    val filename: String,
    val content: String,
    val markdown: String,
    val contentHtml: String?,
    val metadata: SessionNoteMetadata
) {
    companion object {
        fun fromJsonObject(root: JSONObject): SessionNoteDetail =
            fromContent(
                date = root.getString("date"),
                filename = root.getString("filename"),
                content = root.optString("content", ""),
                contentHtml = root.optString("content_html", "").ifBlank { null }
            )

        fun fromJson(json: String): SessionNoteDetail =
            fromJsonObject(JSONObject(json))

        fun fromContent(
            date: String,
            filename: String,
            content: String,
            contentHtml: String? = null
        ): SessionNoteDetail {
            val parsed = parseFrontmatter(content)
            return SessionNoteDetail(
                date = date,
                filename = filename,
                content = content,
                markdown = parsed?.body ?: content,
                contentHtml = contentHtml,
                metadata = parsed?.metadata ?: SessionNoteMetadata()
            )
        }

        private fun parseFrontmatter(content: String): ParsedFrontmatter? {
            if (!content.startsWith("---\n")) return null
            val end = content.indexOf("\n---", startIndex = 4)
            if (end <= 0) return null

            val frontmatter = content.substring(4, end)
            val bodyStart = (end + "\n---".length).let { index ->
                if (content.getOrNull(index) == '\n') index + 1 else index
            }
            val values = frontmatter
                .lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) null
                    else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
                .toMap()

            return ParsedFrontmatter(
                metadata = SessionNoteMetadata(
                    sessionId = values["session_id"]?.takeIf { it.isNotBlank() },
                    chunks = values["chunks"]?.toIntOrNull(),
                    totalDuration = values["total_duration"]?.takeIf { it.isNotBlank() },
                    promptId = values["prompt_id"]?.takeIf { it.isNotBlank() }
                ),
                body = content.substring(bodyStart)
            )
        }
    }
}

private data class ParsedFrontmatter(
    val metadata: SessionNoteMetadata,
    val body: String
)
