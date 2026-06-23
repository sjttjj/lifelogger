package com.sam.lifelogger.data

object ReminderSyncPolicy {
    fun chooseAllReminders(server: List<Reminder>, cache: List<Reminder>): List<Reminder> =
        server.ifEmpty { cache }

    fun chooseCalendarAllReminders(all: List<Reminder>, upcoming: List<Reminder>): List<Reminder> =
        all.ifEmpty { upcoming }

    fun withLocalStatus(
        reminder: Reminder,
        status: String,
        needsReview: Boolean? = null
    ): Reminder =
        reminder.copy(
            status = status,
            needsReview = needsReview ?: reminder.needsReview
        )
}
