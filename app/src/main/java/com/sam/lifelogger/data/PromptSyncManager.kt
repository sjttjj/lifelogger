package com.sam.lifelogger.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Manages syncing prompt templates between the server and the local Room cache.
 *
 * Prompts are stored on the server in prompts.json. The phone fetches them on
 * app launch and caches them locally so the picker works offline. Users can
 * create new prompts from the app, which are POSTed to the server and then
 * added to the local cache on the next sync.
 */
object PromptSyncManager {

    private const val TAG = "PromptSync"

    /**
     * Fetch all prompts from the server and replace the local Room cache.
     * Safe to call on any coroutine context; I/O is dispatched internally.
     */
    suspend fun sync(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val json = ApiClient.getPrompts(context)
                val parsed = parsePromptsResponse(json)

                val db = AppDatabase.get(context)
                val dao = db.promptDao()

                dao.deleteAll()
                dao.insertAll(parsed)

                Log.d(TAG, "Synced ${parsed.size} prompts from server")
            } catch (e: Exception) {
                Log.w(TAG, "Prompt sync failed (using cached prompts)", e)
                // Cache still has the last successful sync data — no action needed
            }
        }
    }

    /**
     * Create a new prompt on the server, then re-sync the cache.
     *
     * @param name Display name for the prompt (e.g. "Meeting Notes")
     * @param promptText The full prompt template text
     * @param isDefault Whether this should be the default pre-selected prompt
     * @return The generated ID (slug) of the new prompt, or null on failure
     */
    suspend fun createPrompt(
        context: Context,
        name: String,
        promptText: String,
        isDefault: Boolean = false
    ): String? {
        val id = generateId(name)

        return try {
            ApiClient.createPrompt(context, id, name, promptText, isDefault)

            // Re-sync to update the local cache
            sync(context)

            Log.d(TAG, "Created prompt '$name' with id=$id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create prompt '$name'", e)
            null
        }
    }

    /** Get the live list of cached prompts as a Flow (for Compose / UI observation). */
    fun getAllPrompts(context: Context): Flow<List<CachedPromptEntity>> {
        val db = AppDatabase.get(context)
        return db.promptDao().getAllPrompts()
    }

    /** Get the default prompt, or the first prompt, or null if none exist. */
    suspend fun getDefaultPrompt(context: Context): CachedPromptEntity? {
        val db = AppDatabase.get(context)
        return db.promptDao().getDefaultPrompt()
    }

    // ---- Internal helpers ----

    private fun parsePromptsResponse(json: String): List<CachedPromptEntity> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("prompts")
        val result = mutableListOf<CachedPromptEntity>()
        val now = System.currentTimeMillis()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                CachedPromptEntity(
                    id = obj.optString("id", "prompt-$i"),
                    name = obj.optString("name", "Prompt ${i + 1}"),
                    prompt = obj.optString("prompt", ""),
                    isDefault = obj.optBoolean("is_default", false),
                    updatedAt = obj.optLong("updated_at", now)
                )
            )
        }

        return result
    }

    /**
     * Generate a human-readable slug from a prompt name.
     * e.g. "Meeting Notes" → "meeting-notes"
     * Collisions are handled by timestamp appending (done by the caller if needed).
     */
    fun generateId(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "prompt-${System.currentTimeMillis()}" }
    }
}