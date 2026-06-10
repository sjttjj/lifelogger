package com.sam.lifelogger.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingModeCoordinatorTest {

    @Test
    fun normalToggleStartsAndStopsNormal() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.toggleNormal()

        assertEquals(
            listOf("start:normal", "stop"),
            sink.events
        )
    }

    @Test
    fun sessionInterruptsNormalViaSwitch() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.toggleSession()
        coordinator.toggleSession()

        assertEquals(
            listOf(
                "start:normal",
                "switch:session",
                "switch:normal"
            ),
            sink.events
        )
    }

    @Test
    fun sessionToggledFromIdleStartsAndStops() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleSession()
        coordinator.toggleSession()

        assertEquals(
            listOf("start:session", "stop"),
            sink.events
        )
    }

    @Test
    fun sessionResumesNormalAfterStopWhenNormalWasRequested() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.toggleSession()
        coordinator.toggleSession()

        // After session stops, normal should resume via switch
        assertTrue(coordinator.isNormalRequested)
        assertEquals(RecordingMode.NORMAL, coordinator.activeMode)
    }

    @Test
    fun reminderInterruptsSessionViaSwitchThenSessionResumes() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleSession()
        coordinator.startReminder()
        coordinator.stopReminder()

        assertEquals(
            listOf(
                "start:session",
                "switch:reminder",
                "switch:session"
            ),
            sink.events
        )
    }

    @Test
    fun reminderInterruptsNormalViaSwitchThenNormalResumes() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.startReminder()
        coordinator.stopReminder()

        assertEquals(
            listOf(
                "start:normal",
                "switch:reminder",
                "switch:normal"
            ),
            sink.events
        )
    }

    @Test
    fun singleSqueezeWhileReminderActiveStopsReminderNotNormal() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.startReminder()
        assertTrue(coordinator.stopReminder())

        assertTrue(coordinator.isNormalRequested)
        assertEquals(RecordingMode.NORMAL, coordinator.activeMode)
    }

    @Test
    fun duplicateReminderStartDoesNothing() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.startReminder()
        assertFalse(coordinator.startReminder())

        assertEquals(listOf("start:reminder"), sink.events)
    }

    @Test
    fun stoppingLastModeShutsDownService() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.toggleSession()
        coordinator.toggleSession()
        coordinator.toggleNormal()

        assertEquals(
            listOf(
                "start:normal",
                "switch:session",
                "switch:normal",
                "stop"
            ),
            sink.events
        )
    }

    @Test
    fun reminderFromNormalSwitchThenNormalToggleOff() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.startReminder()
        coordinator.stopReminder()
        coordinator.toggleNormal()

        assertEquals(
            listOf(
                "start:normal",
                "switch:reminder",
                "switch:normal",
                "stop"
            ),
            sink.events
        )
    }

    @Test
    fun restoredActiveSessionToggleStopsInsteadOfStartingAgain() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.restoreState(
            activeMode = RecordingMode.SESSION,
            isNormalRequested = false,
            isSessionRequested = true
        )
        coordinator.toggleSession()

        assertEquals(listOf("stop"), sink.events)
        assertEquals(null, coordinator.activeMode)
        assertFalse(coordinator.isSessionRequested)
    }

    private class RecordingSink : RecordingModeCoordinator.RecordingSink {
        val events = mutableListOf<String>()

        override fun start(mode: RecordingMode) {
            events.add("start:${mode.wireValue}")
        }

        override fun switchMode(mode: RecordingMode) {
            events.add("switch:${mode.wireValue}")
        }

        override fun stop() {
            events.add("stop")
        }
    }
}
