package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DashboardReminderSelectorTest {

    @Test
    fun selectsTwoEarliestScheduledPendingReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "Later", dayOffset = 2),
            reminder(id = 2, title = "First", dayOffset = 0),
            reminder(id = 3, title = "Second", dayOffset = 1)
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("First", "Second"), selected.map { it.title })
    }

    @Test
    fun putsUndatedRemindersAfterDatedReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "No date", dayOffset = 0, scheduledAtUtc = null),
            reminder(id = 2, title = "Dated", dayOffset = 0)
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("Dated", "No date"), selected.map { it.title })
    }

    @Test
    fun excludesNonPendingReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "Done", status = "completed", dayOffset = 0),
            reminder(id = 2, title = "Pending", status = "pending", dayOffset = 0)
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("Pending"), selected.map { it.title })
    }

    /**
     * Builds a Reminder whose scheduledAtLocal is [dayOffset] days from today (system zone),
     * so the selector's today/future filter always passes. scheduledAtUtc is derived from the
     * same instant unless overridden (e.g. to null to model an undated reminder).
     */
    private fun reminder(
        id: Long,
        title: String,
        status: String = "pending",
        dayOffset: Long = 0L,
        scheduledAtUtc: String? = "__derive__"
    ): Reminder {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone).plusDays(dayOffset)
        val zoned = date.atTime(9, 0).atZone(zone)
        val scheduledLocal = zoned.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val scheduledUtcValue = when (scheduledAtUtc) {
            null -> null
            "__derive__" -> zoned.withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            else -> scheduledAtUtc
        }
        return Reminder(
            id = id,
            sourceSegmentId = null,
            kind = "task",
            title = title,
            description = null,
            status = status,
            needsReview = false,
            timezone = zone.id,
            scheduledAtLocal = scheduledLocal,
            scheduledAtUtc = scheduledUtcValue,
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
