package com.sam.lifelogger.squeeze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqueezeGestureDetectorTest {

    @Test
    fun singleSqueezeStillResolvesAfterGestureWindow() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordEvent()
        scheduler.advanceBy(SqueezeGestureDetector.GESTURE_WINDOW_MS)

        assertEquals(listOf(SqueezeGesture.SINGLE), events)
    }

    @Test
    fun doubleAndTripleSqueezesStillResolveAfterGestureWindow() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordEvent()
        detector.recordEvent()
        scheduler.advanceBy(SqueezeGestureDetector.GESTURE_WINDOW_MS)
        assertEquals(listOf(SqueezeGesture.DOUBLE), events)

        events.clear()
        detector.recordEvent()
        detector.recordEvent()
        detector.recordEvent()
        scheduler.advanceBy(SqueezeGestureDetector.GESTURE_WINDOW_MS)
        assertEquals(listOf(SqueezeGesture.TRIPLE), events)
    }

    @Test
    fun heldProgressEmitsContinuousStartAndEnd() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS - 1)
        assertTrue(events.isEmpty())

        scheduler.advanceBy(1)
        assertEquals(listOf(SqueezeGesture.CONTINUOUS_START), events)

        detector.recordProgress(0.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_RELEASE_DEBOUNCE_MS)
        assertEquals(
            listOf(SqueezeGesture.CONTINUOUS_START, SqueezeGesture.CONTINUOUS_END),
            events
        )
    }

    @Test
    fun slightlyBelowPeakProgressCanStillStartContinuousGesture() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(0.95f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS)

        assertEquals(listOf(SqueezeGesture.CONTINUOUS_START), events)
    }

    @Test
    fun heldProgressSuppressesPendingSingleSqueeze() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordEvent()
        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS)
        detector.recordProgress(0.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_RELEASE_DEBOUNCE_MS)
        scheduler.advanceBy(SqueezeGestureDetector.GESTURE_WINDOW_MS)

        assertEquals(
            listOf(SqueezeGesture.CONTINUOUS_START, SqueezeGesture.CONTINUOUS_END),
            events
        )
    }

    @Test
    fun heldProgressSuppressesLateReleaseDetectedEvent() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS)
        detector.recordProgress(0.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_RELEASE_DEBOUNCE_MS)
        detector.recordEvent()
        scheduler.advanceBy(SqueezeGestureDetector.GESTURE_WINDOW_MS)

        assertEquals(
            listOf(SqueezeGesture.CONTINUOUS_START, SqueezeGesture.CONTINUOUS_END),
            events
        )
    }

    @Test
    fun quickProgressBlipDoesNotEmitContinuousGesture() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS - 1)
        detector.recordProgress(0.0f)
        scheduler.advanceBy(1_000L)

        assertTrue(events.isEmpty())
    }

    @Test
    fun briefReleaseDipDoesNotEndContinuousGesture() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS)
        detector.recordProgress(0.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_RELEASE_DEBOUNCE_MS - 1)
        detector.recordProgress(1.0f)
        scheduler.advanceBy(1_000L)

        assertEquals(listOf(SqueezeGesture.CONTINUOUS_START), events)
    }

    @Test
    fun midProgressAfterReleaseDipCancelsContinuousEnd() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS)
        detector.recordProgress(0.0f)
        scheduler.advanceBy(40L)
        detector.recordProgress(0.52f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_RELEASE_DEBOUNCE_MS)

        assertEquals(listOf(SqueezeGesture.CONTINUOUS_START), events)
    }

    @Test
    fun lowProgressAboveReleaseThresholdCancelsContinuousEnd() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS)
        detector.recordProgress(0.0f)
        scheduler.advanceBy(500L)
        detector.recordProgress(0.25f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_RELEASE_DEBOUNCE_MS)

        assertEquals(listOf(SqueezeGesture.CONTINUOUS_START), events)
    }

    @Test
    fun sustainedReleaseEndsContinuousGestureAfterDebounce() {
        val scheduler = ManualScheduler()
        val events = mutableListOf<SqueezeGesture>()
        val detector = detector(events, scheduler)

        detector.recordProgress(1.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_HOLD_MS)
        detector.recordProgress(0.0f)
        scheduler.advanceBy(SqueezeGestureDetector.CONTINUOUS_RELEASE_DEBOUNCE_MS - 1)
        assertEquals(listOf(SqueezeGesture.CONTINUOUS_START), events)

        scheduler.advanceBy(1)

        assertEquals(
            listOf(SqueezeGesture.CONTINUOUS_START, SqueezeGesture.CONTINUOUS_END),
            events
        )
    }

    private fun detector(
        events: MutableList<SqueezeGesture>,
        scheduler: ManualScheduler
    ): SqueezeGestureDetector {
        return SqueezeGestureDetector(
            onGesture = { events.add(it) },
            clock = { scheduler.now },
            scheduler = scheduler
        )
    }

    private class ManualScheduler : SqueezeGestureDetector.Scheduler {
        var now: Long = 0L
            private set

        private val tasks = mutableListOf<Task>()

        override fun postDelayed(delayMs: Long, action: () -> Unit): SqueezeGestureDetector.Cancelable {
            val task = Task(now + delayMs, action)
            tasks.add(task)
            return object : SqueezeGestureDetector.Cancelable {
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
