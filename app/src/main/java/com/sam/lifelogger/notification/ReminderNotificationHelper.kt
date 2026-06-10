package com.sam.lifelogger.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sam.lifelogger.MainActivity
import com.sam.lifelogger.R
import com.sam.lifelogger.data.NotificationJob
import java.time.Instant

object ReminderNotificationHelper {
    private const val TAG = "ReminderNotif"
    private const val PREFS_NAME = "reminder_notif_prefs"
    private const val KEY_SCHEDULED_IDS = "scheduled_notification_job_ids"
    const val CHANNEL_ID = "reminders"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    fun scheduleLocalNotifications(context: Context, jobs: List<NotificationJob>) {
        createNotificationChannel(context)
        val now = Instant.now()
        val pendingJobs = ReminderNotificationPlanner.pendingFuturePushJobs(jobs, now)
        val dueJobs = ReminderNotificationPlanner.recentlyDuePushJobs(jobs, now)
        val newIds = (pendingJobs.map { it.id } + dueJobs.map { it.id }).toSet()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldIds = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()

        for (jobId in oldIds - newIds) {
            cancelAlarm(context, jobId)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (job in pendingJobs) {
            if (job.id !in oldIds) {
                scheduleAlarm(context, alarmManager, job, Instant.parse(job.notifyAtUtc))
            }
        }
        for (job in dueJobs) {
            if (job.id !in oldIds) {
                Log.d(TAG, "Posting recently due reminder notification ${job.id}")
                postNotification(context, job.id, job.notificationTitle, job.notificationBody)
            }
        }

        prefs.edit().putStringSet(KEY_SCHEDULED_IDS, newIds.map { it.toString() }.toSet()).apply()
    }

    fun clearScheduledTracking(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SCHEDULED_IDS)
            .apply()
    }

    fun postNotification(context: Context, jobId: Long, title: String, body: String?) {
        createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; dropping reminder notification $jobId")
            return
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_route", "reminders")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body.orEmpty())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingContentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(jobId.toInt(), notification)
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        job: NotificationJob,
        notifyInstant: Instant
    ) {
        val intent = Intent(context, ReminderNotificationBroadcastReceiver::class.java).apply {
            putExtra("notification_job_id", job.id)
            putExtra("reminder_id", job.reminderId)
            putExtra("title", job.notificationTitle)
            putExtra("body", job.notificationBody ?: "")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            job.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerMs = notifyInstant.toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, jobId: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            jobId.toInt(),
            Intent(context, ReminderNotificationBroadcastReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
