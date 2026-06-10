# Recording Mode Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real session and reminder recording modes that use the existing recording/upload plumbing and are controlled by squeeze gestures without breaking normal all-day recording.

**Architecture:** Keep gesture detection separate from recording policy. Add a recording-mode coordinator that owns the hierarchy: normal is the base layer, session can interrupt normal, and reminder can interrupt either session or normal. Recording chunks should continue flowing through Room and WorkManager with `RecordingEntity.type` set to `normal`, `session`, or `reminder`.

**Tech Stack:** Kotlin, Android foreground service, MediaRecorder, Room, WorkManager, Jetpack Compose, local JVM unit tests.

---

## Current Facts

- `RecordingService` only supports normal chunked recording today.
- `RecordingEntity` already has `type: String = "normal"`.
- Upload already sends `type` and recorded-at metadata.
- `SqueezeActionMapper` currently maps:
  - `SINGLE` to normal recording toggle by default
  - `DOUBLE` to session placeholder
  - `CONTINUOUS_START` to placeholder reminder mode
  - `CONTINUOUS_END` is ignored while reminder mode is latched
  - `SINGLE` while reminder mode is active stops reminder mode
- The user only requires squeeze behavior while the app is open and visible.

## Proposed File Structure

Create:

- `app/src/main/java/com/sam/lifelogger/recording/RecordingMode.kt`
  enum for `NORMAL`, `SESSION`, `REMINDER`.
- `app/src/main/java/com/sam/lifelogger/recording/RecordingModeCoordinator.kt`
  pure Kotlin state machine for hierarchy decisions.
- `app/src/test/java/com/sam/lifelogger/recording/RecordingModeCoordinatorTest.kt`
  local JVM tests for the hierarchy.

Modify:

- `app/src/main/java/com/sam/lifelogger/recording/RecordingService.kt`
  accept recording type extras and store `RecordingEntity.type` correctly.
- `app/src/main/java/com/sam/lifelogger/squeeze/VisibleSqueezeActionBridge.kt`
  expose foreground calls for session/reminder actions.
- `app/src/main/java/com/sam/lifelogger/squeeze/SqueezeActionMapper.kt`
  route double squeeze and reminder start/stop to the coordinator-facing bridge.
- `app/src/main/java/com/sam/lifelogger/MainActivity.kt`
  own the coordinator instance while visible and call service start/stop.
- `app/src/main/java/com/sam/lifelogger/ui/SettingsScreen.kt`
  keep single/double/triple mapping UI; do not make continuous configurable yet.

Defer unless needed:

- DB columns for `sessionId`, `segmentIndex`, or interruption relationships.
- Server processing changes, except confirming it already respects uploaded `type`.
- Background squeeze behavior.

---

### Task 1: Add Recording Mode Types

**Files:**

- Create: `app/src/main/java/com/sam/lifelogger/recording/RecordingMode.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.sam.lifelogger.recording

enum class RecordingMode(val wireValue: String) {
    NORMAL("normal"),
    SESSION("session"),
    REMINDER("reminder");

    companion object {
        fun fromWireValue(value: String): RecordingMode {
            return entries.firstOrNull { it.wireValue == value } ?: NORMAL
        }
    }
}
```

- [ ] **Step 2: Build**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Test The Recording Mode Coordinator

**Files:**

- Create: `app/src/test/java/com/sam/lifelogger/recording/RecordingModeCoordinatorTest.kt`
- Create: `app/src/main/java/com/sam/lifelogger/recording/RecordingModeCoordinator.kt`

- [ ] **Step 1: Write failing tests first**

```kotlin
package com.sam.lifelogger.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingModeCoordinatorTest {

    @Test
    fun normalToggleStartsAndStopsNormal() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.toggleNormal()

        assertEquals(
            listOf("start:normal", "stop:normal"),
            sink.events
        )
    }

    @Test
    fun sessionInterruptsNormalAndNormalResumesAfterSessionStops() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.toggleSession()
        coordinator.toggleSession()

        assertEquals(
            listOf(
                "start:normal",
                "stop:normal",
                "start:session",
                "stop:session",
                "start:normal"
            ),
            sink.events
        )
    }

    @Test
    fun reminderInterruptsSessionThenSessionResumes() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleSession()
        coordinator.startReminder()
        coordinator.stopReminder()

        assertEquals(
            listOf(
                "start:session",
                "stop:session",
                "start:reminder",
                "stop:reminder",
                "start:session"
            ),
            sink.events
        )
    }

    @Test
    fun reminderInterruptsNormalThenNormalResumes() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.startReminder()
        coordinator.stopReminder()

        assertEquals(
            listOf(
                "start:normal",
                "stop:normal",
                "start:reminder",
                "stop:reminder",
                "start:normal"
            ),
            sink.events
        )
    }

    @Test
    fun singleSqueezeWhileReminderActiveStopsReminderNotNormal() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.toggleNormal()
        coordinator.startReminder()
        assertTrue(coordinator.stopReminder())

        assertTrue(coordinator.isNormalRequested)
        assertEquals(RecordingMode.NORMAL, coordinator.activeMode)
    }

    @Test
    fun duplicateReminderStartDoesNothing() {
        val sink = RecordingSink()
        val coordinator = RecordingModeCoordinator(sink)

        coordinator.startReminder()
        assertFalse(coordinator.startReminder())

        assertEquals(listOf("start:reminder"), sink.events)
    }

    private class RecordingSink : RecordingModeCoordinator.RecordingSink {
        val events = mutableListOf<String>()

        override fun start(mode: RecordingMode) {
            events.add("start:${mode.wireValue}")
        }

        override fun stop(mode: RecordingMode) {
            events.add("stop:${mode.wireValue}")
        }
    }
}
```

- [ ] **Step 2: Run the tests and confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.sam.lifelogger.recording.RecordingModeCoordinatorTest"
```

Expected: fail because `RecordingModeCoordinator` does not exist.

---

### Task 3: Implement The Coordinator

**Files:**

- Create: `app/src/main/java/com/sam/lifelogger/recording/RecordingModeCoordinator.kt`

- [ ] **Step 1: Add minimal implementation**

```kotlin
package com.sam.lifelogger.recording

class RecordingModeCoordinator(
    private val sink: RecordingSink
) {
    interface RecordingSink {
        fun start(mode: RecordingMode)
        fun stop(mode: RecordingMode)
    }

    var isNormalRequested: Boolean = false
        private set

    var isSessionRequested: Boolean = false
        private set

    var activeMode: RecordingMode? = null
        private set

    fun toggleNormal() {
        if (isNormalRequested) {
            isNormalRequested = false
            if (activeMode == RecordingMode.NORMAL) {
                stopActive()
            }
            return
        }

        isNormalRequested = true
        if (activeMode == null) {
            start(RecordingMode.NORMAL)
        }
    }

    fun toggleSession() {
        if (isSessionRequested) {
            isSessionRequested = false
            if (activeMode == RecordingMode.SESSION) {
                stopActive()
                resumeBestAvailable()
            }
            return
        }

        isSessionRequested = true
        if (activeMode == RecordingMode.NORMAL) {
            stopActive()
        }
        if (activeMode == null) {
            start(RecordingMode.SESSION)
        }
    }

    fun startReminder(): Boolean {
        if (activeMode == RecordingMode.REMINDER) return false

        if (activeMode != null) {
            stopActive()
        }
        start(RecordingMode.REMINDER)
        return true
    }

    fun stopReminder(): Boolean {
        if (activeMode != RecordingMode.REMINDER) return false

        stopActive()
        resumeBestAvailable()
        return true
    }

    private fun resumeBestAvailable() {
        when {
            isSessionRequested -> start(RecordingMode.SESSION)
            isNormalRequested -> start(RecordingMode.NORMAL)
        }
    }

    private fun start(mode: RecordingMode) {
        activeMode = mode
        sink.start(mode)
    }

    private fun stopActive() {
        val mode = activeMode ?: return
        sink.stop(mode)
        activeMode = null
    }
}
```

- [ ] **Step 2: Run coordinator tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.sam.lifelogger.recording.RecordingModeCoordinatorTest"
```

Expected: all coordinator tests pass.

---

### Task 4: Let RecordingService Record A Type

**Files:**

- Modify: `app/src/main/java/com/sam/lifelogger/recording/RecordingService.kt`

- [ ] **Step 1: Add service extra**

Add in `companion object`:

```kotlin
const val EXTRA_RECORDING_TYPE = "recording_type"
```

- [ ] **Step 2: Read the type in `onStartCommand`**

Replace the current `ACTION_START` branch with:

```kotlin
ACTION_START -> {
    Log.d("RecordingService", "ACTION_START received")
    val minutes = intent.getIntExtra(EXTRA_CHUNK_MINUTES, DEFAULT_CHUNK_MINUTES)
    val mode = RecordingMode.fromWireValue(
        intent.getStringExtra(EXTRA_RECORDING_TYPE) ?: RecordingMode.NORMAL.wireValue
    )
    startForegroundServiceRecording(minutes, mode)
}
```

Add import if needed:

```kotlin
import com.sam.lifelogger.recording.RecordingMode
```

Because this file is in the same package, the import is optional.

- [ ] **Step 3: Change `startForegroundServiceRecording` signature**

```kotlin
private fun startForegroundServiceRecording(
    chunkMinutes: Int,
    mode: RecordingMode
) {
```

- [ ] **Step 4: Use the mode in the DB row**

Replace the `RecordingEntity(...)` insert with:

```kotlin
RecordingEntity(
    filePath = file.absolutePath,
    createdAt = System.currentTimeMillis(),
    type = mode.wireValue
)
```

- [ ] **Step 5: Build**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Route Foreground Actions Through MainActivity

**Files:**

- Modify: `app/src/main/java/com/sam/lifelogger/squeeze/VisibleSqueezeActionBridge.kt`
- Modify: `app/src/main/java/com/sam/lifelogger/MainActivity.kt`

- [ ] **Step 1: Extend the bridge interface**

Add methods to `VisibleSqueezeActionBridge.Handler`:

```kotlin
fun toggleSessionRecording() {}
fun startReminderRecording() {}
fun stopReminderRecording() {}
```

Add dispatch methods:

```kotlin
fun dispatchToggleSessionRecording(): Boolean {
    val visibleHandler = handler ?: return false
    visibleHandler.toggleSessionRecording()
    return true
}

fun dispatchStartReminderRecording(): Boolean {
    val visibleHandler = handler ?: return false
    visibleHandler.startReminderRecording()
    return true
}

fun dispatchStopReminderRecording(): Boolean {
    val visibleHandler = handler ?: return false
    visibleHandler.stopReminderRecording()
    return true
}
```

- [ ] **Step 2: Add a coordinator in `MainActivity`**

In `MainActivity`, create a `RecordingModeCoordinator` field that starts/stops
`RecordingService` with the proper type. Keep it activity-owned for this pass
because the user currently only requires squeeze gestures while the app is open.

Use this shape:

```kotlin
private val recordingModeCoordinator by lazy {
    RecordingModeCoordinator(
        object : RecordingModeCoordinator.RecordingSink {
            override fun start(mode: RecordingMode) {
                startRecordingMode(mode)
            }

            override fun stop(mode: RecordingMode) {
                stopRecordingMode(mode)
            }
        }
    )
}
```

Add helpers:

```kotlin
private fun startRecordingMode(mode: RecordingMode) {
    val intent = Intent(this, RecordingService::class.java).apply {
        action = RecordingService.ACTION_START
        putExtra(RecordingService.EXTRA_RECORDING_TYPE, mode.wireValue)
    }
    ContextCompat.startForegroundService(this, intent)
}

private fun stopRecordingMode(mode: RecordingMode) {
    val intent = Intent(this, RecordingService::class.java).apply {
        action = RecordingService.ACTION_STOP
    }
    startService(intent)
}
```

Note: this first pass still uses one service, so `stopRecordingMode(mode)` cannot
stop only a specific mode. The coordinator serializes modes, so that is acceptable.

- [ ] **Step 3: Wire bridge callbacks**

In the existing `VisibleSqueezeActionBridge.register(...)` handler:

```kotlin
override fun toggleRecording() {
    recordingModeCoordinator.toggleNormal()
}

override fun toggleSessionRecording() {
    recordingModeCoordinator.toggleSession()
}

override fun startReminderRecording() {
    recordingModeCoordinator.startReminder()
}

override fun stopReminderRecording() {
    recordingModeCoordinator.stopReminder()
}
```

Keep toast/log messages clear:

- normal started/stopped
- session started/stopped
- reminder started/stopped

- [ ] **Step 4: Build**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 6: Replace Placeholder Gesture Actions

**Files:**

- Modify: `app/src/main/java/com/sam/lifelogger/squeeze/SqueezeActionMapper.kt`

- [ ] **Step 1: Route double squeeze session action**

In the `ACTION_TOGGLE_SESSION` branch, replace the placeholder toast with:

```kotlin
if (!VisibleSqueezeActionBridge.dispatchToggleSessionRecording()) {
    Log.w(TAG, "Ignoring session toggle because Lifelogger is not visible")
    Toast.makeText(
        context,
        "Open Lifelogger to use squeeze session recording",
        Toast.LENGTH_SHORT
    ).show()
}
```

- [ ] **Step 2: Route reminder start**

In the `ReminderSqueezeMode(onStart = ...)` callback, dispatch the real bridge
method instead of `dispatchContinuousSqueezeStarted()`:

```kotlin
if (!VisibleSqueezeActionBridge.dispatchStartReminderRecording()) {
    Log.w(TAG, "Reminder recording started while Lifelogger is not visible")
}
```

- [ ] **Step 3: Route reminder stop**

In the `ReminderSqueezeMode(onStop = ...)` callback, dispatch:

```kotlin
if (!VisibleSqueezeActionBridge.dispatchStopReminderRecording()) {
    Log.w(TAG, "Reminder recording stopped while Lifelogger is not visible")
}
```

- [ ] **Step 4: Decide timeout behavior**

For this pass, timeout should call the same stop path:

```kotlin
if (!VisibleSqueezeActionBridge.dispatchStopReminderRecording()) {
    Log.w(TAG, "Reminder recording timed out while Lifelogger is not visible")
}
VisibleSqueezeActionBridge.dispatchReminderModeTimedOut()
```

If this causes duplicate UI messages, prefer one visible message from
`MainActivity`.

- [ ] **Step 5: Run squeeze tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.sam.lifelogger.squeeze.*"
```

Expected: all squeeze tests pass.

---

### Task 7: Add UI Stop Affordance For Reminder Mode

**Files:**

- Modify: `app/src/main/java/com/sam/lifelogger/MainActivity.kt`

- [ ] **Step 1: Add observable reminder/session UI state**

Use Compose state in `MainActivity` or pass coordinator state into `MainScreen`.
Keep the first pass simple:

- show active mode text
- show a stop button when reminder mode is active
- stop button calls `recordingModeCoordinator.stopReminder()`

- [ ] **Step 2: Verify mobile layout**

The stop control must not overlap existing recording controls. Keep it compact.

- [ ] **Step 3: Build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 8: Device Verification

- [ ] **Step 1: Install**

```powershell
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

- [ ] **Step 2: Watch logs**

```powershell
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -c
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -s SqueezeHook SqueezeReceiver SqueezeDetector SqueezeActionMapper MainActivity RecordingService UploadWorker
```

- [ ] **Step 3: Physical tests**

With the app open and visible:

- single squeeze starts normal recording
- single squeeze stops normal recording
- double squeeze starts session recording with `type=session`
- double squeeze stops session recording
- normal -> double stops normal and starts session
- session stop resumes normal if normal was active before session
- continuous starts reminder recording with `type=reminder`
- physical release does not stop reminder recording
- single squeeze stops reminder recording
- reminder stop resumes interrupted session or normal
- upload rows keep the correct `type`

- [ ] **Step 4: Final verification**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Expected: both commands succeed.

## Notes And Risks

- The current `RecordingService` is not designed for simultaneous recordings.
  The coordinator must serialize modes.
- If `MainActivity` is recreated, an activity-owned coordinator may lose state.
  This is acceptable for a first visible-app-only pass, but a future robust version
  should persist active/requested mode in a service or repository.
- The current DB can represent type but not session grouping. If session chunks
  need to be associated across reminder interruptions, add `sessionId` and
  `segmentIndex` in a later migration.
- Do not modify `SqueezeGestureDetector` for recording policy.
