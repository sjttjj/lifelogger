package com.sam.lifelogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val createdAt: Long,
    val type: String = "normal",
    val uploaded: Boolean = false,
    val uploadAttempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val sessionId: String? = null
)
