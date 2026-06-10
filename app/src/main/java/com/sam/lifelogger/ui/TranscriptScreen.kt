package com.sam.lifelogger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.data.ApiClient
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    date: String,
    searchQuery: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var segments by remember { mutableStateOf<List<TranscriptSegment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(date) {
        try {
            android.util.Log.d("TranscriptScreen", "Loading transcripts for date=$date, searchQuery=$searchQuery")
            val json = ApiClient.getTranscripts(context, date)
            android.util.Log.d("TranscriptScreen", "Got response: ${json.take(200)}")
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("segments")
            val list = mutableListOf<TranscriptSegment>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    TranscriptSegment(
                        id = item.getInt("id"),
                        filename = item.optString("filename", ""),
                        createdAt = item.optString("created_at", ""),
                        durationSeconds = item.optDouble("duration_seconds", 0.0),
                        text = item.optString("text", ""),
                        isNoise = item.optBoolean("is_noise", false)
                    )
                )
            }
            segments = list
            error = null

        } catch (e: Exception) {
            android.util.Log.e("TranscriptScreen", "Failed to load transcripts", e)
            error = "Could not load transcripts:\n${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Auto-scroll to matching segment after list renders
    LaunchedEffect(segments, searchQuery) {
        if (!searchQuery.isNullOrBlank() && segments.isNotEmpty()) {
            val matchIndex = segments.indexOfFirst {
                it.text.contains(searchQuery, ignoreCase = true)
            }
            if (matchIndex >= 0) {
                kotlinx.coroutines.delay(300) // wait for LazyColumn to render
                listState.animateScrollToItem(matchIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!searchQuery.isNullOrBlank()) {
                        Text("Transcripts — \"$searchQuery\"")
                    } else {
                        Text("Transcripts — $date")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                    Text(
                        text = error!!,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                segments.isEmpty() -> {
                    Text(
                        text = "No transcripts for this date.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(segments) { segment ->
                            TranscriptCard(
                                segment = segment,
                                searchQuery = searchQuery
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptCard(segment: TranscriptSegment, searchQuery: String? = null) {
    val timeStr = segment.createdAt
        .substringAfter("T")
        .substringBefore("+")
        .substringBefore(".")
        .take(5)

    val durationMin = (segment.durationSeconds / 60).toInt()
    val hasMatch = !searchQuery.isNullOrBlank() &&
            segment.text.contains(searchQuery, ignoreCase = true)

    var isExpanded by remember { mutableStateOf(!hasMatch) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasMatch) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else if (segment.isNoise) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    if (hasMatch) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Match") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = "${durationMin}m · ${segment.filename}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (segment.isNoise) {
                Text(
                    text = "(noise / no speech detected)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (hasMatch && !searchQuery.isNullOrBlank()) {
                if (isExpanded) {
                    // Show full text with highlighting
                    Text(
                        text = highlightMatches(segment.text, searchQuery),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Show excerpt around the match
                    val excerpt = getMatchExcerpt(segment.text, searchQuery, contextChars = 150)
                    Text(
                        text = highlightMatches(excerpt, searchQuery),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { isExpanded = true }) {
                        Text("Show full transcript")
                    }
                }
            } else {
                Text(
                    text = segment.text.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun getMatchExcerpt(text: String, query: String, contextChars: Int = 150): String {
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val idx = lowerText.indexOf(lowerQuery)
    if (idx < 0) return text.take(300)

    val start = maxOf(0, idx - contextChars)
    val end = minOf(text.length, idx + query.length + contextChars)

    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < text.length) "..." else ""

    return "$prefix${text.substring(start, end)}$suffix"
}

@Composable
private fun highlightMatches(
    text: String,
    query: String
): androidx.compose.ui.text.AnnotatedString {
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()

    return buildAnnotatedString {
        var currentIndex = 0
        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex == -1) {
                append(text.substring(currentIndex))
                break
            }
            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFE8A317),
                    background = androidx.compose.ui.graphics.Color(0x33E8A317)
                )
            ) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            currentIndex = matchIndex + query.length
        }
    }
}

data class TranscriptSegment(
    val id: Int,
    val filename: String,
    val createdAt: String,
    val durationSeconds: Double,
    val text: String,
    val isNoise: Boolean
)
