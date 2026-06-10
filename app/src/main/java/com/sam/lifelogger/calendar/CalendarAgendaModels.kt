package com.sam.lifelogger.calendar

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

enum class CalendarAgendaRange(val label: String) {
    Week("W"),
    Month("M"),
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
    val requiredCells = leadingDays + month.lengthOfMonth()
    val totalCells = if (requiredCells <= 35) 35 else 42

    return (0 until totalCells).map { index ->
        val date = start.plusDays(index.toLong())
        val dayItems = items.filter { date in it.coveredDates() }
        MonthMapCell(
            date = date,
            inSelectedMonth = YearMonth.from(date) == month,
            items = dayItems,
            visibleColors = dayItems.take(4).map { CalendarItemColorPalette.colorFor(it.sourceId) },
            overflowCount = (dayItems.size - 4).coerceAtLeast(0)
        )
    }
}
