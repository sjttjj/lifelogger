package com.sam.lifelogger.squeeze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class SqueezeBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SQUEEZE_DETECTED = "com.sam.lifelogger.SQUEEZE_DETECTED"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_PROGRESS = "progress"
        const val EVENT_DETECTED = "detected"
        const val EVENT_PROGRESS = "progress"
        private const val TAG = "SqueezeReceiver"

        private var detector: SqueezeGestureDetector? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SQUEEZE_DETECTED) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return

        Log.d(TAG, "Broadcast received, detector=${detector != null}")

        if (detector == null) {
            Log.d(TAG, "Creating new detector")
            detector = SqueezeGestureDetector(onGesture = { gesture ->
                Log.d(TAG, "Gesture resolved: $gesture")
                SqueezeActionMapper.execute(context.applicationContext, gesture)
                if (
                    gesture == SqueezeGesture.SINGLE ||
                    gesture == SqueezeGesture.DOUBLE ||
                    gesture == SqueezeGesture.TRIPLE
                ) {
                    detector = null
                }
            })
        }

        when (intent.getStringExtra(EXTRA_EVENT_TYPE) ?: EVENT_DETECTED) {
            EVENT_PROGRESS -> detector?.recordProgress(intent.getFloatExtra(EXTRA_PROGRESS, 0f))
            EVENT_DETECTED -> detector?.recordEvent()
            else -> Log.w(TAG, "Unknown squeeze event type: ${intent.getStringExtra(EXTRA_EVENT_TYPE)}")
        }
    }
}
