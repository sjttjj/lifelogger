package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderRecurrenceInferenceTest {
    @Test
    fun weeklyMondayWhenTodayIsMondayReturnsToday() {
        val reminder = reminder(
            recurrence = recurrence(frequency = "weekly", dayOfWeek = 1)
        )

        val result = ReminderRecurrenceInference.inferOccurrenceDate(
            reminder,
            today = LocalDate.of(2026, 6, 22)
        )

        assertEquals(LocalDate.of(2026, 6, 22), result)
    }

    @Test
    fun weeklyMondayWhenTodayIsWednesdayReturnsNextMonday() {
        val reminder = reminder(
            recurrence = recurrence(frequency = "weekly", dayOfWeek = 1)
        )

        val result = ReminderRecurrenceInference.inferOccurrenceDate(
            reminder,
            today = LocalDate.of(2026, 6, 24)
        )

        assertEquals(LocalDate.of(2026, 6, 29), result)
    }

    @Test
    fun monthlySixthWhenFutureThisMonthReturnsThisMonth() {
        val reminder = reminder(
            recurrence = recurrence(frequency = "monthly", dayOfMonth = 6)
        )

        val result = ReminderRecurrenceInference.inferOccurrenceDate(
            reminder,
            today = LocalDate.of(2026, 6, 1)
        )

        assertEquals(LocalDate.of(2026, 6, 6), result)
    }

    @Test
    fun monthlySixthWhenPassedReturnsNextMonth() {
        val reminder = reminder(
            recurrence = recurrence(frequency = "monthly", dayOfMonth = 6)
        )

        val result = ReminderRecurrenceInference.inferOccurrenceDate(
            reminder,
            today = LocalDate.of(2026, 6, 23)
        )

        assertEquals(LocalDate.of(2026, 7, 6), result)
    }

    @Test
    fun monthlyWeekdayWithoutDayOfMonthReturnsNull() {
        val reminder = reminder(
            recurrence = recurrence(frequency = "monthly", dayOfWeek = 1)
        )

        val result = ReminderRecurrenceInference.inferOccurrenceDate(
            reminder,
            today = LocalDate.of(2026, 6, 23)
        )

        assertNull(result)
    }

    @Test
    fun nonRecurringReminderReturnsNull() {
        val reminder = reminder(
            isRecurring = false,
            recurrence = recurrence(frequency = "weekly", dayOfWeek = 1)
        )

        val result = ReminderRecurrenceInference.inferOccurrenceDate(
            reminder,
            today = LocalDate.of(2026, 6, 23)
        )

        assertNull(result)
    }

    @Test
    fun inferredOccurrenceLocalUsesRecurrenceTimeAndTimezone() {
        val reminder = reminder(
            recurrence = recurrence(frequency = "weekly", dayOfWeek = 1)
        )

        val result = ReminderRecurrenceInference.inferOccurrenceLocal(
            reminder = reminder,
            today = LocalDate.of(2026, 6, 24),
            fallbackZone = ZoneId.of("UTC")
        )

        assertEquals("2026-06-29T09:00:00+10:00", result)
    }

    private fun recurrence(
        frequency: String,
        dayOfWeek: Int? = null,
        dayOfMonth: Int? = null
    ) = Recurrence(
        id = 10L,
        title = null,
        kind = null,
        status = "active",
        timezone = "Australia/Sydney",
        frequency = frequency,
        intervalCount = 1,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        timeLocal = "09:00"
    )

    private fun reminder(
        isRecurring: Boolean = true,
        recurrence: Recurrence? = null,
        scheduledAtLocal: String? = null,
        scheduledAtUtc: String? = null,
        recurrenceOccurrenceLocal: String? = null
    ) = Reminder(
        id = 1L,
        sourceSegmentId = null,
        kind = "task",
        title = "Test reminder",
        description = null,
        status = "pending",
        needsReview = true,
        timezone = "Australia/Sydney",
        scheduledAtLocal = scheduledAtLocal,
        scheduledAtUtc = scheduledAtUtc,
        endAtLocal = null,
        endAtUtc = null,
        schedulePrecision = "date",
        usedDefaultTime = false,
        location = null,
        people = null,
        amount = null,
        recurrenceText = null,
        isRecurring = isRecurring,
        recurrenceSeriesId = if (isRecurring) 10L else null,
        recurrenceOccurrenceLocal = recurrenceOccurrenceLocal,
        recurrence = recurrence,
        actions = emptyList(),
        notificationJobs = emptyList()
    )
}
