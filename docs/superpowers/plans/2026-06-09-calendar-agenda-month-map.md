# Calendar Agenda Month Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the narrow portrait month grid with a readable agenda calendar plus compact bottom month map.

**Architecture:** Move range, colour, and month-map day grouping into pure Kotlin helpers under `calendar/` so they can be unit tested. Replace `LifeCalendarScreen` with a single agenda-map UI that consumes those helpers and keeps reminder syncing/navigation behavior intact.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android Room/cache reminder models, JUnit.

---

## File Structure

- Create `app/src/main/java/com/sam/lifelogger/calendar/CalendarAgendaModels.kt`
  - Owns range mode, deterministic colour palette, month map cell calculations, and agenda date filtering.
- Create `app/src/test/java/com/sam/lifelogger/calendar/CalendarAgendaModelsTest.kt`
  - Tests range filtering, 20-colour palette, multi-day expansion, same-day overflow.
- Modify `app/src/main/java/com/sam/lifelogger/ui/LifeCalendarScreen.kt`
  - Replace separate Month/Week/List tabs with agenda list plus compact month map overlay.
- Existing navigation in `MainActivity.kt` stays unchanged.

---

## Task 1: Calendar Agenda Logic

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/calendar/CalendarAgendaModels.kt`
- Create: `app/src/test/java/com/sam/lifelogger/calendar/CalendarAgendaModelsTest.kt`

- [ ] **Step 1: Write failing tests**

Create `CalendarAgendaModelsTest.kt` with tests for the helper API:

```kotlin
package com.sam.lifelogger.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarAgendaModelsTest {
    @Test
    fun paletteHasAtLeastTwentyDistinctColors() {
        assertTrue(CalendarItemColorPalette.colors.size >= 20)
        assertEquals(CalendarItemColorPalette.colors.size, CalendarItemColorPalette.colors.distinct().size)
    }

    @Test
    fun assignsStableColorFromItemId() {
        val first = CalendarItemColorPalette.colorFor(42)
        val second = CalendarItemColorPalette.colorFor(42)
        assertEquals(first, second)
    }

    @Test
    fun filtersDatesForMonthRange() {
        val items = listOf(
            item(1, "May", LocalDate.parse("2026-05-31")),
            item(2, "June", LocalDate.parse("2026-06-09")),
            item(3, "July", LocalDate.parse("2026-07-01"))
        )

        val visible = CalendarAgendaRange.Month.visibleDates(
            anchorDate = LocalDate.parse("2026-06-09"),
            items = items
        )

        assertEquals(listOf(LocalDate.parse("2026-06-09")), visible)
    }

    @Test
    fun filtersDatesForWeekRangeStartingSunday() {
        val items = listOf(
            item(1, "Sunday", LocalDate.parse("2026-06-07")),
            item(2, "Saturday", LocalDate.parse("2026-06-13")),
            item(3, "Next Sunday", LocalDate.parse("2026-06-14"))
        )

        val visible = CalendarAgendaRange.Week.visibleDates(
            anchorDate = LocalDate.parse("2026-06-09"),
            items = items
        )

        assertEquals(
            listOf(LocalDate.parse("2026-06-07"), LocalDate.parse("2026-06-13")),
            visible
        )
    }

    @Test
    fun buildsMonthMapCellsWithOverflowCount() {
        val day = LocalDate.parse("2026-06-18")
        val items = (1L..6L).map { item(it, "Item $it", day) }

        val cell = buildMonthMapCells(
            month = YearMonth.of(2026, 6),
            items = items
        ).single { it.date == day }

        assertEquals(6, cell.items.size)
        assertEquals(4, cell.visibleColors.size)
        assertEquals(2, cell.overflowCount)
    }

    @Test
    fun monthMapIncludesMultiDayReminderOnEveryCoveredDate() {
        val holiday = LifeCalendarItem(
            id = "Reminder-10",
            sourceId = 10,
            type = LifeCalendarItemType.Reminder,
            title = "Conference travel",
            description = null,
            startLocal = "2026-06-18T09:00:00+10:00",
            endLocal = "2026-06-20T23:59:00+10:00"
        )

        val cells = buildMonthMapCells(
            month = YearMonth.of(2026, 6),
            items = listOf(holiday)
        )

        assertTrue(cells.single { it.date == LocalDate.parse("2026-06-18") }.items.contains(holiday))
        assertTrue(cells.single { it.date == LocalDate.parse("2026-06-19") }.items.contains(holiday))
        assertTrue(cells.single { it.date == LocalDate.parse("2026-06-20") }.items.contains(holiday))
    }

    private fun item(id: Long, title: String, date: LocalDate): LifeCalendarItem =
        LifeCalendarItem(
            id = "Reminder-$id",
            sourceId = id,
            type = LifeCalendarItemType.Reminder,
            title = title,
            description = null,
            startLocal = "${date}T09:00:00+10:00",
            endLocal = null
        )
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.calendar.CalendarAgendaModelsTest
```

Expected: FAIL because `CalendarAgendaModels.kt` does not exist.

- [ ] **Step 3: Implement pure Kotlin helpers**

Create `CalendarAgendaModels.kt`:

```kotlin
package com.sam.lifelogger.calendar

import kotlin.math.abs
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarAgendaRange(val label: String) {
    Week("Week"),
    Month("Month"),
    All("All");

    fun next(anchorDate: LocalDate): LocalDate =
        when (this) {
            Week -> anchorDate.plusWeeks(1)
            Month -> anchorDate.plusMonths(1)
            All -> anchorDate.plusMonths(1)
        }

    fun previous(anchorDate: LocalDate): LocalDate =
        when (this) {
            Week -> anchorDate.minusWeeks(1)
            Month -> anchorDate.minusMonths(1)
            All -> anchorDate.minusMonths(1)
        }

    fun visibleDates(anchorDate: LocalDate, items: List<LifeCalendarItem>): List<LocalDate> {
        val dates = items.flatMap { it.coveredDates() }.distinct().sorted()
        return when (this) {
            Week -> {
                val start = anchorDate.minusDays((anchorDate.dayOfWeek.value % 7).toLong())
                val end = start.plusDays(6)
                dates.filter { it in start..end }
            }
            Month -> {
                val month = YearMonth.from(anchorDate)
                dates.filter { YearMonth.from(it) == month }
            }
            All -> dates
        }
    }
}

data class CalendarItemColor(val hex: Long)

object CalendarItemColorPalette {
    val colors: List<CalendarItemColor> = listOf(
        0xFF5B8DEF, 0xFF2E9D74, 0xFFE0A32F, 0xFFD75D7A, 0xFF7B61D1,
        0xFFD66F32, 0xFF2F9EAA, 0xFF9A7B2F, 0xFF4F9D2E, 0xFFC85D9E,
        0xFF3D7C9E, 0xFFB85C38, 0xFF6F8E2E, 0xFF8A6FD1, 0xFF2F8F6B,
        0xFFCF6C5A, 0xFF4E7BC4, 0xFFB68F2A, 0xFF8D5AAE, 0xFF3B9491
    ).map(::CalendarItemColor)

    fun colorFor(sourceId: Long): CalendarItemColor =
        colors[abs(sourceId.hashCode()) % colors.size]
}

data class MonthMapCell(
    val date: LocalDate,
    val inSelectedMonth: Boolean,
    val items: List<LifeCalendarItem>,
    val visibleColors: List<CalendarItemColor>,
    val overflowCount: Int
)

fun buildMonthMapCells(
    month: YearMonth,
    items: List<LifeCalendarItem>
): List<MonthMapCell> {
    val first = month.atDay(1)
    val leadingDays = first.dayOfWeek.value % 7
    val start = first.minusDays(leadingDays.toLong())
    val totalCells = 35
    val dates = (0 until totalCells).map { start.plusDays(it.toLong()) }
    val itemsByDate = dates.associateWith { date ->
        items.filter { date in it.coveredDates() }
    }

    return dates.map { date ->
        val dayItems = itemsByDate.getValue(date)
        val colors = dayItems.take(4).map { CalendarItemColorPalette.colorFor(it.sourceId) }
        MonthMapCell(
            date = date,
            inSelectedMonth = YearMonth.from(date) == month,
            items = dayItems,
            visibleColors = colors,
            overflowCount = (dayItems.size - 4).coerceAtLeast(0)
        )
    }
}
```

- [ ] **Step 4: Run tests and verify pass**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.calendar.CalendarAgendaModelsTest
```

Expected: PASS.

---

## Task 2: Replace Calendar UI

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/ui/LifeCalendarScreen.kt`

- [ ] **Step 1: Replace view state and range controls**

Remove `CalendarViewMode` and use `CalendarAgendaRange`. Screen state should include:

```kotlin
var selectedRange by remember { mutableStateOf(CalendarAgendaRange.Month) }
var anchorDate by remember { mutableStateOf(LocalDate.now()) }
```

The top content should show:

```kotlin
CalendarRangeHeader(
    selectedRange = selectedRange,
    anchorDate = anchorDate,
    onPrevious = { anchorDate = selectedRange.previous(anchorDate) },
    onNext = { anchorDate = selectedRange.next(anchorDate) },
    onRangeSelected = { selectedRange = it }
)
```

- [ ] **Step 2: Replace body with agenda/map layout**

Calculate:

```kotlin
val visibleDates = selectedRange.visibleDates(anchorDate, items)
val mapMonth = YearMonth.from(anchorDate)
val monthMapCells = buildMonthMapCells(mapMonth, items)
```

Render a `Box` containing:

- `AgendaList(...)` as a `LazyColumn`.
- `MonthMapOverlay(...)` aligned to `Alignment.BottomCenter`.

Give the agenda list bottom content padding of about `132.dp`.

- [ ] **Step 3: Implement date jump interaction**

Use a `LazyListState` and coroutine scope:

```kotlin
val listState = rememberLazyListState()
val dateIndex = visibleDates.withIndex().associate { it.value to it.index }
```

When a map cell is tapped:

```kotlin
dateIndex[cell.date]?.let { index ->
    scope.launch { listState.animateScrollToItem(index) }
}
```

If the date is not in the current visible range, update `anchorDate = cell.date`.

- [ ] **Step 4: Implement compact month map cells**

Each cell uses `MonthMapCell.visibleColors`.

Rendering rules:

- Empty: neutral surface.
- One colour: full background.
- Multiple colours: split vertical slices by equal weight in a `Row`.
- Overflow: overlay small `+N` text in bottom-right.
- Adjacent-month days: lower alpha.

- [ ] **Step 5: Compile check**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

---

## Task 3: Full Verification and Install

**Files:**
- No code edits expected.

- [ ] **Step 1: Run all unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install debug APK**

Run:

```powershell
.\gradlew.bat :app:installDebug
```

Expected: installed on connected device.

---

## Self-Review

- Spec coverage: range selector, subtle month navigation, agenda readability, compact month map, 20-colour palette, same-day split cells, overflow marker, multi-day coverage, and tests are all covered.
- Placeholder scan: no `TBD`/`TODO` placeholders.
- Type consistency: helper names match between tests, implementation, and UI plan.
