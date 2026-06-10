package com.sam.lifelogger.data

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReminderDisplayFormatter {
    private val sydneyZone: ZoneId = ZoneId.of("Australia/Sydney")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")

    fun dashboardTime(reminder: Reminder): String {
        reminder.scheduledAtLocal?.let { local ->
            runCatching {
                return OffsetDateTime.parse(local).format(timeFormatter)
            }
        }

        reminder.scheduledAtUtc?.let { utc ->
            runCatching {
                return Instant.parse(utc)
                    .atZone(sydneyZone)
                    .format(timeFormatter)
            }
        }

        return ""
    }

    fun dashboardDateContext(
        reminder: Reminder,
        today: LocalDate = LocalDate.now(sydneyZone)
    ): String {
        val date = reminder.scheduledAtLocal?.let { local ->
            runCatching { OffsetDateTime.parse(local).toLocalDate() }.getOrNull()
        } ?: reminder.scheduledAtUtc?.let { utc ->
            runCatching { Instant.parse(utc).atZone(sydneyZone).toLocalDate() }.getOrNull()
        } ?: return reminder.schedulePrecision

        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.format(dateFormatter)
        }
    }
}
