package com.sam.lifelogger.recording

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingUploadWorkPolicyTest {

    @Test
    fun reminderImmediateUploadUsesAnyConnectedNetwork() {
        assertEquals(
            NetworkType.CONNECTED,
            RecordingUploadWorkPolicy.immediateUploadNetworkType(RecordingMode.REMINDER)
        )
        assertTrue(RecordingUploadWorkPolicy.uploadOnlyCreatedRecording(RecordingMode.REMINDER))
    }

    @Test
    fun normalAndSessionKeepUnmeteredBulkUploadBehavior() {
        assertEquals(
            NetworkType.UNMETERED,
            RecordingUploadWorkPolicy.immediateUploadNetworkType(RecordingMode.NORMAL)
        )
        assertEquals(
            NetworkType.UNMETERED,
            RecordingUploadWorkPolicy.immediateUploadNetworkType(RecordingMode.SESSION)
        )
        assertFalse(RecordingUploadWorkPolicy.uploadOnlyCreatedRecording(RecordingMode.NORMAL))
        assertFalse(RecordingUploadWorkPolicy.uploadOnlyCreatedRecording(RecordingMode.SESSION))
    }

    @Test
    fun reminderPostUploadSyncDelaysMatchServerGuidance() {
        assertEquals(
            listOf(15L, 45L, 120L),
            RecordingUploadWorkPolicy.reminderPostUploadSyncDelaySeconds
        )
    }
}
