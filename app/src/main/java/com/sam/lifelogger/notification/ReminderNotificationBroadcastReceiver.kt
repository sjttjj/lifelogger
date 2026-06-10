package com.sam.lifelogger.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderNotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra("notification_job_id", 0L)
        val title = intent.getStringExtra("title") ?: "Reminder"
        val body = intent.getStringExtra("body")
        ReminderNotificationHelper.postNotification(context, jobId, title, body)
    }
}
