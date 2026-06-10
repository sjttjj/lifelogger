package com.sam.lifelogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ReminderDao {

    @Query("SELECT * FROM cached_reminders ORDER BY scheduledAtUtc IS NULL, scheduledAtUtc ASC, id ASC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Query("SELECT * FROM cached_reminders WHERE id = :id LIMIT 1")
    suspend fun getReminder(id: Long): ReminderEntity?

    @Query("SELECT * FROM cached_reminders WHERE needsReview = 1 ORDER BY id ASC")
    suspend fun getNeedsReviewReminders(): List<ReminderEntity>

    @Query("SELECT * FROM cached_notification_jobs WHERE reminderId = :reminderId ORDER BY notifyAtUtc ASC")
    suspend fun getNotificationJobsForReminder(reminderId: Long): List<NotificationJobEntity>

    @Query("SELECT * FROM cached_notification_jobs WHERE status = 'pending' AND channel = 'push' ORDER BY notifyAtUtc ASC")
    suspend fun getPendingPushNotificationJobs(): List<NotificationJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminders(reminders: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotificationJobs(jobs: List<NotificationJobEntity>)

    @Query("DELETE FROM cached_reminders")
    suspend fun deleteAllReminders()

    @Query("DELETE FROM cached_notification_jobs")
    suspend fun deleteAllNotificationJobs()

    @Transaction
    suspend fun replaceUpcoming(reminders: List<Reminder>) {
        deleteAllNotificationJobs()
        deleteAllReminders()
        upsertReminders(reminders.map { it.toCacheEntity() })
        upsertNotificationJobs(reminders.flatMap { it.notificationJobs }.map { it.toCacheEntity() })
    }

    @Transaction
    suspend fun getCachedReminders(): List<Reminder> {
        return getAllReminders().map { reminder ->
            Reminder.fromCacheEntities(reminder, getNotificationJobsForReminder(reminder.id))
        }
    }

    @Transaction
    suspend fun getCachedReminder(id: Long): Reminder? {
        val reminder = getReminder(id) ?: return null
        return Reminder.fromCacheEntities(reminder, getNotificationJobsForReminder(id))
    }

    @Transaction
    suspend fun getCachedNeedsReviewReminders(): List<Reminder> {
        return getNeedsReviewReminders().map { reminder ->
            Reminder.fromCacheEntities(reminder, getNotificationJobsForReminder(reminder.id))
        }
    }
}
