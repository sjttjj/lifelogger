package com.sam.lifelogger.calendar

import com.sam.lifelogger.data.Recurrence
import com.sam.lifelogger.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LifeCalendarModelsTest {

    @Test
    fun mapsSingleDayReminderToOneCalendarDate() {
        val reminder = reminder(
            scheduledAtLocal = "2026-06-10T09:00:00+10:00",
            endAtLocal = null
        )

        val item = LifeCalendarItem.fromReminder(reminder)

        assertEquals(listOf(LocalDate.of(2026, 6, 10)), item.coveredDates())
    }

    @Test
    fun mapsMultiDayReminderAcrossInclusiveDateRange() {
        val reminder = reminder(
            scheduledAtLocal = "2026-06-10T09:00:00+10:00",
            endAtLocal = "2026-06-12T17:00:00+10:00"
        )

        val item = LifeCalendarItem.fromReminder(reminder)

        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 11),
                LocalDate.of(2026, 6, 12)
            ),
            item.coveredDates()
        )
    }

    @Test
    fun mapsRecurringOccurrenceLocalWhenScheduledLocalIsMissing() {
        val reminder = reminder(
            scheduledAtLocal = null,
            endAtLocal = null,
            recurrenceOccurrenceLocal = "2026-06-17T09:00:00+10:00"
        )

        val item = LifeCalendarItem.fromReminder(reminder)

        assertEquals("2026-06-17T09:00:00+10:00", item.startLocal)
        assertEquals(listOf(LocalDate.of(2026, 6, 17)), item.coveredDates())
    }

    @Test
    fun infersWeeklyRecurringDateWhenServerOmittedConcreteOccurrence() {
        val reminder = reminder(
            scheduledAtLocal = null,
            scheduledAtUtc = null,
            endAtLocal = null,
            isRecurring = true,
            recurrence = Recurrence(
                id = 100,
                title = null,
                kind = null,
                status = "active",
                timezone = "Australia/Sydney",
                frequency = "weekly",
                intervalCount = 1,
                dayOfWeek = 1,
                dayOfMonth = null,
                timeLocal = "09:00"
            )
        )

        val item = LifeCalendarItem.fromReminder(reminder, today = LocalDate.of(2026, 6, 24))

        assertEquals("2026-06-29T09:00:00+10:00", item.startLocal)
        assertEquals(listOf(LocalDate.of(2026, 6, 29)), item.coveredDates())
    }

    private fun reminder(
        scheduledAtLocal: String?,
        endAtLocal: String?,
        recurrenceOccurrenceLocal: String? = null,
        scheduledAtUtc: String? = "2026-06-09T23:00:00Z",
        isRecurring: Boolean = false,
        recurrence: Recurrence? = null
    ): Reminder {
        return Reminder(
            id = 7,
            sourceSegmentId = null,
            kind = "event",
            title = "Calendar item",
            description = null,
            status = "pending",
            needsReview = false,
            timezone = "Australia/Sydney",
            scheduledAtLocal = scheduledAtLocal,
            scheduledAtUtc = scheduledAtUtc,
            endAtLocal = endAtLocal,
            endAtUtc = if (endAtLocal == null) null else "2026-06-12T07:00:00Z",
            schedulePrecision = "datetime",
            usedDefaultTime = false,
            location = null,
            people = null,
            amount = null,
            recurrenceText = null,
            isRecurring = isRecurring,
            recurrenceSeriesId = if (isRecurring) 100 else null,
            recurrenceOccurrenceLocal = recurrenceOccurrenceLocal,
            recurrence = recurrence,
            notificationJobs = emptyList()
        )
    }
}
