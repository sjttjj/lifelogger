package com.sam.lifelogger.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

enum class ReminderNotificationOffsetUnit(val label: String) {
    Minutes("minutes"),
    Hours("hours"),
    Days("days")
}

data class ReminderNotificationOffset(
    val amount: Int,
    val unit: ReminderNotificationOffsetUnit
) {
    fun duration(): Duration =
        when (unit) {
            ReminderNotificationOffsetUnit.Minutes -> Duration.ofMinutes(amount.toLong())
            ReminderNotificationOffsetUnit.Hours -> Duration.ofHours(amount.toLong())
            ReminderNotificationOffsetUnit.Days -> Duration.ofDays(amount.toLong())
        }
}

sealed interface ReminderNotificationConfig {
    fun toRequest(
        scheduledAtUtc: String?,
        title: String,
        body: String?
    ): ReminderNotificationReplaceRequest

    data object None : ReminderNotificationConfig {
        override fun toRequest(
            scheduledAtUtc: String?,
            title: String,
            body: String?
        ): ReminderNotificationReplaceRequest =
            ReminderNotificationReplaceRequest(notificationsEnabled = false, jobs = emptyList())
    }

    data object AtTime : ReminderNotificationConfig {
        override fun toRequest(
            scheduledAtUtc: String?,
            title: String,
            body: String?
        ): ReminderNotificationReplaceRequest {
            val notifyAtUtc = scheduledAtUtc ?: return ReminderNotificationReplaceRequest(false, emptyList())
            return ReminderNotificationReplaceRequest(
                notificationsEnabled = true,
                jobs = listOf(ReminderNotificationJobRequest(notifyAtUtc, title, body))
            )
        }
    }

    data class Custom(val offsets: List<ReminderNotificationOffset>) : ReminderNotificationConfig {
        override fun toRequest(
            scheduledAtUtc: String?,
            title: String,
            body: String?
        ): ReminderNotificationReplaceRequest {
            val scheduled = scheduledAtUtc?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return ReminderNotificationReplaceRequest(false, emptyList())
            val jobs = offsets
                .filter { it.amount > 0 }
                .take(3)
                .map { offset ->
                    ReminderNotificationJobRequest(
                        notifyAtUtc = scheduled.minus(offset.duration()).toString(),
                        notificationTitle = title,
                        notificationBody = body
                    )
                }
            return ReminderNotificationReplaceRequest(notificationsEnabled = jobs.isNotEmpty(), jobs = jobs)
        }
    }

    companion object {
        fun fromReminder(scheduledAtUtc: String?, jobs: List<NotificationJob>): ReminderNotificationConfig {
            val scheduled = scheduledAtUtc?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return None
            val pendingPushJobs = jobs
                .filter { it.channel == "push" && it.status == "pending" }
                .sortedBy { it.notifyAtUtc }
            if (pendingPushJobs.isEmpty()) return None
            if (pendingPushJobs.size == 1 && pendingPushJobs.single().notifyAtUtc == scheduledAtUtc) {
                return AtTime
            }

            val offsets = pendingPushJobs.mapNotNull { job ->
                val notify = runCatching { Instant.parse(job.notifyAtUtc) }.getOrNull() ?: return@mapNotNull null
                val duration = Duration.between(notify, scheduled)
                if (duration.isNegative || duration.isZero) return@mapNotNull null
                duration.toOffset()
            }.take(3)

            return if (offsets.isEmpty()) AtTime else Custom(offsets)
        }
    }
}

data class ReminderNotificationJobRequest(
    val notifyAtUtc: String,
    val notificationTitle: String,
    val notificationBody: String?,
    val channel: String = "push"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("notify_at_utc", notifyAtUtc)
        put("notification_title", notificationTitle)
        put("notification_body", notificationBody ?: "")
        put("channel", channel)
    }
}

data class ReminderNotificationReplaceRequest(
    val notificationsEnabled: Boolean,
    val jobs: List<ReminderNotificationJobRequest>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("notifications_enabled", notificationsEnabled)
        put("jobs", JSONArray().apply {
            jobs.forEach { put(it.toJson()) }
        })
    }
}

private fun Duration.toOffset(): ReminderNotificationOffset {
    val minutes = toMinutes()
    return when {
        minutes % (24 * 60) == 0L -> {
            ReminderNotificationOffset((minutes / (24 * 60)).toInt(), ReminderNotificationOffsetUnit.Days)
        }
        minutes % 60 == 0L -> {
            ReminderNotificationOffset((minutes / 60).toInt(), ReminderNotificationOffsetUnit.Hours)
        }
        else -> ReminderNotificationOffset(minutes.toInt(), ReminderNotificationOffsetUnit.Minutes)
    }
}
