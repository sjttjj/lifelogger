# Recording Inline Player & Live Waveform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an inline expandable audio player to RecordingsScreen with play/pause, scrubbable progress bar, and live waveform visualization.

**Architecture:** Extract two new composables (RecordingPlayer + WaveformView). RecordingPlayer owns MediaPlayer + Android Visualizer lifecycle; WaveformView is a pure Canvas renderer receiving bar heights as a `List<Float>`. RecordingsScreen manages only expand/collapse state.

**Tech Stack:** Jetpack Compose, Material3, Android Visualizer API, MediaPlayer

---

### Task 1: Create WaveformView.kt — waveform rendering composable

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/ui/WaveformView.kt`

A pure rendering composable that draws minimal bars on a Canvas. Takes normalized heights (0..1) and draws them as rounded rectangles.

- [ ] **Step 1: Create the WaveformView composable**

Write `app/src/main/java/com/sam/lifelogger/ui/WaveformView.kt`:

```kotlin
package com.sam.lifelogger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Pure rendering composable — draws minimal waveform bars from normalized heights.
 * Bars are 50% of each slot width (bar + gap), rendered in primary theme color.
 *
 * @param barHeights  Normalized 0..1 values per bar. Empty list = nothing drawn.
 * @param primaryColor  Color for the bars. Defaults to a purple/blue that works in dark/light themes.
 * @param modifier  Standard compose modifier.
 */
@Composable
fun WaveformView(
    barHeights: List<Float>,
    primaryColor: Color = Color(0xFF6C63FF),
    modifier: Modifier = Modifier
) {
    if (barHeights.isEmpty()) return

    Canvas(modifier = modifier) {
        val barCount = barHeights.size
        val slotWidth = size.width / barCount
        val barWidth = slotWidth * 0.5f
        val gap = slotWidth * 0.5f

        barHeights.forEachIndexed { index, height ->
            // Clamp height to [0, 1], scale to canvas height
            val clamped = height.coerceIn(0f, 1f)
            val barHeight = clamped * size.height
            val x = index * slotWidth + gap / 2f

            // Skip drawing if bar is too small to see
            if (barHeight < 0.5f) return@forEachIndexed

            drawRect(
                color = primaryColor.copy(alpha = 0.4f + clamped * 0.3f),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd G:/android_projects/lifelogger && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL` with no errors.

---

### Task 2: Create RecordingPlayer.kt — inline expandable player

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/ui/RecordingPlayer.kt`

An inline audio player composable that manages MediaPlayer + Visualizer lifecycle, renders play/pause button, combined waveform+progress bar, and time display.

- [ ] **Step 1: Create the RecordingPlayer composable with MediaPlayer lifecycle**

Write `app/src/main/java/com/sam/lifelogger/ui/RecordingPlayer.kt`:

```kotlin
package com.sam.lifelogger.ui

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

private const val BAR_COUNT = 32
private const val TAG = "RecordingPlayer"

/**
 * Inline expandable audio player.
 *
 * Manages MediaPlayer + Visualizer lifecycle via DisposableEffect.
 * Bar heights are computed from Visualizer waveform data and passed to WaveformView.
 *
 * @param file  The audio file to play.
 * @param modifier  Standard compose modifier.
 */
@Composable
fun RecordingPlayer(
    file: File,
    modifier: Modifier = Modifier
) {
    // --- State ---
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var barHeights by remember { mutableStateOf(List(BAR_COUNT) { 0f }) }

    // --- MediaPlayer + Visualizer lifecycle ---
    DisposableEffect(file.absolutePath) {
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener {
                durationMs = it.duration
                it.start()
                isPlaying = true
            }
            setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
                progress = 0f
                it.seekTo(0)
            }
            prepareAsync()
        }

        val viz = try {
            Visualizer(mp.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform == null || waveform.isEmpty()) return
                            val samplesPerBar = waveform.size / BAR_COUNT
                            val heights = FloatArray(BAR_COUNT) { i ->
                                val start = i * samplesPerBar
                                var sum = 0f
                                for (j in 0 until samplesPerBar.coerceAtMost(waveform.size - start)) {
                                    sum += abs(waveform[start + j].toInt()).toFloat()
                                }
                                (sum / samplesPerBar) / 128f // normalize to 0..1
                            }
                            barHeights = heights.toList()
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) = Unit
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,  // capture waveform
                    false  // don't capture FFT
                )
                enabled = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer not available", e)
            null
        }

        mediaPlayer = mp

        onDispose {
            try {
                viz?.enabled = false
                viz?.release()
            } catch (_: Exception) {}
            try {
                mp.stop()
            } catch (_: Exception) {}
            mp.release()
            mediaPlayer = null
        }
    }

    // --- Progress polling coroutine ---
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = mediaPlayer ?: break
            try {
                currentPositionMs = mp.currentPosition
                if (durationMs > 0) {
                    progress = currentPositionMs.toFloat() / durationMs.toFloat()
                }
            } catch (_: Exception) {
                break
            }
            delay(200L)
        }
    }

    // Seek when progress bar is dragged (pause polling during drag)
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        if (isDragging) {
            // Short polling during drag to keep UI updating if playback continues
            while (isDragging) {
                delay(50L)
            }
        }
    }

    // --- UI ---
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        FilledIconButton(
            onClick = {
                val mp = mediaPlayer ?: return@FilledIconButton
                if (isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Combined waveform + progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
        ) {
            // Waveform background
            WaveformView(
                barHeights = barHeights,
                primaryColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 4.dp)
            )

            // Progress slider (foreground)
            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    progress = newProgress
                    isDragging = true
                },
                onValueChangeFinished = {
                    isDragging = false
                    val mp = mediaPlayer ?: return@Slider
                    val targetMs = (progress * durationMs).toInt()
                    try {
                        mp.seekTo(targetMs)
                        currentPositionMs = targetMs
                    } catch (_: Exception) {}
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.matchParentSize()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time display
        Text(
            text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd G:/android_projects/lifelogger && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL` with no errors.

---

### Task 3: Modify RecordingsScreen.kt — add expand/collapse state and inline player

**Files:**
- Modify: `app/src/main/java/com/sam/lifelogger/ui/RecordingsScreen.kt`

Replace the old global MediaPlayer + direct play behavior with expand/collapse state management. Each list item conditionally renders RecordingPlayer below the header row when expanded.

- [ ] **Step 1: Replace global player state with expand/collapse state**

Remove these lines from RecordingsScreen (lines 38-49 in the original):
```kotlin
var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
var currentlyPlayingId by remember { mutableStateOf<Long?>(null) }

DisposableEffect(Unit) {
    onDispose {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
```

Add after `var recordings`:
```kotlin
var expandedRecordingId by remember { mutableStateOf<Long?>(null) }
```

- [ ] **Step 2: Refactor the list item layout**

Replace the items block (lines 97-159 in the original) with:

```kotlin
                items(recordings, key = { it.id }) { rec ->
                    val isExpanded = expandedRecordingId == rec.id
                    val file = remember(rec.filePath) { File(rec.filePath) }

                    Column {
                        // Header row — always visible, tappable to expand/collapse/switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isExpanded) {
                                        // Tap same expanded row: collapse, stop, reset
                                        expandedRecordingId = null
                                    } else {
                                        // Tap different row: expand it (previous auto-collapses via condition)
                                        expandedRecordingId = rec.id
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = file.name,
                                modifier = Modifier.weight(1f)
                            )

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
```

- [ ] **Step 3: Update handleDeleteClick and handleDeleteAllClick**

Replace the old `handleDeleteClick`:

```kotlin
private suspend fun handleDeleteClick(
    rec: RecordingEntity,
    file: File,
    dao: com.sam.lifelogger.data.RecordingDao,
    expandedId: Long?,
    onExpandedCleared: () -> Unit,
    onListUpdated: (List<RecordingEntity>) -> Unit
) {
    if (expandedId == rec.id) {
        onExpandedCleared() // collapse player (DisposableEffect handles cleanup)
    }

    runCatching {
        if (file.exists()) {
            file.delete()
        }
    }

    dao.delete(rec)
    onListUpdated(dao.getAll())
}
```

Replace the old `handleDeleteAllClick`:

```kotlin
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
```

Note: `handleDeleteAllClick` doesn't need the expandedId parameter because removing all recordings causes the list to empty, and the player composables naturally leave composition when their recording is no longer in the list.

- [ ] **Step 4: Remove unused imports and old helper functions**

Remove these imports (no longer needed):
- `import android.media.MediaPlayer`  (player moved to RecordingPlayer)
- `import android.util.Log`  (only used by handlePlayClick which is deleted)

Remove the old helper function at the bottom of the file:
- `handlePlayClick()` function (lines 190-237 in the original) — delete entirely

- [ ] **Step 5: Verify the final file structure**

The final `RecordingsScreen.kt` should have these functions:
1. `RecordingsScreen()` — the main composable with expand/collapse state
2. `UploadStatusChip()` — unchanged
3. `handleDeleteClick()` — simplified (no MediaPlayer management)
4. `handleDeleteAllClick()` — simplified (no MediaPlayer management)

- [ ] **Step 6: Full compile check**

Run: `cd G:/android_projects/lifelogger && ./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: `BUILD SUCCESSFUL` with no errors or warnings.
