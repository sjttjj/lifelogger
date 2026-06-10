package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardReminderSelectorTest {

    @Test
    fun selectsTwoEarliestScheduledPendingReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "Later", scheduledAtUtc = "2026-06-10T08:00:00Z"),
            reminder(id = 2, title = "First", scheduledAtUtc = "2026-06-09T08:00:00Z"),
            reminder(id = 3, title = "Second", scheduledAtUtc = "2026-06-09T09:00:00Z")
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("First", "Second"), selected.map { it.title })
    }

    @Test
    fun putsUndatedRemindersAfterDatedReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "No date", scheduledAtUtc = null),
            reminder(id = 2, title = "Dated", scheduledAtUtc = "2026-06-09T08:00:00Z")
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("Dated", "No date"), selected.map { it.title })
    }

    @Test
    fun excludesNonPendingReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "Done", status = "completed", scheduledAtUtc = "2026-06-09T08:00:00Z"),
            reminder(id = 2, title = "Pending", status = "pending", scheduledAtUtc = "2026-06-09T09:00:00Z")
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("Pending"), selected.map { it.title })
    }

    private fun reminder(
        id: Long,
        title: String,
        status: String = "pending",
        scheduledAtUtc: String?
    ): Reminder {
        return Reminder(
            id = id,
            sourceSegmentId = null,
            kind = "task",
            title = title,
            description = null,
            status = status,
            needsReview = false,
            timezone = "Australia/Sydney",
            scheduledAtLocal = null,
            scheduledAtUtc = scheduledAtUtc,
            endAtLocal = null,
            endAtUtc = null,
            schedulePrecision = "datetime",
            usedDefaultTime = false,
            location = null,
            people = null,
            amount = null,
            recurrenceText = null,
            notificationJobs = emptyList()
        )
    }
}
