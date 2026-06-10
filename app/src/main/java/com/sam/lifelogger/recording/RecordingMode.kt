package com.sam.lifelogger.recording

enum class RecordingMode(val wireValue: String) {
    NORMAL("normal"),
    SESSION("session"),
    REMINDER("reminder");

    fun chunkDurationMs(normalChunkMinutes: Int): Long {
        return when (this) {
            NORMAL -> normalChunkMinutes * 60_000L
            SESSION -> 10 * 60_000L
            REMINDER -> 2 * 60_000L
        }
    }

    companion object {
        fun fromWireValue(value: String): RecordingMode {
            return entries.firstOrNull { it.wireValue == value } ?: NORMAL
        }
    }
}
