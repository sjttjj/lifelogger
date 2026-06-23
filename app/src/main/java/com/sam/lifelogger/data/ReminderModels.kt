package com.sam.lifelogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name)

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)

/**
 * Parse booleans that may arrive as SQLite integers (1/0) from the server.
 */
private fun JSONObject.optBoolCompat(name: String, default: Boolean = false): Boolean {
    if (!has(name) || isNull(name)) return default
    return when (val value = get(name)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        else -> default
    }
}

/**
 * A recurrence series descriptor attached to a generated reminder occurrence.
 * Parsed tolerantly from the server "recurrence" object.
 */
data class Recurrence(
    val id: Long,
    val title: String?,
    val kind: String?,
    val status: String?,
    val timezone: String?,
    val frequency: String?,
    val intervalCount: Int?,
    val dayOfWeek: Int?,
    val dayOfMonth: Int?,
    val timeLocal: String?,
    val ordinal: Int? = null,
    val weekdayOrdinal: Int? = null
) {
    companion object {
        fun fromJson(obj: JSONObject?): Recurrence? {
            if (obj == null || obj.isNullSafe()) return null
            return Recurrence(
                id = obj.optLong("id", 0L),
                title = obj.optNullableString("title"),
                kind = obj.optNullableString("kind"),
                status = obj.optNullableString("status"),
                timezone = obj.optNullableString("timezone"),
                frequency = obj.optNullableString("frequency"),
                intervalCount = obj.optNullableInt("interval_count"),
                dayOfWeek = obj.optNullableInt("day_of_week"),
                dayOfMonth = obj.optNullableInt("day_of_month"),
                timeLocal = obj.optNullableString("time_local"),
                ordinal = obj.optNullableInt("ordinal"),
                weekdayOrdinal = obj.optNullableInt("weekday_ordinal")
            )
        }

        fun fromJsonString(raw: String?): Recurrence? {
            if (raw.isNullOrBlank()) return null
            return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
        }
    }

    fun toJsonString(): String? = runCatching {
        JSONObject().apply {
            put("id", id)
            title?.let { put("title", it) }
            kind?.let { put("kind", it) }
            status?.let { put("status", it) }
            timezone?.let { put("timezone", it) }
            frequency?.let { put("frequency", it) }
            intervalCount?.let { put("interval_count", it) }
            dayOfWeek?.let { put("day_of_week", it) }
            dayOfMonth?.let { put("day_of_month", it) }
            timeLocal?.let { put("time_local", it) }
            ordinal?.let { put("ordinal", it) }
            weekdayOrdinal?.let { put("weekday_ordinal", it) }
        }.toString()
    }.getOrNull()
}

private fun JSONObject.isNullSafe(): Boolean = length() == 0

/** Serialise the action list to a JSON array string, or null when empty (to save space). */
private fun List<String>.toJsonString(): String? {
    if (isEmpty()) return null
    return JSONArray().apply { forEach { put(it) } }.toString()
}

/** Parse a cached action list JSON array string back to a List<String>. */
private fun parseActionsString(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i -> arr.optString(i, "").takeIf { it.isNotBlank() } }
    }.getOrDefault(emptyList())
}

data class Reminder(
    val id: Long,
    val sourceSegmentId: Long?,
    val kind: String,
    val title: String,
    val description: String?,
    val status: String,
    val needsReview: Boolean,
    val timezone: String?,
    val scheduledAtLocal: String?,
    val scheduledAtUtc: String?,
    val endAtLocal: String?,
    val endAtUtc: String?,
    val schedulePrecision: String,
    val usedDefaultTime: Boolean,
    val location: String?,
    val people: String?,
    val amount: String?,
    val recurrenceText: String?,
    val isRecurring: Boolean = false,
    val recurrenceSeriesId: Long? = null,
    val recurrenceOccurrenceLocal: String? = null,
    val recurrence: Recurrence? = null,
    val actions: List<String> = emptyList(),
    val notificationJobs: List<NotificationJob> = emptyList()
) {
    companion object {
        fun fromJson(obj: JSONObject): Reminder {
            val jobs = obj.optJSONArray("notification_jobs")?.let { arr ->
                (0 until arr.length()).map { index ->
                    NotificationJob.fromJson(arr.getJSONObject(index))
                }
            } ?: emptyList()

            val actions = obj.optJSONArray("actions")?.let { arr ->
                (0 until arr.length()).mapNotNull { index ->
                    arr.optString(index, "").takeIf { it.isNotBlank() }
                }
            } ?: emptyList()

            val recurrence = Recurrence.fromJson(obj.optJSONObject("recurrence"))
            val recurrenceText = obj.optNullableString("recurrence_text")
            val recurrenceSeriesId = obj.optNullableLong("recurrence_series_id")
            val isRecurring = obj.optBoolCompat("is_recurring") ||
                recurrence != null ||
                recurrenceSeriesId != null ||
                !recurrenceText.isNullOrBlank()

            return Reminder(
                id = obj.getLong("id"),
                sourceSegmentId = obj.optNullableLong("source_segment_id"),
                kind = obj.optString("kind", "other"),
                title = obj.optString("title", "Untitled reminder"),
                description = obj.optNullableString("description"),
                status = obj.optString("status", "pending"),
                needsReview = obj.optBoolCompat("needs_review"),
                timezone = obj.optNullableString("timezone"),
                scheduledAtLocal = obj.optNullableString("scheduled_at_local"),
                scheduledAtUtc = obj.optNullableString("scheduled_at_utc"),
                endAtLocal = obj.optNullableString("end_at_local"),
                endAtUtc = obj.optNullableString("end_at_utc"),
                schedulePrecision = obj.optString("schedule_precision", "date"),
                usedDefaultTime = obj.optBoolCompat("used_default_time"),
                location = obj.optNullableString("location"),
                people = obj.optNullableString("people"),
                amount = obj.optNullableString("amount"),
                recurrenceText = recurrenceText,
                isRecurring = isRecurring,
                recurrenceSeriesId = recurrenceSeriesId,
                recurrenceOccurrenceLocal = obj.optNullableString("recurrence_occurrence_local"),
                recurrence = recurrence,
                actions = actions,
                notificationJobs = jobs
            )
        }

        fun listFromJsonObject(root: JSONObject): List<Reminder> {
            if (!root.has("reminders")) {
                throw IllegalArgumentException("Reminder response missing reminders array")
            }
            val arr = root.optJSONArray("reminders") ?: JSONArray()
            return (0 until arr.length()).map { index ->
                fromJson(arr.getJSONObject(index))
            }
        }

        fun listFromJson(json: String): List<Reminder> {
            val trimmed = json.trim()
            if (trimmed.isBlank()) return emptyList()
            return if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } else {
                listFromJsonObject(JSONObject(trimmed))
            }
        }

        fun fromCacheEntities(
            reminder: ReminderEntity,
            jobs: List<NotificationJobEntity>
        ): Reminder {
            return Reminder(
                id = reminder.id,
                sourceSegmentId = reminder.sourceSegmentId,
                kind = reminder.kind,
                title = reminder.title,
                description = reminder.description,
                status = reminder.status,
                needsReview = reminder.needsReview,
                timezone = reminder.timezone,
                scheduledAtLocal = reminder.scheduledAtLocal,
                scheduledAtUtc = reminder.scheduledAtUtc,
                endAtLocal = reminder.endAtLocal,
                endAtUtc = reminder.endAtUtc,
                schedulePrecision = reminder.schedulePrecision,
                usedDefaultTime = reminder.usedDefaultTime,
                location = reminder.location,
                people = reminder.people,
                amount = reminder.amount,
                recurrenceText = reminder.recurrenceText,
                isRecurring = reminder.isRecurring,
                recurrenceSeriesId = reminder.recurrenceSeriesId,
                recurrenceOccurrenceLocal = reminder.recurrenceOccurrenceLocal,
                recurrence = Recurrence.fromJsonString(reminder.recurrenceJson),
                actions = parseActionsString(reminder.actionsJson),
                notificationJobs = jobs.map { NotificationJob.fromCacheEntity(it) }
            )
        }
    }

    fun toCacheEntity(): ReminderEntity = ReminderEntity(
        id = id,
        sourceSegmentId = sourceSegmentId,
        kind = kind,
        title = title,
        description = description,
        status = status,
        needsReview = needsReview,
        timezone = timezone,
        scheduledAtLocal = scheduledAtLocal,
        scheduledAtUtc = scheduledAtUtc,
        endAtLocal = endAtLocal,
        endAtUtc = endAtUtc,
        schedulePrecision = schedulePrecision,
        usedDefaultTime = usedDefaultTime,
        location = location,
        people = people,
        amount = amount,
        recurrenceText = recurrenceText,
        isRecurring = isRecurring,
        recurrenceSeriesId = recurrenceSeriesId,
        recurrenceOccurrenceLocal = recurrenceOccurrenceLocal,
        recurrenceJson = recurrence?.toJsonString(),
        actionsJson = actions.toJsonString()
    )
}

data class NotificationJob(
    val id: Long,
    val reminderId: Long,
    val notifyAtUtc: String,
    val notificationTitle: String,
    val notificationBody: String?,
    val channel: String,
    val status: String
) {
    companion object {
        fun fromJson(obj: JSONObject): NotificationJob = NotificationJob(
            id = obj.getLong("id"),
            reminderId = obj.optLong("reminder_id", 0L),
            notifyAtUtc = obj.optString("notify_at_utc"),
            notificationTitle = obj.optString("notification_title", "Reminder"),
            notificationBody = obj.optNullableString("notification_body"),
            channel = obj.optString("channel", "push"),
            status = obj.optString("status", "pending")
        )

        fun fromCacheEntity(entity: NotificationJobEntity): NotificationJob = NotificationJob(
            id = entity.id,
            reminderId = entity.reminderId,
            notifyAtUtc = entity.notifyAtUtc,
            notificationTitle = entity.notificationTitle,
            notificationBody = entity.notificationBody,
            channel = entity.channel,
            status = entity.status
        )
    }

    fun toCacheEntity(): NotificationJobEntity = NotificationJobEntity(
        id = id,
        reminderId = reminderId,
        notifyAtUtc = notifyAtUtc,
        notificationTitle = notificationTitle,
        notificationBody = notificationBody,
        channel = channel,
        status = status
    )
}

data class ReminderPatch(
    val title: String? = null,
    val description: String? = null,
    val kind: String? = null,
    val status: String? = null,
    val needsReview: Boolean? = null,
    val timezone: String? = null,
    val scheduledAtLocal: String? = null,
    val scheduledAtUtc: String? = null,
    val endAtLocal: String? = null,
    val endAtUtc: String? = null,
    val clearEndAt: Boolean = false,
    val schedulePrecision: String? = null,
    val usedDefaultTime: Boolean? = null,
    val location: String? = null,
    val people: String? = null,
    val amount: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        title?.let { put("title", it) }
        description?.let { put("description", it) }
        kind?.let { put("kind", it) }
        status?.let { put("status", it) }
        needsReview?.let { put("needs_review", it) }
        timezone?.let { put("timezone", it) }
        scheduledAtLocal?.let { put("scheduled_at_local", it) }
        scheduledAtUtc?.let { put("scheduled_at_utc", it) }
        if (clearEndAt) {
            put("end_at_local", JSONObject.NULL)
            put("end_at_utc", JSONObject.NULL)
        } else {
            endAtLocal?.let { put("end_at_local", it) }
            endAtUtc?.let { put("end_at_utc", it) }
        }
        schedulePrecision?.let { put("schedule_precision", it) }
        usedDefaultTime?.let { put("used_default_time", it) }
        location?.let { put("location", it) }
        people?.let { put("people", it) }
        amount?.let { put("amount", it) }
    }
}

@Entity(tableName = "cached_reminders")
data class ReminderEntity(
    @PrimaryKey val id: Long,
    val sourceSegmentId: Long?,
    val kind: String,
    val title: String,
    val description: String?,
    val status: String,
    val needsReview: Boolean,
    val timezone: String?,
    val scheduledAtLocal: String?,
    val scheduledAtUtc: String?,
    val endAtLocal: String?,
    val endAtUtc: String?,
    val schedulePrecision: String,
    val usedDefaultTime: Boolean,
    val location: String?,
    val people: String?,
    val amount: String?,
    val recurrenceText: String?,
    val isRecurring: Boolean = false,
    val recurrenceSeriesId: Long? = null,
    val recurrenceOccurrenceLocal: String? = null,
    val recurrenceJson: String? = null,
    val actionsJson: String? = null
)

@Entity(tableName = "cached_notification_jobs")
data class NotificationJobEntity(
    @PrimaryKey val id: Long,
    val reminderId: Long,
    val notifyAtUtc: String,
    val notificationTitle: String,
    val notificationBody: String?,
    val channel: String,
    val status: String
)
