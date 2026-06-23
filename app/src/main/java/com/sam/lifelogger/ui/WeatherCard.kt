package com.sam.lifelogger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.lifelogger.data.WeatherDaily
import com.sam.lifelogger.data.WeatherResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Content for a weather dashboard card — a horizontal row of compact daily forecasts.
 * Shows today + up to 3 more days.
 */
@Composable
fun WeatherDashboardContent(weather: WeatherResponse) {
    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Show today + up to 3 more days (4 total)
        weather.daily.take(4).forEach { day ->
            MiniForecastCard(day, isToday = day.date == todayDateStr)
        }
    }
}

@Composable
private fun MiniForecastCard(day: WeatherDaily, isToday: Boolean) {
    val dayLabel = if (isToday) "Today" else formatDayLabel(day.date)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(72.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = weatherCodeToEmoji(day.weatherCode),
                fontSize = 18.sp
            )
            Text(
                text = day.condition,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = day.temperatureMaxC.toInt().toString() + "\u00B0 / " + day.temperatureMinC.toInt().toString() + "\u00B0",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (day.precipitationProbabilityMax > 0) {
                Text(
                    text = "\uD83D\uDCA7 " + day.precipitationProbabilityMax.toString() + "%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

/** Format a date string (yyyy-MM-dd) into a short day label. */
private fun formatDayLabel(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (dateStr == today) return "Today"
        SimpleDateFormat("EEE", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        dateStr
    }
}

/**
 * Map Open-Meteo weather codes to emoji symbols.
 */
private fun weatherCodeToEmoji(code: Int): String = when (code) {
    0 -> "\u2600\uFE0F"
    1 -> "\uD83C\uDF24\uFE0F"
    2 -> "\u26C5"
    3 -> "\u2601\uFE0F"
    45, 48 -> "\uD83C\uDF2B\uFE0F"
    51, 53, 55 -> "\uD83C\uDF26\uFE0F"
    61, 63, 65 -> "\uD83C\uDF27\uFE0F"
    71, 73, 75 -> "\u2744\uFE0F"
    80, 81, 82 -> "\uD83C\uDF26\uFE0F"
    95, 96, 99 -> "\u26C8\uFE0F"
    else -> "\uD83C\uDF21\uFE0F"
}
