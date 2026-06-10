package com.sam.lifelogger.squeeze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSqueezeModeTest {

    @Test
    fun startActivatesReminderModeAndSchedulesTimeout() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<String>()
        val mode = mode(events, scheduler)

        assertTrue(mode.start())

        assertTrue(mode.isActive)
        assertEquals(listOf("start"), events)

        scheduler.advanceBy(ReminderSqueezeMode.AUTO_STOP_MS)

        assertFalse(mode.isActive)
        assertEquals(listOf("start", "timeout"), events)
    }

    @Test
    fun duplicateStartDoesNotRestartOrNotifyAgain() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<String>()
        val mode = mode(events, scheduler)

        assertTrue(mode.start())
        scheduler.advanceBy(60_000L)
        assertFalse(mode.start())
        scheduler.advanceBy(60_000L)

        assertFalse(mode.isActive)
        assertEquals(listOf("start", "timeout"), events)
    }

    @Test
    fun stopEndsReminderModeAndCancelsTimeout() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<String>()
        val mode = mode(events, scheduler)

        mode.start()
        assertTrue(mode.stop(StopReason.MANUAL))
        scheduler.advanceBy(ReminderSqueezeMode.AUTO_STOP_MS)

        assertFalse(mode.isActive)
        assertEquals(listOf("start", "stop:MANUAL"), events)
    }

    @Test
    fun stopReturnsFalseWhenInactive() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<String>()
        val mode = mode(events, scheduler)

        assertFalse(mode.stop(StopReason.MANUAL))

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun cancelClearsReminderModeWithoutStopCallback() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<String>()
        val mode = mode(events, scheduler)

        mode.start()
        assertTrue(mode.cancel())
        scheduler.advanceBy(ReminderSqueezeMode.AUTO_STOP_MS)

        assertFalse(mode.isActive)
        assertEquals(listOf("start"), events)
    }

    private fun mode(
        events: MutableList<String>,
        scheduler: ManualScheduler
    ): ReminderSqueezeMode {
        return ReminderSqueezeMode(
            scheduler = scheduler,
            onStart = { events.add("start") },
            onStop = { reason -> events.add("stop:$reason") },
            onTimeout = { events.add("timeout") }
        )
    }

    private class ManualScheduler : ReminderSqueezeMode.Scheduler {
        var now: Long = 0L
            private set

        private val tasks = mutableListOf<Task>()

        override fun postDelayed(delayMs: Long, action: () -> Unit): ReminderSqueezeMode.Cancelable {
            val task = Task(now + delayMs, action)
            tasks.add(task)
            return object : ReminderSqueezeMode.Cancelable {
                override fun cancel() {
                    task.canceled = true
                }
            }
        }

        fun advanceBy(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks
                    .filter { !it.canceled && it.dueAt <= target }
                    .minByOrNull { it.dueAt } ?: break

                tasks.remove(next)
                now = next.dueAt
                next.action()
            }
            now = target
        }
    }

    private data class Task(
        val dueAt: Long,
        val action: () -> Unit,
        var canceled: Boolean = false
    )
}
