package com.sam.lifelogger.ui

data class RecordingMetadata(
    val typeLabel: String,
    val detailLabel: String
)

object RecordingMetadataFormatter {
    fun format(type: String, sessionId: String?): RecordingMetadata {
        return when (type.lowercase()) {
            "session" -> RecordingMetadata(
                typeLabel = "Session",
                detailLabel = sessionId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Session ${it.take(8)}" }
                    ?: "Missing session ID"
            )
            "reminder" -> RecordingMetadata(
                typeLabel = "Reminder",
                detailLabel = "Reminder chunk"
            )
            else -> RecordingMetadata(
                typeLabel = "Normal",
                detailLabel = "Standard lifelog chunk"
            )
        }
    }
}
