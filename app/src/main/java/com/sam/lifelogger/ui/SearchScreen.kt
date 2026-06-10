package com.sam.lifelogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onDateSelected: (String) -> Unit,
    onTranscriptsSelected: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var resultCount by remember { mutableStateOf<Int?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doSearch() {
        if (query.length < 2) return
        scope.launch {
            isSearching = true
            error = null
            try {
                val json = ApiClient.search(context, query)
                val obj = JSONObject(json)
                val arr = obj.getJSONArray("results")
                val list = mutableListOf<SearchResult>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(
                        SearchResult(
                            id = item.getInt("id"),
                            date = item.optString("date", ""),
                            filename = item.optString("filename", ""),
                            snippet = item.optString("snippet", ""),
                            createdAt = item.optString("created_at", "")
                        )
                    )
                }
                results = list
                resultCount = obj.getInt("count")
            } catch (e: Exception) {
                error = "Search failed: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search transcripts...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { doSearch() },
                    enabled = query.length >= 2 && !isSearching
                ) {
                    Icon(Icons.Default.Search, "Search")
                }
            }

            // Results
            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = error!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                resultCount != null && results.isEmpty() -> {
                    Text(
                        text = "No results for \"$query\"",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                results.isNotEmpty() -> {
                    Text(
                        text = "$resultCount result${if (resultCount != 1) "s" else ""}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { result ->
                            SearchResultCard(
                                result = result,
                                searchQuery = query,
                                onViewSummary = {
                                    if (result.date.isNotBlank()) {
                                        onDateSelected(result.date)
                                    }
                                },
                                onViewTranscript = {
                                    if (result.date.isNotBlank()) {
                                        onTranscriptsSelected(result.date, query)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    result: SearchResult,
    searchQuery: String,
    onViewSummary: () -> Unit,
    onViewTranscript: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.date,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.filename,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Highlighted snippet
            Text(
                text = highlightQuery(result.snippet, searchQuery),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewSummary,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("View Summary", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onViewTranscript,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("View Transcript", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Builds an AnnotatedString that bolds the search query within the snippet.
 */
@Composable
private fun highlightQuery(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return buildAnnotatedString { append(text) }

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
            // Text before match
            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }
            // The match itself — bold and colored
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFE8A317)
                )
            ) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            currentIndex = matchIndex + query.length
        }
    }
}

data class SearchResult(
    val id: Int,
    val date: String,
    val filename: String,
    val snippet: String,
    val createdAt: String
)