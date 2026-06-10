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
            sourceType = LifeCalendarItemType.Reminder,
            title = "Conference travel",
            description = null,
            startUtc = "2026-06-17T23:00:00Z",
            startLocal = "2026-06-18T09:00:00+10:00",
            endUtc = "2026-06-20T13:59:00Z",
            endLocal = "2026-06-20T23:59:00+10:00",
            status = "pending",
            needsReview = false
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
            sourceType = LifeCalendarItemType.Reminder,
            title = title,
            description = null,
            startUtc = "${date.minusDays(1)}T23:00:00Z",
            startLocal = "${date}T09:00:00+10:00",
            endUtc = null,
            endLocal = null,
            status = "pending",
            needsReview = false
        )
}
