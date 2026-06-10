package com.sam.lifelogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_prompts")
data class CachedPromptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val prompt: String,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)