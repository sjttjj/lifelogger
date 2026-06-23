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
        val start = Instant.parse("2025-01-01T00:00:00Z").toString()
        val end = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        val reminders = Reminder.listFromJson(ApiClient.getUpcomingReminders(context, start, end))
        val dao = AppDatabase.get(context).reminderDao()
        dao.upsertUpcoming(reminders)
        // Only schedule notifications for non-review reminders (or review items that the
        // server has explicitly attached pending push jobs to). The planner already filters
        // to pending push jobs, so needs_review items with no jobs schedule nothing.
        val notifiableJobs = reminders
            .filter { !it.needsReview || it.notificationJobs.any { job -> job.status == "pending" && job.channel == "push" } }
            .flatMap { it.notificationJobs }
        ReminderNotificationHelper.scheduleLocalNotifications(context, notifiableJobs)
        reminders
    }

    suspend fun syncAllReminders(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        runCatching {
            val reminders = Reminder.listFromJson(ApiClient.getAllReminders(context))
            val dao = AppDatabase.get(context).reminderDao()
            val selected = ReminderSyncPolicy.chooseAllReminders(
                server = reminders,
                cache = dao.getCachedReminders()
            )
            if (selected.isNotEmpty() && selected === reminders) {
                dao.replaceAllSynced(selected)
            }
            selected
        }.getOrElse { error ->
            Log.w(TAG, "All-reminders sync failed; using reminder cache", error)
            getCachedUpcoming(context)
        }
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

    /**
     * Mark a reminder done. Prefers the action endpoint; falls back to PATCH status=done
     * if the action endpoint is unavailable (older server builds).
     */
    suspend fun markDone(context: Context, id: Long) = withContext(Dispatchers.IO) {
        runCatching { ApiClient.markReminderDone(context, id) }
            .recoverCatching { error ->
                if (isActionEndpointMissing(error)) {
                    ApiClient.patchReminder(context, id, ReminderPatch(status = "done"))
                } else {
                    throw error
                }
            }
            .getOrThrow()
        updateCachedStatus(context, id, status = "done")
        syncUpcoming(context)
    }

    /**
     * Cancel a reminder occurrence / reject a review item. Prefers the action endpoint;
     * falls back to PATCH status=cancelled, needs_review=false on older servers.
     */
    suspend fun cancelReminder(context: Context, id: Long) = withContext(Dispatchers.IO) {
        runCatching { ApiClient.cancelReminder(context, id) }
            .recoverCatching { error ->
                if (isActionEndpointMissing(error)) {
                    ApiClient.patchReminder(
                        context, id,
                        ReminderPatch(status = "cancelled", needsReview = false)
                    )
                } else {
                    throw error
                }
            }
            .getOrThrow()
        updateCachedStatus(context, id, status = "cancelled", needsReview = false)
        syncUpcoming(context)
    }

    /** Archive a single reminder occurrence so it no longer shows in the app. */
    suspend fun archiveReminder(context: Context, id: Long) = withContext(Dispatchers.IO) {
        ApiClient.patchReminder(context, id, ReminderPatch(status = "archived"))
        updateCachedStatus(context, id, status = "archived")
        syncUpcoming(context)
    }

    /** Restore a done/cancelled/past reminder back to pending so it returns to month/week. */
    suspend fun restoreReminder(context: Context, id: Long) = withContext(Dispatchers.IO) {
        ApiClient.patchReminder(
            context, id,
            ReminderPatch(status = "pending", needsReview = false)
        )
        updateCachedStatus(context, id, status = "pending", needsReview = false)
        syncUpcoming(context)
    }

    /** Stop future occurrences of a recurring series while preserving history. */
    suspend fun stopRecurrenceSeries(context: Context, seriesId: Long) = withContext(Dispatchers.IO) {
        ApiClient.stopRecurrenceSeries(context, seriesId)
        syncUpcoming(context)
    }

    /** Archive an entire recurring series so it stops surfacing occurrences. */
    suspend fun archiveRecurrenceSeries(context: Context, seriesId: Long) = withContext(Dispatchers.IO) {
        ApiClient.archiveRecurrenceSeries(context, seriesId)
        syncUpcoming(context)
    }

    private fun isActionEndpointMissing(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("HTTP 404") || message.contains("HTTP 405")
    }

    private suspend fun updateCachedStatus(
        context: Context,
        id: Long,
        status: String,
        needsReview: Boolean? = null
    ) {
        val dao = AppDatabase.get(context).reminderDao()
        val cached = dao.getCachedReminder(id) ?: return
        dao.upsertReminders(
            listOf(ReminderSyncPolicy.withLocalStatus(cached, status, needsReview).toCacheEntity())
        )
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
