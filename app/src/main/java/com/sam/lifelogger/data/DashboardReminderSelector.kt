package com.sam.lifelogger.data

import java.time.LocalDate
import java.time.OffsetDateTime

object DashboardReminderSelector {
    fun selectPressing(reminders: List<Reminder>, limit: Int = 2): List<Reminder> {
        val today = LocalDate.now()
        return reminders
            .filter { it.status.equals("pending", ignoreCase = true) }
            .filter { reminder ->
                // Only show reminders for today or the future
                val date = reminder.scheduledAtLocal?.let {
                    runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull()
                }
                date != null && !date.isBefore(today)
            }
            .sortedWith(
                compareBy<Reminder> { it.scheduledAtUtc == null }
                    .thenBy { it.scheduledAtUtc ?: "" }
                    .thenBy { it.id }
            )
            .take(limit)
    }
}