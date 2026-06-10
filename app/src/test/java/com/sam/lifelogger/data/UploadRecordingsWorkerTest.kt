package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadRecordingsWorkerTest {

    @Test
    fun sessionUploadWithoutSessionIdIsRejectedBeforeHttp() {
        val error = kotlin.runCatching {
            UploadRecordingsWorker.validateUploadMetadataForTest(
                recordingType = "session",
                sessionId = null
            )
        }.exceptionOrNull()

        assertEquals(
            "Session upload missing sessionId",
            error?.message
        )
    }

    @Test
    fun reminderUploadMayOmitSessionId() {
        UploadRecordingsWorker.validateUploadMetadataForTest(
            recordingType = "reminder",
            sessionId = null
        )
    }
}
