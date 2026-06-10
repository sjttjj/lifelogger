# Recording Inline Player & Live Waveform

**Date:** 2026-05-28
**Status:** Design (ready for implementation)

## Overview

Add an inline expandable audio player to the RecordingsScreen that shows a play/pause button, a combined waveform + scrubbable progress bar, and time display. The waveform is generated live from Android's `Visualizer` API during playback — no pre-processing needed.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Drawer style | Inline expand (within list) | Keeps context, matches list UX |
| Waveform source | Live `Visualizer` API | No pre-processing, simpler than decoding AAC to PCM |
| Waveform style | Minimal bars (3px wide, primary color at 40–70% alpha) | User-approved — readable without being distracting |
| Waveform + bar overlap | Waveform Canvas behind Material `Slider` | Simplest to implement, standard UX |
| Expand behavior | Single expand at a time | One expanded player visible |
| Architecture | Extracted composables (RecordingPlayer + WaveformView) | Clean separation, ViewModel-ready interface for future adoption |
| State pattern | Composable-level state with lambda callbacks | Simple for personal use; state + callbacks map 1:1 to ViewModel StateFlow + functions |

## Behavioural Requirements

1. **Tap a collapsed recording row** → expand it inline, showing the player panel below the filename/info header
2. **Tap the play/pause button** → start/stop playback
3. **Drag the slider** → scrub through the audio
4. **Tap the expanded row's header area** (filename row, not the play button or slider) → collapse, stop playback, reset position to 0:00
5. **Tap a different recording** → collapse current (stop + reset), expand the new one (show player, reset position)
6. **Playback ends naturally** → stop, show play button, keep expanded, reset position to 0:00
7. **Delete or status chip area** → no expansion interaction (existing delete behavior preserved)
8. **Scroll gestures** → no expand/collapse triggers (standard `clickable` behavior)

## Component Architecture

### Existing — RecordingsScreen (modified)

State added:
- `expandedRecordingId: Long?` — which recording is currently expanded (null = none)
- `playerResetKey: Int` — bumped on collapse to force fresh player creation

Changes:
- Each list item's `Row` is split into two click zones when expanded:
  - **Header zone** (filename, status chip, size, delete) — `clickable` for collapse
  - **Player zone** (the RecordingPlayer composable) — manages its own controls
- `items()` key uses `it.id` (already done), plus the player is conditional on `expandedRecordingId == rec.id`

### New — RecordingPlayer.kt

A `@Composable` that takes:
- `file: File` — the audio file to play
- `isActive: Boolean` — whether this is the currently expanded item
- `onReset: () -> Unit` — callback from parent to trigger collapse

Internal state:
- `mediaPlayer: MediaPlayer?` — created in `DisposableEffect` keyed on file path
- `isPlaying: Boolean` — tracks play state
- `progress: Float` — 0f..1f, updated by a coroutine polling `mediaPlayer.currentPosition`
- `duration: Long` — total duration from media player

Layout (inside a `Column`):
1. **Row**: Play/pause `IconButton` (36dp circle) + `Box` containing `WaveformView` (background) + `Slider` (foreground, overlapped) + `Text` time display
2. The `Slider` value is bound to `progress`, and `onValueChange` calls `mediaPlayer.seekTo()`

Lifecycle (`DisposableEffect`):
- On creation: initialize `MediaPlayer` with file, prepare, wire up `Visualizer` via audio session ID
- On cleanup: stop both, release both, null out

### New — WaveformView.kt

A `@Composable` that takes:
- `mediaPlayerSessionId: Int` — audio session ID to attach Visualizer to
- `isPlaying: Boolean` — controls whether Visualizer is actively capturing

Internal:
- `Visualizer` created in `DisposableEffect` with the given audio session ID
- `barHeights: List<Float>` state — updated by `setDataCaptureListener` callback
- Captures 128 FFT buckets, down-sampled to ~32 bars
- Each bar: 3dp wide, 3dp gap, height mapped from FFT magnitude, drawn via `Canvas.drawRect`
- Color: `MaterialTheme.colorScheme.primary` at ~0.5 alpha

Lifecycle:
- Visualizer is released in `onDispose`
- When `isPlaying` becomes false, last capture remains visible (frozen waveform)

### Waveform + Progress Bar Overlap

```kotlin
Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
    WaveformView(...)  // fills the Box
    Slider(            // overlays on top
        value = progress,
        onValueChange = { seekTo(it) },
        modifier = Modifier.matchParentSize()
    )
}
```

The `Slider`'s track has a transparent background (or very subtle), so the waveform bars show through. The scrub handle and active track indicator are drawn on top.

## File Changes Summary

| File | Action |
|------|--------|
| `RecordingScreen.kt` | Modify — add expand/collapse state, conditional player rendering, split click zones |
| `RecordingPlayer.kt` | Create — expandable player composable |
| `WaveformView.kt` | Create — Canvas-based live waveform renderer |

No changes to: data layer, Room database, ApiClient, RecordingService, theme, or navigation.

## Future ViewModel Path

To adopt a ViewModel later:
1. Extract `expandedRecordingId`, `isPlaying`, `progress`, `playerError` into `RecordingsViewModel`
2. Expose as `StateFlow`, collect in composable via `collectAsState()`
3. Replace lambda callbacks with ViewModel function calls
4. `MediaPlayer` still lives in RecordingPlayer (it's UI-bound), but state flows through ViewModel
