package com.sam.lifelogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {

    @Query("SELECT * FROM cached_prompts ORDER BY isDefault DESC, name ASC")
    fun getAllPrompts(): Flow<List<CachedPromptEntity>>

    @Query("SELECT * FROM cached_prompts WHERE id = :id")
    suspend fun getPromptById(id: String): CachedPromptEntity?

    @Query("SELECT * FROM cached_prompts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPrompt(): CachedPromptEntity?

    /**
     * Replace the entire prompt cache with a fresh list from the server.
     * This runs inside a single transaction.
     */
    @Query("DELETE FROM cached_prompts")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prompts: List<CachedPromptEntity>)
}