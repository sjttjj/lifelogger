# Reminder System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reminder recording upload, syncing, editing, needs-review inbox, and local fallback notifications to the Lifelogger Android app.

**Architecture:** The existing recording pipeline already captures reminder audio (2min chunks with `type=reminder`) and uploads it to `/transcribe`. This plan adds the retrieval and management side: syncing structured reminders from `GET /api/reminders/upcoming`, displaying them in a two-tab Compose UI (Upcoming + Needs Review), editing via `PATCH /api/reminders/{id}`, and scheduling local fallback `AlarmManager` notifications from synced `notification_jobs`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, `HttpURLConnection` (existing), `AlarmManager` (local notifications), `JSONObject` parsing (existing pattern).

---

### Task 1: Data Models

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/data/ReminderModels.kt`

- [ ] **Step 1: Create ReminderModels.kt with Reminder, NotificationJob, and ReminderPatch data classes**

```kotlin
package com.sam.lifelogger.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parse booleans that may arrive as SQLite integers (1/0) from the server.
 * Handles: JSON boolean, Number (0/1), or String ("true"/"false"/"1"/"0").
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
    val schedulePrecision: String,
    val usedDefaultTime: Boolean,
    val location: String?,
    val people: String?,
    val amount: String?,
    val recurrenceText: String?,
    val notificationJobs: List<NotificationJob> = emptyList()
) {
    companion object {
        fun fromJson(obj: JSONObject): Reminder {
            val jobsArray = obj.optJSONArray("notification_jobs")
            val jobs = if (jobsArray != null) {
                (0 until jobsArray.length()).map { i ->
                    NotificationJob.fromJson(jobsArray.getJSONObject(i))
                }
            } else emptyList()

            return Reminder(
                id = obj.getLong("id"),
                sourceSegmentId = if (obj.isNull("source_segment_id")) null else obj.optLong("source_segment_id"),
                kind = obj.optString("kind", "other"),
                title = obj.getString("title"),
                description = if (obj.isNull("description")) null else obj.optString("description", null),
                status = obj.optString("status", "pending"),
                needsReview = obj.optBoolCompat("needs_review"),
                timezone = if (obj.isNull("timezone")) null else obj.optString("timezone", null),
                scheduledAtLocal = if (obj.isNull("scheduled_at_local")) null else obj.optString("scheduled_at_local", null),
                scheduledAtUtc = if (obj.isNull("scheduled_at_utc")) null else obj.optString("scheduled_at_utc", null),
                schedulePrecision = obj.optString("schedule_precision", "date"),
                usedDefaultTime = obj.optBoolCompat("used_default_time"),
                location = if (obj.isNull("location")) null else obj.optString("location", null),
                people = if (obj.isNull("people")) null else obj.optString("people", null),
                amount = if (obj.isNull("amount")) null else obj.optString("amount", null),
                recurrenceText = if (obj.isNull("recurrence_text")) null else obj.optString("recurrence_text", null),
                notificationJobs = jobs
            )
        }

        fun listFromJsonObject(root: JSONObject): List<Reminder> {
            val arr = root.getJSONArray("reminders")
            return (0 until arr.length()).map { i ->
                fromJson(arr.getJSONObject(i))
            }
        }
    }
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
        fun fromJson(obj: JSONObject): NotificationJob {
            return NotificationJob(
                id = obj.getLong("id"),
                reminderId = obj.optLong("reminder_id", 0L),
                notifyAtUtc = obj.getString("notify_at_utc"),
                notificationTitle = obj.getString("notification_title"),
                notificationBody = if (obj.isNull("notification_body")) null else obj.optString("notification_body", null),
                channel = obj.optString("channel", "push"),
                status = obj.optString("status", "pending")
            )
        }
    }
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
    val schedulePrecision: String? = null,
    val usedDefaultTime: Boolean? = null,
    val location: String? = null,
    val people: String? = null,
    val amount: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            title?.let { put("title", it) }
            description?.let { put("description", it) }
            kind?.let { put("kind", it) }
            status?.let { put("status", it) }
            needsReview?.let { put("needs_review", it) }
            timezone?.let { put("timezone", it) }
            scheduledAtLocal?.let { put("scheduled_at_local", it) }
            scheduledAtUtc?.let { put("scheduled_at_utc", it) }
            schedulePrecision?.let { put("schedule_precision", it) }
            usedDefaultTime?.let { put("used_default_time", it) }
            location?.let { put("location", it) }
            people?.let { put("people", it) }
            amount?.let { put("amount", it) }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/sam/lifelogger/data/ReminderModels.kt
git commit -m "feat: add reminder data models (Reminder, NotificationJob, ReminderPatch)"
```

---

### Task 2: ApiClient Reminder Endpoints

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/data/ApiClient.kt`

- [ ] **Step 1: Add getUpcomingReminders method**

Add after the existing `search()` method (around line 71):

```kotlin
/** GET /api/reminders/upcoming?start=<iso>&end=<iso>&status=pending[&kind=...] */
suspend fun getUpcomingReminders(
    context: Context,
    start: String,
    end: String,
    kind: String? = null
): String {
    val base = getBaseUrl(context)
    var url = "$base/api/reminders/upcoming?start=${java.net.URLEncoder.encode(start, "UTF-8")}&end=${java.net.URLEncoder.encode(end, "UTF-8")}&status=pending"
    if (kind != null) {
        url += "&kind=${java.net.URLEncoder.encode(kind, "UTF-8")}"
    }
    return httpGet(url)
}
```

- [ ] **Step 2: Add getNeedsReviewReminders method (placeholder)**

> **Note:** This endpoint (`GET /api/reminders?status=pending&needs_review=true`) does not exist on the server yet. Add it now on the server side, OR skip this step and build the Needs Review tab UI knowing it will return empty until the endpoint is added. The method signature is correct for the planned contract.

```kotlin
/** GET /api/reminders?status=pending&needs_review=true */
suspend fun getNeedsReviewReminders(context: Context): String {
    val base = getBaseUrl(context)
    return httpGet("$base/api/reminders?status=pending&needs_review=true")
}
```

- [ ] **Step 3: Add patchReminder method**

```kotlin
/** PATCH /api/reminders/{id} */
suspend fun patchReminder(context: Context, id: Long, patch: ReminderPatch): String = withContext(Dispatchers.IO) {
    val base = getBaseUrl(context)
    val url = URL("$base/api/reminders/$id")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    try {
        val body = patch.toJson().toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
            throw Exception("Patch reminder failed HTTP $code: $errorBody")
        }
    } finally {
        conn.disconnect()
    }
}
```

- [ ] **Step 4: Add createNotificationJob method**

```kotlin
/** POST /api/reminders/{id}/notifications */
suspend fun createNotificationJob(
    context: Context,
    reminderId: Long,
    notifyAtUtc: String,
    notificationTitle: String,
    notificationBody: String?
): String = withContext(Dispatchers.IO) {
    val base = getBaseUrl(context)
    val url = URL("$base/api/reminders/$reminderId/notifications")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    try {
        val jsonBody = JSONObject().apply {
            put("notify_at_utc", notifyAtUtc)
            put("notification_title", notificationTitle)
            put("notification_body", notificationBody ?: "")
            put("channel", "push")
        }
        conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
        val code = conn.responseCode
        if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
            throw Exception("Create notification job failed HTTP $code: $errorBody")
        }
    } finally {
        conn.disconnect()
    }
}
```

Add these imports at the top if not already present:
```kotlin
import org.json.JSONObject  // already present
import java.net.URL  // already present
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sam/lifelogger/data/ApiClient.kt
git commit -m "feat: add reminder API endpoints (upcoming, needs-review, patch, notification jobs)"
```

---

### Task 3: Local Notification Infrastructure

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/notification/ReminderNotificationHelper.kt`
- Create: `app/src/main/java/com/sam/lifelogger/notification/ReminderNotificationBroadcastReceiver.kt`
- Create: `app/src/main/java/com/sam/lifelogger/notification/ReminderBootReceiver.kt`

- [ ] **Step 1: Create the notification directory (empty placeholder)**

```powershell
New-Item -ItemType Directory -Path "app/src/main/java/com/sam/lifelogger/notification" -Force
```

- [ ] **Step 2: Create ReminderNotificationHelper.kt**

```kotlin
package com.sam.lifelogger.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sam.lifelogger.data.NotificationJob
import java.time.Instant
import java.time.temporal.ChronoUnit

// NOTE: NotificationJob is defined in ReminderModels.kt (Task 1)

/**
 * Manages local fallback notifications for reminders.
 *
 * The server is canonical. Local notifications are a fallback path so reminders
 * fire even if the server's FCM push isn't ready yet.
 *
 * On every sync, the app compares the synced set of pending notification_jobs
 * against the locally recorded set of scheduled job IDs, cancels stale ones,
 * and schedules new ones.
 */
object ReminderNotificationHelper {

    private const val TAG = "ReminderNotif"
    private const val PREFS_NAME = "reminder_notif_prefs"
    private const val KEY_SCHEDULED_IDS = "scheduled_notification_job_ids"
    const val CHANNEL_ID = "reminders"
    const val CHANNEL_NAME = "Reminders"

    /** Create the notification channel (call once, e.g. from Application.onCreate or first sync). */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminder notifications from Lifelogger"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedule local fallback alarms for the given notification jobs.
     * Compares against previously scheduled IDs to avoid duplicates and cancel stale ones.
     */
    fun scheduleLocalNotifications(context: Context, jobs: List<NotificationJob>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyScheduled = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()

        val newJobIds = jobs.map { it.id }.toSet()

        // Cancel alarms for jobs that are no longer in the pending set
        val toCancel = previouslyScheduled - newJobIds
        for (jobId in toCancel) {
            cancelAlarm(context, jobId)
        }

        // Schedule alarms for new pending jobs
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Instant.now()

        for (job in jobs) {
            if (job.id in previouslyScheduled) continue  // already scheduled

            val notifyInstant = try {
                Instant.parse(job.notifyAtUtc)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse notifyAtUtc for job ${job.id}: ${job.notifyAtUtc}")
                continue
            }

            if (notifyInstant.isBefore(now)) continue  // already due, skip

            scheduleAlarm(context, alarmManager, job, notifyInstant)
        }

        // Persist the new set of scheduled IDs
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_IDS, newJobIds.map { it.toString() }.toSet())
            .apply()

        Log.d(TAG, "Scheduled ${(newJobIds - previouslyScheduled).size} new alarms, cancelled ${toCancel.size} stale")
    }

    /** Cancel a single local notification alarm by job ID. */
    fun cancelLocalNotification(context: Context, jobId: Long) {
        cancelAlarm(context, jobId)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scheduled = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        scheduled.remove(jobId.toString())
        prefs.edit().putStringSet(KEY_SCHEDULED_IDS, scheduled).apply()
    }

    /** Clear all scheduled alarms and reset tracking (e.g. on logout). */
    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scheduled = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet()) ?: emptySet()
        for (idStr in scheduled) {
            idStr.toLongOrNull()?.let { cancelAlarm(context, it) }
        }
        prefs.edit().remove(KEY_SCHEDULED_IDS).apply()
    }

    /** Called after BOOT_COMPLETED — reschedule all still-pending alarms from scratch. */
    fun onBootCompleted(context: Context) {
        Log.d(TAG, "Boot completed — clearing stale alarm tracking. Next sync will reschedule.")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SCHEDULED_IDS).apply()
        // The next syncUpcoming() call will re-schedule everything
    }

    // ---- Internal helpers ----

    private fun scheduleAlarm(context: Context, alarmManager: AlarmManager, job: NotificationJob, notifyInstant: Instant) {
        val intent = Intent(context, ReminderNotificationBroadcastReceiver::class.java).apply {
            putExtra("notification_job_id", job.id)
            putExtra("reminder_id", job.reminderId)
            putExtra("title", job.notificationTitle)
            putExtra("body", job.notificationBody ?: "")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            job.id.toInt(),  // stable request code from job ID
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerMs = notifyInstant.toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
                Log.w(TAG, "Exact alarm permission denied for job ${job.id} — using inexact fallback")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }

        Log.d(TAG, "Scheduled alarm for job ${job.id} at ${job.notifyAtUtc}")
    }

    private fun cancelAlarm(context: Context, jobId: Long) {
        val intent = Intent(context, ReminderNotificationBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            jobId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for job $jobId")
        }
    }
}
```

- [ ] **Step 3: Create ReminderNotificationBroadcastReceiver.kt**

```kotlin
package com.sam.lifelogger.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sam.lifelogger.MainActivity

class ReminderNotificationBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderNotifRCV"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra("notification_job_id", -1L)
        val reminderId = intent.getLongExtra("reminder_id", -1L)
        val title = intent.getStringExtra("title") ?: "Reminder"
        val body = intent.getStringExtra("body") ?: ""

        Log.d(TAG, "Alarm fired for job $jobId, reminder $reminderId: $title")

        // Deep link to the reminders screen
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reminders")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, ReminderNotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + jobId.toInt(),
                notification
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted — dropping notification for job $jobId")
        }
    }
}
```

- [ ] **Step 4: Create ReminderBootReceiver.kt**

```kotlin
package com.sam.lifelogger.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed — clearing stale notification tracking")
            ReminderNotificationHelper.onBootCompleted(context)
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sam/lifelogger/notification/
git commit -m "feat: add local reminder notification infra (helper, broadcast receiver, boot receiver)"
```

---

### Task 4: ReminderSyncManager

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/data/ReminderSyncManager.kt`

- [ ] **Step 1: Create ReminderSyncManager with syncUpcoming, syncNeedsReview, patchAndResync**

```kotlin
package com.sam.lifelogger.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Depends on: ReminderModels (Task 1), ApiClient (Task 2), ReminderNotificationHelper (Task 3)

/**
 * Manages syncing reminders and notification jobs between the server and the app.
 *
 * The server is canonical. The app syncs upcoming reminders and needs-review
 * reminders on demand, schedules local fallback notifications, and patches
 * reminders back to the server when the user edits or reviews them.
 */
object ReminderSyncManager {

    private const val TAG = "ReminderSync"

    /**
     * Sync upcoming pending reminders for the next 14 days.
     * Also schedules local fallback notifications for synced notification jobs.
     */
    suspend fun syncUpcoming(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        try {
            val now = Instant.now()
            val end = now.plus(14, ChronoUnit.DAYS)
            val startIso = now.atZone(ZoneId.systemDefault()).toString()
            val endIso = end.atZone(ZoneId.systemDefault()).toString()

            val json = ApiClient.getUpcomingReminders(context, startIso, endIso)
            val reminders = Reminder.listFromJsonObject(JSONObject(json))

            // Schedule local fallback notifications
            val pendingJobs = reminders.flatMap { r ->
                r.notificationJobs.filter { j -> j.status == "pending" && j.channel == "push" }
            }
            com.sam.lifelogger.notification.ReminderNotificationHelper.scheduleLocalNotifications(context, pendingJobs)

            Log.d(TAG, "Synced ${reminders.size} upcoming reminders")
            reminders
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync upcoming reminders", e)
            emptyList()
        }
    }

    /**
     * Sync reminders that need review.
     */
    suspend fun syncNeedsReview(context: Context): List<Reminder> = withContext(Dispatchers.IO) {
        try {
            val json = ApiClient.getNeedsReviewReminders(context)
            val reminders = Reminder.listFromJsonObject(JSONObject(json))
            Log.d(TAG, "Synced ${reminders.size} needs-review reminders")
            reminders
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync needs-review reminders", e)
            emptyList()
        }
    }

    /**
     * Patch a reminder and re-sync both lists.
     * If the reminder was needs_review and is being confirmed with a date/time,
     * also creates a notification job on the server.
     *
     * @return Pair of (updated upcoming list, updated needs-review list)
     */
    suspend fun patchAndResync(
        context: Context,
        id: Long,
        patch: ReminderPatch,
        createNotificationJob: Boolean = false,
        notifyAtUtc: String? = null,
        notificationTitle: String? = null,
        notificationBody: String? = null
    ): Pair<List<Reminder>, List<Reminder>> = withContext(Dispatchers.IO) {
        try {
            ApiClient.patchReminder(context, id, patch)

            // If user confirmed a needs-review reminder with a date/time, create a notification job
            if (createNotificationJob && notifyAtUtc != null && notificationTitle != null) {
                ApiClient.createNotificationJob(
                    context = context,
                    reminderId = id,
                    notifyAtUtc = notifyAtUtc,
                    notificationTitle = notificationTitle,
                    notificationBody = notificationBody
                )
            }

            // Re-sync both lists
            val upcoming = syncUpcoming(context)
            val review = syncNeedsReview(context)
            Pair(upcoming, review)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch reminder $id", e)
            throw e
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/sam/lifelogger/data/ReminderSyncManager.kt
git commit -m "feat: add ReminderSyncManager (sync upcoming, needs-review, patch and resync)"
```

---

### Task 5: RemindersScreen (Two-Tab UI)

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/ui/RemindersScreen.kt`

- [ ] **Step 1: Create RemindersScreen with Upcoming and Needs Review tabs**

```kotlin
package com.sam.lifelogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderPatch
import com.sam.lifelogger.data.ReminderSyncManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    onEditReminder: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Upcoming", "Needs Review")

    var upcomingReminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var reviewReminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        scope.launch {
            isLoading = true
            error = null
            try {
                upcomingReminders = ReminderSyncManager.syncUpcoming(context)
                reviewReminders = ReminderSyncManager.syncNeedsReview(context)
            } catch (e: Exception) {
                error = "Could not sync: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { loadData() }) {
                            Text("Retry")
                        }
                    }
                }
                selectedTab == 0 -> {
                    UpcomingTab(
                        reminders = upcomingReminders,
                        onTap = onEditReminder,
                        onDone = { reminder ->
                            scope.launch {
                                try {
                                    ReminderSyncManager.patchAndResync(
                                        context, reminder.id,
                                        ReminderPatch(status = "done")
                                    )
                                    loadData()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Failed to update")
                                }
                            }
                        },
                        onCancel = { reminder ->
                            scope.launch {
                                try {
                                    ReminderSyncManager.patchAndResync(
                                        context, reminder.id,
                                        ReminderPatch(status = "cancelled")
                                    )
                                    loadData()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Failed to update")
                                }
                            }
                        }
                    )
                }
                selectedTab == 1 -> {
                    NeedsReviewTab(
                        reminders = reviewReminders,
                        onTap = onEditReminder
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingTab(
    reminders: List<Reminder>,
    onTap: (Long) -> Unit,
    onDone: (Reminder) -> Unit,
    onCancel: (Reminder) -> Unit
) {
    if (reminders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No upcoming reminders.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Group by date bucket: Today, Tomorrow, then date
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val grouped = remember(reminders) {
        val groups = linkedMapOf<String, MutableList<Reminder>>()
        for (r in reminders) {
            val bucket = if (r.scheduledAtLocal != null) {
                try {
                    val odt = java.time.OffsetDateTime.parse(r.scheduledAtLocal)
                    val d = odt.toLocalDate()
                    when (d) {
                        today -> "Today"
                        tomorrow -> "Tomorrow"
                        else -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
                    }
                } catch (_: Exception) { "Other" }
            } else {
                "Unscheduled"
            }
            groups.getOrPut(bucket) { mutableListOf() }.add(r)
        }
        groups.toMap()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (header, items) ->
            item(key = "header-$header") {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
            items(items, key = { it.id }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    showActions = true,
                    onTap = { onTap(reminder.id) },
                    onDone = { onDone(reminder) },
                    onCancel = { onCancel(reminder) }
                )
            }
        }
    }
}

@Composable
private fun NeedsReviewTab(
    reminders: List<Reminder>,
    onTap: (Long) -> Unit
) {
    if (reminders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No reminders to review.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(reminders, key = { it.id }) { reminder ->
            NeedsReviewCard(
                reminder = reminder,
                onTap = { onTap(reminder.id) }
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    showActions: Boolean,
    onTap: () -> Unit,
    onDone: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KindBadge(reminder.kind)
                        if (reminder.scheduledAtLocal != null) {
                            Text(
                                text = formatReminderTime(reminder.scheduledAtLocal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (showActions) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDone) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun NeedsReviewCard(
    reminder: Reminder,
    onTap: () -> Unit
) {
    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KindBadge(reminder.kind)
                        Text(
                            text = "Needs review",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onTap) {
                Text("Edit & Confirm")
            }
        }
    }
}

@Composable
private fun KindBadge(kind: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = kind.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

private fun formatReminderTime(isoLocal: String): String {
    return try {
        val odt = java.time.OffsetDateTime.parse(isoLocal)
        val date = odt.toLocalDate()
        val time = odt.toLocalTime()
        val today = LocalDate.now(odt.offset)
        val tomorrow = today.plusDays(1)

        val dateLabel = when (date) {
            today -> "Today"
            tomorrow -> "Tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("d MMM"))
        }
        "$dateLabel, ${time.format(DateTimeFormatter.ofPattern("h:mm a"))}"
    } catch (e: Exception) {
        isoLocal
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/sam/lifelogger/ui/RemindersScreen.kt
git commit -m "feat: add RemindersScreen with Upcoming and Needs Review tabs"
```

---

### Task 6: EditReminderScreen

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/ui/EditReminderScreen.kt`

- [ ] **Step 1: Create EditReminderScreen with full edit form**

```kotlin
package com.sam.lifelogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.BlackOutlineButton
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderPatch
import com.sam.lifelogger.data.ReminderSyncManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderScreen(
    reminderId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var reminder by remember { mutableStateOf<Reminder?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Edit fields
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editKind by remember { mutableStateOf("other") }
    var editDate by remember { mutableStateOf("") }
    var editTime by remember { mutableStateOf("") }
    var editStatus by remember { mutableStateOf("pending") }

    val kindOptions = listOf("task", "event", "shopping", "fact", "bill", "other")
    var kindExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    // Load reminder data
    LaunchedEffect(reminderId) {
        try {
            val all = ReminderSyncManager.syncUpcoming(context) +
                    ReminderSyncManager.syncNeedsReview(context)
            val found = all.find { it.id == reminderId }
            if (found != null) {
                reminder = found
                editTitle = found.title
                editDescription = found.description ?: ""
                editKind = found.kind
                editStatus = found.status
                if (found.scheduledAtLocal != null) {
                    try {
                        val odt = OffsetDateTime.parse(found.scheduledAtLocal)
                        editDate = odt.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        editTime = odt.format(DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (_: Exception) {}
                }
            } else {
                error = "Reminder not found (id=$reminderId)"
            }
        } catch (e: Exception) {
            error = "Could not load reminder: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Shared save logic — used by both top-bar check button and bottom Save button (fixes #8)
    fun saveReminder() {
        if (isSaving) return
        if (editTitle.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Title is required") }
            return
        }
        // Guard: review mode requires a date (fixes #5)
        if (reminder?.needsReview == true && editDate.isBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Choose a date before confirming this reminder",
                    duration = SnackbarDuration.Long
                )
            }
            return
        }

        scope.launch {
            isSaving = true
            try {
                val timezone = ZoneId.systemDefault().id
                var scheduledLocal: String? = null
                var scheduledUtc: String? = null
                var precision = "date"
                var usedDefault = false

                if (editDate.isNotBlank()) {
                    val date = LocalDate.parse(editDate)
                    val time = if (editTime.isNotBlank()) {
                        precision = "datetime"
                        LocalTime.parse(editTime)
                    } else {
                        usedDefault = true
                        LocalTime.of(9, 0) // default 9am
                    }
                    val zone = ZoneId.of(timezone)
                    val zoned = date.atTime(time).atZone(zone)
                    scheduledLocal = zoned
                        .toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    scheduledUtc = zoned
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }

                val patch = ReminderPatch(
                    title = editTitle,
                    description = editDescription.ifBlank { null },
                    kind = editKind,
                    status = editStatus,
                    // Only set needsReview=false when a date has been chosen (fixes #5)
                    needsReview = if (editDate.isNotBlank() && editStatus == "pending") false else null,
                    timezone = timezone,
                    scheduledAtLocal = scheduledLocal,
                    scheduledAtUtc = scheduledUtc,
                    schedulePrecision = precision,
                    usedDefaultTime = usedDefault
                )

                // If confirming a reviewed reminder with date/time, create a notification job
                val isReviewConfirm = reminder?.needsReview == true && scheduledLocal != null

                ReminderSyncManager.patchAndResync(
                    context = context,
                    id = reminderId,
                    patch = patch,
                    createNotificationJob = isReviewConfirm,
                    notifyAtUtc = scheduledUtc,
                    notificationTitle = editTitle,
                    notificationBody = if (precision == "date") "Due today" else "Due at $editTime"
                )

                onSaved()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    message = "Save failed: ${e.message ?: "unknown error"}",
                    actionLabel = "Retry",
                    duration = SnackbarDuration.Long
                )
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(reminder?.let { if (it.needsReview) "Review Reminder" else "Edit Reminder" } ?: "Reminder")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { saveReminder() },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Check, "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Text(
                        text = error!!,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Review banner
                        if (reminder?.needsReview == true) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "This reminder needs review — please check the details below and confirm.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Title
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Description
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text("Description (optional)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp),
                            minLines = 3
                        )

                        // Kind dropdown
                        ExposedDropdownMenuBox(
                            expanded = kindExpanded,
                            onExpandedChange = { kindExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = editKind.replaceFirstChar { it.uppercase() },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kind") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = kindExpanded,
                                onDismissRequest = { kindExpanded = false }
                            ) {
                                kindOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            editKind = option
                                            kindExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Date
                        OutlinedTextField(
                            value = editDate,
                            onValueChange = { editDate = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            placeholder = { Text("2026-06-07") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Time
                        OutlinedTextField(
                            value = editTime,
                            onValueChange = { editTime = it },
                            label = { Text("Time (HH:MM, optional)") },
                            placeholder = { Text("17:30") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Status dropdown
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = editStatus.replaceFirstChar { it.uppercase() },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Status") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                listOf("pending", "done", "cancelled").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            editStatus = option
                                            statusExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save button (calls same extracted saveReminder() as top-bar check)
                        BlackOutlineButton(
                            text = if (isSaving) "Saving…" else "Save",
                            onClick = { saveReminder() },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/sam/lifelogger/ui/EditReminderScreen.kt
git commit -m "feat: add EditReminderScreen with full edit form and review mode"
```

---

### Task 7: Navigation Wiring and Manifest Updates

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add "Reminders" button and NavHost routes in MainActivity.kt**

Add a new parameter to `AppNavigation`:

```kotlin
// Change the AppNavigation call in MainActivity.setContent (around line 259):
AppNavigation(
    activeRecordingMode = activeModeState.value,
    onStopActiveMode = { stopReminderRecordingFromUi() },
    onStartRecording = { ... },
    onStopRecording = { ... },
    onUploadPending = { ... },
    onOpenPrompts = { ... },   // already present
    onOpenReminders = { navController.navigate("reminders") },  // NEW
    syncStatus = syncStatus,
    batteryLevel = batteryLevel,
    batteryTemp = batteryTemp,
    currentTime = currentTime,
    timeToNextChunk = timeToNextChunk
)
```

Update the `AppNavigation` composable signature (around line 465):

```kotlin
@Composable
fun AppNavigation(
    activeRecordingMode: RecordingMode?,
    onStopActiveMode: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onUploadPending: () -> Unit,
    onOpenPrompts: () -> Unit = {},
    onOpenReminders: () -> Unit = {},  // NEW
    syncStatus: String,
    batteryLevel: Int?,
    batteryTemp: Float?,
    currentTime: String,
    timeToNextChunk: String?
)
```

Add a "Reminders" button in the `MainScreen` composable (around line 730, after the Life Log button):

```kotlin
Spacer(modifier = Modifier.height(24.dp))

BlackOutlineButton(
    text = "Reminders",
    onClick = onOpenReminders,
    modifier = Modifier.width(200.dp)
)
```

Add the NavHost routes inside the `NavHost` block (after the existing routes, around line 570):

```kotlin
composable("reminders") {
    RemindersScreen(
        onBack = { navController.popBackStack() },
        onEditReminder = { id -> navController.navigate("reminders/edit/$id") }
    )
}

composable("reminders/edit/{id}") { backStackEntry ->
    val idStr = backStackEntry.arguments?.getString("id")
    val id = idStr?.toLongOrNull() ?: return@composable
    EditReminderScreen(
        reminderId = id,
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() }
    )
}
```

Add the import at the top:

```kotlin
import com.sam.lifelogger.ui.RemindersScreen
import com.sam.lifelogger.ui.EditReminderScreen
```

- [ ] **Step 2: Update AndroidManifest.xml**

Add permissions and receivers:

```xml
<!-- Add after existing POST_NOTIFICATIONS permission -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Add inside <application> after the squeeze receiver block -->
<receiver
    android:name=".notification.ReminderNotificationBroadcastReceiver"
    android:exported="false" />

<receiver
    android:name=".notification.ReminderBootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 3: Add notification channel creation at app start**

In `MainActivity.onCreate()`, after the existing lifecycleScope.launch block (around line 102-104):

```kotlin
// Create notification channel for reminders
import com.sam.lifelogger.notification.ReminderNotificationHelper

// Add this line after the PromptSyncManager.sync() call:
ReminderNotificationHelper.createNotificationChannel(this)
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sam/lifelogger/MainActivity.kt
git add app/src/main/AndroidManifest.xml
git commit -m "feat: wire reminders navigation and manifest permissions"
```

---

### Spec Coverage Check

| Spec requirement | Task |
|---|---|
| Data models (Reminder, NotificationJob, ReminderPatch) | Task 1 |
| API methods (upcoming, needs-review, patch, notification jobs) | Task 2 |
| Local notification infra (channel, schedule, cancel, boot) | Task 3 |
| ReminderSyncManager (sync, patch-and-resync, two-step review save) | Task 4 |
| RemindersScreen with two tabs | Task 5 |
| EditReminderScreen with review mode | Task 6 |
| Navigation wiring (button + routes) | Task 7 |
| Manifest permissions and receivers | Task 7 |
| Exact alarm fallback | Task 3 (`ReminderNotificationHelper.scheduleAlarm`) |
| Reboot handling | Task 3 (`ReminderBootReceiver`) |
| Notification channel creation | Task 7 (call in `MainActivity.onCreate`) |
| Scheduled job ID persistence in SharedPreferences | Task 3 (`KEY_SCHEDULED_IDS`) |
| Needs Review tab (built, waiting for server endpoint) | Task 5 (tab exists, calls `syncNeedsReview`) |
| Two-step review save (PATCH then POST notifications) | Task 4 (`patchAndResync` with `createNotificationJob` flag) |
| Upload integration (already working) | No changes needed — existing pipeline |

### Upload Metadata Verification

Before starting implementation, verify that the existing upload pipeline sends these fields for reminder recordings:

- `type=reminder` — `RecordingMode.REMINDER.wireValue = "reminder"`, passed as `recordingType` in `UploadRecordingsWorker`
- `recorded_at_iso` — sent via `RecordingUploadMetadata.fromCreatedAt(recordedAt).recordedAtIso`
- `recorded_timezone` — sent via `RecordingUploadMetadata.fromCreatedAt(recordedAt).recordedTimezone`
- `recorded_at_ms` — sent via `RecordingUploadMetadata.fromCreatedAt(recordedAt).recordedAtMs`
- `recorded_date` — sent via `RecordingUploadMetadata.fromCreatedAt(recordedAt).recordedDate`

All five fields are uploaded at `UploadRecordingsWorker.kt` lines 170-181 when `recordedAt != null`. The `RecordingService` already provides `createdAt` timestamps per chunk. **Confirmed working — no changes needed.**

### Known Limitations (MVP)

- **Needs Review endpoint:** `GET /api/reminders?status=pending&needs_review=true` is not yet implemented on the server. The Needs Review tab UI is built and will work immediately once the server endpoint is added.
- **Reboot recovery:** `ReminderBootReceiver` only clears stale alarm tracking. It does NOT trigger a re-sync. After reboot, notifications won't reschedule until the user opens the Reminders screen (which triggers `syncUpcoming`). Full auto-recovery is deferred.
- **Recurring reminders:** Not supported. The `recurrenceText` field is stored/displayed but no recurring scheduling logic.
- **Location-based reminders:** Not supported.
- **Rich notifications (Done/Snooze from notification shade):** Future work.
- **Multiple notification editor UI:** Future work — v1 uses single notification per reminder.
- **Server FCM push:** Not wired. Push token registration endpoint exists but won't be called until the server's FCM worker is complete. Local fallback notifications cover this gap.