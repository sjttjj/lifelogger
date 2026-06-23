package com.sam.lifelogger.squeeze

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Maps a squeeze gesture to an app action based on user preferences.
 *
 * Recording toggles are foreground-only: MainActivity registers a visible
 * handler while resumed, and this mapper dispatches through that handler.
 */
object SqueezeActionMapper {

    private const val TAG = "SqueezeActionMapper"
    private const val PREFS_NAME = "lifelogger_prefs"

    const val ACTION_TOGGLE_RECORDING = "toggle_recording"
    const val ACTION_TOGGLE_SESSION = "toggle_session"
    const val ACTION_QUICK_NOTE = "quick_note"
    const val ACTION_NONE = "none"

    val DEFAULT_MAP = mapOf(
        SqueezeGesture.SINGLE to ACTION_TOGGLE_RECORDING,
        SqueezeGesture.DOUBLE to ACTION_TOGGLE_SESSION,
        SqueezeGesture.TRIPLE to ACTION_NONE
    )

    private val reminderMode = ReminderSqueezeMode(
        onStart = {},
        onStop = {
            if (!VisibleSqueezeActionBridge.dispatchStopReminderRecording()) {
                Log.w(TAG, "Reminder recording stopped while Lifelogger is not visible")
            }
        },
        onTimeout = {
            if (!VisibleSqueezeActionBridge.dispatchStopReminderRecording()) {
                Log.w(TAG, "Reminder recording timed out while Lifelogger is not visible")
            }
            if (!VisibleSqueezeActionBridge.dispatchReminderModeTimedOut()) {
                Log.w(TAG, "Reminder mode timed out while Lifelogger is not visible")
            }
        }
    )

    private fun getAction(context: Context, gesture: SqueezeGesture): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (gesture) {
            SqueezeGesture.SINGLE -> "squeeze_single"
            SqueezeGesture.DOUBLE -> "squeeze_double"
            SqueezeGesture.TRIPLE -> "squeeze_triple"
            SqueezeGesture.CONTINUOUS_START,
            SqueezeGesture.CONTINUOUS_END -> return ACTION_QUICK_NOTE
        }
        val default = DEFAULT_MAP[gesture] ?: ACTION_NONE
        return prefs.getString(key, default) ?: default
    }

    fun execute(context: Context, gesture: SqueezeGesture) {
        when (gesture) {
            SqueezeGesture.CONTINUOUS_START -> {
                continuousSqueezeStarted(context)
                return
            }
            SqueezeGesture.CONTINUOUS_END -> {
                continuousSqueezeEnded(context)
                return
            }
            SqueezeGesture.SINGLE,
            SqueezeGesture.DOUBLE,
            SqueezeGesture.TRIPLE -> Unit
        }

        if (gesture == SqueezeGesture.SINGLE &&
            VisibleSqueezeActionBridge.dispatchStopReminderRecording()
        ) {
            reminderMode.cancel()
            return
        }

        val action = getAction(context, gesture)

        when (action) {
            ACTION_TOGGLE_RECORDING -> {
                toggleRecording(context)
                showToast(context, gesture, "Recording toggled")
            }
            ACTION_TOGGLE_SESSION -> {
                if (!VisibleSqueezeActionBridge.dispatchToggleSessionRecording()) {
                    Log.w(TAG, "Ignoring session toggle because Lifelogger is not visible")
                    Toast.makeText(
                        context,
                        "Open Lifelogger to use squeeze session recording",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            ACTION_QUICK_NOTE -> {
                showToast(context, gesture, "Quick note - coming soon")
            }
            ACTION_NONE -> {
                showToast(context, gesture, "No action assigned")
            }
        }
    }

    private fun continuousSqueezeStarted(context: Context) {
        if (reminderMode.isActive) {
            Log.d(TAG, "Ignoring continuous squeeze start because reminder mode is already active")
            return
        }

        if (VisibleSqueezeActionBridge.dispatchStartReminderRecording()) {
            reminderMode.start()
        } else {
            Log.w(TAG, "Ignoring reminder start because Lifelogger is not visible or reminder is already active")
        }
    }

    private fun continuousSqueezeEnded(context: Context) {
        if (reminderMode.isActive) {
            Log.d(TAG, "Ignoring physical continuous squeeze end while reminder mode is latched")
        } else {
            Toast.makeText(
                context,
                "Reminder squeeze ended",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun toggleRecording(context: Context) {
        if (!VisibleSqueezeActionBridge.dispatchToggleRecording()) {
            Log.w(TAG, "Ignoring recording toggle because Lifelogger is not visible")
            Toast.makeText(
                context,
                "Open Lifelogger to use squeeze recording",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    const val EXTRA_SQUEEZE_TOGGLE_RECORDING = "squeeze_toggle_recording"
    const val EXTRA_SQUEEZE_ACTION = "squeeze_action"

    private fun showToast(context: Context, gesture: SqueezeGesture, message: String) {
        val label = when (gesture) {
            SqueezeGesture.SINGLE -> "Single squeeze"
            SqueezeGesture.DOUBLE -> "Double squeeze"
            SqueezeGesture.TRIPLE -> "Triple squeeze"
            SqueezeGesture.CONTINUOUS_START -> "Continuous squeeze"
            SqueezeGesture.CONTINUOUS_END -> "Continuous squeeze"
        }
        Toast.makeText(context, "$label: $message", Toast.LENGTH_SHORT).show()
    }

    fun setMapping(context: Context, gesture: SqueezeGesture, action: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (gesture) {
            SqueezeGesture.SINGLE -> "squeeze_single"
            SqueezeGesture.DOUBLE -> "squeeze_double"
            SqueezeGesture.TRIPLE -> "squeeze_triple"
            SqueezeGesture.CONTINUOUS_START,
            SqueezeGesture.CONTINUOUS_END -> return
        }
        prefs.edit().putString(key, action).apply()
    }

    fun getMapping(context: Context, gesture: SqueezeGesture): String {
        return getAction(context, gesture)
    }

    fun clearReminderModeTimer() {
        reminderMode.cancel()
    }

    fun startReminderModeTimerAfterPermissionGrant(): Boolean =
        reminderMode.start()

    val allActions: List<String> get() = listOf(
        ACTION_TOGGLE_RECORDING,
        ACTION_TOGGLE_SESSION,
        ACTION_QUICK_NOTE,
        ACTION_NONE
    )

    fun actionLabel(action: String): String = when (action) {
        ACTION_TOGGLE_RECORDING -> "Start/Stop recording"
        ACTION_TOGGLE_SESSION -> "Start/Stop session recording"
        ACTION_QUICK_NOTE -> "Record a quick note"
        ACTION_NONE -> "Not assigned"
        else -> action
    }
}
