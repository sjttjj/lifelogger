package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSyncPolicyTest {
    @Test
    fun usesCachedAllRemindersWhenServerAllResponseIsEmpty() {
        val cached = listOf(reminder(1, "Cached"))

        assertEquals(cached, ReminderSyncPolicy.chooseAllReminders(server = emptyList(), cache = cached))
    }

    @Test
    fun usesServerAllRemindersWhenServerReturnsItems() {
        val server = listOf(reminder(2, "Server"))
        val cached = listOf(reminder(1, "Cached"))

        assertEquals(server, ReminderSyncPolicy.chooseAllReminders(server = server, cache = cached))
    }

    @Test
    fun usesUpcomingRemindersForCalendarAllWhenFullListIsEmpty() {
        val upcoming = listOf(reminder(3, "Upcoming"))

        assertEquals(upcoming, ReminderSyncPolicy.chooseCalendarAllReminders(all = emptyList(), upcoming = upcoming))
    }

    @Test
    fun usesFullListForCalendarAllWhenAvailable() {
        val all = listOf(reminder(2, "All"))
        val upcoming = listOf(reminder(3, "Upcoming"))

        assertEquals(all, ReminderSyncPolicy.chooseCalendarAllReminders(all = all, upcoming = upcoming))
    }

    @Test
    fun updatesCachedReminderStatusAndReviewState() {
        val existing = reminder(4, "Existing").copy(needsReview = true)

        val updated = ReminderSyncPolicy.withLocalStatus(
            reminder = existing,
            status = "archived",
            needsReview = false
        )

        assertEquals("archived", updated.status)
        assertEquals(false, updated.needsReview)
    }

    private fun reminder(id: Long, title: String): Reminder =
        Reminder(
            id = id,
            sourceSegmentId = null,
            kind = "task",
            title = title,
            description = null,
            status = "pending",
            needsReview = false,
            timezone = "Australia/Sydney",
            scheduledAtLocal = "2026-06-18T09:00:00+10:00",
            scheduledAtUtc = "2026-06-17T23:00:00Z",
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
