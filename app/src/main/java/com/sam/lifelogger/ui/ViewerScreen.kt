package com.sam.lifelogger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.data.ApiClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onDateSelected: (String) -> Unit,
    onSearch: () -> Unit
) {
    val context = LocalContext.current

    var dates by remember { mutableStateOf<List<DateEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val json = ApiClient.getDates(context)
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("dates")
            val list = mutableListOf<DateEntry>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    DateEntry(
                        date = item.getString("date"),
                        segmentCount = item.getInt("segment_count"),
                        totalDuration = item.getDouble("total_duration_seconds"),
                        hasSummary = item.getBoolean("has_summary"),
                        title = if (item.isNull("title")) null else item.optString("title", "").ifBlank { null }
                    )
                )
            }
            dates = list
            error = null
        } catch (e: Exception) {
            error = "Could not connect to server:\n${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Group dates by year and month
    val groupedDates = remember(dates) {
        dates.groupBy { it.date.substring(0, 4) } // group by year
            .mapValues { (_, yearDates) ->
                yearDates.groupBy { it.date.substring(5, 7) } // group by month within year
            }
            .toSortedMap(compareByDescending { it }) // newest year first
    }

    // Track which years and months are expanded
    // Default: current year and current month expanded
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
    val currentMonth = String.format("%02d", java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)

    val expandedYears = remember { mutableStateMapOf(currentYear to true) }
    val expandedMonths = remember { mutableStateMapOf("$currentYear-$currentMonth" to true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Life Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Make sure you're on the same WiFi as your server.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                dates.isEmpty() -> {
                    Text(
                        text = "No recordings yet.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedDates.forEach { (year, monthMap) ->
                            val yearExpanded = expandedYears[year] == true

                            // Year header
                            item(key = "year-$year") {
                                YearHeader(
                                    year = year,
                                    entryCount = monthMap.values.sumOf { it.size },
                                    isExpanded = yearExpanded,
                                    onClick = { expandedYears[year] = !yearExpanded }
                                )
                            }

                            if (yearExpanded) {
                                val sortedMonths = monthMap.toSortedMap(compareByDescending { it })
                                sortedMonths.forEach { (month, entries) ->
                                    val monthKey = "$year-$month"
                                    val monthExpanded = expandedMonths[monthKey] == true
                                    val monthName = getMonthName(month)

                                    // Month header
                                    item(key = "month-$monthKey") {
                                        MonthHeader(
                                            monthName = monthName,
                                            entryCount = entries.size,
                                            isExpanded = monthExpanded,
                                            onClick = { expandedMonths[monthKey] = !monthExpanded }
                                        )
                                    }

                                    if (monthExpanded) {
                                        items(entries, key = { "entry-${it.date}" }) { entry ->
                                            DateCard(
                                                entry = entry,
                                                onClick = { onDateSelected(entry.date) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearHeader(year: String, entryCount: Int, isExpanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = year,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$entryCount entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MonthHeader(monthName: String, entryCount: Int, isExpanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$entryCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DateCard(entry: DateEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .clickable(onClick = onClick),
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
                    if (entry.title != null) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = formatDatePretty(entry.date),
                        style = if (entry.title != null) MaterialTheme.typography.bodySmall
                        else MaterialTheme.typography.titleSmall,
                        fontWeight = if (entry.title == null) FontWeight.Bold else FontWeight.Normal,
                        color = if (entry.title != null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (entry.hasSummary) {
                    Text(
                        text = "\uD83D\uDCC4",
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${entry.segmentCount} recordings \u00B7 ${formatDuration(entry.totalDuration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDatePretty(dateStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val outputFormat = SimpleDateFormat("EEE, dd-MMM-yyyy", Locale.ENGLISH)
        val date = inputFormat.parse(dateStr) ?: return dateStr
        outputFormat.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

private fun getMonthName(monthNum: String): String {
    val months = mapOf(
        "01" to "January", "02" to "February", "03" to "March",
        "04" to "April", "05" to "May", "06" to "June",
        "07" to "July", "08" to "August", "09" to "September",
        "10" to "October", "11" to "November", "12" to "December"
    )
    return months[monthNum] ?: monthNum
}

private fun formatDuration(seconds: Double): String {
    val totalMin = (seconds / 60).toInt()
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

data class DateEntry(
    val date: String,
    val segmentCount: Int,
    val totalDuration: Double,
    val hasSummary: Boolean,
    val title: String? = null
)