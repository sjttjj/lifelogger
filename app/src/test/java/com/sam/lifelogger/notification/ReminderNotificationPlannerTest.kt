package com.sam.lifelogger.notification

import com.sam.lifelogger.data.NotificationJob
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReminderNotificationPlannerTest {

    @Test
    fun filtersToPendingFuturePushJobs() {
        val now = Instant.parse("2026-06-05T00:00:00Z")
        val jobs = listOf(
            job(id = 1, notifyAtUtc = "2026-06-05T01:00:00Z", channel = "push", status = "pending"),
            job(id = 2, notifyAtUtc = "2026-06-04T23:00:00Z", channel = "push", status = "pending"),
            job(id = 3, notifyAtUtc = "2026-06-05T01:00:00Z", channel = "email", status = "pending"),
            job(id = 4, notifyAtUtc = "2026-06-05T01:00:00Z", channel = "push", status = "cancelled"),
            job(id = 5, notifyAtUtc = "not-a-date", channel = "push", status = "pending")
        )

        assertEquals(listOf(1L), ReminderNotificationPlanner.pendingFuturePushJobs(jobs, now).map { it.id })
    }

    @Test
    fun filtersToRecentlyDuePendingPushJobs() {
        val now = Instant.parse("2026-06-05T08:35:00Z")
        val jobs = listOf(
            job(id = 1, notifyAtUtc = "2026-06-05T08:30:00Z", channel = "push", status = "pending"),
            job(id = 2, notifyAtUtc = "2026-06-05T08:36:00Z", channel = "push", status = "pending"),
            job(id = 3, notifyAtUtc = "2026-06-04T08:34:59Z", channel = "push", status = "pending"),
            job(id = 4, notifyAtUtc = "2026-06-05T08:30:00Z", channel = "email", status = "pending"),
            job(id = 5, notifyAtUtc = "2026-06-05T08:30:00Z", channel = "push", status = "cancelled"),
            job(id = 6, notifyAtUtc = "not-a-date", channel = "push", status = "pending")
        )

        assertEquals(
            listOf(1L),
            ReminderNotificationPlanner.recentlyDuePushJobs(jobs, now, Duration.ofDays(1)).map { it.id }
        )
    }

    private fun job(id: Long, notifyAtUtc: String, channel: String, status: String): NotificationJob =
        NotificationJob(
            id = id,
            reminderId = 10,
            notifyAtUtc = notifyAtUtc,
            notificationTitle = "Reminder",
            notificationBody = null,
            channel = channel,
            status = status
        )
}
