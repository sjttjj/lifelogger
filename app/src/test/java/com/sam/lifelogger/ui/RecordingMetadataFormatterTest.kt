package com.sam.lifelogger.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingMetadataFormatterTest {

    @Test
    fun formatsNormalRecordingMetadata() {
        assertEquals(
            RecordingMetadata(
                typeLabel = "Normal",
                detailLabel = "Standard lifelog chunk"
            ),
            RecordingMetadataFormatter.format(type = "normal", sessionId = null)
        )
    }

    @Test
    fun formatsReminderRecordingMetadata() {
        assertEquals(
            RecordingMetadata(
                typeLabel = "Reminder",
                detailLabel = "Reminder chunk"
            ),
            RecordingMetadataFormatter.format(type = "reminder", sessionId = null)
        )
    }

    @Test
    fun formatsSessionRecordingMetadataWithShortSessionId() {
        assertEquals(
            RecordingMetadata(
                typeLabel = "Session",
                detailLabel = "Session 12345678"
            ),
            RecordingMetadataFormatter.format(
                type = "session",
                sessionId = "12345678-90ab-cdef-1234-567890abcdef"
            )
        )
    }

    @Test
    fun formatsSessionRecordingMetadataWhenSessionIdIsMissing() {
        assertEquals(
            RecordingMetadata(
                typeLabel = "Session",
                detailLabel = "Missing session ID"
            ),
            RecordingMetadataFormatter.format(type = "session", sessionId = null)
        )
    }
}
