package com.sam.lifelogger.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingModeTest {

    @Test
    fun normalUsesConfiguredChunkLength() {
        assertEquals(
            5 * 60_000L,
            RecordingMode.NORMAL.chunkDurationMs(normalChunkMinutes = 5)
        )
    }

    @Test
    fun sessionUsesTenMinuteChunkLength() {
        assertEquals(
            10 * 60_000L,
            RecordingMode.SESSION.chunkDurationMs(normalChunkMinutes = 5)
        )
    }

    @Test
    fun reminderUsesTwoMinuteChunkLength() {
        assertEquals(
            2 * 60_000L,
            RecordingMode.REMINDER.chunkDurationMs(normalChunkMinutes = 5)
        )
    }
}
