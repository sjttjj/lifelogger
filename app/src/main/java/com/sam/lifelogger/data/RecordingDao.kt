package com.sam.lifelogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface RecordingDao {

    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("SELECT * FROM recordings WHERE uploaded = 0 ORDER BY createdAt ASC")
    suspend fun getPending(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecordingEntity?

    @Update
    suspend fun update(recording: RecordingEntity)

    // NEWEST FIRST for playback screen
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAll(): List<RecordingEntity>

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()
}
