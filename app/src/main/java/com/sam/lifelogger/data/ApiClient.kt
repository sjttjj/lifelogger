package com.sam.lifelogger.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Simple API client for the Voxtral server's viewer endpoints.
 * Reads the server URL from SharedPreferences (same as upload settings).
 */
object ApiClient {

    private const val PREFS_NAME = "lifelogger_prefs"
    private const val KEY_LOCAL_URL = "local_server_url"
    private const val DEFAULT_URL = "http://100.78.20.28:8000"

    private fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fullUrl = prefs.getString(KEY_LOCAL_URL, "$DEFAULT_URL/transcribe?task=transcribe") ?: DEFAULT_URL
        return normalizeBaseUrl(fullUrl)
    }

    internal fun normalizeBaseUrl(fullUrl: String): String {
        val trimmed = fullUrl.trim()
        val queryless = trimmed.substringBefore('?').trimEnd('/')
        return try {
            val parsed = URL(queryless)
            "${parsed.protocol}://${parsed.authority}"
        } catch (_: Exception) {
            queryless
                .replace("/transcribe", "")
                .trimEnd('/')
                .ifBlank { DEFAULT_URL }
        }
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        try {
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
            }
            body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * POST with no request body. Used for action endpoints (done/cancel/series stop/archive)
     * that take no parameters. Throws on non-2xx.
     */
    private suspend fun httpPostEmpty(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("POST $url failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** GET /api/dates */
    suspend fun getDates(context: Context): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/dates")
    }

    /** GET /api/summaries/{date} */
    suspend fun getSummary(context: Context, date: String): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/summaries/$date")
    }

    /** GET /api/transcripts/{date} */
    suspend fun getTranscripts(context: Context, date: String): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/transcripts/$date")
    }

    /** GET /api/sessions/{date} */
    suspend fun getSessionsForDate(context: Context, date: String): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/sessions/$date")
    }

    /** GET /api/sessions/{date}/{encodedFilename} */
    suspend fun getSessionNote(context: Context, date: String, filename: String): String {
        val base = getBaseUrl(context)
        val encodedFilename = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        return httpGet("$base/api/sessions/$date/$encodedFilename")
    }

    /** GET /api/search?q=... */
    suspend fun search(context: Context, query: String): String {
        val base = getBaseUrl(context)
        val encoded = URLEncoder.encode(query, "UTF-8")
        return httpGet("$base/api/search?q=$encoded")
    }

    /** POST /api/devices/register */
    suspend fun registerDevice(context: Context, deviceId: String, deviceName: String): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/devices/register")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val jsonBody = JSONObject().apply {
                put("device_id", deviceId)
                put("device_name", deviceName)
                put("platform", "android")
            }
            conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Register device failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** GET /api/reminders/upcoming?start=<iso>&end=<iso>&status=pending */
    suspend fun getUpcomingReminders(
        context: Context,
        start: String,
        end: String,
        kind: String? = null
    ): String {
        val base = getBaseUrl(context)
        val startParam = URLEncoder.encode(start, "UTF-8")
        val endParam = URLEncoder.encode(end, "UTF-8")
        var url = "$base/api/reminders/upcoming?start=$startParam&end=$endParam&status=pending"
        if (!kind.isNullOrBlank()) {
            url += "&kind=${URLEncoder.encode(kind, "UTF-8")}"
        }
        return httpGet(url)
    }

    /** GET /api/reminders?status=pending&needs_review=true */
    suspend fun getNeedsReviewReminders(context: Context): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/reminders?status=pending&needs_review=true")
    }

    /** GET /api/reminders?status=all — full list across all statuses, for the All/archive view. */
    suspend fun getAllReminders(context: Context): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/reminders?status=all")
    }

    /** PATCH /api/reminders/{id} */
    suspend fun patchReminder(context: Context, id: Long, patch: ReminderPatch): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/reminders/$id")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PATCH"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            conn.outputStream.use { it.write(patch.toJson().toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Patch reminder failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** POST /api/reminders/{id}/actions/done — preferred over patching status for marking done. */
    suspend fun markReminderDone(context: Context, id: Long): String {
        val base = getBaseUrl(context)
        return httpPostEmpty("$base/api/reminders/$id/actions/done")
    }

    /** POST /api/reminders/{id}/actions/cancel — cancel the current occurrence / reject a review item. */
    suspend fun cancelReminder(context: Context, id: Long): String {
        val base = getBaseUrl(context)
        return httpPostEmpty("$base/api/reminders/$id/actions/cancel")
    }

    /** POST /api/recurrence-series/{seriesId}/actions/stop — stop future occurrences, keep history. */
    suspend fun stopRecurrenceSeries(context: Context, seriesId: Long): String {
        val base = getBaseUrl(context)
        return httpPostEmpty("$base/api/recurrence-series/$seriesId/actions/stop")
    }

    /** POST /api/recurrence-series/{seriesId}/actions/archive — archive the whole series. */
    suspend fun archiveRecurrenceSeries(context: Context, seriesId: Long): String {
        val base = getBaseUrl(context)
        return httpPostEmpty("$base/api/recurrence-series/$seriesId/actions/archive")
    }

    /** POST /api/reminders/{id}/notifications */
    suspend fun createNotificationJob(
        context: Context,
        reminderId: Long,
        notifyAtUtc: String,
        notificationTitle: String,
        notificationBody: String?
    ): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/reminders/$reminderId/notifications")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val jsonBody = JSONObject().apply {
                put("notify_at_utc", notifyAtUtc)
                put("notification_title", notificationTitle)
                put("notification_body", notificationBody ?: "")
                put("channel", "push")
            }
            conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Create notification job failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** PUT /api/reminders/{id}/notifications */
    suspend fun replaceReminderNotifications(
        context: Context,
        reminderId: Long,
        request: ReminderNotificationReplaceRequest
    ): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/reminders/$reminderId/notifications")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            conn.outputStream.use { it.write(request.toJson().toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Replace reminder notifications failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** GET /api/config */
    suspend fun getConfig(context: Context): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/config")
    }

    /** POST /api/config */
    suspend fun updateConfig(context: Context, key: String, value: Boolean): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/config")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val body = """{"$key": $value}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
            }
        } finally {
            conn.disconnect()
        }
    }

    /** GET /api/prompts — fetch all prompt templates from the server */
    suspend fun getPrompts(context: Context): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/prompts")
    }

    /** POST /api/prompts — create or update a prompt template on the server */
    suspend fun createPrompt(context: Context, id: String, name: String, prompt: String, isDefault: Boolean): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/prompts")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val jsonBody = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("prompt", prompt)
                put("is_default", isDefault)
            }
            conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Create prompt failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** PUT /api/summaries/{date} — save edited summary title and/or markdown */
    suspend fun updateSummary(context: Context, date: String, markdown: String?, title: String?): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/summaries/$date")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val jsonBody = JSONObject()
            if (markdown != null) jsonBody.put("markdown", markdown)
            if (title != null) jsonBody.put("title", title)
            conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Update summary failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }
    /** GET /api/weather */
    suspend fun getWeather(context: Context): String {
        val base = getBaseUrl(context)
        return httpGet("$base/api/weather")
    }

    /** POST /api/ask — send a text question to the LLM */
    suspend fun askText(
        context: Context,
        question: String,
        scope: AskScope? = null
    ): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/ask")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            val jsonBody = JSONObject().apply {
                put("question", question)
                scope?.let { put("scope", it.toJson()) }
            }
            conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Ask text failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** POST /api/ask/audio — upload a voice question for transcription and answering */
    suspend fun askAudio(
        context: Context,
        audioFile: java.io.File,
        scope: AskScope? = null,
        recordedAtMs: String? = null,
        recordedAtIso: String? = null,
        recordedTimezone: String? = null
    ): String = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val url = URL("$base/api/ask/audio")
        val boundary = "----AskBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.doInput = true
        conn.useCaches = false
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setRequestProperty("Connection", "close")
        conn.connectTimeout = 15_000
        conn.readTimeout = 120_000

        try {
            val output = java.io.DataOutputStream(conn.outputStream)

            // Audio file part
            output.writeBytes("--$boundary$lineEnd")
            output.writeBytes("Content-Disposition: form-data; name=\"audio\"; filename=\"${audioFile.name}\"$lineEnd")
            output.writeBytes("Content-Type: audio/mp4$lineEnd")
            output.writeBytes(lineEnd)
            audioFile.inputStream().use { input ->
                input.copyTo(output)
            }
            output.writeBytes(lineEnd)

            // Optional scope as JSON string
            if (scope != null) {
                output.writeBytes("--$boundary$lineEnd")
                output.writeBytes("Content-Disposition: form-data; name=\"scope\"$lineEnd")
                output.writeBytes(lineEnd)
                output.writeBytes(scope.toJson().toString())
                output.writeBytes(lineEnd)
            }

            // Optional recording metadata
            if (recordedAtMs != null) {
                output.writeBytes("--$boundary$lineEnd")
                output.writeBytes("Content-Disposition: form-data; name=\"recorded_at_ms\"$lineEnd")
                output.writeBytes(lineEnd)
                output.writeBytes(recordedAtMs)
                output.writeBytes(lineEnd)
            }
            if (recordedAtIso != null) {
                output.writeBytes("--$boundary$lineEnd")
                output.writeBytes("Content-Disposition: form-data; name=\"recorded_at_iso\"$lineEnd")
                output.writeBytes(lineEnd)
                output.writeBytes(recordedAtIso)
                output.writeBytes(lineEnd)
            }
            if (recordedTimezone != null) {
                output.writeBytes("--$boundary$lineEnd")
                output.writeBytes("Content-Disposition: form-data; name=\"recorded_timezone\"$lineEnd")
                output.writeBytes(lineEnd)
                output.writeBytes(recordedTimezone)
                output.writeBytes(lineEnd)
            }

            output.writeBytes("--$boundary--$lineEnd")
            output.flush()
            output.close()

            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                throw Exception("Ask audio failed HTTP $code: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }
}
