package com.sam.lifelogger.squeeze

/**
 * In-process bridge for squeeze actions that are only allowed while the app has
 * a visible activity. This avoids Android background activity launch limits.
 */
object VisibleSqueezeActionBridge {
    interface Handler {
        fun toggleRecording()
        fun toggleSessionRecording() {}
        fun startReminderRecording(): Boolean = false
        fun stopReminderRecording(): Boolean = false
        fun continuousSqueezeStarted() {}
        fun continuousSqueezeEnded() {}
        fun reminderModeTimedOut() {}
    }

    @Volatile
    private var handler: Handler? = null

    fun register(handler: Handler) {
        this.handler = handler
    }

    fun unregister() {
        handler = null
    }

    fun dispatchToggleRecording(): Boolean {
        val visibleHandler = handler ?: return false
        visibleHandler.toggleRecording()
        return true
    }

    fun dispatchToggleSessionRecording(): Boolean {
        val visibleHandler = handler ?: return false
        visibleHandler.toggleSessionRecording()
        return true
    }

    fun dispatchStartReminderRecording(): Boolean {
        val visibleHandler = handler ?: return false
        return visibleHandler.startReminderRecording()
    }

    fun dispatchStopReminderRecording(): Boolean {
        val visibleHandler = handler ?: return false
        return visibleHandler.stopReminderRecording()
    }

    fun dispatchContinuousSqueezeStarted(): Boolean {
        val visibleHandler = handler ?: return false
        visibleHandler.continuousSqueezeStarted()
        return true
    }

    fun dispatchContinuousSqueezeEnded(): Boolean {
        val visibleHandler = handler ?: return false
        visibleHandler.continuousSqueezeEnded()
        return true
    }

    fun dispatchReminderModeTimedOut(): Boolean {
        val visibleHandler = handler ?: return false
        visibleHandler.reminderModeTimedOut()
        return true
    }
}
