package com.sam.lifelogger.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Lightweight audio recorder for short voice questions sent to /api/ask/audio.
 * Separate from the main RecordingService pipeline — these clips are not stored
 * in the lifelogger DB or processed as summaries/reminders.
 */
class AskAudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Start recording a voice question to a temp file. */
    fun start(): File {
        stop()

        val file = File(
            context.cacheDir,
            "ask_question_${System.currentTimeMillis()}.aac"
        )
        outputFile = file

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(48_000)
        r.setAudioSamplingRate(16_000)
        r.setOutputFile(file.absolutePath)
        r.setMaxDuration(60_000) // server max is 60s

        r.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                Log.d(TAG, "Ask audio max duration reached (60s)")
            }
        }

        try {
            r.prepare()
            r.start()
            recorder = r
            Log.d(TAG, "Recording started: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            r.release()
            recorder = null
            outputFile = null
            throw e
        }

        return file
    }

    /** Stop recording and return the audio file, or null if nothing was recorded. */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = outputFile

        recorder = null
        outputFile = null

        try {
            r.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recorder", e)
        }
        r.release()

        // Discard tiny files (e.g. user tapped mic and immediately stopped)
        if (file != null && file.exists() && file.length() > 1_000L) {
            Log.d(TAG, "Recording saved: ${file.absolutePath} (${file.length()} bytes)")
            return file
        }

        // File too small or missing — clean up
        file?.delete()
        Log.d(TAG, "Recording discarded (too short or empty)")
        return null
    }

    /** Whether a recording is currently in progress. */
    val isRecording: Boolean get() = recorder != null

    /** Cancel an in-progress recording without saving. */
    fun cancel() {
        val r = recorder ?: return
        val file = outputFile
        recorder = null
        outputFile = null

        try {
            r.stop()
        } catch (_: Exception) {}
        r.release()
        file?.delete()
        Log.d(TAG, "Recording cancelled")
    }

    companion object {
        private const val TAG = "AskAudioRecorder"
    }
}
