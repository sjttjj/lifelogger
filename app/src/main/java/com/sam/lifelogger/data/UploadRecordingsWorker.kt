package com.sam.lifelogger.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sam.lifelogger.recording.RecordingUploadWorkPolicy
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class UploadRecordingsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Typed HTTP error so we can distinguish permanent vs transient failures.
     */
    class UploadHttpException(val code: Int, val body: String) :
        Exception("Upload failed with HTTP $code: $body")

    companion object {
        private const val TAG = "UploadWorker"
        const val KEY_RECORDING_ID = "recording_id"

        // Supabase function (existing behaviour)
        private const val SUPABASE_UPLOAD_URL =
            "" // Supabase disabled — rotate this key if re-enabling

        // SharedPreferences keys (must match strings in MainScreen)
        private const val PREFS_NAME = "lifelogger_prefs"
        private const val KEY_UPLOAD_TARGET = "upload_target"
        private const val KEY_LOCAL_URL = "local_server_url"
        // Must match the key in SettingsScreen
        private const val KEY_SELECTED_PROMPT_ID = "selected_prompt_id"

        /**
         * Thrown when a file fails pre-upload validation and should be
         * marked as permanently failed without attempting upload.
         */
        class FileValidationException(message: String) : Exception(message)

        class UploadMetadataException(message: String) : Exception(message)

        @Throws(UploadMetadataException::class)
        fun validateUploadMetadataForTest(recordingType: String, sessionId: String?) {
            validateUploadMetadata(recordingType, sessionId)
        }

        @Throws(UploadMetadataException::class)
        private fun validateUploadMetadata(recordingType: String, sessionId: String?) {
            if (recordingType == "session" && sessionId.isNullOrBlank()) {
                throw UploadMetadataException("Session upload missing sessionId")
            }
        }

        /**
         * Validates that [file] is a real audio file fit for upload.
         * @throws FileValidationException if any check fails.
         */
        @Throws(FileValidationException::class)
        private fun validateFile(file: File) {
            if (!file.exists()) {
                throw FileValidationException("File does not exist: ${file.absolutePath}")
            }
            if (file.length() < 16_384) {
                throw FileValidationException(
                    "File too small (${file.length()} bytes, minimum 16384): ${file.name}"
                )
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                if (durationStr != null) {
                    val durationMs = durationStr.toLongOrNull() ?: -1L
                    if (durationMs < 1_000) {
                        throw FileValidationException(
                            "Audio too short (${durationMs}ms, minimum 1000ms): ${file.name}"
                        )
                    }
                    if (durationMs > 900_000) {
                        throw FileValidationException(
                            "Audio too long (${durationMs / 1000}s, maximum 900s): ${file.name}"
                        )
                    }
                } else {
                    Log.w(TAG, "Could not read duration metadata for ${file.name} — proceeding")
                }
            } catch (e: FileValidationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "MediaMetadataRetriever failed for ${file.name}", e)
                throw FileValidationException(
                    "Could not read audio metadata — file may be corrupted: ${file.name}"
                )
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        /**
         * Shared HTTP upload function used by both WorkManager and manual sync.
         * @param recordingType the recording type tag, sent as extra form field
         * @param recordedAt the device-side recording timestamp in epoch milliseconds
         * @throws FileValidationException if the file fails pre-upload validation
         */
        @Throws(Exception::class)
        fun uploadFileStatic(
            context: Context,
            file: File,
            recordingType: String = "normal",
            recordedAt: Long? = null,
            promptId: String? = null,
            sessionId: String? = null
        ) {
            validateUploadMetadata(recordingType, sessionId)
            validateFile(file)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val target = prefs.getString(KEY_UPLOAD_TARGET, "local") ?: "local"
            val localUrl = prefs.getString(
                KEY_LOCAL_URL,
                "http://100.78.20.28:8000/transcribe?task=transcribe"
            ) ?: ""

            if (target == "local" && localUrl.isNotBlank()) {
                uploadToLocal(localUrl, file, recordingType, recordedAt, promptId, sessionId)
            } else {
                uploadToSupabase(file)
            }
        }

        @Throws(Exception::class)
        private fun uploadToLocal(
            localUrl: String,
            file: File,
            recordingType: String = "normal",
            recordedAt: Long? = null,
            promptId: String? = null,
            sessionId: String? = null
        ) {
            val url = URL(localUrl)
            val boundary = "----LifeloggerBoundary${System.currentTimeMillis()}"
            val lineEnd = "\r\n"

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.doInput = true
            conn.useCaches = false
            conn.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )
            conn.setRequestProperty("Connection", "close")

            try {
                val output = java.io.DataOutputStream(conn.outputStream)

                // audio file part — matches curl -F "audio=@file"
                output.writeBytes("--$boundary$lineEnd")
                output.writeBytes(
                    "Content-Disposition: form-data; name=\"audio\"; filename=\"${file.name}\"$lineEnd"
                )
                output.writeBytes("Content-Type: audio/mp4$lineEnd")
                output.writeBytes(lineEnd)

                file.inputStream().use { input ->
                    input.copyTo(output)
                }

                output.writeBytes(lineEnd)

                // recording type part — server may ignore if not yet supported
                output.writeBytes("--$boundary$lineEnd")
                output.writeBytes("Content-Disposition: form-data; name=\"type\"$lineEnd")
                output.writeBytes(lineEnd)
                output.writeBytes(recordingType)
                output.writeBytes(lineEnd)

                if (recordedAt != null) {
                    val metadata = RecordingUploadMetadata.fromCreatedAt(recordedAt)
                    writeFormField(output, boundary, lineEnd, "recorded_at_ms", metadata.recordedAtMs)
                    writeFormField(output, boundary, lineEnd, "recorded_at_iso", metadata.recordedAtIso)
                    writeFormField(output, boundary, lineEnd, "recorded_date", metadata.recordedDate)
                    writeFormField(
                        output,
                        boundary,
                        lineEnd,
                        "recorded_timezone",
                        metadata.recordedTimezone
                    )
                }

                // Send prompt_id for session recordings
                if (promptId != null) {
                    writeFormField(output, boundary, lineEnd, "prompt_id", promptId)
                }

                // Send session_id chunk-grouping ID (for session recordings)
                if (sessionId != null) {
                    writeFormField(output, boundary, lineEnd, "session_id", sessionId)
                }

                output.writeBytes("--$boundary--$lineEnd")
                output.flush()
                output.close()

                val code = conn.responseCode
                val body = try {
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (code !in 200..299) {
                    Log.e(TAG, "HTTP $code while uploading ${file.name}. Error body: $body")
                    throw UploadHttpException(code, body)
                } else {
                    Log.d(
                        TAG,
                        "Local upload success HTTP $code for ${file.name}. Response: $body"
                    )
                }
            } finally {
                conn.disconnect()
            }
        }

        @Throws(Exception::class)
        private fun uploadToSupabase(file: File) {
            val url = URL(SUPABASE_UPLOAD_URL)
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "audio/mp4")
            conn.setRequestProperty("Connection", "close")
            conn.setRequestProperty(
                "x-api-key",
                "" // Supabase disabled — rotate this key if re-enabling
            )
            conn.setRequestProperty("x-file-name", file.name)

            try {
                file.inputStream().use { input ->
                    conn.outputStream.use { output: OutputStream ->
                        input.copyTo(output)
                    }
                }

                val code = conn.responseCode
                val body = try {
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (code !in 200..299) {
                    Log.e(TAG, "HTTP $code while uploading ${file.name}. Error body: $body")
                    throw UploadHttpException(code, body)
                } else {
                    Log.d(TAG, "Upload success HTTP $code for ${file.name}")
                }
            } finally {
                conn.disconnect()
            }
        }

        /** Read the user's selected prompt ID from SharedPreferences. */
        private fun readSelectedPromptId(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_SELECTED_PROMPT_ID, null)?.takeIf { it.isNotBlank() }
        }
    }

    // -------------------------------------------------------------------------
    // WorkManager entry point – this stays OUTSIDE the companion object
    // -------------------------------------------------------------------------
    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() started on device")
        val db = AppDatabase.get(applicationContext)
        val dao = db.recordingDao()
        val targetRecordingId = inputData.getLong(KEY_RECORDING_ID, 0L)
        val pending = if (targetRecordingId > 0L) {
            dao.getById(targetRecordingId)
                ?.takeIf { !it.uploaded }
                ?.let { listOf(it) }
                ?: emptyList()
        } else {
            dao.getPending()
        }

        if (pending.isEmpty()) {
            Log.d(TAG, "No pending recordings to upload.")
            return Result.success()
        }

        Log.d(TAG, "Found ${pending.size} pending recordings.")

        // Track if we saw any transient (retryable) failures in this pass
        var hadTransientFailure = false

        for (rec in pending) {
            val file = File(rec.filePath)
            val now = System.currentTimeMillis()

            try {
                // Use shared static uploader so manual + automatic behave the same
                val selectedPromptId = readSelectedPromptId(applicationContext)
                uploadFileStatic(
                    applicationContext,
                    file,
                    rec.type,
                    rec.createdAt,
                    if (rec.type == "session") selectedPromptId else null,
                    rec.sessionId
                )
                Log.d(TAG, "Uploaded: ${file.name}")

                dao.update(
                    rec.copy(
                        uploaded = true,
                        uploadAttempts = rec.uploadAttempts + 1,
                        lastAttemptAt = now
                    )
                )

            } catch (e: FileValidationException) {
                // Permanent failure – file is invalid or too long; never retry.
                Log.e(TAG, "File validation failed for ${file.name}: ${e.message}")
                dao.update(
                    rec.copy(
                        uploaded = true,
                        uploadAttempts = rec.uploadAttempts + 1,
                        lastAttemptAt = now
                    )
                )
                // Do NOT mark as transient; just move on.

            } catch (e: UploadMetadataException) {
                Log.e(TAG, "Upload metadata invalid for ${file.name}: ${e.message}")
                dao.update(
                    rec.copy(
                        uploaded = true,
                        uploadAttempts = rec.uploadAttempts + 1,
                        lastAttemptAt = now
                    )
                )
                if (rec.type == "reminder") {
                    ReminderSyncWorker.enqueueFollowUps(
                        applicationContext,
                        RecordingUploadWorkPolicy.reminderPostUploadSyncDelaySeconds
                    )
                }

            } catch (e: UploadHttpException) {
                Log.e(TAG, "HTTP error for ${file.name}: code=${e.code}, body=${e.body}")

                if (e.code in 400..499) {
                    // All 400-level errors are permanent – corrupted file, too long,
                    // unsupported format, or app-side bug. Never retry.
                    Log.e(TAG, "Marking ${file.name} as permanently failed (HTTP ${e.code}).")
                    dao.update(
                        rec.copy(
                            uploaded = true,
                            uploadAttempts = rec.uploadAttempts + 1,
                            lastAttemptAt = now
                        )
                    )
                } else {
                    // 500+ or unexpected – transient; retry later.
                    dao.update(
                        rec.copy(
                            uploadAttempts = rec.uploadAttempts + 1,
                            lastAttemptAt = now
                        )
                    )
                    hadTransientFailure = true
                }

            } catch (e: Exception) {
                // Network timeout, IO error, etc. – transient; retry later.
                Log.e(TAG, "Upload failed for ${file.name}", e)
                dao.update(
                    rec.copy(
                        uploadAttempts = rec.uploadAttempts + 1,
                        lastAttemptAt = now
                    )
                )
                hadTransientFailure = true
            }
        }

        // If *any* transient failures happened in this run, ask WorkManager to retry.
        return if (hadTransientFailure) {
            Log.w(TAG, "Some uploads failed transiently; requesting retry.")
            Result.retry()
        } else {
            Log.d(TAG, "All pending uploads handled successfully.")
            Result.success()
        }
    }
}

private fun writeFormField(
    output: java.io.DataOutputStream,
    boundary: String,
    lineEnd: String,
    name: String,
    value: String
) {
    output.writeBytes("--$boundary$lineEnd")
    output.writeBytes("Content-Disposition: form-data; name=\"$name\"$lineEnd")
    output.writeBytes(lineEnd)
    output.writeBytes(value)
    output.writeBytes(lineEnd)
}
