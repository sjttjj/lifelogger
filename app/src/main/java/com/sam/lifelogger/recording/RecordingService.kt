package com.sam.lifelogger.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sam.lifelogger.MainActivity
import com.sam.lifelogger.R
import com.sam.lifelogger.data.AppDatabase
import com.sam.lifelogger.data.RecordingEntity
import com.sam.lifelogger.data.UploadRecordingsWorker
import com.sam.lifelogger.util.FileUtils
import kotlinx.coroutines.*
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy

private const val PREFS_NAME = "lifelogger_prefs"
private const val KEY_IS_RECORDING = "is_recording"
private const val KEY_RECORDING_STARTED_AT = "recording_started_at"
private const val KEY_CHUNK_START = "current_chunk_start"
private const val KEY_CHUNK_LENGTH = "chunk_length_ms"
private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
private const val KEY_ACTIVE_RECORDING_MODE = "active_recording_mode"

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.sam.lifelogger.action.START"
        const val ACTION_SWITCH_MODE = "com.sam.lifelogger.action.SWITCH_MODE"
        const val ACTION_STOP = "com.sam.lifelogger.action.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "life_logger_recording"
        private const val NOTIFICATION_ID = 1

        const val EXTRA_CHUNK_MINUTES = "chunk_minutes"
        const val EXTRA_RECORDING_TYPE = "recording_type"
        const val DEFAULT_CHUNK_MINUTES = 5

        /** Minimum recording duration per chunk in milliseconds.
         *  A mode-switch force-expire waits at least this long so every chunk
         *  captures meaningful audio (≈ 72 KB at 192 kbps AAC). */
        private const val MIN_CHUNK_MS = 3_000L
    }

    @Volatile
    var currentRecordingMode: RecordingMode = RecordingMode.NORMAL
        private set

    /** Force the current chunk to end immediately so a new chunk starts with the new mode. */
    @Volatile
    private var chunkForceExpired = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    @Volatile
    private var isRecording = false

    private val prefs by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Generate a fresh UUID for a new session.
     *  Called on ACTION_START so a stale crash ID is always replaced. */
    private fun generateSessionId(): String {
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ACTIVE_SESSION_ID, id).apply()
        Log.d("RecordingService", "Generated new sessionId=$id")
        return id
    }

    /** Read the current sessionId without creating one. Returns null if none is set
     *  (normal/reminder-only recording, or after service stop). */
    private fun readSessionId(): String? {
        return prefs.getString(KEY_ACTIVE_SESSION_ID, null)
    }

    private fun ensureSessionId(): String {
        return readSessionId() ?: generateSessionId()
    }

    private fun clearSessionId() {
        prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply()
        Log.d("RecordingService", "Cleared active sessionId")
    }

    private fun setRecordingActive(active: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_RECORDING, active)
            .apply()

        if (active) {
            prefs.edit()
                .putLong(KEY_RECORDING_STARTED_AT, System.currentTimeMillis())
                .apply()
        } else {
            prefs.edit()
                .remove(KEY_RECORDING_STARTED_AT)
                .remove(KEY_CHUNK_START)
                .remove(KEY_CHUNK_LENGTH)
                .remove(KEY_ACTIVE_RECORDING_MODE)
                .apply()
        }
    }

    private fun setActiveRecordingMode(mode: RecordingMode?) {
        prefs.edit().apply {
            if (mode == null) remove(KEY_ACTIVE_RECORDING_MODE)
            else putString(KEY_ACTIVE_RECORDING_MODE, mode.wireValue)
        }.apply()
    }

    private fun setCurrentChunk(startMs: Long, lengthMs: Long) {
        prefs.edit()
            .putLong(KEY_CHUNK_START, startMs)
            .putLong(KEY_CHUNK_LENGTH, lengthMs)
            .apply()
    }

    private fun enqueueAutoUploadWork(mode: RecordingMode, recordingId: Long) {
        Log.d("RecordingService", "enqueueAutoUploadWork() called mode=${mode.wireValue} recordingId=$recordingId")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(RecordingUploadWorkPolicy.immediateUploadNetworkType(mode))
            .build()

        // Immediate one-time upload for this chunk
        val immediateBuilder = OneTimeWorkRequestBuilder<UploadRecordingsWorker>()
            .setConstraints(constraints)
            .addTag("auto-upload")

        if (RecordingUploadWorkPolicy.uploadOnlyCreatedRecording(mode)) {
            immediateBuilder.setInputData(
                workDataOf(UploadRecordingsWorker.KEY_RECORDING_ID to recordingId)
            )
        }

        val immediateRequest = immediateBuilder.build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            if (mode == RecordingMode.REMINDER) "auto-upload-reminder-$recordingId" else "auto-upload-immediate",
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )

        if (mode == RecordingMode.REMINDER) return

        // Periodic background upload every 15 minutes (survives app backgrounding)
        val periodicRequest = PeriodicWorkRequestBuilder<UploadRecordingsWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("auto-upload")
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "auto-upload-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("RecordingService", "onCreate()")
        createNotificationChannel()
    }

    override fun onDestroy() {
        Log.d("RecordingService", "onDestroy()")
        stopRecordingInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d("RecordingService", "ACTION_START received")
                val minutes = intent.getIntExtra(EXTRA_CHUNK_MINUTES, DEFAULT_CHUNK_MINUTES)
                val mode = RecordingMode.fromWireValue(
                    intent.getStringExtra(EXTRA_RECORDING_TYPE) ?: RecordingMode.NORMAL.wireValue
                )
                currentRecordingMode = mode
                setActiveRecordingMode(mode)
                startForegroundServiceRecording(minutes)
            }
            ACTION_SWITCH_MODE -> {
                Log.d("RecordingService", "ACTION_SWITCH_MODE received")
                val mode = RecordingMode.fromWireValue(
                    intent.getStringExtra(EXTRA_RECORDING_TYPE) ?: RecordingMode.NORMAL.wireValue
                )
                currentRecordingMode = mode
                setActiveRecordingMode(mode)
                // Generate session ID on transition to session (e.g. normal → session)
                if (mode == RecordingMode.SESSION && readSessionId() == null) {
                    generateSessionId()
                }
                chunkForceExpired = true
                Log.d("RecordingService", "Switched recording mode to ${mode.wireValue}, chunk force-expired")
            }
            ACTION_STOP -> {
                Log.d("RecordingService", "ACTION_STOP received")
                stopRecordingInternal()
                stopForeground(STOP_FOREGROUND_REMOVE)

                // Ensure notification fully disappears
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(NOTIFICATION_ID)

                stopSelf()
            }
            else -> Log.d("RecordingService", "onStartCommand: unknown action")
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceRecording(chunkMinutes: Int) {
        if (isRecording) {
            Log.d("RecordingService", "Already recording — ignoring START.")
            return
        }

        val notification = buildNotification("Recording…")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("RecordingService", "Cannot start recording: RECORD_AUDIO is not granted")
            setRecordingActive(false)
            setActiveRecordingMode(null)
            stopSelf()
            return
        }

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e("RecordingService", "Cannot start microphone foreground service", e)
            setRecordingActive(false)
            setActiveRecordingMode(null)
            stopSelf()
            return
        }

        // Generate a fresh session ID if this is a brand-new session start.
        // The first chunk will stamp it onto the DB entity; reminder interruptions
        // preserve it via the prefs key (not regenerated on switch).
        if (currentRecordingMode == RecordingMode.SESSION) {
            generateSessionId()
        }

        isRecording = true
        setRecordingActive(true)

        recordingJob = serviceScope.launch {
            while (isActive && isRecording) {
                val file = FileUtils.createNewChunkFile(this@RecordingService)

                // Capture the mode at chunk start so DB labeling is correct even if
                // a mode switch forces the chunk to end early.
                val chunkMode = currentRecordingMode
                val activeSessionId = if (chunkMode == RecordingMode.SESSION) ensureSessionId() else readSessionId()
                val chunkSessionId = SessionIdPolicy.sessionIdForChunk(chunkMode, activeSessionId)
                val chunkMillis = chunkMode.chunkDurationMs(chunkMinutes)

                // 1) Start writing audio
                startMediaRecorder(file)

                val startTs = System.currentTimeMillis()
                setCurrentChunk(startTs, chunkMillis)

                // 2) Wait for chunk duration or stop (or mode switch force-expire).
                //    Always record at least MIN_CHUNK_MS so we don't create tiny files.
                val minEnd = startTs + MIN_CHUNK_MS
                while (isActive && isRecording &&
                    System.currentTimeMillis() - startTs < chunkMillis && !chunkForceExpired
                ) {
                    delay(1_000L)
                }
                // If force-expired and hasn't reached the minimum yet, keep recording.
                if (chunkForceExpired && System.currentTimeMillis() - minEnd < 0) {
                    val remaining = minEnd - System.currentTimeMillis()
                    Log.d("RecordingService", "Chunk force-expired but waiting ${remaining}ms for minimum duration")
                    delay(remaining)
                }

                chunkForceExpired = false

                // 3) Fully stop recorder
                stopMediaRecorder()

                // 4) Save DB record with the mode captured when this chunk started
                val db = AppDatabase.get(this@RecordingService)
                val recordingId = db.recordingDao().insert(
                    RecordingEntity(
                        filePath = file.absolutePath,
                        createdAt = System.currentTimeMillis(),
                        type = chunkMode.wireValue,
                        sessionId = chunkSessionId
                    )
                )

                Log.d(
                    "RecordingService",
                    "Chunk finished: ${file.absolutePath} type=${chunkMode.wireValue}. Enqueuing auto upload."
                )

                // 5) Trigger upload worker
                enqueueAutoUploadWork(chunkMode, recordingId)
            }

            Log.d("RecordingService", "Recording loop ended — stopping service.")
            setRecordingActive(false)
            stopSelf()
        }
    }

    private fun stopRecordingInternal() {
        if (!isRecording) {
            Log.d("RecordingService", "stopRecordingInternal(): no recording active.")
            return
        }

        Log.d("RecordingService", "stopRecordingInternal(): stopping recorder and job.")

        isRecording = false
        setRecordingActive(false)
        setActiveRecordingMode(null)
        clearSessionId()

        stopMediaRecorder()

        recordingJob = null
    }

    private fun startMediaRecorder(outputFile: java.io.File) {
        stopMediaRecorder()

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(192_000)
        r.setAudioSamplingRate(48_000)
        r.setOutputFile(outputFile.absolutePath)

        r.prepare()
        r.start()

        recorder = r
    }

    private fun stopMediaRecorder() {
        try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            recorder = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Life Logger Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pixel Life Logger")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
