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
    val needsReview: Boolean
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
                startLocal = reminder.scheduledAtLocal,
                endUtc = reminder.endAtUtc,
                endLocal = reminder.endAtLocal,
                status = reminder.status,
                needsReview = reminder.needsReview
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
