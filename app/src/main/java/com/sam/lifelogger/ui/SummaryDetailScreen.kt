package com.sam.lifelogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.sam.lifelogger.BlackOutlineButton
import com.sam.lifelogger.data.ApiClient
import com.sam.lifelogger.data.SessionNoteEntry
import com.sam.lifelogger.data.SummaryDetail
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDetailScreen(
    date: String,
    onBack: () -> Unit,
    onViewTranscripts: (String) -> Unit,
    onOpenSession: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var title by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summaryHtml by remember { mutableStateOf<String?>(null) }
    var segmentCount by remember { mutableStateOf(0) }
    var totalDuration by remember { mutableStateOf(0.0) }
    var sessions by remember { mutableStateOf<List<SessionNoteEntry>>(emptyList()) }
    var sessionsError by remember { mutableStateOf<String?>(null) }
    var editedAt by remember { mutableStateOf<String?>(null) }
    var isEdited by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Edit-mode state
    var isEditMode by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editBody by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(date) {
        try {
            val json = ApiClient.getSummary(context, date)
            val obj = JSONObject(json)
            if (obj.has("error")) {
                error = obj.getString("error")
            } else {
                val parsed = SummaryDetail.fromJsonObject(obj)
                summary = parsed.summary
                summaryHtml = parsed.summaryHtml
                segmentCount = parsed.segmentCount
                totalDuration = parsed.totalDurationSeconds
                title = parsed.title
                editedAt = parsed.editedAt
                isEdited = parsed.isEdited
            }
        } catch (e: Exception) {
            error = "Could not load summary:\n${e.message}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(date) {
        try {
            val json = ApiClient.getSessionsForDate(context, date)
            sessions = SessionNoteEntry.listFromJson(json)
            sessionsError = null
        } catch (e: Exception) {
            sessions = emptyList()
            sessionsError = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title ?: date)
                        if (title != null) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onViewTranscripts(date) }) {
                        Icon(Icons.AutoMirrored.Filled.List, "View transcripts")
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
                    }
                }
                summary != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Stats bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(
                                    label = "Recordings",
                                    value = "$segmentCount"
                                )
                                StatItem(
                                    label = "Duration",
                                    value = formatDurationShort(totalDuration)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        SessionNotesSection(
                            date = date,
                            sessions = sessions,
                            error = sessionsError,
                            onOpenSession = onOpenSession
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- EDIT / VIEW MODE TOGGLE ---
                        if (isEditMode) {
                            // --- EDIT MODE ---
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Title (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = editBody,
                                onValueChange = { editBody = it },
                                label = { Text("Summary") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 300.dp),
                                minLines = 10
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BlackOutlineButton(
                                    text = if (isSaving) "Saving…" else "Save",
                                    onClick = {
                                        if (isSaving) return@BlackOutlineButton
                                        scope.launch {
                                            isSaving = true
                                            try {
                                                val response = ApiClient.updateSummary(
                                                    context = context,
                                                    date = date,
                                                    markdown = editBody,
                                                    title = editTitle.ifBlank { null }
                                                )
                                                val respObj = JSONObject(response)
                                                val rawEdited = respObj.optString("edited_at", "")
                                                val newEditedAt = if (respObj.has("edited_at") && !respObj.isNull("edited_at") && rawEdited.isNotBlank()) rawEdited else null

                                                summary = editBody
                                                summaryHtml = null
                                                title = editTitle.ifBlank { null }
                                                editedAt = newEditedAt
                                                isEdited = true
                                                isEditMode = false
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(
                                                    message = "Save failed — ${e.message ?: "unknown error"}",
                                                    actionLabel = "Retry",
                                                    duration = SnackbarDuration.Long
                                                )
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = {
                                        isEditMode = false
                                        editTitle = ""
                                        editBody = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isSaving
                                ) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            // --- VIEW MODE ---
                            BlackOutlineButton(
                                text = "Edit",
                                onClick = {
                                    editTitle = title ?: ""
                                    editBody = summary ?: ""
                                    isEditMode = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Edited indicator (only when isEdited && editedAt != null)
                            if (isEdited && editedAt != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                EditedBadge(editedAt = editedAt!!)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            RenderRichText(html = summaryHtml, fallbackMarkdown = summary!!)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // View transcripts button (visible in both modes)
                        OutlinedButton(
                            onClick = { onViewTranscripts(date) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View raw transcripts")
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RenderRichText(html: String?, fallbackMarkdown: String) {
    if (!html.isNullOrBlank()) {
        val textColor = MaterialTheme.colorScheme.onSurface
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                android.widget.TextView(context).apply {
                    setTextColor(textColor.toArgb())
                    textSize = 16f
                }
            },
            update = { textView ->
                textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                textView.setTextColor(textColor.toArgb())
            }
        )
    } else {
        RenderMarkdown(fallbackMarkdown)
    }
}

@Composable
private fun EditedBadge(editedAt: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "Last edited: ${formatEditedAt(editedAt)}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun SessionNotesSection(
    date: String,
    sessions: List<SessionNoteEntry>,
    error: String?,
    onOpenSession: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Session Recordings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        when {
            sessions.isNotEmpty() -> {
                sessions.forEach { session ->
                    SessionNoteCard(
                        session = session,
                        onClick = { onOpenSession(date, session.filename) }
                    )
                }
            }
            error != null -> {
                Text(
                    text = "No processed sessions available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Text(
                    text = "No processed sessions for this date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionNoteCard(
    session: SessionNoteEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = session.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = listOfNotNull(
                    session.type?.replaceFirstChar { it.uppercase() },
                    session.sizeBytes?.let { formatBytes(it) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun SessionNoteEntry.displayTitle(): String {
    val name = filename
        .removeSuffix(".md")
        .replace("_session", "")
        .replace(Regex("^\\d{4}-\\d{2}-\\d{2}_"), "")
        .replace('_', ' ')
        .trim()
    return name.ifBlank { filename }
}

private fun formatBytes(bytes: Long): String =
    if (bytes >= 1024) "${bytes / 1024} KB" else "$bytes B"

/** Parse ISO 8601 timestamp to human-readable "d MMM yyyy, HH:mm" */
private fun formatEditedAt(isoString: String): String {
    return try {
        val odt = OffsetDateTime.parse(isoString)
        odt.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
    } catch (e: Exception) {
        isoString
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Simple markdown-ish renderer for the summary text.
 * Handles headers (###, ####), bold (**text**), and bullet points (- ).
 */
@Composable
fun RenderMarkdown(text: String) {
    val lines = text.split("\n")

    for (line in lines) {
        val trimmed = line.trimEnd()
        when {
            trimmed.isBlank() -> {
                Spacer(modifier = Modifier.height(6.dp))
            }
            trimmed.startsWith("#### ") -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = trimmed.removePrefix("#### "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            trimmed.startsWith("### ") -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            trimmed.startsWith("- **") && trimmed.contains(":**") -> {
                // Bold label bullet: - **Label:** value
                val content = trimmed.removePrefix("- ")
                val boldEnd = content.indexOf(":**")
                if (boldEnd > 0) {
                    val label = content.substring(2, boldEnd) // strip **
                    val rest = content.substring(boldEnd + 3).trim()
                    Row(modifier = Modifier.padding(start = 12.dp, top = 2.dp)) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "$label: ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rest,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    BulletLine(trimmed)
                }
            }
            trimmed.startsWith("  - ") -> {
                // Sub-bullet
                Row(modifier = Modifier.padding(start = 28.dp, top = 2.dp)) {
                    Text("◦ ", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = trimmed.removePrefix("  - ").cleanBold(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            trimmed.startsWith("- ") -> {
                BulletLine(trimmed)
            }
            trimmed.startsWith("* ") -> {
                // Alternative bullet style
                Row(modifier = Modifier.padding(start = 12.dp, top = 2.dp)) {
                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = trimmed.removePrefix("* ").cleanBold(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                Text(
                    text = trimmed.cleanBold(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BulletLine(line: String) {
    Row(modifier = Modifier.padding(start = 12.dp, top = 2.dp)) {
        Text("• ", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = line.removePrefix("- ").cleanBold(),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Strip markdown bold markers for simple display */
private fun String.cleanBold(): String {
    return this.replace("**", "")
}

private fun formatDurationShort(seconds: Double): String {
    val totalMin = (seconds / 60).toInt()
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
