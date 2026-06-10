package com.sam.lifelogger.ui

import android.content.Context
import android.provider.Settings
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sam.lifelogger.data.ApiClient
import com.sam.lifelogger.data.CachedPromptEntity
import com.sam.lifelogger.data.PromptSyncManager
import com.sam.lifelogger.squeeze.SqueezeActionMapper
import com.sam.lifelogger.squeeze.SqueezeGesture

private const val KEY_SELECTED_PROMPT_ID = "selected_prompt_id"
private const val KEY_PINNED_IDS = "pinned_prompt_ids"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNewPrompt: () -> Unit = {},
    onUploadPending: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("lifelogger_prefs", Context.MODE_PRIVATE)
    }

    var uploadTarget by remember {
        mutableStateOf(
            prefs.getString("upload_target", "local") ?: "local"
        )
    }

    var localUrl by remember {
        mutableStateOf(
            prefs.getString(
                "local_server_url",
                "http://100.78.20.28:8000/transcribe?task=transcribe"
            ) ?: ""
        )
    }

    var notionEnabled by remember {
        mutableStateOf(
            prefs.getBoolean("notion_push_enabled", true)
        )
    }

    LaunchedEffect(uploadTarget) {
        prefs.edit().putString("upload_target", uploadTarget).apply()
    }
    LaunchedEffect(localUrl) {
        prefs.edit().putString("local_server_url", localUrl).apply()
    }
    LaunchedEffect(notionEnabled) {
        prefs.edit().putBoolean("notion_push_enabled", notionEnabled).apply()
        try {
            ApiClient.updateConfig(context, "notion_push_enabled", notionEnabled)
        } catch (e: Exception) {
            android.util.Log.e("Settings", "Could not sync Notion setting to server", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Upload destination ---
            Text(
                text = "Upload destination",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = uploadTarget == "local",
                    onClick = { uploadTarget = "local" }
                )
                Text(
                    text = "Local server",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (uploadTarget == "local") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = localUrl,
                    onValueChange = { localUrl = it },
                    label = { Text("Local server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = uploadTarget == "supabase",
                    onClick = { uploadTarget = "supabase" }
                )
                Text(
                    text = "Supabase (cloud)",
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uploadTarget == "supabase") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: Supabase upload is currently disabled. Rotate your API key before re-enabling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // --- Manual Sync ---
            Text(
                text = "Manual Sync",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Upload pending recordings to your configured destination.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedButton(
                onClick = onUploadPending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sync manually")
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // --- Squeeze gesture remapping ---
            Text(
                text = "Squeeze Gestures",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            GestureMappingRow(
                gesture = SqueezeGesture.SINGLE,
                label = "Single squeeze",
                currentAction = SqueezeActionMapper.getMapping(context, SqueezeGesture.SINGLE),
                onActionSelected = { action ->
                    SqueezeActionMapper.setMapping(context, SqueezeGesture.SINGLE, action)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            GestureMappingRow(
                gesture = SqueezeGesture.DOUBLE,
                label = "Double squeeze",
                currentAction = SqueezeActionMapper.getMapping(context, SqueezeGesture.DOUBLE),
                onActionSelected = { action ->
                    SqueezeActionMapper.setMapping(context, SqueezeGesture.DOUBLE, action)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            GestureMappingRow(
                gesture = SqueezeGesture.TRIPLE,
                label = "Triple squeeze",
                currentAction = SqueezeActionMapper.getMapping(context, SqueezeGesture.TRIPLE),
                onActionSelected = { action ->
                    SqueezeActionMapper.setMapping(context, SqueezeGesture.TRIPLE, action)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // --- Session Prompts ---
            Text(
                text = "Session Prompts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Choose which prompt to use when processing session recordings on the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val prompts by PromptSyncManager.getAllPrompts(context)
                .collectAsState(initial = emptyList())

            var selectedId by remember {
                mutableStateOf(
                    prefs.getString(KEY_SELECTED_PROMPT_ID, null)?.takeIf { it.isNotBlank() }
                )
            }

            // Pinned prompts — stored locally in SharedPreferences, not synced to server
            var pinnedIds by remember {
                mutableStateOf(
                    prefs.getStringSet("pinned_prompt_ids", emptySet()) ?: emptySet()
                )
            }

            fun togglePinned(id: String) {
                val updated = pinnedIds.toMutableSet()
                if (id in updated) updated.remove(id) else updated.add(id)
                pinnedIds = updated
                prefs.edit().putStringSet("pinned_prompt_ids", updated).apply()
            }

            // Sorted: pinned first, then by name
            val sortedPrompts = remember(prompts, pinnedIds) {
                prompts.sortedByDescending { it.id in pinnedIds }
            }

            if (sortedPrompts.isEmpty()) {
                Text(
                    text = "No prompts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Constrained scrolling container — shows ~4-5 prompts, scroll for the rest
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        sortedPrompts.forEach { prompt ->
                            PromptSettingsCard(
                                prompt = prompt,
                                isSelected = prompt.id == selectedId,
                                isPinned = prompt.id in pinnedIds,
                                onTogglePin = { togglePinned(prompt.id) },
                                onClick = {
                                    selectedId = prompt.id
                                    prefs.edit().putString(KEY_SELECTED_PROMPT_ID, prompt.id).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // Clear selection + New prompt buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedId != null) {
                    OutlinedButton(
                        onClick = {
                            selectedId = null
                            prefs.edit().remove(KEY_SELECTED_PROMPT_ID).apply()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("No prompt")
                    }
                }

                OutlinedButton(
                    onClick = onNewPrompt,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Prompt")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // --- Notion push toggle ---
            Text(
                text = "Notion integration",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Push summaries to Notion",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (notionEnabled) "Summaries will be sent to your Notion database"
                        else "Summaries will only be available in the Life Log tab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notionEnabled,
                    onCheckedChange = { notionEnabled = it }
                )
            }
        }
    }
}

@Composable
private fun PromptSettingsCard(
    prompt: CachedPromptEntity,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = prompt.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (prompt.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prompt.prompt.take(150).replace("\n", " ") +
                            if (prompt.prompt.length > 150) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Pin icon
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun GestureMappingRow(
    gesture: SqueezeGesture,
    label: String,
    currentAction: String,
    onActionSelected: (String) -> Unit
) {
    val actions = SqueezeActionMapper.allActions
    var expanded by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf(currentAction) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(SqueezeActionMapper.actionLabel(selectedAction))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(SqueezeActionMapper.actionLabel(action)) },
                        onClick = {
                            selectedAction = action
                            onActionSelected(action)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
