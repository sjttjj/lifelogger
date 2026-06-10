package com.sam.lifelogger.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ReminderSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        ReminderSyncManager.syncUpcomingOrUseCache(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "reminder-boot-sync"
        private const val PERIODIC_WORK_NAME = "reminder-periodic-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReminderSyncWorker>()
                .addTag("reminder-sync")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueFollowUps(context: Context, delaySeconds: List<Long>) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workManager = WorkManager.getInstance(context)
            delaySeconds.forEach { delay ->
                val request = OneTimeWorkRequestBuilder<ReminderSyncWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(delay, TimeUnit.SECONDS)
                    .addTag("reminder-sync")
                    .build()
                workManager.enqueueUniqueWork(
                    "reminder-post-upload-sync-${delay}s",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ReminderSyncWorker>(
                ReminderSyncSchedulePolicy.PERIODIC_INTERVAL_MINUTES,
                ReminderSyncSchedulePolicy.PERIODIC_INTERVAL_UNIT
            )
                .setConstraints(constraints)
                .addTag("reminder-sync")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
