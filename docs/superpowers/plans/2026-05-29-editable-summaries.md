# Editable Summaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to edit the AI-generated daily summary title and markdown body inline on the SummaryDetailScreen, with Save/Cancel controls and an "edited" visual indicator.

**Architecture:** Add a `PUT /api/summaries/{date}` method to the existing `ApiClient`; add edit/view mode state to `SummaryDetailScreen` with text fields, Save/Cancel buttons, and an edited-at badge. No local DB changes — all summary data lives on the server.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `HttpURLConnection`, `java.time`

---

### Task 1: Add `updateSummary()` to ApiClient

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/data/ApiClient.kt` (append after `updateConfig()`)

- [ ] **Step 1: Add `updateSummary()` method**

  Append this method to the `ApiClient` object (after the closing brace of `updateConfig`):

  ```kotlin
  /** PUT /api/summaries/{date} — save edited summary title and/or markdown */
  suspend fun updateSummary(context: Context, date: String, markdown: String?, title: String?): String = withContext(Dispatchers.IO) {
      val base = getBaseUrl(context)
      val url = URL("$base/api/summaries/$date")
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "PUT"
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/json")
      conn.connectTimeout = 10_000
      conn.readTimeout = 10_000
      try {
          val jsonBody = JSONObject()
          if (markdown != null) jsonBody.put("markdown", markdown)
          if (title != null) jsonBody.put("title", title)
          conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
          val code = conn.responseCode
          if (code in 200..299) {
              conn.inputStream.bufferedReader().use { it.readText() }
          } else {
              val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
              throw Exception("Update summary failed HTTP $code: $errorBody")
          }
      } finally {
          conn.disconnect()
      }
  }
  ```

- [ ] **Step 2: Verify compilation**

  ```powershell
  cd G:\android_projects\lifelogger
  ./gradlew :app:compileDebugKotlin 2>&1 | Select-String -Pattern "error|success|BUILD"
  ```

  Expected: "BUILD SUCCESSFUL" with no errors. If the import for `JSONObject` is missing, add `import org.json.JSONObject` at the top of the file (it's already imported).

### Task 2: Update SummaryDetailScreen — edit mode, title, save/cancel, edited indicator

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/ui/SummaryDetailScreen.kt` (full file — substantial changes)

- [ ] **Step 1: Update imports**

  Replace the existing imports block in `SummaryDetailScreen.kt` with:

  ```kotlin
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
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import com.sam.lifelogger.data.ApiClient
  import kotlinx.coroutines.launch
  import org.json.JSONObject
  import java.time.OffsetDateTime
  import java.time.format.DateTimeFormatter
  ```

  Note: Added `import kotlinx.coroutines.launch` (for the save scope), removed unused import `com.sam.lifelogger.ui.theme.*` if present, and added `java.time` imports.

- [ ] **Step 2: Replace SummaryDetailScreen composable**

  Replace the entire `SummaryDetailScreen` function with this version that adds title parsing, edit/view mode, Save/Cancel, and the edited indicator:

  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun SummaryDetailScreen(
      date: String,
      onBack: () -> Unit,
      onViewTranscripts: (String) -> Unit
  ) {
      val context = LocalContext.current
      val scope = rememberCoroutineScope()
      val snackbarHostState = remember { SnackbarHostState() }

      var title by remember { mutableStateOf<String?>(null) }
      var summary by remember { mutableStateOf<String?>(null) }
      var segmentCount by remember { mutableStateOf(0) }
      var totalDuration by remember { mutableStateOf(0.0) }
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
                  summary = obj.getString("summary")
                  segmentCount = obj.optInt("segment_count", 0)
                  totalDuration = obj.optDouble("total_duration_seconds", 0.0)
                  title = if (obj.has("title") && !obj.isNull("title")) obj.optString("title", null) else null
                  editedAt = if (obj.has("edited_at") && !obj.isNull("edited_at")) obj.optString("edited_at", null) else null
                  isEdited = obj.optBoolean("is_edited", false)
              }
          } catch (e: Exception) {
              error = "Could not load summary:\n${e.message}"
          } finally {
              isLoading = false
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
                                  StatItem(label = "Recordings", value = "$segmentCount")
                                  StatItem(
                                      label = "Duration",
                                      value = formatDurationShort(totalDuration)
                                  )
                              }
                          }

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
                                                  val newEditedAt = if (respObj.has("edited_at") && !respObj.isNull("edited_at"))
                                                      respObj.optString("edited_at", null) else null

                                                  // Update local state to reflect saved data
                                                  summary = editBody
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

                              // Render the markdown summary
                              RenderMarkdown(summary!!)
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
  ```

- [ ] **Step 3: Add EditedBadge composable**

  Add this composable before the `StatItem` composable (or anywhere in the file after the `SummaryDetailScreen` function):

  ```kotlin
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

  /**
   * Format ISO 8601 timestamp (e.g. "2026-05-29T14:30:00Z") to
   * human-readable form (e.g. "29 May 2026, 14:30").
   */
  private fun formatEditedAt(isoString: String): String {
      return try {
          val odt = OffsetDateTime.parse(isoString)
          odt.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
      } catch (e: Exception) {
          // Fallback: return the raw string if parsing fails
          isoString
      }
  }
  ```

- [ ] **Step 4: Verify compilation**

  ```powershell
  cd G:\android_projects\lifelogger
  ./gradlew :app:compileDebugKotlin 2>&1 | Select-String -Pattern "error|success|BUILD"
  ```

  Expected: "BUILD SUCCESSFUL" with no errors. If there are errors, fix them and re-run.

- [ ] **Step 5: Full build and APK assembly**

  ```powershell
  cd G:\android_projects\lifelogger
  ./gradlew :app:assembleDebug 2>&1 | Select-String -Pattern "error|success|BUILD"
  ```

  Expected: "BUILD SUCCESSFUL in [X]s". The debug APK is now ready at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Self-Review

**Spec coverage:**
- Edit button between stats and content ✅ (Task 2, Step 2 — view mode section)
- Title + body text fields in edit mode ✅ (Task 2, Step 2 — edit mode section)
- Save sends PUT, parses response for new edited_at ✅ (Task 2, Step 2 — save onClick handler)
- Cancel discards changes ✅ (Task 2, Step 2 — cancel onClick)
- Edited badge with "Last edited: [timestamp]" ✅ (Task 2, Step 3 — EditedBadge)
- edited_at / is_edited absent handled via optString/optBoolean defaults ✅ (Task 2, Step 2 — parsing)
- Title in top bar with date subtitle ✅ (Task 2, Step 2 — top bar title composable)
- Save error handling with Snackbar + Retry ✅ (Task 2, Step 2 — catch block with snackbar)
- Saving state disables buttons ✅ (Task 2, Step 2 — isSaving check on Save, enabled on Cancel)
- API contract: PUT /api/summaries/{date} with JSON body ✅ (Task 1, Step 1)
