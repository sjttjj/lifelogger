package com.sam.lifelogger.data

import android.content.Context
import android.util.Log
import com.sam.lifelogger.notification.ReminderNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

object ReminderSyncManager {
    private const val TAG = "ReminderSync"

    suspend fun syncUpcoming(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        val start = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        val end = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        val reminders = Reminder.listFromJson(ApiClient.getUpcomingReminders(context, start, end))
        val dao = AppDatabase.get(context).reminderDao()
        dao.replaceUpcoming(reminders)
        ReminderNotificationHelper.scheduleLocalNotifications(
            context,
            reminders.flatMap { it.notificationJobs }
        )
        reminders
    }

    suspend fun syncNeedsReview(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        runCatching {
            val reminders = Reminder.listFromJson(ApiClient.getNeedsReviewReminders(context))
            val dao = AppDatabase.get(context).reminderDao()
            dao.upsertReminders(reminders.map { it.toCacheEntity() })
            dao.upsertNotificationJobs(reminders.flatMap { it.notificationJobs }.map { it.toCacheEntity() })
            reminders
        }.getOrElse { error ->
            Log.w(TAG, "Needs-review sync failed; using reminder cache", error)
            AppDatabase.get(context).reminderDao().getCachedNeedsReviewReminders()
        }
    }

    suspend fun getCachedReminder(context: Context, id: Long): Reminder? = withContext(Dispatchers.IO) {
        AppDatabase.get(context).reminderDao().getCachedReminder(id)
    }

    suspend fun getCachedUpcoming(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        AppDatabase.get(context).reminderDao().getCachedReminders()
    }

    suspend fun patchAndResync(
        context: Context,
        id: Long,
        patch: ReminderPatch,
        createNotificationJob: Boolean = false,
        notifyAtUtc: String? = null,
        notificationTitle: String? = null,
        notificationBody: String? = null,
        notificationReplaceRequest: ReminderNotificationReplaceRequest? = null
    ): List<Reminder> = withContext(Dispatchers.IO) {
        ApiClient.patchReminder(context, id, patch)
        notificationReplaceRequest?.let { request ->
            ApiClient.replaceReminderNotifications(
                context = context,
                reminderId = id,
                request = request
            )
        }
        if (createNotificationJob && notifyAtUtc != null && notificationTitle != null) {
            ApiClient.createNotificationJob(
                context = context,
                reminderId = id,
                notifyAtUtc = notifyAtUtc,
                notificationTitle = notificationTitle,
                notificationBody = notificationBody
            )
        }
        syncUpcoming(context)
    }

    suspend fun syncUpcomingOrUseCache(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        runCatching { syncUpcoming(context) }.getOrElse { error ->
            Log.w(TAG, "Upcoming sync failed; rescheduling from cache", error)
            val reminders = AppDatabase.get(context).reminderDao().getCachedReminders()
            ReminderNotificationHelper.scheduleLocalNotifications(
                context,
                reminders.flatMap { it.notificationJobs }
            )
            reminders
        }
    }
}
