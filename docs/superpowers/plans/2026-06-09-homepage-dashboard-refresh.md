# Homepage Dashboard Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the home screen's large vertical button stack with a compact dashboard-first layout using a centered carousel, real reminder data, placeholder module cards, and a Settings-based manual sync action.

**Architecture:** Keep the implementation local to the existing Compose app structure. Extract small pure helpers for dashboard reminder selection so they can be unit tested, then update `MainScreen` and `SettingsScreen` without changing recording service behavior or adding server APIs.

**Tech Stack:** Android Kotlin, Jetpack Compose Material 3, Room-backed reminder cache, JUnit 4.

---

## File Structure

- Modify `app/src/main/java/com/sam/lifelogger/MainActivity.kt`
  - Add dashboard state loading in `MainScreen`.
  - Replace the current centered vertical button layout with title row, centered carousel, recording strip, action grid, and status tiles.
  - Add small private composables in the same file to match current project style.
- Modify `app/src/main/java/com/sam/lifelogger/ui/SettingsScreen.kt`
  - Add `onUploadPending` parameter.
  - Add a compact "Sync manually" action near the top of Settings.
- Modify `app/src/main/java/com/sam/lifelogger/MainActivity.kt`
  - Pass `onUploadPending` into `SettingsScreen`.
- Create `app/src/main/java/com/sam/lifelogger/data/DashboardReminderSelector.kt`
  - Pure helper that chooses up to two pressing reminders from cached/synced reminder lists.
- Create `app/src/test/java/com/sam/lifelogger/data/DashboardReminderSelectorTest.kt`
  - Unit tests for reminder selection ordering and null-date behavior.

## Task 1: Dashboard Reminder Selection

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/data/DashboardReminderSelector.kt`
- Test: `app/src/test/java/com/sam/lifelogger/data/DashboardReminderSelectorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/sam/lifelogger/data/DashboardReminderSelectorTest.kt`:

```kotlin
package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardReminderSelectorTest {

    @Test
    fun selectsTwoEarliestScheduledPendingReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "Later", scheduledAtUtc = "2026-06-10T08:00:00Z"),
            reminder(id = 2, title = "First", scheduledAtUtc = "2026-06-09T08:00:00Z"),
            reminder(id = 3, title = "Second", scheduledAtUtc = "2026-06-09T09:00:00Z")
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("First", "Second"), selected.map { it.title })
    }

    @Test
    fun putsUndatedRemindersAfterDatedReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "No date", scheduledAtUtc = null),
            reminder(id = 2, title = "Dated", scheduledAtUtc = "2026-06-09T08:00:00Z")
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("Dated", "No date"), selected.map { it.title })
    }

    @Test
    fun excludesNonPendingReminders() {
        val reminders = listOf(
            reminder(id = 1, title = "Done", status = "completed", scheduledAtUtc = "2026-06-09T08:00:00Z"),
            reminder(id = 2, title = "Pending", status = "pending", scheduledAtUtc = "2026-06-09T09:00:00Z")
        )

        val selected = DashboardReminderSelector.selectPressing(reminders)

        assertEquals(listOf("Pending"), selected.map { it.title })
    }

    private fun reminder(
        id: Long,
        title: String,
        status: String = "pending",
        scheduledAtUtc: String?
    ): Reminder {
        return Reminder(
            id = id,
            sourceSegmentId = null,
            kind = "task",
            title = title,
            description = null,
            status = status,
            needsReview = false,
            timezone = "Australia/Sydney",
            scheduledAtLocal = null,
            scheduledAtUtc = scheduledAtUtc,
            schedulePrecision = "datetime",
            usedDefaultTime = false,
            location = null,
            people = null,
            amount = null,
            recurrenceText = null,
            notificationJobs = emptyList()
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.data.DashboardReminderSelectorTest
```

Expected: compilation fails because `DashboardReminderSelector` does not exist.

- [ ] **Step 3: Add the selector**

Create `app/src/main/java/com/sam/lifelogger/data/DashboardReminderSelector.kt`:

```kotlin
package com.sam.lifelogger.data

object DashboardReminderSelector {
    fun selectPressing(reminders: List<Reminder>, limit: Int = 2): List<Reminder> {
        return reminders
            .filter { it.status.equals("pending", ignoreCase = true) }
            .sortedWith(
                compareBy<Reminder> { it.scheduledAtUtc == null }
                    .thenBy { it.scheduledAtUtc ?: "" }
                    .thenBy { it.id }
            )
            .take(limit)
    }
}
```

- [ ] **Step 4: Run selector tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.sam.lifelogger.data.DashboardReminderSelectorTest
```

Expected: tests pass.

## Task 2: Refresh Home Screen

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/MainActivity.kt`

- [ ] **Step 1: Add required imports**

In `MainActivity.kt`, add:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.sam.lifelogger.data.DashboardReminderSelector
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderSyncManager
```

If an import already exists, do not duplicate it.

- [ ] **Step 2: Add dashboard reminder state in `MainScreen`**

Inside `MainScreen`, after the existing `elapsedSeconds` state:

```kotlin
    var dashboardReminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
```

After the existing recording-state `LaunchedEffect(Unit)` block, add:

```kotlin
    LaunchedEffect(Unit) {
        dashboardReminders = DashboardReminderSelector.selectPressing(
            ReminderSyncManager.syncUpcomingOrUseCache(context)
        )
    }
```

- [ ] **Step 3: Replace the old `Box` home layout**

Replace the contents of the `Surface { Box(...) { ... } }` in `MainScreen` with:

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HomeTitleRow(onOpenSettings = onOpenSettings)
            DashboardCarousel(
                reminders = dashboardReminders,
                onOpenReminders = onOpenReminders
            )
            RecordingStatusStrip(
                isRecording = isRecording,
                activeRecordingMode = activeRecordingMode,
                timerText = timerText,
                onStopActiveMode = onStopActiveMode,
                onStop = onStop
            )
            HomeActionGrid(
                isRecording = isRecording,
                onRecordClick = {
                    if (isRecording) onStop() else onStart()
                },
                onOpenViewer = onOpenViewer,
                onOpenReminders = onOpenReminders,
                onOpenRecordings = onOpenRecordings,
                onOpenPrompts = onOpenPrompts,
                onOpenSettings = onOpenSettings
            )
            HomeStatusTiles(
                syncStatus = syncStatus,
                timeToNextChunk = timeToNextChunk
            )
        }
```

Keep the outer `Surface` unchanged.

- [ ] **Step 4: Add helper composables below `MainScreen`**

Add these composables after `MainScreen` and before `formatElapsed`:

```kotlin
@Composable
private fun HomeTitleRow(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Lifelogger",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardCarousel(
    reminders: List<Reminder>,
    onOpenReminders: () -> Unit
) {
    val modules = listOf("Reminders", "Finance", "Ask AI")
    val pagerState = rememberPagerState(pageCount = { modules.size })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Swipe",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalPager(
            state = pagerState,
            pageSpacing = 18.dp,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> ReminderDashboardCard(reminders, onOpenReminders)
                1 -> PlaceholderDashboardCard(
                    title = "Finance",
                    items = listOf(
                        "Cashflow snapshot" to "Future integration",
                        "Bills and alerts" to "Future money reminders"
                    )
                )
                2 -> PlaceholderDashboardCard(
                    title = "Ask AI",
                    items = listOf(
                        "Ask across notes" to "Future transcript and reminder search"
                    )
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            modules.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun ReminderDashboardCard(
    reminders: List<Reminder>,
    onOpenReminders: () -> Unit
) {
    DashboardCard(
        title = "Reminders",
        chip = if (reminders.isEmpty()) "Clear" else "${reminders.size} pressing",
        onClick = onOpenReminders
    ) {
        if (reminders.isEmpty()) {
            Text(
                text = "No upcoming reminders",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                reminders.forEach { reminder ->
                    MiniDashboardItem(
                        title = reminder.title,
                        subtitle = reminder.scheduledAtLocal ?: reminder.schedulePrecision,
                        trailing = reminder.scheduledAtUtc?.substringAfter("T")?.take(5).orEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderDashboardCard(
    title: String,
    items: List<Pair<String, String>>
) {
    DashboardCard(
        title = title,
        chip = "Soon",
        enabled = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items.forEach { (itemTitle, itemSubtitle) ->
                MiniDashboardItem(
                    title = itemTitle,
                    subtitle = itemSubtitle,
                    trailing = ""
                )
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    chip: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 146.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(chip) }
                )
            }
            content()
        }
    }
}

@Composable
private fun MiniDashboardItem(
    title: String,
    subtitle: String,
    trailing: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing.isNotBlank()) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RecordingStatusStrip(
    isRecording: Boolean,
    activeRecordingMode: RecordingMode?,
    timerText: String,
    onStopActiveMode: () -> Unit,
    onStop: () -> Unit
) {
    val modeText = if (isRecording) {
        when (activeRecordingMode) {
            RecordingMode.SESSION -> "Recording session"
            RecordingMode.REMINDER -> "Recording reminder"
            else -> "Recording"
        }
    } else {
        "Ready"
    }
    val detailText = if (isRecording) {
        "Elapsed $timerText"
    } else {
        "Normal recording · squeeze for reminder"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modeText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isRecording) {
                IconButton(
                    onClick = {
                        if (activeRecordingMode == RecordingMode.REMINDER) onStopActiveMode() else onStop()
                    }
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop recording")
                }
            } else {
                Text(
                    text = "00:00",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeActionGrid(
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onOpenViewer: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenPrompts: () -> Unit,
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
            HomeActionButton("Reminders", Icons.Filled.Event, onOpenReminders, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeActionButton("Recordings", Icons.Filled.LibraryMusic, onOpenRecordings, Modifier.weight(1f))
            HomeActionButton("Prompts", Icons.Filled.Edit, onOpenPrompts, Modifier.weight(1f))
            HomeActionButton("Settings", Icons.Filled.Settings, onOpenSettings, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeStatusTiles(
    syncStatus: String,
    timeToNextChunk: String?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        HomeStatusTile("Sync", syncStatus, Modifier.weight(1f))
        HomeStatusTile("Next chunk", timeToNextChunk ?: "Idle", Modifier.weight(1f))
    }
}

@Composable
private fun HomeStatusTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
```

If `ImageVector` or `ColumnScope` are unresolved, add:

```kotlin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ColumnScope
```

- [ ] **Step 5: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compilation succeeds. If Compose import names differ in the project's dependency version, fix imports without changing behavior.

## Task 3: Move Manual Sync to Settings

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/sam/lifelogger/MainActivity.kt`

- [ ] **Step 1: Update `SettingsScreen` signature**

Change:

```kotlin
fun SettingsScreen(
    onBack: () -> Unit,
    onNewPrompt: () -> Unit = {}
)
```

to:

```kotlin
fun SettingsScreen(
    onBack: () -> Unit,
    onNewPrompt: () -> Unit = {},
    onUploadPending: () -> Unit = {}
)
```

- [ ] **Step 2: Add sync action near the top of Settings**

Inside the Settings content `Column`, before the "Upload destination" heading, add:

```kotlin
            Button(
                onClick = onUploadPending,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Sync manually")
            }

            Spacer(modifier = Modifier.height(24.dp))
```

- [ ] **Step 3: Pass the callback from navigation**

In `AppNavigation`, change the Settings route:

```kotlin
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNewPrompt = { navController.navigate("prompts/new") }
            )
```

to:

```kotlin
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNewPrompt = { navController.navigate("prompts/new") },
                onUploadPending = onUploadPending
            )
```

- [ ] **Step 4: Remove manual sync from the home screen API**

Remove `onUploadPending` from the `MainScreen` parameter list and remove `onUploadPending = onUploadPending` from the `MainScreen` call in `AppNavigation`. `AppNavigation` should keep its own `onUploadPending` parameter so it can pass the callback to `SettingsScreen`.

- [ ] **Step 5: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compilation succeeds.

## Task 4: Verification

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual UI check**

Install/run the app and check:

- Home shows one centered dashboard card.
- Swiping changes dashboard modules.
- No horizontal scrollbar is visible.
- Dots update with the current dashboard page.
- Reminders card shows up to two real reminders or an empty state.
- Finance and Ask AI are marked Soon and do not fake data.
- Home has compact square action buttons.
- "Sync manually" is in Settings and no longer on Home.
