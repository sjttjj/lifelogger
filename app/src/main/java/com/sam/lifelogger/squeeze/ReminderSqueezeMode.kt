package com.sam.lifelogger.squeeze

import android.os.Handler
import android.os.Looper

enum class StopReason {
    MANUAL,
    TIMEOUT
}

class ReminderSqueezeMode(
    private val scheduler: Scheduler = HandlerScheduler(),
    private val onStart: () -> Unit,
    private val onStop: (StopReason) -> Unit,
    private val onTimeout: () -> Unit
) {

    interface Cancelable {
        fun cancel()
    }

    interface Scheduler {
        fun postDelayed(delayMs: Long, action: () -> Unit): Cancelable
    }

    companion object {
        const val AUTO_STOP_MS = 120_000L
    }

    var isActive: Boolean = false
        private set

    private var timeout: Cancelable? = null

    fun start(): Boolean {
        if (isActive) return false

        isActive = true
        timeout = scheduler.postDelayed(AUTO_STOP_MS) {
            if (isActive) {
                isActive = false
                timeout = null
                onTimeout()
            }
        }
        onStart()
        return true
    }

    fun stop(reason: StopReason): Boolean {
        if (!isActive) return false

        isActive = false
        timeout?.cancel()
        timeout = null
        onStop(reason)
        return true
    }

    fun cancel(): Boolean {
        if (!isActive) return false

        isActive = false
        timeout?.cancel()
        timeout = null
        return true
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
}
