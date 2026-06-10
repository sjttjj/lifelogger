package com.sam.lifelogger.recording

import androidx.work.NetworkType

object RecordingUploadWorkPolicy {
    val reminderPostUploadSyncDelaySeconds = listOf(15L, 45L, 120L)

    fun immediateUploadNetworkType(mode: RecordingMode): NetworkType =
        if (mode == RecordingMode.REMINDER) NetworkType.CONNECTED else NetworkType.UNMETERED

    fun uploadOnlyCreatedRecording(mode: RecordingMode): Boolean =
        mode == RecordingMode.REMINDER
}
