# Recurring Reminders App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add app-side fallback scheduling for unambiguous recurring reminders and make recurring reminder controls fit the existing edit screen.

**Architecture:** Add a focused recurrence inference helper in the data layer, then consume it from calendar/edit display paths when the server omits a concrete occurrence date. Keep recurrence UI changes inside the existing `EditReminderScreen`, using the same dropdown components already used for kind/status/notifications.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Java Time, JUnit 4, Gradle Android plugin.

---

## File Structure

- Create `app/src/main/java/com/sam/lifelogger/data/ReminderRecurrenceInference.kt`
  - Computes the closest not-past occurrence date for supported recurrence shapes.
  - Returns `null` for ambiguous or unsupported rules.
- Create `app/src/test/java/com/sam/lifelogger/data/ReminderRecurrenceInferenceTest.kt`
  - Covers weekly and monthly day-of-month inference plus unsupported monthly weekday cases.
- Modify `app/src/main/java/com/sam/lifelogger/data/ReminderModels.kt`
  - Add optional recurrence fields needed by the app if the server returns them later, without requiring them now.
- Modify `app/src/main/java/com/sam/lifelogger/calendar/LifeCalendarModels.kt`
  - Use inferred recurrence date as a display fallback.
- Modify `app/src/main/java/com/sam/lifelogger/calendar/CalendarAgendaModels.kt`
  - Use inferred recurrence date for agenda grouping and filtering.
- Modify `app/src/main/java/com/sam/lifelogger/ui/EditReminderScreen.kt`
  - Populate the Date field from inferred recurrence date when needed.
  - Replace the colored repeats panel with neutral dropdown-style fields and stacked full-width series action buttons.

---

### Task 1: Recurrence Inference Tests

**Files:**
- Create: `app/src/test/java/com/sam/lifelogger/data/ReminderRecurrenceInferenceTest.kt`

- [ ] **Step 1: Write failing tests**

Create tests for:

```kotlin
@Test fun weeklyMondayWhenTodayIsMondayReturnsToday()
@Test fun weeklyMondayWhenTodayIsWednesdayReturnsNextMonday()
@Test fun monthlySixthWhenFutureThisMonthReturnsThisMonth()
@Test fun monthlySixthWhenPassedReturnsNextMonth()
@Test fun monthlyWeekdayWithoutDayOfMonthReturnsNull()
@Test fun nonRecurringReminderReturnsNull()
```

The tests should call:

```kotlin
ReminderRecurrenceInference.inferOccurrenceDate(reminder, today)
```

Expected examples:

```kotlin
assertEquals(LocalDate.of(2026, 6, 22), result)
assertEquals(LocalDate.of(2026, 6, 29), result)
assertEquals(LocalDate.of(2026, 7, 6), result)
assertNull(result)
```

- [ ] **Step 2: Verify tests fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.data.ReminderRecurrenceInferenceTest
```

Expected: fails because `ReminderRecurrenceInference` does not exist.

---

### Task 2: Recurrence Inference Implementation

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/data/ReminderRecurrenceInference.kt`
- Modify: `app/src/main/java/com/sam/lifelogger/data/ReminderModels.kt`

- [ ] **Step 1: Add missing optional model fields**

Extend `Recurrence` with nullable fields that are safe if absent:

```kotlin
val ordinal: Int?,
val weekdayOrdinal: Int?
```

Parse tolerant server names:

```kotlin
ordinal = obj.optNullableInt("ordinal"),
weekdayOrdinal = obj.optNullableInt("weekday_ordinal")
```

These are not used for inference in this pass; they are kept for future server work.

- [ ] **Step 2: Implement inference helper**

Create `ReminderRecurrenceInference` with:

```kotlin
object ReminderRecurrenceInference {
    fun inferOccurrenceDate(reminder: Reminder, today: LocalDate = LocalDate.now()): LocalDate?
}
```

Behavior:

- Return `null` if `reminder.isRecurring` is false.
- Return `null` if any concrete date already exists.
- Weekly: require `recurrence.frequency == "weekly"` and `dayOfWeek` in 1..7.
- Monthly: require `recurrence.frequency == "monthly"` and `dayOfMonth` in 1..31.
- Monthly weekday-only and ordinal weekday rules return `null`.
- If the monthly target day is invalid for the current/next month, return `null` rather than clamping.

- [ ] **Step 3: Verify focused tests pass**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.data.ReminderRecurrenceInferenceTest
```

Expected: pass.

---

### Task 3: Use Inference in Calendar and Edit Display

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/calendar/LifeCalendarModels.kt`
- Modify: `app/src/main/java/com/sam/lifelogger/calendar/CalendarAgendaModels.kt`
- Modify: `app/src/main/java/com/sam/lifelogger/ui/EditReminderScreen.kt`

- [ ] **Step 1: Add display fallback**

When code currently uses:

```kotlin
scheduledAtLocal ?: recurrenceOccurrenceLocal
```

extend it to use:

```kotlin
scheduledAtLocal ?: recurrenceOccurrenceLocal ?: inferredOccurrenceLocal(...)
```

where inferred occurrence is converted to a local offset date-time at the recurrence time, reminder time, or 09:00 fallback.

- [ ] **Step 2: Preserve Needs Review behavior for ambiguous recurrence**

If inference returns `null`, keep the existing behavior: no date field is populated and the user must choose a date before confirming.

- [ ] **Step 3: Run existing calendar tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.calendar.CalendarAgendaModelsTest --tests com.sam.lifelogger.calendar.LifeCalendarModelsTest
```

Expected: pass.

---

### Task 4: Recurring Reminder Detail UI

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/ui/EditReminderScreen.kt`

- [ ] **Step 1: Replace the colored repeat panel**

Remove `Surface(color = MaterialTheme.colorScheme.tertiaryContainer...)` from `RepeatsSection`.

Render recurrence controls like the rest of the form:

```kotlin
DropdownField(label = "Repeat frequency", value = repeatFrequency, ...)
DropdownField(label = "Repeat day", value = repeatDay, ...)
```

Only show this block when `reminder?.isRecurring == true`.

- [ ] **Step 2: Stack series action buttons**

Replace the horizontal `Row` with:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(modifier = Modifier.fillMaxWidth(), ...)
    OutlinedButton(modifier = Modifier.fillMaxWidth(), ...)
    OutlinedButton(modifier = Modifier.fillMaxWidth(), ...)
}
```

- [ ] **Step 3: Keep existing fields unchanged**

Confirm the form still includes title, description, kind, date, time, end date, notifications, custom offsets, status, and save.

---

### Task 5: Final Verification and APK

**Files:**
- No new file edits expected.

- [ ] **Step 1: Run full unit test suite**

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
& "C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "G:\android_projects\lifelogger\app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 4: Commit implementation**

Commit app/test changes only. Leave generated build output and `.superpowers/brainstorm` files unstaged.
