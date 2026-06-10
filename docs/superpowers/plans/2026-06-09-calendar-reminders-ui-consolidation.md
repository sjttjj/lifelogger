# Calendar + Reminders UI Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge needs-review into the Calendar screen, delete the standalone RemindersScreen, remove the Prompts home button, simplify the grid.

**Architecture:** The Calendar screen (`LifeCalendarScreen.kt`) gains a view-mode toggle (Calendar icon / Edit icon) and hosts the transplanted Needs Review UI. The `RemindersScreen.kt` file is deleted — its Needs Review composables move into `LifeCalendarScreen.kt`. `MainActivity.kt` removes old routes/callbacks and reflows the home grid to 2×3.

**Tech Stack:** Kotlin, Jetpack Compose, AnimatedContent crossfade

---

### Task 1: Shorten range labels in CalendarAgendaModels

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/calendar/CalendarAgendaModels.kt:7-10`

- [ ] **Step 1: Change labels from full words to single-letter + "All"**

Edit `CalendarAgendaModels.kt`:

```kotlin
enum class CalendarAgendaRange(val label: String) {
    Week("W"),
    Month("M"),
    All("All");
```

- [ ] **Step 2: Compile to verify no breakage**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

### Task 2: Add view-mode toggle + Needs Review mode to LifeCalendarScreen

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/ui/LifeCalendarScreen.kt`
- (Uses): `com.sam.lifelogger.data.Reminder`, `ReminderSyncManager`, `ReminderPatch`
- (Uses): `com.sam.lifelogger.calendar.*` (already imported)

- [ ] **Step 1: Add imports for new needs-review + animation dependencies**

Add these imports near the top of `LifeCalendarScreen.kt`:

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderPatch
import com.sam.lifelogger.data.ReminderSyncManager
```

Note: `clickable`, `Button`, `TextButton`, `Surface`, `rememberCoroutineScope` may already be imported — check and only add missing ones.

- [ ] **Step 2: Add view-mode state and needs-review loading**

Inside the `LifeCalendarScreen` composable, after the existing state declarations, add:

```kotlin
enum class CalendarViewMode { Calendar, NeedsReview }

var viewMode by remember { mutableStateOf(CalendarViewMode.Calendar) }
var needsReviewItems by remember { mutableStateOf<List<Reminder>>(emptyList()) }
var cancelCandidate by remember { mutableStateOf<Reminder?>(null) }
```

- [ ] **Step 3: Add needs-review data loading**

Inside the `load()` function, after the existing calendar items load, add needs-review loading:

```kotlin
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
```

- [ ] **Step 4: Rename the top-bar title from "Life Calendar" to "Calendar"**

Change the existing `TopAppBar` title:
```kotlin
title = { Text("Calendar") },
```

- [ ] **Step 5: Add the cancel confirmation dialog**

Inside the `Scaffold`, before the `Column` block, add the cancel dialog (same pattern as the old `RemindersScreen`):

```kotlin
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
```

- [ ] **Step 6: Add view-mode toggle to CalendarRangeHeader**

Replace the existing `CalendarRangeHeader` call with a version that receives and passes the view mode + toggle callback. Change the call site first (inside `Column` modifier = ...padding(padding)):

Update the `CalendarRangeHeader` signature and add the new toggle row. Replace the existing `CalendarRangeHeader` function completely:

```kotlin
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
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        // Row 1: Date arrows and label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = rangeLabel(selectedRange, anchorDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Row 2: Range selector + view mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Range selector (W / M / All)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CalendarAgendaRange.entries.forEach { range ->
                    val isEnabled = viewMode == CalendarViewMode.Calendar
                    Surface(
                        onClick = { if (isEnabled) onRangeSelected(range) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedRange == range && isEnabled) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        enabled = isEnabled
                    ) {
                        Text(
                            text = range.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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

            // View-mode toggle (Calendar / Edit)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CalendarViewMode.entries.forEach { mode ->
                    val isSelected = viewMode == mode
                    Surface(
                        onClick = { onViewModeChange(mode) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    ) {
                        Icon(
                            imageVector = if (mode == CalendarViewMode.Calendar) Icons.Default.CalendarMonth else Icons.Default.EditNote,
                            contentDescription = if (mode == CalendarViewMode.Calendar) "Calendar view" else "Needs Review view",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: Update the CalendarRangeHeader call site**

Replace the existing `CalendarRangeHeader(...)` call inside the `Column`:

```kotlin
CalendarRangeHeader(
    selectedRange = selectedRange,
    anchorDate = anchorDate,
    viewMode = viewMode,
    onPrevious = { anchorDate = selectedRange.previous(anchorDate) },
    onNext = { anchorDate = selectedRange.next(anchorDate) },
    onRangeSelected = { selectedRange = it },
    onViewModeChange = { viewMode = it }
)
```

Add a missing import for `Icons.Default.CalendarMonth` and `Icons.Default.EditNote` — they should be in the imports you added in Step 1.

- [ ] **Step 8: Wrap the main content area with crossfade AnimatedContent**

Replace the existing `Box(modifier = Modifier.fillMaxSize()) { ... }` block with:

```kotlin
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
                            items = items,
                            listState = listState,
                            onOpenReminder = onOpenReminder
                        )
                    }
                }

                MonthMapOverlay(
                    month = mapMonth,
                    cells = monthMapCells,
                    today = today,
                    modifier = Modifier.align(Alignment.BottomCenter),
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
```

Note: `Icons.Default.CalendarMonth` and `Icons.Default.EditNote` need proper resolution. `CalendarMonth` is in `material.icons.filled`. `EditNote` may need `material-icons-extended` — let's verify. Check if it resolves:

Actually, `Icons.Filled.CalendarMonth` is in the base material-icons. `Icons.Filled.EditNote` is in material-icons-extended (which is already a dependency). Let me double-check by using a simpler pair to avoid build issues.

Use `Icons.Filled.CalendarMonth` and `Icons.Filled.Edit` (from base icons, no extended needed). `Edit` is a standard Material icon (pencil/edit icon). This avoids any risk with extended icons not resolving.

Update: Change `Icons.Default.EditNote` → `Icons.Default.Edit` in the code above.

- [ ] **Step 9: Add the NeedsReviewView composable**

Add this new composable function at the bottom of `LifeCalendarScreen.kt` (before the existing utility functions at the bottom):

```kotlin
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
```

- [ ] **Step 10: Add the NeedsReviewCard composable**

```kotlin
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
```

Note: This is a simplified card compared to the old ReminderCard — it only has Cancel (no Done). In the old Needs Review tab, Done was a no-op anyway, so removing it is cleaner. If the item is Needs Review, the user's actions are: edit (tap the card), cancel, or review (tap opens EditReminderScreen where they can set a date).

If you prefer to keep the Done button visible (matching the original exactly), replace the `Row` with:
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    TextButton(onClick = {}) { Text("Done") }
    TextButton(onClick = onCancel) { Text("Cancel") }
}
```

- [ ] **Step 11: Add the formatReminderTime helper function**

Add at the bottom of the file, before the closing brace or after the existing helper functions:

```kotlin
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
```

- [ ] **Step 12: Add missing imports at the top of the file**

Add to the existing import block:
```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderPatch
import com.sam.lifelogger.data.ReminderSyncManager
```

Skip any that are already imported.

- [ ] **Step 13: Compile to verify**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

### Task 3: Update MainActivity — remove Prompts/Reminders buttons, remove RemindersScreen route, reflow grid

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/MainActivity.kt`

- [ ] **Step 1: Remove the RemindersScreen import**

Delete line 75:
```kotlin
import com.sam.lifelogger.ui.RemindersScreen
```

- [ ] **Step 2: Remove the `composable("reminders")` route**

Delete lines 707-712:
```kotlin
        composable("reminders") {
            RemindersScreen(
                onBack = { navController.popBackStack() },
                onEditReminder = { id -> navController.navigate("reminders/edit/$id") }
            )
        }
```

The `composable("reminders/edit/{id}")` route and the `composable("calendar")` route remain untouched.

- [ ] **Step 3: Remove `onOpenReminders` from `AppNavigation` signature**

Change the `AppNavigation` function signature (line 584-597):

Remove `onOpenReminders: () -> Unit = {},` from the parameter list.

- [ ] **Step 4: Remove `onOpenReminders` from the main-screen composable call**

In the `composable("main")` block (line 606), remove the line:
```kotlin
                onOpenReminders = { navController.navigate("reminders") },
```

And change the `onOpenCalendar` line from (line 617):
```kotlin
                onOpenCalendar = { navController.navigate("calendar") },
```
Keep this line as-is — Calendar stays.

- [ ] **Step 5: Remove `onOpenReminders` from `MainScreen` signature**

From the `MainScreen` function (line 733):
```kotlin
    onOpenReminders: () -> Unit = {},
```
Remove this parameter.

- [ ] **Step 6: Update the `DashboardCarousel` call in `MainScreen`**

Change (line 799-801):
```kotlin
            DashboardCarousel(
                reminders = dashboardReminders,
                onOpenReminders = onOpenReminders
            )
```
to:
```kotlin
            DashboardCarousel(
                reminders = dashboardReminders,
                onOpenCalendar = onOpenCalendar
            )
```

- [ ] **Step 7: Update the `HomeActionGrid` call in `MainScreen`**

Remove `onOpenReminders` from the call (line 817):
```kotlin
                onOpenReminders = onOpenReminders,
```

- [ ] **Step 8: Update `DashboardCarousel` composable**

Change the `DashboardCarousel` signature from:
```kotlin
private fun DashboardCarousel(
    reminders: List<Reminder>,
    onOpenReminders: () -> Unit
) {
```
to:
```kotlin
private fun DashboardCarousel(
    reminders: List<Reminder>,
    onOpenCalendar: () -> Unit
) {
```

And change line 878:
```kotlin
                0 -> ReminderDashboardCard(reminders, onOpenReminders)
```
to:
```kotlin
                0 -> ReminderDashboardCard(reminders, onOpenCalendar)
```

- [ ] **Step 9: Update `ReminderDashboardCard` composable**

Change the `ReminderDashboardCard` signature from:
```kotlin
private fun ReminderDashboardCard(
    reminders: List<Reminder>,
    onOpenReminders: () -> Unit
) {
```
to:
```kotlin
private fun ReminderDashboardCard(
    reminders: List<Reminder>,
    onOpenCalendar: () -> Unit
) {
```

And change line 925:
```kotlin
        onClick = onOpenReminders
```
to:
```kotlin
        onClick = onOpenCalendar
```

- [ ] **Step 10: Update `HomeActionGrid` — remove Reminders/Prompts, reflow to 2×3**

Replace the entire `HomeActionGrid` function (lines 1156-1189):

```kotlin
@Composable
private fun HomeActionGrid(
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onOpenViewer: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeActionButton(
                label = if (isRecording) "Stop" else "Record",
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                primary = true,
                onClick = onRecordClick,
                modifier = Modifier.weight(1f)
            )
            HomeActionButton("Life Log", Icons.Filled.ViewList, onOpenViewer, Modifier.weight(1f))
            HomeActionButton("Calendar", Icons.Filled.Event, onOpenCalendar, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeActionButton("Recordings", Icons.Filled.LibraryMusic, onOpenRecordings, Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
            HomeActionButton("Settings", Icons.Filled.SettingsIcon, onOpenSettings, Modifier.weight(1f))
        }
    }
}
```

- [ ] **Step 11: Update all callers of `HomeActionGrid` and `MainScreen`**

In `AppNavigation`, update the `main` composable section. The `MainScreen` call should no longer pass `onOpenReminders` or `onOpenPrompts`:

In `composable("main")` (line 606), remove:
```kotlin
                onOpenPrompts = { navController.navigate("prompts") },
                onOpenReminders = { navController.navigate("reminders") },
```

And update the `MainScreen` call in `AppNavigation` to match the new signature.

- [ ] **Step 12: Update the `setContent` `AppNavigation` call (line 300-344)**

Remove the `onOpenReminders = {},` line (338).

Remove the `onOpenPrompts` parameter if it was passed... actually `onOpenPrompts` was not passed in the `setContent` block — it defaults. But the `AppNavigation` signature had `onOpenPrompts: () -> Unit = {},` — keep this default parameter since Settings still navigates to `"prompts/new"`.

Actually wait — `onOpenPrompts` still exists in the `AppNavigation` signature as a default param. It's only used in the `composable("main")` block. Since we're removing the `onOpenPrompts = { navController.navigate("prompts") }` from the `composable("main")` block, the default value handles it. The `"prompts"` route itself still exists (for Settings → prompts picker), so keep the `composable("prompts")` and `composable("prompts/new")` routes untouched.

- [ ] **Step 13: Compile to verify**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

### Task 4: Delete RemindersScreen.kt

**Files:**
- Delete: `app/src/main/java/com/sam/lifelogger/ui/RemindersScreen.kt`

- [ ] **Step 1: Delete the file**

```powershell
Remove-Item app/src/main/java/com/sam/lifelogger/ui/RemindersScreen.kt
```

- [ ] **Step 2: Compile to verify nothing references it**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL (all references were removed in Task 3)

---

### Task 5: Full build + unit tests + install

- [ ] **Step 1: Run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: All tests pass (unchanged — we didn't touch data/calendar models)

- [ ] **Step 2: Assemble debug APK**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Install on device**

```powershell
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: Success