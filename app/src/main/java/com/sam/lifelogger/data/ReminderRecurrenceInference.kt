package com.sam.lifelogger.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object ReminderRecurrenceInference {
    fun inferOccurrenceDate(
        reminder: Reminder,
        today: LocalDate = LocalDate.now()
    ): LocalDate? {
        if (!reminder.isRecurring || reminder.hasConcreteOccurrence()) return null
        val recurrence = reminder.recurrence ?: return null
        return when (recurrence.frequency?.lowercase()) {
            "weekly" -> inferWeekly(today, recurrence.dayOfWeek)
            "monthly" -> inferMonthlyDayOfMonth(today, recurrence)
            else -> null
        }
    }

    fun inferOccurrenceLocal(
        reminder: Reminder,
        today: LocalDate = LocalDate.now(),
        fallbackZone: ZoneId = ZoneId.systemDefault()
    ): String? {
        val date = inferOccurrenceDate(reminder, today) ?: return null
        val zone = reminder.zoneOrFallback(fallbackZone)
        val time = reminder.recurrence?.timeLocal?.let { parseTime(it) } ?: LocalTime.of(9, 0)
        return date.atTime(time)
            .atZone(zone)
            .toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun Reminder.hasConcreteOccurrence(): Boolean =
        !scheduledAtLocal.isNullOrBlank() ||
            !scheduledAtUtc.isNullOrBlank() ||
            !recurrenceOccurrenceLocal.isNullOrBlank()

    private fun inferWeekly(today: LocalDate, recurrenceDayOfWeek: Int?): LocalDate? {
        val targetDay = recurrenceDayOfWeek
            ?.takeIf { it in 1..7 }
            ?.let { DayOfWeek.of(it) }
            ?: return null
        val daysUntil = (targetDay.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(daysUntil.toLong())
    }

    private fun inferMonthlyDayOfMonth(today: LocalDate, recurrence: Recurrence): LocalDate? {
        if (recurrence.dayOfWeek != null || recurrence.ordinal != null || recurrence.weekdayOrdinal != null) {
            return null
        }
        val dayOfMonth = recurrence.dayOfMonth?.takeIf { it in 1..31 } ?: return null
        val thisMonth = YearMonth.from(today)
        val current = dateInMonth(thisMonth, dayOfMonth)
        if (current != null && !current.isBefore(today)) return current
        return dateInMonth(thisMonth.plusMonths(1), dayOfMonth)
    }

    private fun dateInMonth(month: YearMonth, dayOfMonth: Int): LocalDate? {
        if (dayOfMonth > month.lengthOfMonth()) return null
        return month.atDay(dayOfMonth)
    }

    private fun Reminder.zoneOrFallback(fallback: ZoneId): ZoneId {
        val raw = recurrence?.timezone ?: timezone
        return raw?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
    }

    private fun parseTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value) }.getOrNull()
            ?: runCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
}
