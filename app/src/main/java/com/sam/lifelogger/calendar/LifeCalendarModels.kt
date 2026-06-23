package com.sam.lifelogger.calendar

import com.sam.lifelogger.data.Reminder
import java.time.LocalDate
import java.time.OffsetDateTime

enum class LifeCalendarItemType {
    Reminder,
    Habit,
    Workout,
    FinancialAlert
}

data class LifeCalendarItem(
    val id: String,
    val sourceType: LifeCalendarItemType,
    val sourceId: Long,
    val title: String,
    val description: String?,
    val startUtc: String?,
    val startLocal: String?,
    val endUtc: String?,
    val endLocal: String?,
    val status: String,
    val needsReview: Boolean,
    val isRecurring: Boolean = false,
    val recurrenceLabel: String? = null,
    val usedDefaultTime: Boolean = false,
    val schedulePrecision: String = "date"
) {
    companion object {
        fun fromReminder(reminder: Reminder): LifeCalendarItem =
            LifeCalendarItem(
                id = "reminder:${reminder.id}",
                sourceType = LifeCalendarItemType.Reminder,
                sourceId = reminder.id,
                title = reminder.title,
                description = reminder.description,
                startUtc = reminder.scheduledAtUtc,
                startLocal = reminder.scheduledAtLocal ?: reminder.recurrenceOccurrenceLocal,
                endUtc = reminder.endAtUtc,
                endLocal = reminder.endAtLocal,
                status = reminder.status,
                needsReview = reminder.needsReview,
                isRecurring = reminder.isRecurring,
                recurrenceLabel = reminder.recurrence?.toLabel() ?: reminder.recurrenceText,
                usedDefaultTime = reminder.usedDefaultTime,
                schedulePrecision = reminder.schedulePrecision
            )
    }

    fun coveredDates(): List<LocalDate> {
        val start = parseDate(startLocal) ?: return emptyList()
        val end = parseDate(endLocal) ?: start
        if (end.isBefore(start)) return listOf(start)

        return generateSequence(start) { date ->
            val next = date.plusDays(1)
            if (next.isAfter(end)) null else next
        }.toList()
    }
}

private fun parseDate(value: String?): LocalDate? =
    value?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }

/**
 * Human-readable summary of a recurrence series, e.g. "Monthly on day 6" or "Weekly".
 */
private fun com.sam.lifelogger.data.Recurrence.toLabel(): String {
    val freq = frequency?.lowercase()?.let { freqWord(it) } ?: "recurring"
    val interval = if (intervalCount != null && intervalCount > 1) "every $intervalCount " else ""
    val on = when {
        dayOfMonth != null && dayOfWeek == null -> " on day $dayOfMonth"
        dayOfWeek != null -> " on ${dayOfWeekName(dayOfWeek)}"
        else -> ""
    }
    val at = timeLocal?.takeIf { it.isNotBlank() && timeLocal != "12:00" }?.let { " at $it" } ?: ""
    return "${interval}${freq}$on$at".trim().replaceFirstChar { it.uppercase() }
}

private fun freqWord(freq: String): String = when (freq) {
    "daily" -> "daily"
    "weekly" -> "weekly"
    "monthly" -> "monthly"
    "yearly", "annually" -> "yearly"
    else -> freq
}

private fun dayOfWeekName(value: Int?): String = when (value) {
    0 -> "Sunday"
    1 -> "Monday"
    2 -> "Tuesday"
    3 -> "Wednesday"
    4 -> "Thursday"
    5 -> "Friday"
    6 -> "Saturday"
    else -> "day $value"
}
