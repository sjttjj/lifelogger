# Reminder System — App-Side Design

**Date:** 2026-06-05
**Status:** Approved for implementation
**Server contract:** See `Reminder Integration Contract` (server team doc)

---

## Overview

Add reminder recording, syncing, editing, and local fallback notification support to the Lifelogger Android app. The server is the canonical source of truth; the app is a client that uploads reminder audio, syncs processed reminders, and schedules local fallback notifications from synced `notification_jobs`.

---

## 1. Data Models

### `data/ReminderModels.kt`

```kotlin
data class Reminder(
    val id: Long,
    val sourceSegmentId: Long?,
    val kind: String,                // "task", "event", "shopping", "fact", "bill", "other"
    val title: String,
    val description: String?,
    val status: String,              // "pending", "done", "cancelled"
    val needsReview: Boolean,
    val timezone: String?,
    val scheduledAtLocal: String?,   // ISO 8601 with offset
    val scheduledAtUtc: String?,
    val schedulePrecision: String,   // "date" or "datetime"
    val usedDefaultTime: Boolean,
    val location: String?,
    val people: String?,
    val amount: String?,
    val recurrenceText: String?,
    val notificationJobs: List<NotificationJob> = emptyList()
)

data class NotificationJob(
    val id: Long,
    val reminderId: Long,
    val notifyAtUtc: String,         // ISO 8601
    val notificationTitle: String,
    val notificationBody: String?,
    val channel: String,             // "push"
    val status: String               // "pending", "sent", "cancelled"
)

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
)
```

**JSON parsing:** Server returns `snake_case` fields. Parse via `JSONObject` (consistent with existing `ApiClient` pattern) rather than adding a serialization library. Each data class gets a `companion object { fun fromJson(obj: JSONObject): Reminder }` factory.

---

## 2. API Client Additions

### `data/ApiClient.kt` — new methods

All follow the existing `httpGet`/`HttpURLConnection` pattern, returning raw `String`:

```kotlin
/** GET /api/reminders/upcoming?start=<iso>&end=<iso>&status=pending[&kind=...] */
suspend fun getUpcomingReminders(context: Context, start: String, end: String, kind: String? = null): String

/** GET /api/reminders?status=pending&needs_review=true (or /api/reminders/review) */
suspend fun getNeedsReviewReminders(context: Context): String

/** PATCH /api/reminders/{id} */
suspend fun patchReminder(context: Context, id: Long, patch: ReminderPatch): String

/** POST /api/reminders/{id}/notifications */
suspend fun createNotificationJob(context: Context, reminderId: Long, notifyAtUtc: String, title: String, body: String?): String
```

---

## 3. Sync Manager

### `data/ReminderSyncManager.kt`

Follows the `PromptSyncManager` pattern (top-level functions, not a ViewModel).

```kotlin
suspend fun syncUpcoming(context: Context): List<Reminder>
suspend fun syncNeedsReview(context: Context): List<Reminder>
suspend fun patchAndResync(context: Context, id: Long, patch: ReminderPatch): List<Reminder>
```

- `syncUpcoming`: calls `GET /api/reminders/upcoming`, parses into `List<Reminder>`, then calls `scheduleLocalNotifications()`.
- `syncNeedsReview`: calls `GET /api/reminders?status=pending&needs_review=true`, parses into `List<Reminder>`.
- `patchAndResync`: calls `PATCH /api/reminders/{id}`. If `needsReview` was changed from `true` to `false` and a date/time was set, then calls `POST /api/reminders/{id}/notifications` to create a notification job. Then re-syncs upcoming + needs-review lists and reschedules local notifications.

---

## 4. Local Fallback Notifications

### `notification/ReminderNotificationHelper.kt`

**Channel setup** (at init):
- `channel_id = "reminders"`
- `name = "Reminders"`
- `importance = NotificationManager.IMPORTANCE_HIGH`

**Core functions:**

```kotlin
fun scheduleLocalNotifications(context: Context, jobs: List<NotificationJob>)
fun cancelLocalNotification(context: Context, jobId: Long)
fun rescheduleAll(context: Context, jobs: List<NotificationJob>)
fun onBootCompleted(context: Context)
```

**Logic:**

1. On each `syncUpcoming` call, compare the set of notification job IDs against a stored set in SharedPreferences (`scheduled_notification_job_ids`).
2. Cancel alarms for jobs that no longer exist or are no longer `pending`.
3. Schedule alarms for pending future jobs not yet scheduled.
4. Use stable request codes based on `notificationJob.id`.
5. On Android 12+: check `alarmManager.canScheduleExactAlarms()`. If denied, fall back to `set()` (inexact) and optionally prompt user to grant exact alarm permission.
6. Filter: only schedule jobs where `job.status == "pending" && job.channel == "push"`.

### `notification/ReminderNotificationBroadcastReceiver.kt`

- Receives `AlarmManager` intents.
- Extracts `notificationJobId`, `reminderId`, `title`, `body` from intent extras.
- Posts a `NotificationCompat.Builder` notification on the `reminders` channel.
- Tapping the notification opens `MainActivity` → navigates to `reminders` route.

### `AndroidManifest.xml` additions

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- already present -->

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

**Reboot handling:** `ReminderBootReceiver` receives `BOOT_COMPLETED`, triggers a re-sync + reschedule via `ReminderSyncManager`. This is a best-effort recovery; if the device is offline at boot, the next manual or periodic sync will catch up.

---

## 5. UI Screens

### `ui/RemindersScreen.kt`

A single screen with `TabRow` containing two tabs:

**Upcoming tab:**
- Calls `syncUpcoming()` on entry.
- Groups reminders by date bucket (Today, Tomorrow, This Week, Later dates).
- Each card shows: title, kind badge, formatted date/time, status.
- Inline actions: Done (sets `status=done`), Cancel (sets `status=cancelled`).
- Tapping card → navigates to `edit/{id}`.
- Pull-to-refresh re-syncs.

**Needs Review tab:**
- Calls `syncNeedsReview()` on entry.
- Lists reminders where `needsReview == true`.
- Each card shows: title (or partial transcript), kind, reason hint (e.g. "No date set").
- Tapping card → navigates to `edit/{id}` (review mode derived from `needsReview`).
- Pull-to-refresh re-syncs.

**States:**
- Loading: `CircularProgressIndicator`
- Error: error message with retry button
- Empty: "No upcoming reminders" / "No reminders to review"
- Content: grouped list

### `ui/EditReminderScreen.kt`

Route: `reminders/edit/{id}` (review mode derived from `reminder.needsReview`).

**Fields:**
- Title (text field)
- Description (multiline, optional)
- Kind (dropdown: task, event, shopping, fact, bill, other)
- Date (date picker)
- Time (time picker, optional — if omitted, schedule_precision = "date")
- Status (dropdown: pending, done, cancelled)
- Timezone (hidden, defaults to device timezone)

**Review mode:**
- Banner at top: "This reminder needs review — please check and confirm the details below."
- On save: `PATCH /api/reminders/{id}` with `needs_review: false` + date/time + timezone. Then `POST /api/reminders/{id}/notifications` with a notification job at the selected time.

**Save flow:**
1. Validate: if date is set but no time, use 09:00 local as default time (`used_default_time = true`).
2. Compute `scheduledAtLocal` and `scheduledAtUtc` from the chosen date/time + device timezone.
3. Call `patchAndResync()`.
4. Show success snackbar, navigate back.
5. On error: show error snackbar with Retry.

---

## 6. MainScreen Navigation

### `MainActivity.kt` changes

Add to `MainScreen`:
```kotlin
BlackOutlineButton(
    text = "Reminders",
    onClick = onOpenReminders,
    modifier = Modifier.width(200.dp)
)
```

Add to `AppNavigation` parameters:
```kotlin
onOpenReminders: () -> Unit
```

Add NavHost routes:
```kotlin
composable("reminders") {
    RemindersScreen(
        onBack = { navController.popBackStack() },
        onEditReminder = { id -> navController.navigate("reminders/edit/$id") }
    )
}

composable("reminders/edit/{id}") { backStackEntry ->
    val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
    EditReminderScreen(
        reminderId = id,
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() }
    )
}
```

---

## 7. Upload Integration (Already Working)

No changes needed. The existing `RecordingMode.REMINDER` already:
- Records 2-minute chunks
- Saves to DB with `type = "reminder"`
- Uploads via `UploadRecordingsWorker` with `recordingType = "reminder"`
- Sends `recorded_at_iso`, `recorded_timezone`, `recorded_date`, `recorded_at_ms` via `RecordingUploadMetadata`

The server processes the audio and creates structured reminders which the app then syncs.

---

## 8. Implementation Order

| Step | Files | Description |
|------|-------|-------------|
| 1 | `ReminderModels.kt`, `ApiClient.kt` | Data models + API additions |
| 2 | `ReminderSyncManager.kt` | Sync + parse + notify scheduling trigger |
| 3 | `ReminderNotificationHelper.kt`, `ReminderNotificationBroadcastReceiver.kt`, `ReminderBootReceiver.kt` | Local notification infra |
| 4 | `RemindersScreen.kt` | Two-tab UI |
| 5 | `EditReminderScreen.kt` | Edit/review form |
| 6 | `MainActivity.kt`, `AndroidManifest.xml` | Navigation wiring + manifest permissions |

---

## 9. Known Limitations (MVP)

- **Recurring reminders:** Not supported. The server's `recurrenceText` field is stored/displayed but no recurring logic.
- **Location-based reminders:** Not supported.
- **Rich notifications (Done/Snooze from shade):** Future work.
- **Multiple notification editor UI:** Future work — v1 uses single notification per reminder from the server's `notification_jobs`.
- **Server FCM push:** Not wired yet. The push token registration endpoint exists but won't be called until the server's FCM worker is finished. Local fallback notifications cover this gap.