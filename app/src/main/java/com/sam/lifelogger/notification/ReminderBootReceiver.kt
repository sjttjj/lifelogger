package com.sam.lifelogger.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sam.lifelogger.data.ReminderSyncWorker

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderNotificationHelper.clearScheduledTracking(context)
            ReminderSyncWorker.enqueue(context)
            ReminderSyncWorker.enqueuePeriodic(context)
        }
    }
}
