package com.sam.lifelogger.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionIdPolicyTest {

    @Test
    fun sessionChunkRequiresActiveSessionId() {
        val error = kotlin.runCatching {
            SessionIdPolicy.sessionIdForChunk(RecordingMode.SESSION, null)
        }.exceptionOrNull()

        assertEquals("Session chunk missing active sessionId", error?.message)
    }

    @Test
    fun reminderChunkDoesNotUseSessionId() {
        assertNull(
            SessionIdPolicy.sessionIdForChunk(
                RecordingMode.REMINDER,
                "existing-session"
            )
        )
    }

    @Test
    fun sessionChunkUsesExistingSessionId() {
        assertEquals(
            "existing-session",
            SessionIdPolicy.sessionIdForChunk(
                RecordingMode.SESSION,
                "existing-session"
            )
        )
    }
}
