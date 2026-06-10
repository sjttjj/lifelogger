package com.sam.lifelogger.recording

object SessionIdPolicy {
    class MissingSessionIdException(message: String) : Exception(message)

    @Throws(MissingSessionIdException::class)
    fun sessionIdForChunk(mode: RecordingMode, activeSessionId: String?): String? {
        return when (mode) {
            RecordingMode.SESSION -> activeSessionId?.takeIf { it.isNotBlank() }
                ?: throw MissingSessionIdException("Session chunk missing active sessionId")
            RecordingMode.NORMAL,
            RecordingMode.REMINDER -> null
        }
    }
}
