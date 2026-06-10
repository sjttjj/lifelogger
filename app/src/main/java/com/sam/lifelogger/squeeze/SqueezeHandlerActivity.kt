package com.sam.lifelogger.squeeze

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Invisible activity that intercepts ACTION_ASSIST from Active Edge squeeze.
 *
 * Uses singleInstance launch mode, so all squeezes within the gesture window
 * arrive via onNewIntent(). The activity stays alive for the detection window,
 * then fires the mapped action and finishes — ensuring the app is still
 * in the foreground when it starts/stop the recording service.
 */
class SqueezeHandlerActivity : Activity() {

    companion object {
        private const val TAG = "SqueezeHandler"
    }

    private val finished = AtomicBoolean(false)
    private var detector: SqueezeGestureDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Only respond when screen is on
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            Log.d(TAG, "Screen off, ignoring")
            finishAndRemove()
            return
        }

        setupDetector()
        detector?.recordEvent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent — additional squeeze")

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            finishAndRemove()
            return
        }

        // Lazy-create detector in case it was nulled after flush
        if (detector == null) {
            setupDetector()
        }
        detector?.recordEvent()
    }

    private fun setupDetector() {
        detector = SqueezeGestureDetector(onGesture = { gesture ->
            Log.d(TAG, "Gesture resolved: $gesture")
            SqueezeActionMapper.execute(applicationContext, gesture)
            finishAndRemove()
        })
    }

    override fun onDestroy() {
        detector = null
        super.onDestroy()
    }

    private fun finishAndRemove() {
        if (finished.compareAndSet(false, true)) {
            finish()
        }
    }
}
