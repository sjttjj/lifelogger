package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class RecordingUploadMetadataTest {

    @Test
    fun fromCreatedAtFormatsEpochIsoDateAndTimezone() {
        val previousTimezone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"))
        try {
            val metadata = RecordingUploadMetadata.fromCreatedAt(1764590400000L)

            assertEquals("1764590400000", metadata.recordedAtMs)
            assertEquals("2025-12-01T23:00:00+11:00", metadata.recordedAtIso)
            assertEquals("2025-12-01", metadata.recordedDate)
            assertEquals("Australia/Sydney", metadata.recordedTimezone)
        } finally {
            TimeZone.setDefault(previousTimezone)
        }
    }
}
