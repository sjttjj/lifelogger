package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationConfigTest {
    @Test
    fun noneBuildsDisabledEmptyRequest() {
        val request = ReminderNotificationConfig.None.toRequest(
            scheduledAtUtc = "2026-06-10T07:00:00Z",
            title = "Dentist",
            body = "Clinic"
        )

        assertFalse(request.notificationsEnabled)
        assertEquals(emptyList<ReminderNotificationJobRequest>(), request.jobs)
    }

    @Test
    fun atTimeBuildsSingleJobAtScheduledInstant() {
        val request = ReminderNotificationConfig.AtTime.toRequest(
            scheduledAtUtc = "2026-06-10T07:00:00Z",
            title = "Dentist",
            body = "Clinic"
        )

        assertTrue(request.notificationsEnabled)
        assertEquals(1, request.jobs.size)
        assertEquals("2026-06-10T07:00:00Z", request.jobs.single().notifyAtUtc)
    }

    @Test
    fun customOffsetsBuildUpToThreeJobsBeforeScheduledInstant() {
        val request = ReminderNotificationConfig.Custom(
            offsets = listOf(
                ReminderNotificationOffset(2, ReminderNotificationOffsetUnit.Days),
                ReminderNotificationOffset(1, ReminderNotificationOffsetUnit.Days),
                ReminderNotificationOffset(1, ReminderNotificationOffsetUnit.Hours),
                ReminderNotificationOffset(30, ReminderNotificationOffsetUnit.Minutes)
            )
        ).toRequest(
            scheduledAtUtc = "2026-06-10T07:00:00Z",
            title = "Dentist",
            body = "Clinic"
        )

        assertTrue(request.notificationsEnabled)
        assertEquals(
            listOf(
                "2026-06-08T07:00:00Z",
                "2026-06-09T07:00:00Z",
                "2026-06-10T06:00:00Z"
            ),
            request.jobs.map { it.notifyAtUtc }
        )
    }

    @Test
    fun notificationRequestSerializesServerContractShape() {
        val request = ReminderNotificationReplaceRequest(
            notificationsEnabled = true,
            jobs = listOf(
                ReminderNotificationJobRequest(
                    notifyAtUtc = "2026-06-10T06:00:00Z",
                    notificationTitle = "Dentist",
                    notificationBody = "Clinic",
                    channel = "push"
                )
            )
        ).toJson()

        assertTrue(request.getBoolean("notifications_enabled"))
        val job = request.getJSONArray("jobs").getJSONObject(0)
        assertEquals("2026-06-10T06:00:00Z", job.getString("notify_at_utc"))
        assertEquals("Dentist", job.getString("notification_title"))
        assertEquals("Clinic", job.getString("notification_body"))
        assertEquals("push", job.getString("channel"))
    }

    @Test
    fun derivesInitialConfigFromExistingJobs() {
        val config = ReminderNotificationConfig.fromReminder(
            scheduledAtUtc = "2026-06-10T07:00:00Z",
            jobs = listOf(
                NotificationJob(
                    id = 1,
                    reminderId = 42,
                    notifyAtUtc = "2026-06-09T07:00:00Z",
                    notificationTitle = "Dentist",
                    notificationBody = null,
                    channel = "push",
                    status = "pending"
                )
            )
        )

        val custom = config as ReminderNotificationConfig.Custom
        assertEquals(listOf(ReminderNotificationOffset(1, ReminderNotificationOffsetUnit.Days)), custom.offsets)
    }
}
