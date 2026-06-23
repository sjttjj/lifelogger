package com.sam.lifelogger.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderModelsTest {

    @Test
    fun parsesSQLiteIntegerBooleansAndNotificationJobs() {
        val json = JSONObject(
            """
            {
              "reminders": [
                {
                  "id": 42,
                  "source_segment_id": null,
                  "kind": "task",
                  "title": "Pay rego",
                  "description": null,
                  "status": "pending",
                  "needs_review": 1,
                  "timezone": "Australia/Sydney",
                  "scheduled_at_local": "2026-06-07T09:00:00+10:00",
                  "scheduled_at_utc": "2026-06-06T23:00:00Z",
                  "schedule_precision": "date",
                  "used_default_time": 0,
                  "location": null,
                  "people": "Sam",
                  "amount": "120",
                  "recurrence_text": null,
                  "notification_jobs": [
                    {
                      "id": 9,
                      "reminder_id": 42,
                      "notify_at_utc": "2026-06-06T23:00:00Z",
                      "notification_title": "Pay rego",
                      "notification_body": null,
                      "channel": "push",
                      "status": "pending"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val reminder = Reminder.listFromJsonObject(json).single()

        assertEquals(42L, reminder.id)
        assertTrue(reminder.needsReview)
        assertFalse(reminder.usedDefaultTime)
        assertNull(reminder.description)
        assertEquals(9L, reminder.notificationJobs.single().id)
        assertNull(reminder.notificationJobs.single().notificationBody)
    }

    @Test
    fun parsesOptionalReminderEndDates() {
        val json = JSONObject(
            """
            {
              "reminders": [
                {
                  "id": 42,
                  "source_segment_id": null,
                  "kind": "event",
                  "title": "Conference",
                  "description": null,
                  "status": "pending",
                  "needs_review": false,
                  "timezone": "Australia/Sydney",
                  "scheduled_at_local": "2026-06-10T09:00:00+10:00",
                  "scheduled_at_utc": "2026-06-09T23:00:00Z",
                  "end_at_local": "2026-06-12T17:00:00+10:00",
                  "end_at_utc": "2026-06-12T07:00:00Z",
                  "schedule_precision": "datetime",
                  "used_default_time": false,
                  "location": null,
                  "people": null,
                  "amount": null,
                  "recurrence_text": null,
                  "notification_jobs": []
                }
              ]
            }
            """.trimIndent()
        )

        val reminder = Reminder.listFromJsonObject(json).single()

        assertEquals("2026-06-12T17:00:00+10:00", reminder.endAtLocal)
        assertEquals("2026-06-12T07:00:00Z", reminder.endAtUtc)
    }

    @Test
    fun infersRecurringFromRecurrenceObjectWhenServerOmitsBoolean() {
        val json = JSONObject(
            """
            {
              "reminders": [
                {
                  "id": 88,
                  "kind": "task",
                  "title": "Water plants",
                  "status": "pending",
                  "needs_review": false,
                  "scheduled_at_local": "2026-06-10T09:00:00+10:00",
                  "scheduled_at_utc": "2026-06-09T23:00:00Z",
                  "schedule_precision": "datetime",
                  "used_default_time": false,
                  "recurrence_text": null,
                  "recurrence_series_id": 12,
                  "recurrence_occurrence_local": "2026-06-10T09:00:00+10:00",
                  "recurrence": {
                    "id": 12,
                    "frequency": "weekly",
                    "interval_count": 1,
                    "day_of_week": 3,
                    "time_local": "09:00"
                  },
                  "notification_jobs": []
                }
              ]
            }
            """.trimIndent()
        )

        val reminder = Reminder.listFromJsonObject(json).single()

        assertTrue(reminder.isRecurring)
        assertEquals(12L, reminder.recurrenceSeriesId)
        assertEquals("weekly", reminder.recurrence?.frequency)
    }

    @Test
    fun infersRecurringFromLegacyRecurrenceTextWhenServerOmitsBoolean() {
        val json = JSONObject(
            """
            {
              "reminders": [
                {
                  "id": 89,
                  "kind": "task",
                  "title": "Bins",
                  "status": "pending",
                  "needs_review": false,
                  "scheduled_at_local": "2026-06-10T09:00:00+10:00",
                  "scheduled_at_utc": "2026-06-09T23:00:00Z",
                  "schedule_precision": "date",
                  "used_default_time": true,
                  "recurrence_text": "Weekly on Wednesday",
                  "notification_jobs": []
                }
              ]
            }
            """.trimIndent()
        )

        val reminder = Reminder.listFromJsonObject(json).single()

        assertTrue(reminder.isRecurring)
    }

    @Test
    fun serializesReminderPatchEndDates() {
        val patch = ReminderPatch(
            endAtLocal = "2026-06-12T17:00:00+10:00",
            endAtUtc = "2026-06-12T07:00:00Z"
        ).toJson()

        assertEquals("2026-06-12T17:00:00+10:00", patch.getString("end_at_local"))
        assertEquals("2026-06-12T07:00:00Z", patch.getString("end_at_utc"))
    }

    @Test
    fun serializesReminderPatchClearedEndDates() {
        val patch = ReminderPatch(clearEndAt = true).toJson()

        assertTrue(patch.isNull("end_at_local"))
        assertTrue(patch.isNull("end_at_utc"))
    }

    @Test
    fun roundTripsReminderThroughCacheEntities() {
        val reminder = Reminder(
            id = 7,
            sourceSegmentId = 3,
            kind = "event",
            title = "Dentist",
            description = "Checkup",
            status = "pending",
            needsReview = false,
            timezone = "Australia/Sydney",
            scheduledAtLocal = "2026-06-08T17:00:00+10:00",
            scheduledAtUtc = "2026-06-08T07:00:00Z",
            endAtLocal = null,
            endAtUtc = null,
            schedulePrecision = "datetime",
            usedDefaultTime = false,
            location = "Clinic",
            people = null,
            amount = null,
            recurrenceText = null,
            notificationJobs = listOf(
                NotificationJob(
                    id = 99,
                    reminderId = 7,
                    notifyAtUtc = "2026-06-08T07:00:00Z",
                    notificationTitle = "Dentist",
                    notificationBody = "Checkup",
                    channel = "push",
                    status = "pending"
                )
            )
        )

        val cached = Reminder.fromCacheEntities(
            reminder.toCacheEntity(),
            reminder.notificationJobs.map { it.toCacheEntity() }
        )

        assertEquals(reminder, cached)
    }
}
