# Continuous Squeeze Design

## Goal

Recognize Pixel 2 XL continuous squeeze through LineageOS Elmyra progress events
without interfering with single, double, or triple squeeze gestures.

## Scope

This pass builds gesture plumbing and a placeholder reminder-mode action. It does
not yet record reminder audio. Reminder recording should be implemented later by
plugging a real recording-mode coordinator into the existing placeholder hooks.

## Final Behavior

Continuous squeeze is treated as a latch, not a physical hold-to-record mode.

1. Elmyra progress reaches the high threshold and stays there for the hold delay.
2. `SqueezeGestureDetector` emits `CONTINUOUS_START`.
3. `SqueezeActionMapper` starts placeholder reminder mode.
4. Physical release still causes the detector to emit `CONTINUOUS_END`, but the
   mapper ignores it while reminder mode is latched.
5. A later single squeeze stops reminder mode.
6. Reminder mode auto-stops after 2 minutes as a guardrail.

This was chosen because the physical continuous pressure stream is not stable
enough for reliable hold-to-stop behavior. It often drops after roughly 4 to 5
seconds even with firm pressure. Latching makes the feature usable while keeping
the continuous squeeze gesture as the start signal for short important notes.

## Pipeline

### Hook Layer

`squeezehook/src/main/java/com/sam/lifelogger/hook/HookMain.kt` hooks:

- `ElmyraService.onGestureDetected`
- `ElmyraService.onGestureProgress`

It broadcasts `com.sam.lifelogger.SQUEEZE_DETECTED` with:

- `event_type = "detected"` for completed squeeze events
- `event_type = "progress"` and `progress = Float` for progress events

The LSPosed module must be enabled and scoped to `org.protonaosp.elmyra`.

### App Receiver Layer

`SqueezeBroadcastReceiver` receives the broadcast and feeds the shared detector:

- detected events call `SqueezeGestureDetector.recordEvent()`
- progress events call `SqueezeGestureDetector.recordProgress(progress)`

### Detector Layer

`SqueezeGestureDetector` emits:

- `SINGLE`
- `DOUBLE`
- `TRIPLE`
- `CONTINUOUS_START`
- `CONTINUOUS_END`

The detector remains policy-free. It does not know about recording modes.

Current constants:

```kotlin
GESTURE_WINDOW_MS = 2000L
CONTINUOUS_HOLD_MS = 600L
CONTINUOUS_RELEASE_DEBOUNCE_MS = 1200L
CONTINUOUS_HIGH_PROGRESS = 0.99f
CONTINUOUS_RELEASE_PROGRESS = 0.20f
LATE_DISCRETE_SUPPRESS_MS = 1000L
```

### Action Layer

`SqueezeActionMapper` owns the current placeholder reminder mode through
`ReminderSqueezeMode`.

Current reminder state behavior:

- `CONTINUOUS_START`: start reminder mode if inactive
- `CONTINUOUS_END`: ignore while reminder mode is active
- `SINGLE` while reminder mode active: stop reminder mode
- timeout: auto-stop after `ReminderSqueezeMode.AUTO_STOP_MS`, currently 120000 ms

The visible UI is notified through `VisibleSqueezeActionBridge`.

## Tests

Relevant tests:

- `SqueezeGestureDetectorTest`
- `ReminderSqueezeModeTest`

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.sam.lifelogger.squeeze.SqueezeGestureDetectorTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.sam.lifelogger.squeeze.ReminderSqueezeModeTest"
```

## Future Integration

When reminder recording is implemented:

- keep `SqueezeGestureDetector` unchanged unless new raw gesture evidence demands it
- replace placeholder reminder callbacks in `SqueezeActionMapper` with calls into
  a recording-mode coordinator
- keep continuous squeeze reserved for reminder start
- keep single squeeze as the current manual stop mechanism while reminder is active,
  unless a dedicated UI stop control is added
- use recording `type = "reminder"` for uploaded reminder chunks

The coordinator should own recording hierarchy policy:

- normal recording is the base layer
- session recording interrupts normal
- reminder recording interrupts session or normal
- after reminder ends, resume the interrupted higher-priority mode first
- after intentional session stop, resume normal only if normal was active before
  the session began
