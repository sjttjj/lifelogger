package com.sam.lifelogger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.calendar.CalendarAgendaRange
import com.sam.lifelogger.calendar.CalendarItemColor
import com.sam.lifelogger.calendar.CalendarItemColorPalette
import com.sam.lifelogger.calendar.LifeCalendarItem
import com.sam.lifelogger.calendar.MonthMapCell
import com.sam.lifelogger.calendar.buildMonthMapCells
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderPatch
import com.sam.lifelogger.data.ReminderSyncManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CalendarViewMode { Calendar, NeedsReview }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeCalendarScreen(
    onBack: () -> Unit,
    onOpenReminder: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var selectedRange by remember { mutableStateOf(CalendarAgendaRange.Month) }
    var anchorDate by remember { mutableStateOf(LocalDate.now()) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.Calendar) }
    var items by remember { mutableStateOf<List<LifeCalendarItem>>(emptyList()) }
    var needsReviewItems by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var cancelCandidate by remember { mutableStateOf<Reminder?>(null) }
    var monthMapHeight by remember { mutableStateOf(0.dp) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var dismissedIds by remember { mutableStateOf(emptySet<Long>()) }

    fun markDone(item: LifeCalendarItem) {
        scope.launch {
            dismissedIds = dismissedIds + item.sourceId
            try {
                ReminderSyncManager.patchAndResync(
                    context, item.sourceId,
                    ReminderPatch(status = "done")
                )
                val result = snackbarHostState.showSnackbar(
                    message = "\"${item.title}\" marked as done",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    dismissedIds = dismissedIds - item.sourceId
                    ReminderSyncManager.patchAndResync(
                        context, item.sourceId,
                        ReminderPatch(status = "pending")
                    )
                }
            } catch (e: Exception) {
                dismissedIds = dismissedIds - item.sourceId
            }
        }
    }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                items = ReminderSyncManager.syncUpcomingOrUseCache(context)
                    .filter { it.status.equals("pending", ignoreCase = true) && it.scheduledAtLocal != null }
                    .map { LifeCalendarItem.fromReminder(it) }
                needsReviewItems = ReminderSyncManager.syncNeedsReview(context)
            } catch (e: Exception) {
                error = e.message ?: "Could not load calendar"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val visibleDates = remember(selectedRange, anchorDate, items) {
        selectedRange.visibleDates(anchorDate = anchorDate, items = items)
    }
    val mapMonth = remember(anchorDate) { YearMonth.from(anchorDate) }
    val today = remember { LocalDate.now() }
    val monthMapCells = remember(mapMonth, items) {
        buildMonthMapCells(month = mapMonth, items = items)
    }
    val dateIndex = remember(visibleDates) {
        visibleDates.withIndex().associate { it.value to it.index }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CalendarRangeHeader(
                selectedRange = selectedRange,
                anchorDate = anchorDate,
                viewMode = viewMode,
                onPrevious = { anchorDate = selectedRange.previous(anchorDate) },
                onNext = { anchorDate = selectedRange.next(anchorDate) },
                onRangeSelected = { selectedRange = it },
                onViewModeChange = { viewMode = it }
            )

            AnimatedContent(
                targetState = viewMode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "view_mode_transition"
            ) { currentView ->
                when (currentView) {
                    CalendarViewMode.Calendar -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when {
                                isLoading -> {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }
                                error != null -> {
                                    Text(
                                        text = error ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(24.dp)
                                    )
                                }
                                else -> {
                                    AgendaList(
                                        dates = visibleDates,
                                        items = items.filter { it.sourceId !in dismissedIds },
                                        listState = listState,
                                        onOpenReminder = onOpenReminder,
                                        bottomPadding = monthMapHeight,
                                        onMarkDone = { item -> markDone(item) }
                                    )
                                }
                            }

                            MonthMapOverlay(
                                month = mapMonth,
                                cells = monthMapCells,
                                today = today,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .onSizeChanged { size ->
                                        monthMapHeight = with(density) { size.height.toDp() }
                                    },
                                onDateSelected = { date ->
                                    val index = dateIndex[date]
                                    if (index != null) {
                                        scope.launch { listState.animateScrollToItem(index) }
                                    } else {
                                        anchorDate = date
                                    }
                                }
                            )
                        }
                    }

                    CalendarViewMode.NeedsReview -> {
                        NeedsReviewView(
                            items = needsReviewItems,
                            isLoading = isLoading,
                            error = error,
                            onOpenReminder = onOpenReminder,
                            onCancelReminder = { reminder -> cancelCandidate = reminder },
                            onRetry = { load() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRangeHeader(
    selectedRange: CalendarAgendaRange,
    anchorDate: LocalDate,
    viewMode: CalendarViewMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRangeSelected: (CalendarAgendaRange) -> Unit,
    onViewModeChange: (CalendarViewMode) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date navigation [<] June 2026 [>]
            IconButton(onClick = onPrevious, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = rangeLabel(selectedRange, anchorDate),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // W / M / All pills
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                CalendarAgendaRange.entries.forEach { range ->
                    val isEnabled = viewMode == CalendarViewMode.Calendar
                    Surface(
                        onClick = { if (isEnabled) onRangeSelected(range) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selectedRange == range && isEnabled) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        enabled = isEnabled
                    ) {
                        Text(
                            text = range.label,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedRange == range && isEnabled) FontWeight.Bold else FontWeight.Normal,
                            color = if (isEnabled) {
                                if (selectedRange == range) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }

            // Calendar / Needs Review toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                CalendarViewMode.entries.forEach { mode ->
                    val isSelected = viewMode == mode
                    Surface(
                        onClick = { onViewModeChange(mode) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    ) {
                        Icon(
                            imageVector = if (mode == CalendarViewMode.Calendar) Icons.Default.CalendarMonth else Icons.Default.Edit,
                            contentDescription = if (mode == CalendarViewMode.Calendar) "Calendar view" else "Needs Review view",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp).size(16.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaList(
    dates: List<LocalDate>,
    items: List<LifeCalendarItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenReminder: (Long) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onMarkDone: ((LifeCalendarItem) -> Unit)? = null
) {
    if (dates.isEmpty()) {
        Text("No calendar items", modifier = Modifier.padding(24.dp))
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        dates.forEach { date ->
            item(key = date.toString()) {
                DateAgendaGroup(
                    date = date,
                    items = items.onDate(date),
                    onOpenReminder = onOpenReminder,
                    onMarkDone = onMarkDone
                )
            }
        }
    }
}

@Composable
private fun DateAgendaGroup(
    date: LocalDate,
    items: List<LifeCalendarItem>,
    onOpenReminder: (Long) -> Unit,
    onMarkDone: ((LifeCalendarItem) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        DateBadge(date)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items.forEach { item ->
                if (onMarkDone != null) {
                    SwipeToDismissBox(
                        state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    onMarkDone(item)
                                    false // stay in default state, we remove via dismissedIds
                                } else {
                                    false // don't allow other directions
                                }
                            }
                        ),
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Mark as done",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false
                    ) {
                        CalendarItemCard(item = item, onOpenReminder = onOpenReminder)
                    }
                } else {
                    CalendarItemCard(item = item, onOpenReminder = onOpenReminder)
                }
            }
        }
    }
}

@Composable
private fun DateBadge(date: LocalDate) {
    Surface(
        modifier = Modifier.width(46.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEE")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarItemCard(
    item: LifeCalendarItem,
    onOpenReminder: (Long) -> Unit
) {
    val accent = CalendarItemColorPalette.colorFor(item.sourceId).toComposeColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenReminder(item.sourceId) }
    ) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = itemTimeLabel(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthMapOverlay(
    month: YearMonth,
    cells: List<MonthMapCell>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onDateSelected: (LocalDate) -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Month map",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            WeekdayHeader()
            cells.chunked(7).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row.forEach { cell ->
                        MonthMapDayCell(
                            cell = cell,
                            isToday = cell.date == today,
                            modifier = Modifier.weight(1f),
                            onClick = { onDateSelected(cell.date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthMapDayCell(
    cell: MonthMapCell,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hasItems = cell.visibleColors.isNotEmpty()
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .aspectRatio(1.28f)
            .alpha(if (cell.inSelectedMonth) 1f else 0.38f)
            .clip(shape)
            .background(if (hasItems) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = hasItems, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (hasItems) {
            Row(modifier = Modifier.fillMaxSize()) {
                cell.visibleColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(color.toComposeColor())
                    )
                }
            }
        }
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (hasItems) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (cell.overflowCount > 0) {
            Text(
                text = "+${cell.overflowCount}",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
        if (isToday) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(width = 3.dp, color = Color.White, shape = shape)
            )
        }
    }
}

@Composable
private fun NeedsReviewView(
    items: List<Reminder>,
    isLoading: Boolean,
    error: String?,
    onOpenReminder: (Long) -> Unit,
    onCancelReminder: (Reminder) -> Unit,
    onRetry: () -> Unit
) {
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
        items.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No reminders to review",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { reminder ->
                    NeedsReviewCard(
                        reminder = reminder,
                        onClick = { onOpenReminder(reminder.id) },
                        onCancel = { onCancelReminder(reminder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NeedsReviewCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = {}, label = { Text(reminder.kind) })
            }
            reminder.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatReminderTime(reminder),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

private fun formatReminderTime(reminder: Reminder): String {
    val scheduled = reminder.scheduledAtLocal ?: return "No date set"
    return runCatching {
        val dateTime = OffsetDateTime.parse(scheduled)
        if (reminder.schedulePrecision == "date") {
            dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
    }.getOrDefault(scheduled)
}

private fun List<LifeCalendarItem>.onDate(date: LocalDate): List<LifeCalendarItem> =
    filter { date in it.coveredDates() }

private fun rangeLabel(range: CalendarAgendaRange, anchorDate: LocalDate): String =
    when (range) {
        CalendarAgendaRange.Week -> {
            val start = anchorDate.minusDays((anchorDate.dayOfWeek.value % 7).toLong())
            val end = start.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("dMMM").withLocale(Locale.ENGLISH)
            "${start.format(fmt)} - ${end.format(fmt)}"
        }
        CalendarAgendaRange.Month ->
            YearMonth.from(anchorDate).format(DateTimeFormatter.ofPattern("MMM yyyy").withLocale(Locale.ENGLISH))
        CalendarAgendaRange.All -> "All"
    }

private fun itemTimeLabel(item: LifeCalendarItem): String {
    val start = parseOffset(item.startLocal) ?: return "No date set"
    val end = parseOffset(item.endLocal)
    val time = start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (end != null && end.toLocalDate().isAfter(start.toLocalDate())) {
        "$time - ${end.toLocalDate().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"))}"
    } else {
        time
    }
}

private fun parseOffset(value: String?): OffsetDateTime? =
    value?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

private fun CalendarItemColor.toComposeColor(): Color = Color(hex)
