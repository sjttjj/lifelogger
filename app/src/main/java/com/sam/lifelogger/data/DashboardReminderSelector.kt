package com.sam.lifelogger.data

object DashboardReminderSelector {
    fun selectPressing(reminders: List<Reminder>, limit: Int = 2): List<Reminder> {
        return reminders
            .filter { it.status.equals("pending", ignoreCase = true) }
            .sortedWith(
                compareBy<Reminder> { it.scheduledAtUtc == null }
                    .thenBy { it.scheduledAtUtc ?: "" }
                    .thenBy { it.id }
            )
            .take(limit)
    }
}
