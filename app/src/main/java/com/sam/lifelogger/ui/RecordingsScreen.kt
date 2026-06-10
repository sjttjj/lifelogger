package com.sam.lifelogger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.lifelogger.data.AppDatabase
import com.sam.lifelogger.data.RecordingEntity
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val dao = remember { db.recordingDao() }
    val scope = rememberCoroutineScope()

    var recordings by remember { mutableStateOf<List<RecordingEntity>>(emptyList()) }

    // Load recordings once when this screen is first shown (newest first via DAO)
    LaunchedEffect(Unit) {
        recordings = dao.getAll()
    }

    var expandedRecordingId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                handleDeleteAllClick(
                                    recordings = recordings,
                                    dao = dao,
                                    onListUpdated = { recordings = it }
                                )
                            }
                        }
                    ) {
                        Text("Delete all")
                    }
                }
            )
        }
    ) { padding ->

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No recordings yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(recordings, key = { it.id }) { rec ->
                    val isExpanded = expandedRecordingId == rec.id
                    val file = remember(rec.filePath) { File(rec.filePath) }
                    val metadata = remember(rec.type, rec.sessionId) {
                        RecordingMetadataFormatter.format(rec.type, rec.sessionId)
                    }

                    Column {
                        // Header row — always visible, tappable to expand/collapse/switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isExpanded) {
                                        expandedRecordingId = null
                                    } else {
                                        expandedRecordingId = rec.id
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(text = file.name)

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MetadataBadge(
                                        text = metadata.typeLabel,
                                        kind = metadata.typeLabel
                                    )
                                    MetadataBadge(
                                        text = metadata.detailLabel,
                                        kind = if (metadata.detailLabel == "Missing session ID") {
                                            "Warning"
                                        } else {
                                            metadata.typeLabel
                                        },
                                        monospace = metadata.typeLabel == "Session" &&
                                            metadata.detailLabel.startsWith("Session ")
                                    )
                                }
                            }

                            UploadStatusChip(rec)

                            Text(
                                text = "${file.length() / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        handleDeleteClick(
                                            rec = rec,
                                            file = file,
                                            dao = dao,
                                            expandedId = expandedRecordingId,
                                            onExpandedCleared = { expandedRecordingId = null },
                                            onListUpdated = { recordings = it }
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete recording"
                                )
                            }
                        }

                        // Inline player — only rendered when this recording is expanded
                        if (isExpanded) {
                            RecordingPlayer(
                                file = file,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                            )
                        }

                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String, kind: String, monospace: Boolean = false) {
    val color = when (kind) {
        "Session" -> MaterialTheme.colorScheme.tertiaryContainer
        "Reminder" -> MaterialTheme.colorScheme.secondaryContainer
        "Warning" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (kind) {
        "Session" -> MaterialTheme.colorScheme.onTertiaryContainer
        "Reminder" -> MaterialTheme.colorScheme.onSecondaryContainer
        "Warning" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = color,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            fontFamily = if (monospace) FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- Upload status chip ---

@Composable
private fun UploadStatusChip(rec: RecordingEntity) {
    val (label, color) = when {
        rec.uploaded -> "Uploaded" to MaterialTheme.colorScheme.secondaryContainer
        rec.uploadAttempts == 0 -> "Pending" to MaterialTheme.colorScheme.primaryContainer
        else -> "Retrying…" to MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// --- Helpers ---

private suspend fun handleDeleteClick(
    rec: RecordingEntity,
    file: File,
    dao: com.sam.lifelogger.data.RecordingDao,
    expandedId: Long?,
    onExpandedCleared: () -> Unit,
    onListUpdated: (List<RecordingEntity>) -> Unit
) {
    if (expandedId == rec.id) {
        onExpandedCleared()
    }

    runCatching {
        if (file.exists()) {
            file.delete()
        }
    }

    dao.delete(rec)
    onListUpdated(dao.getAll())
}

private suspend fun handleDeleteAllClick(
    recordings: List<RecordingEntity>,
    dao: com.sam.lifelogger.data.RecordingDao,
    onListUpdated: (List<RecordingEntity>) -> Unit
) {
    recordings.forEach { rec ->
        runCatching {
            val f = File(rec.filePath)
            if (f.exists()) f.delete()
        }
    }

    dao.deleteAll()
    onListUpdated(dao.getAll())
}
