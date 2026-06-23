package com.sam.lifelogger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderNotificationConfig
import com.sam.lifelogger.data.ReminderNotificationOffset
import com.sam.lifelogger.data.ReminderNotificationOffsetUnit
import com.sam.lifelogger.data.ReminderPatch
import com.sam.lifelogger.data.ReminderSyncManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private data class NotificationOffsetInput(
    val amount: String,
    val unit: ReminderNotificationOffsetUnit
)

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

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("task") }
    var status by remember { mutableStateOf("pending") }
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var endDateText by remember { mutableStateOf("") }
    var notificationMode by remember { mutableStateOf("At time") }
    var notificationOffsets by remember {
        mutableStateOf(
            listOf(
                NotificationOffsetInput("1", ReminderNotificationOffsetUnit.Days),
                NotificationOffsetInput("1", ReminderNotificationOffsetUnit.Hours),
                NotificationOffsetInput("", ReminderNotificationOffsetUnit.Hours)
            )
        )
    }
    var kindExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var seriesAction by remember { mutableStateOf<String?>(null) }
    var seriesBusy by remember { mutableStateOf(false) }

    LaunchedEffect(reminderId) {
        isLoading = true
        error = null
        val loaded = ReminderSyncManager.getCachedReminder(context, reminderId)
            ?: ReminderSyncManager.syncUpcomingOrUseCache(context).firstOrNull { it.id == reminderId }
        if (loaded == null) {
            error = "Reminder not found"
        } else {
            reminder = loaded
            title = loaded.title
            description = loaded.description.orEmpty()
            kind = loaded.kind
            status = loaded.status
            (loaded.scheduledAtLocal ?: loaded.recurrenceOccurrenceLocal)?.let {
                runCatching {
                    val parsed = OffsetDateTime.parse(it)
                    dateText = parsed.toLocalDate().toString()
                    if (loaded.schedulePrecision == "datetime") {
                        timeText = parsed.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    }
                }
            }
            loaded.endAtLocal?.let {
                runCatching {
                    endDateText = OffsetDateTime.parse(it).toLocalDate().toString()
                }
            }
            when (val config = ReminderNotificationConfig.fromReminder(loaded.scheduledAtUtc, loaded.notificationJobs)) {
                ReminderNotificationConfig.None -> notificationMode = "None"
                ReminderNotificationConfig.AtTime -> notificationMode = "At time"
                is ReminderNotificationConfig.Custom -> {
                    notificationMode = "Custom"
                    notificationOffsets = config.offsets.toOffsetInputs()
                }
            }
        }
        isLoading = false
    }

    fun save() {
        if (isSaving) return
        if (title.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Title is required") }
            return
        }
        if (reminder?.needsReview == true && dateText.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Choose a date before confirming") }
            return
        }

        scope.launch {
            isSaving = true
            try {
                val zone = ZoneId.systemDefault()
                val timezone = zone.id
                var scheduledLocal: String? = null
                var scheduledUtc: String? = null
                var endLocal: String? = null
                var endUtc: String? = null
                var precision = "date"
                var usedDefaultTime = false

                if (dateText.isNotBlank()) {
                    val date = LocalDate.parse(dateText)
                    val time = if (timeText.isNotBlank()) {
                        precision = "datetime"
                        LocalTime.parse(timeText)
                    } else {
                        usedDefaultTime = true
                        LocalTime.of(9, 0)
                    }
                    val zoned = date.atTime(time).atZone(zone)
                    scheduledLocal = zoned.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    scheduledUtc = zoned.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    if (endDateText.isNotBlank()) {
                        val endDate = LocalDate.parse(endDateText)
                        if (endDate.isBefore(date)) {
                            snackbarHostState.showSnackbar("End date cannot be before start date")
                            isSaving = false
                            return@launch
                        }
                        val endZoned = endDate.atTime(LocalTime.of(23, 59)).atZone(zone)
                        endLocal = endZoned.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        endUtc = endZoned.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    }
                } else if (endDateText.isNotBlank()) {
                    snackbarHostState.showSnackbar("Choose a start date before adding an end date")
                    isSaving = false
                    return@launch
                }

                val notificationConfig = when (notificationMode) {
                    "None" -> ReminderNotificationConfig.None
                    "At time" -> ReminderNotificationConfig.AtTime
                    else -> {
                        val offsets = notificationOffsets.mapNotNull { input ->
                            val amount = input.amount.trim().toIntOrNull()
                            if (amount != null && amount > 0) {
                                ReminderNotificationOffset(amount, input.unit)
                            } else {
                                null
                            }
                        }
                        if (offsets.isEmpty()) {
                            snackbarHostState.showSnackbar("Add at least one custom notification or choose None")
                            isSaving = false
                            return@launch
                        }
                        ReminderNotificationConfig.Custom(offsets)
                    }
                }
                val notificationReplaceRequest = notificationConfig.toRequest(
                    scheduledAtUtc = scheduledUtc,
                    title = title.trim(),
                    body = description.ifBlank { null }
                )

                val confirmingReview = reminder?.needsReview == true && scheduledUtc != null
                ReminderSyncManager.patchAndResync(
                    context = context,
                    id = reminderId,
                    patch = ReminderPatch(
                        title = title.trim(),
                        description = description.ifBlank { null },
                        kind = kind,
                        status = status,
                        needsReview = if (confirmingReview) false else null,
                        timezone = timezone,
                        scheduledAtLocal = scheduledLocal,
                        scheduledAtUtc = scheduledUtc,
                        endAtLocal = endLocal,
                        endAtUtc = endUtc,
                        clearEndAt = reminder?.endAtLocal != null && endDateText.isBlank(),
                        schedulePrecision = precision,
                        usedDefaultTime = usedDefaultTime
                    ),
                    notificationReplaceRequest = notificationReplaceRequest
                )
                onSaved()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Save failed: ${e.message ?: "unknown error"}")
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reminder?.needsReview == true) "Review Reminder" else "Edit Reminder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { save() }, enabled = !isSaving) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
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
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (reminder?.needsReview == true) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "This reminder needs review. Check the details and choose a date before confirming.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        description,
                        { description = it },
                        label = { Text("Description") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownField(
                        label = "Kind",
                        value = kind,
                        expanded = kindExpanded,
                        options = listOf("task", "event", "shopping", "fact", "bill", "other"),
                        onExpandedChange = { kindExpanded = it },
                        onSelected = { kind = it; kindExpanded = false }
                    )

                    DatePickerField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = "Date"
                    )
                    TimePickerField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = "Time (optional)"
                    )
                    DatePickerField(
                        value = endDateText,
                        onValueChange = { endDateText = it },
                        label = "End date (optional)"
                    )
                    NotificationModeField(
                        value = notificationMode,
                        onSelected = { notificationMode = it }
                    )
                    if (notificationMode == "Custom") {
                        NotificationOffsetFields(
                            offsets = notificationOffsets,
                            onChange = { notificationOffsets = it }
                        )
                    }

                    DropdownField(
                        label = "Status",
                        value = status,
                        expanded = statusExpanded,
                        options = listOf("pending", "done", "cancelled"),
                        onExpandedChange = { statusExpanded = it },
                        onSelected = { status = it; statusExpanded = false }
                    )

                    if (reminder?.isRecurring == true) {
                        RepeatsSection(
                            reminder = reminder,
                            busy = seriesBusy,
                            onAction = { seriesAction = it }
                        )
                    }
                    SeriesActionDialog(
                        action = seriesAction,
                        reminder = reminder,
                        onDismiss = { seriesAction = null },
                        onConfirm = { action ->
                            seriesBusy = true
                            scope.launch {
                                try {
                                    val seriesId = reminder?.recurrenceSeriesId
                                    when (action) {
                                        "cancel_occurrence" -> ReminderSyncManager.cancelReminder(context, reminder!!.id)
                                        "stop_series" -> seriesId?.let { ReminderSyncManager.stopRecurrenceSeries(context, it) }
                                        "archive_series" -> seriesId?.let { ReminderSyncManager.archiveRecurrenceSeries(context, it) }
                                    }
                                    seriesAction = null
                                    onSaved()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Action failed: ${e.message ?: "error"}")
                                } finally {
                                    seriesBusy = false
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { save() }, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isSaving) "Saving..." else "Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationModeField(
    value: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    DropdownField(
        label = "Notifications",
        value = value,
        expanded = expanded,
        options = listOf("None", "At time", "Custom"),
        onExpandedChange = { expanded = it },
        onSelected = {
            onSelected(it)
            expanded = false
        }
    )
}

@Composable
private fun NotificationOffsetFields(
    offsets: List<NotificationOffsetInput>,
    onChange: (List<NotificationOffsetInput>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Custom notifications before start time",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        offsets.take(3).forEachIndexed { index, offset ->
            NotificationOffsetRow(
                index = index,
                offset = offset,
                onChange = { updated ->
                    onChange(offsets.toMutableList().also { it[index] = updated })
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationOffsetRow(
    index: Int,
    offset: NotificationOffsetInput,
    onChange: (NotificationOffsetInput) -> Unit
) {
    var unitExpanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = offset.amount,
            onValueChange = { onChange(offset.copy(amount = it.filter(Char::isDigit))) },
            label = { Text("Reminder ${index + 1}") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        ExposedDropdownMenuBox(
            expanded = unitExpanded,
            onExpandedChange = { unitExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = offset.unit.label.replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Unit") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false }
            ) {
                ReminderNotificationOffsetUnit.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(unit.label.replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            onChange(offset.copy(unit = unit))
                            unitExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    options: List<String>,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = value.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                    onClick = { onSelected(option) }
                )
            }
        }
    }
}

private fun List<ReminderNotificationOffset>.toOffsetInputs(): List<NotificationOffsetInput> {
    val existing = map { NotificationOffsetInput(it.amount.toString(), it.unit) }
    val defaults = listOf(
        NotificationOffsetInput("1", ReminderNotificationOffsetUnit.Days),
        NotificationOffsetInput("1", ReminderNotificationOffsetUnit.Hours),
        NotificationOffsetInput("", ReminderNotificationOffsetUnit.Hours)
    )
    return (existing + defaults).take(3)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    if (showPicker) {
        val initialDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
        val millis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val date = Instant.ofEpochMilli(selected)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onValueChange(date.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (editing) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { editing = false }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Pick date"
                        )
                    }
                }
            )
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                readOnly = true,
                enabled = true,
                trailingIcon = {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit as text")
                    }
                }
            )
        }
        IconButton(
            onClick = { showPicker = true }
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "Open date picker",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    if (showPicker) {
        val (h, m) = runCatching {
            val parts = value.split(":")
            parts[0].toInt() to parts[1].toInt()
        }.getOrElse { 9 to 0 }
        val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Pick time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange("%02d:%02d".format(state.hour, state.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (editing) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { editing = false }) {
                        Icon(Icons.Default.Schedule, contentDescription = "Pick time")
                    }
                }
            )
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                readOnly = true,
                enabled = true,
                trailingIcon = {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit as text")
                    }
                }
            )
        }
        IconButton(
            onClick = { showPicker = true }
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = "Open time picker",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RepeatsSection(reminder: Reminder?, busy: Boolean, onAction: (String) -> Unit) {
    val recurrence = reminder?.recurrence
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Repeats" + (recurrence?.frequency?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            reminder?.recurrenceText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This is one scheduled occurrence. These actions affect the whole series. Marking this done or cancelled does not stop the series.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAction("cancel_occurrence") }, enabled = !busy) { Text("Cancel this time") }
                OutlinedButton(onClick = { onAction("stop_series") }, enabled = !busy) { Text("Stop repeats") }
                OutlinedButton(onClick = { onAction("archive_series") }, enabled = !busy) { Text("Archive all repeats") }
            }
        }
    }
}

@Composable
private fun SeriesActionDialog(
    action: String?,
    reminder: Reminder?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (action == null || reminder == null) return
    val (title, message) = when (action) {
        "cancel_occurrence" -> "Cancel this occurrence" to "Cancel only this scheduled occurrence (${reminder.title})? The series will keep repeating."
        "stop_series" -> "Stop repeats" to "Stop future occurrences of ${reminder.title}? Past occurrences are kept."
        "archive_series" -> "Archive all repeats" to "Archive the whole series for ${reminder.title}? It will be hidden from the app."
        else -> "Confirm" to "Are you sure?"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(action) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

