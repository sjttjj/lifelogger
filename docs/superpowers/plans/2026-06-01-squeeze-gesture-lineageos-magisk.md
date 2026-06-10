# Squeeze Gesture LineageOS + Magisk Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bridge the gap between LineageOS Elmyra hardware squeeze detection and the Lifelogger app's existing squeeze infrastructure using a root-owned logcat watcher (Magisk module) and an in-app BroadcastReceiver.

**Architecture:** LineageOS's `org.protonaosp.elmyra` service detects squeezes at the hardware level and logs "Gesture detected hostSuspended=false" but never fires `ACTION_ASSIST`. A Magisk module's `service.sh` runs a continuous `logcat` pipeline that matches these log lines and issues `am broadcast` with a custom action string. The app's `SqueezeBroadcastReceiver` catches this broadcast, feeds raw events into the existing `SqueezeGestureDetector` (800ms window), and resolves single/double/triple squeezes via `SqueezeActionMapper`.

**Tech Stack:** Android 15 (minSdk 35), Kotlin/Jetpack Compose, Magisk module (shell/busybox), ProtonAOSP Elmyra, logcat

---

## File Structure Summary

| # | Action | File | Responsibility |
|---|--------|------|----------------|
| 1 | Create | `app/src/main/java/com/sam/lifelogger/squeeze/SqueezeBroadcastReceiver.kt` | Receive custom broadcast, accumulate squeeze events via shared detector, execute mapped action |
| 2 | Modify | `app/src/main/AndroidManifest.xml` | Register BroadcastReceiver with exported=true and intent-filter for custom action |
| 3 | Delete | `app/src/main/java/com/sam/lifelogger/squeeze/SqueezeGestureDetectorShared.kt` | Dead code from prior approach (multi-activity singleton) |
| 4 | Create | `squeeze-module/module.prop` | Magisk module metadata |
| 5 | Create | `squeeze-module/service.sh` | Continuous logcat watcher that broadcasts custom intents |
| 6 | Create | `squeeze-module/uninstall.sh` | Cleanup on module removal |
| 7 | Create | `squeeze-module/META-INF/com/google/android/update-binary` | Standard Magisk update-binary |
| 8 | Create | `squeeze-module/META-INF/com/google/android/updater-script` | Standard Magisk marker script |

---

### Task 1: Create `SqueezeBroadcastReceiver.kt`

**Files:**
- Create: `app/src/main/java/com/sam/lifelogger/squeeze/SqueezeBroadcastReceiver.kt`
- Depends on: `SqueezeGestureDetector`, `SqueezeActionMapper` (both exist)

- [ ] **Step 1: Write `SqueezeBroadcastReceiver.kt`**

```kotlin
package com.sam.lifelogger.squeeze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Receives custom [ACTION_SQUEEZE_DETECTED] broadcasts from the Magisk module's
 * logcat watcher and feeds them into the shared [SqueezeGestureDetector] for
 * single/double/triple accumulation.
 *
 * Uses [goAsync] to keep the receiver alive for the full GESTURE_WINDOW_MS
 * (800 ms). Gesture resolution callback finishes all pending results.
 */
class SqueezeBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SqueezeReceiver"

        /** Custom action fired by the Magisk module on each hardware squeeze. */
        const val ACTION_SQUEEZE_DETECTED = "com.sam.lifelogger.SQUEEZE_DETECTED"

        /**
         * Shared detector instance that lives across multiple broadcast deliveries.
         * Nulled after each gesture window completes.
         *
         * Thread safety: all [onReceive] invocations arrive on the main thread,
         * and the detector's Handler posts its flush callback to the main looper.
         * No additional synchronization is needed.
         */
        private var pendingResults = mutableListOf<BroadcastReceiver.PendingResult>()

        private var detector: SqueezeGestureDetector? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_SQUEEZE_DETECTED != intent.action) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            Log.d(TAG, "Screen off, ignoring squeeze")
            return
        }

        Log.d(TAG, "Squeeze detected, accumulating")

        // Keep receiver alive across the gesture window
        val pendingResult = goAsync()
        pendingResults.add(pendingResult)

        // Lazy-create shared detector — one-shot per gesture window
        if (detector == null) {
            detector = SqueezeGestureDetector { gesture ->
                Log.d(TAG, "Gesture resolved: $gesture")
                SqueezeActionMapper.execute(context, gesture)

                // Finish all pending results to release the receiver
                val toFinish = pendingResults.toList()
                pendingResults.clear()
                toFinish.forEach { it.finish() }

                // Detector is exhausted — next squeeze starts fresh
                detector = null
            }
        }

        detector?.recordEvent()
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `cd G:\android_projects\lifelogger && ./gradlew :app:compileDebugKotlin 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL (no errors referencing SqueezeBroadcastReceiver)

- [ ] **Step 3: Commit**

Not yet — wait until manifest modification is done. Both app-side changes will be committed together.

---

### Task 2: Register BroadcastReceiver in AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the `<receiver>` element inside `<application>`**

Insert after the squeeze handler activity (around line 54, before `</application>`):

```xml

        <!-- Squeeze gesture broadcast receiver — receives custom intents from
             LineageOS Magisk module logcat watcher -->
        <receiver
            android:name=".squeeze.SqueezeBroadcastReceiver"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="com.sam.lifelogger.SQUEEZE_DETECTED" />
            </intent-filter>
        </receiver>
```

The relevant section of the manifest should now look like:

```xml
        <!-- Squeeze gesture handler — receives ACTION_ASSIST from Active Edge -->
        <activity
            android:name=".squeeze.SqueezeHandlerActivity"
            android:exported="true"
            android:launchMode="singleInstance"
            android:theme="@android:style/Theme.NoDisplay"
            android:excludeFromRecents="true"
            android:taskAffinity="">
            <intent-filter>
                <action android:name="android.intent.action.ASSIST" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- Squeeze gesture broadcast receiver — receives custom intents from
             LineageOS Magisk module logcat watcher -->
        <receiver
            android:name=".squeeze.SqueezeBroadcastReceiver"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="com.sam.lifelogger.SQUEEZE_DETECTED" />
            </intent-filter>
        </receiver>
    </application>

</manifest>
```

- [ ] **Step 2: Verify manifest merges correctly**

Run: `cd G:\android_projects\lifelogger && ./gradlew :app:processDebugManifest 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit both app-side changes**

```bash
cd G:\android_projects\lifelogger
git add app/src/main/java/com/sam/lifelogger/squeeze/SqueezeBroadcastReceiver.kt
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add SqueezeBroadcastReceiver for Magisk module bridge

Register a BroadcastReceiver with exported=true and intent-filter for
com.sam.lifelogger.SQUEEZE_DETECTED. The receiver uses goAsync() to
stay alive across the 800ms gesture window and shares a single
SqueezeGestureDetector instance via companion object.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: Delete `SqueezeGestureDetectorShared.kt`

**Files:**
- Delete: `app/src/main/java/com/sam/lifelogger/squeeze/SqueezeGestureDetectorShared.kt`
- Verify: no remaining references to `SqueezeGestureDetectorShared` anywhere in the codebase

- [ ] **Step 1: Check for remaining references**

Run: `grep -r "SqueezeGestureDetectorShared" app/src/ 2>/dev/null || echo "No references found"`
Expected: "No references found" (the shared singleton pattern has been superseded by the companion-object-based singleton in `SqueezeBroadcastReceiver`)

- [ ] **Step 2: Delete the file**

```bash
rm app/src/main/java/com/sam/lifelogger/squeeze/SqueezeGestureDetectorShared.kt
```

- [ ] **Step 3: Verify compilation**

Run: `cd G:\android_projects\lifelogger && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd G:\android_projects\lifelogger
git add app/src/main/java/com/sam/lifelogger/squeeze/SqueezeGestureDetectorShared.kt
git commit -m "refactor: remove SqueezeGestureDetectorShared dead code

The shared singleton pattern for multi-activity communication has been
replaced by the companion-object-based singleton in SqueezeBroadcastReceiver.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: Create Magisk module `module.prop`

**Files:**
- Create: `squeeze-module/module.prop`

- [ ] **Step 1: Create the directory and file**

```bash
mkdir -p squeeze-module
```

- [ ] **Step 2: Write `squeeze-module/module.prop`**

```properties
id=squeeze-bridge
name=Squeeze Bridge for Lifelogger
version=v1.0
versionCode=1
author=Lifelogger
description=Watches logcat for ProtonAOSP Elmyra gesture detection and broadcasts custom intents to the Lifelogger app.
```

- [ ] **Step 3: Commit**

Not yet — all Magisk module files will be committed together in a final commit.

---

### Task 5: Create Magisk module `service.sh`

**Files:**
- Create: `squeeze-module/service.sh`

- [ ] **Step 1: Write `squeeze-module/service.sh`**

```bash
#!/system/bin/sh
# service.sh — Squeeze Bridge for Lifelogger
# Magisk's late_start service — runs after boot complete.
#
# Continuously monitors logcat output from org.protonaosp.elmyra and
# fires a custom broadcast intent each time a hardware squeeze is detected.

MODDIR=${0%/*}

SQUEEZE_ACTION="com.sam.lifelogger.SQUEEZE_DETECTED"
SQUEEZE_COMPONENT="com.sam.lifelogger/.squeeze.SqueezeBroadcastReceiver"
TAG="SqueezeBridge"

log -t "$TAG" "Starting squeeze bridge watcher"

# Outer loop: restart logcat if the pipe breaks (e.g. logcatd crash/rotation)
while true; do
    logcat -s org.protonaosp.elmyra -b main 2>/dev/null | while read -r line; do
        case "$line" in
            *"Gesture detected"*)
                am broadcast \
                    -a "$SQUEEZE_ACTION" \
                    -n "$SQUEEZE_COMPONENT" \
                    --user 0 \
                    2>/dev/null
                ;;
        esac
    done

    # If we reach here, logcat pipe broke — wait and restart
    sleep 2
    log -t "$TAG" "Restarting logcat watcher after pipe break"
done
```

Key design notes:
- **Outer `while true`** with **inner `while read`** handles logcat pipe breaks gracefully. If logcat crashes or rotates, the inner loop exits and the outer loop restarts after a 2-second sleep.
- **`-s org.protonaosp.elmyra -b main`** filters to only Elmyra's tag and the main buffer, minimizing CPU overhead.
- **`2>/dev/null`** on both logcat and am broadcast suppresses stderr (which in shell is "noisy but harmless" on some Magisk/busybox combinations).
- **`--user 0`** ensures the broadcast is delivered to the primary user (works on work-profile and multi-user setups).
- **`case ... in *"Gesture detected"*`** uses a glob pattern match rather than grep, avoiding an extra fork per line.
- **`log -t "$TAG"`** writes to logcat with a proper tag for debugging.

- [ ] **Step 2: Verify shell syntax**

Run: `bash -n squeeze-module/service.sh`
Expected: no output (syntax is valid)

---

### Task 6: Create Magisk module `uninstall.sh`

**Files:**
- Create: `squeeze-module/uninstall.sh`

- [ ] **Step 1: Write `squeeze-module/uninstall.sh`**

```bash
#!/system/bin/sh
# uninstall.sh — Squeeze Bridge for Lifelogger
# Cleans up when the module is removed via Magisk Manager.

TAG="SqueezeBridge"

log -t "$TAG" "Squeeze Bridge module removed. No persistent changes to clean up."
exit 0
```

This is minimal because the module is stateless — it only runs a logcat watcher that fires broadcasts. No files are created outside the module directory, no system properties are set, and no SELinux rules are modified.

---

### Task 7: Create Magisk module `META-INF/com/google/android/update-binary`

**Files:**
- Create: `squeeze-module/META-INF/com/google/android/update-binary`

- [ ] **Step 1: Create the directory**

```bash
mkdir -p squeeze-module/META-INF/com/google/android
```

- [ ] **Step 2: Write `update-binary`**

```bash
#!/sbin/sh
#
# Standard Magisk module update-binary
# This is a template that works for all Magisk 20.4+ modules.

#################
# Initialization
#################

umask 022

# echo to log
echo_write() {
    echo "$1"
    echo "$1" > /dev/kmsg 2>/dev/null || true
}

# Get the parent directory of this script
OUTFD=$2
ZIPFILE=$3

mount /data 2>/dev/null
mount /system 2>/dev/null

###################
# Loading functions
###################

. /data/adb/magisk/util_functions.sh

[ $? -ne 0 ] && exit 1

#################
# Installation
#################

install_module
exit 0
```

---

### Task 8: Create Magisk module `updater-script`

**Files:**
- Create: `squeeze-module/META-INF/com/google/android/updater-script`

- [ ] **Step 1: Write `updater-script`**

```
#MAGISK
```

That's it — `#MAGISK` is the only line Magisk needs to recognize this as a Magisk module zip. The real installation logic lives in `update-binary`.

---

### Task 9: Create module installation script (customize.sh)

While not in the original requirements, Magisk modules typically use a `customize.sh` to set up the `service.sh` properly. Let's add this to ensure `service.sh` is executable and the module is well-formed.

**Files:**
- Create: `squeeze-module/customize.sh`

- [ ] **Step 1: Write `squeeze-module/customize.sh`**

```bash
#!/system/bin/sh
# customize.sh — Squeeze Bridge for Lifelogger
# Called by Magisk's update-binary after extracting module files.

# Ensure service.sh has executable permissions
set_perm $MODPATH/service.sh 0 0 0755

# No SELinux modifications needed — the app receiver is already exported=true
```

---

### Task 10: Package and install the Magisk module

- [ ] **Step 1: Create the module zip**

```bash
cd G:\android_projects\lifelogger
zip -r squeeze-module.zip squeeze-module/ -x "*.DS_Store"
```

- [ ] **Step 2: Verify the zip contents**

```bash
unzip -l squeeze-module.zip
```

Expected output should list all files:
```
squeeze-module/module.prop
squeeze-module/service.sh
squeeze-module/uninstall.sh
squeeze-module/customize.sh
squeeze-module/META-INF/com/google/android/update-binary
squeeze-module/META-INF/com/google/android/updater-script
```

- [ ] **Step 3: Commit all Magisk module files**

```bash
cd G:\android_projects\lifelogger
git add squeeze-module/
git commit -m "feat: add Magisk module for LineageOS squeeze-to-broadcast bridge

Monitors logcat for ProtonAOSP Elmyra gesture detection and fires
custom intents to the Lifelogger SqueezeBroadcastReceiver.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

- [ ] **Step 4: Transfer zip to device and install**

```bash
# Push to device
adb push squeeze-module.zip /sdcard/Download/

# Install via Magisk Manager (manual step on device):
# 1. Open Magisk Manager
# 2. Tap Modules > Install from storage
# 3. Select squeeze-module.zip
# 4. Reboot when prompted
```

---

## Testing & Verification

### App-side verification

1. **Build and install the app** with the new BroadcastReceiver:
   ```bash
   cd G:\android_projects\lifelogger
   ./gradlew :app:installDebug
   ```

2. **Verify receiver is registered** (on device):
   ```bash
   adb shell dumpsys package com.sam.lifelogger | grep -A3 SqueezeBroadcastReceiver
   ```
   Expected output shows the receiver with intent-filter for `com.sam.lifelogger.SQUEEZE_DETECTED`.

3. **Test the receiver directly** (before installing the module):
   ```bash
   adb shell am broadcast -a com.sam.lifelogger.SQUEEZE_DETECTED -n com.sam.lifelogger/.squeeze.SqueezeBroadcastReceiver --user 0
   ```
   Expected: logcat shows `SqueezeReceiver: Squeeze detected, accumulating` and after 800ms shows `SqueezeReceiver: Gesture resolved: SINGLE` and a Toast appears.

4. **Test double-squeeze** by running the broadcast twice within 800ms:
   ```bash
   adb shell am broadcast -a com.sam.lifelogger.SQUEEZE_DETECTED -n com.sam.lifelogger/.squeeze.SqueezeBroadcastReceiver --user 0
   # Wait ~200ms, then:
   adb shell am broadcast -a com.sam.lifelogger.SQUEEZE_DETECTED -n com.sam.lifelogger/.squeeze.SqueezeBroadcastReceiver --user 0
   ```
   Expected: logcat shows `Gesture resolved: DOUBLE`.

### Module-side verification

1. **After module installation and reboot**, check the service is running:
   ```bash
   adb shell ps -A | grep logcat
   ```
   There should be a logcat process owned by root.

2. **Test end-to-end** by physically squeezing the Pixel 2 XL:
   ```bash
   adb logcat -s SqueezeBridge SqueezeReceiver
   ```
   Expected output on squeeze:
   ```
   SqueezeBridge: (no direct log — service.sh uses `log` which may not show on the same buffer)
   ```
   Actually, since the module's `log` calls use logcat too, they should appear:
   ```
   SqueezeBridge: Starting squeeze bridge watcher
   ```
   And the app's tag:
   ```
   SqueezeReceiver: Squeeze detected, accumulating
   SqueezeReceiver: Gesture resolved: SINGLE
   ```

3. **Check that Elmyra lines are visible** (confirming the logcat filter works):
   ```bash
   adb logcat -s org.protonaosp.elmyra
   ```
   Expected on each squeeze: `Gesture detected hostSuspended=false`

---

## Edge Cases & Failure Modes

| Scenario | Behavior |
|----------|----------|
| App not installed when module fires | `am broadcast` fails silently (2>/dev/null), module keeps running |
| Screen off | `SqueezeBroadcastReceiver` checks `PowerManager.isInteractive()` and drops the event |
| Multiple rapid squeezes beyond triple | `SqueezeGestureDetector` caps at `count >= 3` → TRIPLE |
| logcat crashes / rotates | Outer `while true` catches the pipe break, sleeps 2s, restarts |
| App process killed by OOM | Broadcast wakes the process on next squeeze (exported receiver) |
| Receiver goAsync timeout (10s default) | Gesture window is 800ms — well within the 10s limit |
| Device reboots | Magisk `late_start` service restarts automatically |
| User switches to guest profile | `--user 0` targets the primary user. Guest profile doesn't receive broadcasts. |

---

## Files That Remain Unchanged (Intentional)

- **`SqueezeHandlerActivity.kt`** — Still registered in the manifest for `ACTION_ASSIST`. Harmless if `ACTION_ASSIST` is never fired. Could be removed in a future cleanup if the Magisk approach proves reliable, but keeping it avoids breaking any device where Elmyra *does* fire ASSIST (e.g., stock Pixel firmware).
- **`SqueezeGestureDetector.kt`** and **`SqueezeActionMapper.kt`** — No changes needed; the new receiver consumes these existing components.
- **Settings UI** — Already remaps single/double/triple actions. No changes needed.