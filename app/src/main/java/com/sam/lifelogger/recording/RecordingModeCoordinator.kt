package com.sam.lifelogger.recording

class RecordingModeCoordinator(
    private val sink: RecordingSink
) {
    interface RecordingSink {
        /** Start the service. Only called when no mode was previously active. */
        fun start(mode: RecordingMode)
        /** Switch the live recording mode without stopping the service. */
        fun switchMode(mode: RecordingMode)
        /** Stop the service entirely. Only called when the last requested mode ends. */
        fun stop()
    }

    var isNormalRequested: Boolean = false
        private set

    var isSessionRequested: Boolean = false
        private set

    var activeMode: RecordingMode? = null
        private set

    fun restoreState(
        activeMode: RecordingMode?,
        isNormalRequested: Boolean,
        isSessionRequested: Boolean
    ) {
        this.activeMode = activeMode
        this.isNormalRequested = isNormalRequested
        this.isSessionRequested = isSessionRequested
    }

    fun toggleNormal() {
        if (isNormalRequested) {
            isNormalRequested = false
            if (activeMode == RecordingMode.NORMAL) {
                endActiveMode() // just clears activeMode, doesn't stop service
            }
            checkShutdown()
            return
        }

        isNormalRequested = true
        if (activeMode == null) {
            start(RecordingMode.NORMAL)
        }
    }

    fun toggleSession() {
        if (isSessionRequested) {
            isSessionRequested = false
            if (activeMode == RecordingMode.SESSION) {
                endActiveMode() // just clears activeMode
                resumeBestAvailable()
                checkShutdown()
            }
            return
        }

        isSessionRequested = true
        if (activeMode == RecordingMode.NORMAL) {
            // Transition: normal → session, switch in place
            switchMode(RecordingMode.SESSION)
        } else if (activeMode == null) {
            start(RecordingMode.SESSION)
        }
    }

    fun startReminder(): Boolean {
        if (activeMode == RecordingMode.REMINDER) return false

        if (activeMode != null) {
            // Transition: whatever → reminder, switch in place
            switchMode(RecordingMode.REMINDER)
        } else {
            start(RecordingMode.REMINDER)
        }
        return true
    }

    fun stopReminder(): Boolean {
        if (activeMode != RecordingMode.REMINDER) return false

        endActiveMode() // just clears activeMode
        resumeBestAvailable()
        checkShutdown()
        return true
    }

    private fun resumeBestAvailable() {
        when {
            isSessionRequested -> switchMode(RecordingMode.SESSION)
            isNormalRequested -> switchMode(RecordingMode.NORMAL)
        }
    }

    private fun switchMode(mode: RecordingMode) {
        activeMode = mode
        sink.switchMode(mode)
    }

    private fun start(mode: RecordingMode) {
        activeMode = mode
        sink.start(mode)
    }

    /** Clears the active mode without stopping the service.
     *  The service keeps running; [checkShutdown] decides if it should stop. */
    private fun endActiveMode() {
        activeMode = null
    }

    /** Stops the service if no modes are requested anymore. */
    private fun checkShutdown() {
        if (!isNormalRequested && !isSessionRequested && activeMode == null) {
            sink.stop()
        }
    }
}
