package com.sam.lifelogger.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PREFS_NAME = "lifelogger_prefs"
private const val KEY_SELECTED_PROMPT_ID = "selected_prompt_id"

/**
 * Direct, manual upload of all pending recordings.
 * This mirrors the logic inside UploadRecordingsWorker.doWork(),
 * but can be called from an Activity via a coroutine.
 */
suspend fun uploadPendingNow(context: Context) {
    withContext(Dispatchers.IO) {
        Log.d("Uploader", "uploadPendingNow() started")

        val db = AppDatabase.get(context)
        val dao = db.recordingDao()
        val pending = dao.getPending()

        if (pending.isEmpty()) {
            Log.d("Uploader", "No pending recordings to upload.")
            return@withContext
        }

        Log.d("Uploader", "Found ${pending.size} pending recordings.")

        for (rec in pending) {
            val file = File(rec.filePath)
            if (!file.exists()) {
                Log.w("Uploader", "File missing: ${rec.filePath}")
                dao.update(
                    rec.copy(
                        uploaded = true,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
                continue
            }

            try {
                // Reuse the same HTTP upload logic as the worker
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val selectedPromptId = prefs.getString(KEY_SELECTED_PROMPT_ID, null)?.takeIf { it.isNotBlank() }

                UploadRecordingsWorker.uploadFileStatic(
                    context.applicationContext,
                    file,
                    rec.type,
                    rec.createdAt,
                    if (rec.type == "session") selectedPromptId else null,
                    rec.sessionId
                )
                Log.d("Uploader", "Uploaded: ${file.name}")

                dao.update(
                    rec.copy(
                        uploaded = true,
                        uploadAttempts = rec.uploadAttempts + 1,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
            } catch (e: UploadRecordingsWorker.Companion.UploadMetadataException) {
                Log.e("Uploader", "Upload metadata invalid for ${file.name}: ${e.message}")
                dao.update(
                    rec.copy(
                        uploaded = true,
                        uploadAttempts = rec.uploadAttempts + 1,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e("Uploader", "Upload failed for ${file.name}", e)
                dao.update(
                    rec.copy(
                        uploadAttempts = rec.uploadAttempts + 1,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
                // we *don’t* throw here: this is a manual sync, best effort per file
            }
        }
    }
}
