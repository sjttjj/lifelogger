package com.sam.lifelogger.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    private val dateFolderFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeStampFormat = SimpleDateFormat("HHmmss", Locale.getDefault())

    fun getDailyFolder(context: Context): File {
        val day = dateFolderFormat.format(Date())
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val daily = File(baseDir, day)
        if (!daily.exists()) daily.mkdirs()
        return daily
    }

    fun createNewChunkFile(context: Context): File {
        val folder = getDailyFolder(context)
        val timestamp = timeStampFormat.format(Date())
        return File(folder, "chunk_$timestamp.m4a")
    }
}
