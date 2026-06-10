# Life Calendar v1 and Reminder End Dates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Life Calendar screen with month, week, and list views populated by reminders, add app-side reminder end-date support, and allow confirmed cancellation of Needs Review reminders.

**Architecture:** Extend reminder models/cache with nullable end-date fields, then map reminders into a small `LifeCalendarItem` abstraction. Build a Compose calendar screen that uses cached/synced reminders and filter chips, and wire it from the home screen. Keep server-dependent end-date persistence backward-compatible while the server adds the new fields.

**Tech Stack:** Android Kotlin, Jetpack Compose Material 3, Room, JUnit 4.

---

## File Structure

- Modify `app/src/main/java/com/sam/lifelogger/data/ReminderModels.kt`
  - Add `endAtLocal` and `endAtUtc` to `Reminder`, `ReminderPatch`, and `ReminderEntity`.
  - Parse `end_at_local` / `end_at_utc` and serialize patch fields.
- Modify `app/src/main/java/com/sam/lifelogger/data/AppDatabase.kt`
  - Increment Room version from 5 to 6.
  - Add migration adding nullable end-date columns.
- Create `app/src/main/java/com/sam/lifelogger/calendar/LifeCalendarModels.kt`
  - `LifeCalendarItemType`, `LifeCalendarItem`, and reminder-to-calendar mapping.
- Create `app/src/main/java/com/sam/lifelogger/ui/LifeCalendarScreen.kt`
  - Month, Week, List views and filter chips.
- Modify `app/src/main/java/com/sam/lifelogger/MainActivity.kt`
  - Add Calendar route and home action button.
- Modify `app/src/main/java/com/sam/lifelogger/ui/RemindersScreen.kt`
  - Add confirm dialog for Needs Review cancellation and call the existing patch flow.
- Test `app/src/test/java/com/sam/lifelogger/data/ReminderModelsTest.kt`
- Test `app/src/test/java/com/sam/lifelogger/calendar/LifeCalendarModelsTest.kt`

## Task 1: Reminder End-Date Model Support

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/data/ReminderModels.kt`
- Modify: `app/src/main/java/com/sam/lifelogger/data/AppDatabase.kt`
- Test: `app/src/test/java/com/sam/lifelogger/data/ReminderModelsTest.kt`

- [ ] **Step 1: Add failing tests for end-date parsing and patch serialization**

Add this test to `ReminderModelsTest`:

```kotlin
@Test
fun parsesOptionalReminderEndDates() {
    val json = JSONObject(
        """
        {
          "reminders": [
            {
              "id": 42,
              "source_segment_id": null,
              "kind": "event",
              "title": "Conference",
              "description": null,
              "status": "pending",
              "needs_review": false,
              "timezone": "Australia/Sydney",
              "scheduled_at_local": "2026-06-10T09:00:00+10:00",
              "scheduled_at_utc": "2026-06-09T23:00:00Z",
              "end_at_local": "2026-06-12T17:00:00+10:00",
              "end_at_utc": "2026-06-12T07:00:00Z",
              "schedule_precision": "datetime",
              "used_default_time": false,
              "location": null,
              "people": null,
              "amount": null,
              "recurrence_text": null,
              "notification_jobs": []
            }
          ]
        }
        """.trimIndent()
    )

    val reminder = Reminder.listFromJsonObject(json).single()

    assertEquals("2026-06-12T17:00:00+10:00", reminder.endAtLocal)
    assertEquals("2026-06-12T07:00:00Z", reminder.endAtUtc)
}

@Test
fun serializesReminderPatchEndDates() {
    val patch = ReminderPatch(
        endAtLocal = "2026-06-12T17:00:00+10:00",
        endAtUtc = "2026-06-12T07:00:00Z"
    ).toJson()

    assertEquals("2026-06-12T17:00:00+10:00", patch.getString("end_at_local"))
    assertEquals("2026-06-12T07:00:00Z", patch.getString("end_at_utc"))
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.data.ReminderModelsTest
```

Expected: compilation fails because `endAtLocal` / `endAtUtc` do not exist.

- [ ] **Step 3: Add nullable end fields to reminder models**

In `Reminder`, add:

```kotlin
val endAtLocal: String?,
val endAtUtc: String?,
```

In `Reminder.fromJson`, parse:

```kotlin
endAtLocal = obj.optNullableString("end_at_local"),
endAtUtc = obj.optNullableString("end_at_utc"),
```

In `Reminder.fromCacheEntities`, map:

```kotlin
endAtLocal = reminder.endAtLocal,
endAtUtc = reminder.endAtUtc,
```

In `Reminder.toCacheEntity`, pass:

```kotlin
endAtLocal = endAtLocal,
endAtUtc = endAtUtc,
```

In `ReminderPatch`, add:

```kotlin
val endAtLocal: String? = null,
val endAtUtc: String? = null
```

In `ReminderPatch.toJson`, add:

```kotlin
endAtLocal?.let { put("end_at_local", it) }
endAtUtc?.let { put("end_at_utc", it) }
```

In `ReminderEntity`, add:

```kotlin
val endAtLocal: String?,
val endAtUtc: String?
```

- [ ] **Step 4: Add Room migration**

In `AppDatabase.kt`:

- Change `version = 5` to `version = 6`.
- Add `endAtLocal` and `endAtUtc` nullable columns to the `cached_reminders` create-table SQL in `MIGRATION_4_5`.
- Add:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_reminders ADD COLUMN endAtLocal TEXT")
        db.execSQL("ALTER TABLE cached_reminders ADD COLUMN endAtUtc TEXT")
    }
}
```

- Register the migration:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```

- [ ] **Step 5: Run model tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.data.ReminderModelsTest
```

Expected: tests pass.

## Task 2: Life Calendar Mapping

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/calendar/LifeCalendarModels.kt`
- Test: `app/src/test/java/com/sam/lifelogger/calendar/LifeCalendarModelsTest.kt`

- [ ] **Step 1: Write failing calendar mapper tests**

Create `LifeCalendarModelsTest.kt`:

```kotlin
package com.sam.lifelogger.calendar

import com.sam.lifelogger.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LifeCalendarModelsTest {

    @Test
    fun mapsSingleDayReminderToOneCalendarDate() {
        val reminder = reminder(
            scheduledAtLocal = "2026-06-10T09:00:00+10:00",
            endAtLocal = null
        )

        val item = LifeCalendarItem.fromReminder(reminder)

        assertEquals(listOf(LocalDate.of(2026, 6, 10)), item.coveredDates())
    }

    @Test
    fun mapsMultiDayReminderAcrossInclusiveDateRange() {
        val reminder = reminder(
            scheduledAtLocal = "2026-06-10T09:00:00+10:00",
            endAtLocal = "2026-06-12T17:00:00+10:00"
        )

        val item = LifeCalendarItem.fromReminder(reminder)

        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 11),
                LocalDate.of(2026, 6, 12)
            ),
            item.coveredDates()
        )
    }

    private fun reminder(
        scheduledAtLocal: String?,
        endAtLocal: String?
    ): Reminder {
        return Reminder(
            id = 7,
            sourceSegmentId = null,
            kind = "event",
            title = "Calendar item",
            description = null,
            status = "pending",
            needsReview = false,
            timezone = "Australia/Sydney",
            scheduledAtLocal = scheduledAtLocal,
            scheduledAtUtc = "2026-06-09T23:00:00Z",
            endAtLocal = endAtLocal,
            endAtUtc = if (endAtLocal == null) null else "2026-06-12T07:00:00Z",
            schedulePrecision = "datetime",
            usedDefaultTime = false,
            location = null,
            people = null,
            amount = null,
            recurrenceText = null,
            notificationJobs = emptyList()
        )
    }
}
```

- [ ] **Step 2: Run failing mapper tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.calendar.LifeCalendarModelsTest
```

Expected: compilation fails because calendar models do not exist.

- [ ] **Step 3: Implement calendar models**

Create `LifeCalendarModels.kt`:

```kotlin
package com.sam.lifelogger.calendar

import com.sam.lifelogger.data.Reminder
import java.time.LocalDate
import java.time.OffsetDateTime

enum class LifeCalendarItemType {
    Reminder,
    Habit,
    Workout,
    FinancialAlert
}

data class LifeCalendarItem(
    val id: String,
    val sourceType: LifeCalendarItemType,
    val sourceId: Long,
    val title: String,
    val description: String?,
    val startUtc: String?,
    val startLocal: String?,
    val endUtc: String?,
    val endLocal: String?,
    val status: String,
    val needsReview: Boolean
) {
    companion object {
        fun fromReminder(reminder: Reminder): LifeCalendarItem =
            LifeCalendarItem(
                id = "reminder:${reminder.id}",
                sourceType = LifeCalendarItemType.Reminder,
                sourceId = reminder.id,
                title = reminder.title,
                description = reminder.description,
                startUtc = reminder.scheduledAtUtc,
                startLocal = reminder.scheduledAtLocal,
                endUtc = reminder.endAtUtc,
                endLocal = reminder.endAtLocal,
                status = reminder.status,
                needsReview = reminder.needsReview
            )
    }

    fun coveredDates(): List<LocalDate> {
        val start = parseDate(startLocal) ?: return emptyList()
        val end = parseDate(endLocal) ?: start
        if (end.isBefore(start)) return listOf(start)

        return generateSequence(start) { date ->
            val next = date.plusDays(1)
            if (next.isAfter(end)) null else next
        }.toList()
    }
}

private fun parseDate(value: String?): LocalDate? =
    value?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }
```

- [ ] **Step 4: Run mapper tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.calendar.LifeCalendarModelsTest
```

Expected: tests pass.

## Task 3: Needs Review Cancel Confirmation

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/ui/RemindersScreen.kt`

- [ ] **Step 1: Add dialog state to `RemindersScreen`**

Inside `RemindersScreen`, add:

```kotlin
var cancelCandidate by remember { mutableStateOf<Reminder?>(null) }
```

- [ ] **Step 2: Add confirm dialog**

Inside `Scaffold` content, before the root `Column`, add:

```kotlin
cancelCandidate?.let { reminder ->
    AlertDialog(
        onDismissRequest = { cancelCandidate = null },
        title = { Text("Cancel reminder?") },
        text = { Text("This will remove it from Needs Review and mark it cancelled.") },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        ReminderSyncManager.patchAndResync(
                            context,
                            reminder.id,
                            ReminderPatch(status = "cancelled", needsReview = false)
                        )
                        cancelCandidate = null
                        load()
                    }
                }
            ) {
                Text("Cancel reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = { cancelCandidate = null }) {
                Text("Keep")
            }
        }
    )
}
```

Add missing imports:

```kotlin
import androidx.compose.material3.AlertDialog
```

- [ ] **Step 3: Wire Needs Review cancel**

In the Needs Review `ReminderList` call, change:

```kotlin
onPatchReminder = { _, _ -> }
```

to:

```kotlin
onPatchReminder = { id, patch ->
    val reminder = needsReview.firstOrNull { it.id == id }
    if (patch.status == "cancelled" && reminder != null) {
        cancelCandidate = reminder
    }
}
```

- [ ] **Step 4: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compilation succeeds.

## Task 4: Life Calendar Screen and Navigation

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/ui/LifeCalendarScreen.kt`
- Modify: `app/src/main/java/com/sam/lifelogger/MainActivity.kt`

- [ ] **Step 1: Create `LifeCalendarScreen.kt`**

Create a Compose screen with:

- Top app bar title `Life Calendar`.
- Back button.
- Segmented/tab row for `Month`, `Week`, `List`.
- Filter chips for `Reminders`, `Habits`, `Workouts`, `Financial Alerts`.
- Reminders active; other chips disabled.
- Loads reminders via `ReminderSyncManager.syncUpcomingOrUseCache(context)`.
- Maps reminders through `LifeCalendarItem.fromReminder`.
- Month view: current month grid with item counts/titles.
- Week view: compact seven-day agenda.
- List view: grouped by date.

- [ ] **Step 2: Add route and home button**

In `MainActivity.kt`:

- Import `LifeCalendarScreen`.
- Add `onOpenCalendar` to `MainScreen` and `HomeActionGrid`.
- Add third home action row with Calendar button.
- Add NavHost route:

```kotlin
composable("calendar") {
    LifeCalendarScreen(
        onBack = { navController.popBackStack() },
        onOpenReminder = { id -> navController.navigate("reminders/edit/$id") }
    )
}
```

- Pass `onOpenCalendar = { navController.navigate("calendar") }` to `MainScreen`.

- [ ] **Step 3: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compilation succeeds.

## Task 5: Verification and Install

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run full unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install debug APK**

Run:

```powershell
.\gradlew.bat :app:installDebug
```

Expected: installed on connected device.

- [ ] **Step 4: Manual checks**

Check:

- Home shows Calendar button.
- Calendar opens.
- Month/Week/List switch without crashing.
- Reminders appear on expected dates.
- Future filters are visible but disabled.
- Needs Review cancel shows confirmation before patching.
- Existing reminders without end dates still display normally.
