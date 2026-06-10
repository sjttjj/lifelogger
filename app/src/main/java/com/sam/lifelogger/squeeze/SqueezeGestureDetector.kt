package com.sam.lifelogger.squeeze

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Detects single, double, and triple squeeze patterns from raw event timestamps.
 *
 * Call [recordEvent] each time a squeeze broadcast is received. The detector waits
 * [GESTURE_WINDOW_MS] after the first event to collect multi-squeeze patterns,
 * then invokes [onGesture] with the identified gesture type.
 */
class SqueezeGestureDetector(
    private val onGesture: (SqueezeGesture) -> Unit,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val scheduler: Scheduler = HandlerScheduler()
) {

    interface Cancelable {
        fun cancel()
    }

    interface Scheduler {
        fun postDelayed(delayMs: Long, action: () -> Unit): Cancelable
    }

    companion object {
        const val GESTURE_WINDOW_MS = 2000L
        const val CONTINUOUS_HOLD_MS = 1000L
        const val CONTINUOUS_RELEASE_DEBOUNCE_MS = 1200L
        private const val CONTINUOUS_HIGH_PROGRESS = 0.95f
        private const val CONTINUOUS_RELEASE_PROGRESS = 0.20f
        private const val LATE_DISCRETE_SUPPRESS_MS = 1000L
        private const val TAG = "SqueezeDetector"
    }

    private val events = mutableListOf<Long>()
    private var discreteTimer: Cancelable? = null
    private var continuousHoldTimer: Cancelable? = null
    private var continuousReleaseTimer: Cancelable? = null
    private var continuousPressing = false
    private var continuousActive = false
    private var suppressDiscreteUntil = 0L

    fun recordEvent() {
        val now = clock()
        if (continuousActive || now < suppressDiscreteUntil) {
            logDebug("recordEvent: suppressed after continuous squeeze")
            return
        }

        events.add(now)
        logDebug("recordEvent: count=${events.size}, timestamps=$events")

        if (discreteTimer == null) {
            discreteTimer = scheduler.postDelayed(GESTURE_WINDOW_MS) { flush() }
        }
    }

    fun recordProgress(progress: Float) {
        val now = clock()
        logDebug("recordProgress: progress=$progress")

        when {
            progress >= CONTINUOUS_HIGH_PROGRESS -> {
                cancelContinuousRelease()

                if (!continuousPressing) {
                    continuousPressing = true
                    continuousHoldTimer = scheduler.postDelayed(CONTINUOUS_HOLD_MS) {
                        if (continuousPressing && !continuousActive) {
                            continuousActive = true
                            clearDiscreteEvents()
                            logDebug("gesture=CONTINUOUS_START")
                            onGesture(SqueezeGesture.CONTINUOUS_START)
                        }
                    }
                }
            }

            progress >= CONTINUOUS_RELEASE_PROGRESS -> {
                if (continuousActive) {
                    continuousPressing = true
                    cancelContinuousRelease()
                }
            }

            progress < CONTINUOUS_RELEASE_PROGRESS -> {
                if (continuousPressing) {
                    continuousPressing = false
                    continuousHoldTimer?.cancel()
                    continuousHoldTimer = null

                    if (continuousActive) {
                        continuousReleaseTimer?.cancel()
                        continuousReleaseTimer = scheduler.postDelayed(
                            CONTINUOUS_RELEASE_DEBOUNCE_MS
                        ) {
                            if (!continuousPressing && continuousActive) {
                                continuousActive = false
                                clearDiscreteEvents()
                                suppressDiscreteUntil = clock() + LATE_DISCRETE_SUPPRESS_MS
                                logDebug("gesture=CONTINUOUS_END")
                                onGesture(SqueezeGesture.CONTINUOUS_END)
                            }
                            continuousReleaseTimer = null
                        }
                    }
                }
            }
        }
    }

    private fun flush() {
        discreteTimer = null
        val count = events.size
        logDebug("flush: count=$count, timestamps=$events")
        events.clear()

        val gesture = when {
            count >= 3 -> SqueezeGesture.TRIPLE
            count == 2 -> SqueezeGesture.DOUBLE
            count == 1 -> SqueezeGesture.SINGLE
            else -> return
        }
        logDebug("gesture=$gesture")
        onGesture(gesture)
    }

    private fun clearDiscreteEvents() {
        events.clear()
        discreteTimer?.cancel()
        discreteTimer = null
    }

    private fun cancelContinuousRelease() {
        continuousReleaseTimer?.cancel()
        continuousReleaseTimer = null
    }

    private class HandlerScheduler : Scheduler {
        private val handler = Handler(Looper.getMainLooper())

        override fun postDelayed(delayMs: Long, action: () -> Unit): Cancelable {
            val runnable = Runnable(action)
            handler.postDelayed(runnable, delayMs)
            return object : Cancelable {
                override fun cancel() {
                    handler.removeCallbacks(runnable)
                }
            }
        }
    }

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // android.jar logging methods are unavailable in local JVM tests.
        }
    }
}

enum class SqueezeGesture {
    SINGLE,
    DOUBLE,
    TRIPLE,
    CONTINUOUS_START,
    CONTINUOUS_END
}
