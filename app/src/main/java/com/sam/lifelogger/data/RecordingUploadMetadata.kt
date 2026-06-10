package com.sam.lifelogger.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecordingUploadMetadata(
    val recordedAtMs: String,
    val recordedAtIso: String,
    val recordedDate: String,
    val recordedTimezone: String
) {
    companion object {
        fun fromCreatedAt(createdAt: Long): RecordingUploadMetadata {
            val zone = ZoneId.systemDefault()
            val zonedDateTime = Instant.ofEpochMilli(createdAt).atZone(zone)

            return RecordingUploadMetadata(
                recordedAtMs = createdAt.toString(),
                recordedAtIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zonedDateTime),
                recordedDate = DateTimeFormatter.ISO_LOCAL_DATE.format(zonedDateTime),
                recordedTimezone = zone.id
            )
        }
    }
}

