package com.sam.lifelogger.notification

import com.sam.lifelogger.data.NotificationJob
import java.time.Duration
import java.time.Instant

object ReminderNotificationPlanner {
    fun pendingFuturePushJobs(jobs: List<NotificationJob>, now: Instant = Instant.now()): List<NotificationJob> {
        return jobs.filter { job ->
            job.isPendingPush() &&
                runCatching { Instant.parse(job.notifyAtUtc).isAfter(now) }.getOrDefault(false)
        }
    }

    fun recentlyDuePushJobs(
        jobs: List<NotificationJob>,
        now: Instant = Instant.now(),
        graceWindow: Duration = Duration.ofDays(1)
    ): List<NotificationJob> {
        val oldestDueInstant = now.minus(graceWindow)
        return jobs.filter { job ->
            job.isPendingPush() &&
                runCatching {
                    val notifyInstant = Instant.parse(job.notifyAtUtc)
                    !notifyInstant.isAfter(now) && notifyInstant.isAfter(oldestDueInstant)
                }.getOrDefault(false)
        }
    }

    private fun NotificationJob.isPendingPush(): Boolean =
        status == "pending" && channel == "push"
}
