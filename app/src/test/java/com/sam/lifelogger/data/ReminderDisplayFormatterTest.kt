package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderDisplayFormatterTest {

    @Test
    fun formatsUtcReminderTimeInSydneyWithDaylightSavings() {
        val reminder = reminder(
            scheduledAtLocal = null,
            scheduledAtUtc = "2026-01-10T06:00:00Z"
        )

        val formatted = ReminderDisplayFormatter.dashboardTime(reminder)

        assertEquals("17:00", formatted)
    }

    @Test
    fun prefersScheduledLocalOffsetWhenPresent() {
        val reminder = reminder(
            scheduledAtLocal = "2026-06-08T17:00:00+10:00",
            scheduledAtUtc = "2026-06-08T07:00:00Z"
        )

        val formatted = ReminderDisplayFormatter.dashboardTime(reminder)

        assertEquals("17:00", formatted)
    }

    @Test
    fun formatsDashboardDateContextForTodayTomorrowAndLaterDate() {
        val today = LocalDate.of(2026, 2, 5)

        assertEquals(
            "Today",
            ReminderDisplayFormatter.dashboardDateContext(
                reminder("2026-02-05T17:00:00+11:00", "2026-02-05T06:00:00Z"),
                today
            )
        )
        assertEquals(
            "Tomorrow",
            ReminderDisplayFormatter.dashboardDateContext(
                reminder("2026-02-06T17:00:00+11:00", "2026-02-06T06:00:00Z"),
                today
            )
        )
        assertEquals(
            "07-Feb-2026",
            ReminderDisplayFormatter.dashboardDateContext(
                reminder("2026-02-07T17:00:00+11:00", "2026-02-07T06:00:00Z"),
                today
            )
        )
    }

    private fun reminder(
        scheduledAtLocal: String?,
        scheduledAtUtc: String?
    ): Reminder {
        return Reminder(
            id = 1,
            sourceSegmentId = null,
            kind = "task",
            title = "Reminder",
            description = null,
            status = "pending",
            needsReview = false,
            timezone = "Australia/Sydney",
            scheduledAtLocal = scheduledAtLocal,
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
