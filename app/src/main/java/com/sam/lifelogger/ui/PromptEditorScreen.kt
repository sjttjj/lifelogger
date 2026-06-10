package com.sam.lifelogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.BlackOutlineButton
import com.sam.lifelogger.data.PromptSyncManager
import kotlinx.coroutines.launch

/**
 * Full-screen editor for creating a new session processing prompt.
 *
 * Follows the same pattern as SummaryDetailScreen's edit mode:
 * - Name field (single line)
 * - Prompt text area (multiline, min 200dp)
 * - Save validates and calls PromptSyncManager.createPrompt()
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var promptText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Prompt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Create a template for processing session recordings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Prompt Name") },
                placeholder = { Text("e.g. Meeting Notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp),
                minLines = 10,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BlackOutlineButton(
                    text = if (isSaving) "Saving…" else "Save",
                    onClick = {
                        if (isSaving || name.isBlank() || promptText.isBlank()) return@BlackOutlineButton
                        scope.launch {
                            isSaving = true
                            try {
                                val id = PromptSyncManager.createPrompt(
                                    context = context,
                                    name = name.trim(),
                                    promptText = promptText.trim(),
                                    isDefault = false
                                )
                                if (id != null) {
                                    snackbarHostState.showSnackbar("Prompt saved")
                                    onCreated()
                                } else {
                                    snackbarHostState.showSnackbar(
                                        "Failed to save prompt — check server connection"
                                    )
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    "Error: ${e.message ?: "unknown"}"
                                )
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}