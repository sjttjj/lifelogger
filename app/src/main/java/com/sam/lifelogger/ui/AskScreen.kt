package com.sam.lifelogger.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sam.lifelogger.data.ApiClient
import com.sam.lifelogger.data.AskAudioRecorder
import kotlinx.coroutines.delay
import com.sam.lifelogger.data.AskResponse
import com.sam.lifelogger.data.AskScope
import com.sam.lifelogger.data.Citation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "AskScreen"

/**
 * Full-screen Ask LifeLogger screen.
 *
 * Accepts text or voice questions, sends them to the LLM server,
 * and renders the answer as markdown with citations.
 *
 * Future: tap a summary citation to navigate to that date's summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { AskAudioRecorder(context) }

    // Input state
    var questionText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isRecentOnly by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Result state
    var response by remember { mutableStateOf<AskResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Recording timer state
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // Recording timer tick
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (true) {
                delay(1_000L)
                recordingSeconds++
                if (recordingSeconds >= 60) {
                    // Auto-stop at 60 seconds
                    Toast.makeText(context, "Max recording length reached", Toast.LENGTH_SHORT).show()
                    isRecording = false
                    recorder.stop()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask LifeLogger") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Input Section ──
            item {
                Text(
                    text = "Your Question",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Text input
            item {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Type a question...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSubmitting
                )
            }

            // Action row: mic button + recent chip + submit
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic button
                    FilledTonalIconButton(
                        onClick = {
                            if (isRecording) {
                                // Stop recording and submit
                                isRecording = false
                                val file = recorder.stop()
                                if (file != null) {
                                    submitVoiceQuestion(
                                        context = context,
                                        audioRecorder = recorder,
                                        questionInput = null,
                                        audioFile = file,
                                        isRecentOnly = isRecentOnly,
                                        onSubmitting = { isSubmitting = true },
                                        onResult = { resp -> response = resp; errorMessage = null },
                                        onError = { msg -> errorMessage = msg; response = null },
                                        onDone = { isSubmitting = false }
                                    )
                                }
                            } else {
                                // Check audio permission
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    try {
                                        recorder.start()
                                        isRecording = true
                                        errorMessage = null
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Audio recording permission required", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Record voice question"
                            )
                        }
                    }

                    // Recording timer indicator
                    if (isRecording) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Recording ${recordingSeconds}s / 60s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Recent 30 days chip
                    FilterChip(
                        selected = isRecentOnly,
                        onClick = { isRecentOnly = !isRecentOnly },
                        label = { Text("Recent 30 days") },
                        enabled = !isSubmitting
                    )

                    // Submit button
                    FilledIconButton(
                        onClick = {
                            val trimmed = questionText.trim()
                            if (trimmed.isNotBlank()) {
                                submitTextQuestion(
                                    context = context,
                                    question = trimmed,
                                    isRecentOnly = isRecentOnly,
                                    onSubmitting = { isSubmitting = true },
                                    onResult = { resp -> response = resp; errorMessage = null },
                                    onError = { msg -> errorMessage = msg; response = null },
                                    onDone = { isSubmitting = false }
                                )
                            }
                        },
                        enabled = questionText.trim().isNotBlank() && !isSubmitting
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Submit question")
                    }
                }
            }

            // ── Loading Indicator ──
            if (isSubmitting) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Asking LifeLogger...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Error Message ──
            if (errorMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // ── Answer Section ──
            if (response != null && !isSubmitting) {
                val resp = response!!

                // Transcript (for voice questions)
                if (resp.inputType == "voice" && !resp.transcript.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Transcribed question:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = resp.transcript,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Question shown for context
                item {
                    Text(
                        text = resp.question,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Confidence badge
                if (resp.confidence != null) {
                    item {
                        val confidenceColor = when (resp.confidence.lowercase()) {
                            "high" -> MaterialTheme.colorScheme.tertiary
                            "medium" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = confidenceColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Confidence: ${resp.confidence.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = confidenceColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Answer as markdown
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            RenderMarkdown(resp.answer)
                        }
                    }
                }

                // Search scope info
                if (resp.searched != null) {
                    item {
                        val s = resp.searched
                        val sourceText = s.sources?.joinToString(", ") ?: "all sources"
                        val dateText = buildString {
                            s.startDate?.let { append("$it to ") }
                            s.endDate?.let { append(it) }
                        }
                        Text(
                            text = "Searched: $sourceText" +
                                    (if (dateText.isNotBlank()) " | $dateText" else "") +
                                    (s.resultCount?.let { " | $it results" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Citations ──
                if (!resp.citations.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Citations",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(resp.citations) { citation ->
                        CitationCard(citation)
                    }

                    // Future note about tap behavior
                    item {
                        Text(
                            text = "Future: tap a summary citation to open that day's summary.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ── Empty state (no question asked yet) ──
            if (response == null && !isSubmitting && errorMessage == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "\uD83E\uDD16",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Ask anything about your life",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Type a question or tap the mic to record",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card displaying a single citation.
 * Source-specific icons/colours help the user identify the origin.
 *
 * Future: Make citation cards tappable — e.g. tap a "summary" citation
 * to navigate to that date's summary screen.
 */
@Composable
private fun CitationCard(citation: Citation) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = citationSourceColor(citation.source)
                ) {
                    Text(
                        text = citation.source.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // Date
                if (citation.date != null) {
                    Text(
                        text = citation.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Title
            if (citation.title != null) {
                Text(
                    text = citation.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Snippet
            if (citation.snippet != null) {
                Text(
                    text = citation.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
/** Map citation source to a badge colour. */
private fun citationSourceColor(source: String): androidx.compose.ui.graphics.Color {
    return when (source.lowercase()) {
        "summary" -> MaterialTheme.colorScheme.tertiaryContainer
        "reminder" -> MaterialTheme.colorScheme.primaryContainer
        "whatsapp" -> MaterialTheme.colorScheme.secondaryContainer
        "event" -> MaterialTheme.colorScheme.tertiary
        "task" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

// ── Helper functions ──

/**
 * Submit a text question to the LLM server.
 */
private fun submitTextQuestion(
    context: Context,
    question: String,
    isRecentOnly: Boolean,
    onSubmitting: () -> Unit,
    onResult: (AskResponse) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit
) {
    onSubmitting()
    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
        try {
            val scope = buildScope(isRecentOnly)
            val json = withContext(Dispatchers.IO) {
                ApiClient.askText(context, question, scope)
            }
            val resp = AskResponse.fromJson(json)
            onResult(resp)
        } catch (e: Exception) {
            Log.e(TAG, "Text ask failed", e)
            onError("Failed to get answer: ${e.message ?: "Unknown error"}")
        } finally {
            onDone()
        }
    }
}

/**
 * Submit a voice recording to the LLM server.
 */
private fun submitVoiceQuestion(
    context: Context,
    audioRecorder: AskAudioRecorder,
    questionInput: String?,
    audioFile: java.io.File,
    isRecentOnly: Boolean,
    onSubmitting: () -> Unit,
    onResult: (AskResponse) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit
) {
    onSubmitting()
    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
        try {
            val scope = buildScope(isRecentOnly)
            val json = withContext(Dispatchers.IO) {
                ApiClient.askAudio(
                    context = context,
                    audioFile = audioFile,
                    scope = scope,
                    recordedAtMs = System.currentTimeMillis().toString(),
                    recordedAtIso = java.time.Instant.now().toString(),
                    recordedTimezone = java.time.ZoneId.systemDefault().id
                )
            }
            val resp = AskResponse.fromJson(json)
            onResult(resp)

            // Clean up temp file
            try { audioFile.delete() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Voice ask failed", e)
            onError("Failed to process voice question: ${e.message ?: "Unknown error"}")
        } finally {
            onDone()
        }
    }
}

/** Build an optional AskScope based on the "Recent 30 days" toggle. */
private fun buildScope(isRecentOnly: Boolean): AskScope? {
    if (!isRecentOnly) return null
    val today = LocalDate.now()
    val thirtyDaysAgo = today.minusDays(30)
    return AskScope(
        startDate = thirtyDaysAgo.format(DateTimeFormatter.ISO_LOCAL_DATE),
        endDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
    )
}
