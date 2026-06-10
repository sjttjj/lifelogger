package com.sam.lifelogger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecordingEntity::class,
        CachedPromptEntity::class,
        ReminderEntity::class,
        NotificationJobEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun promptDao(): PromptDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN type TEXT NOT NULL DEFAULT 'normal'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cached_prompts` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN sessionId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cached_reminders` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `sourceSegmentId` INTEGER,
                        `kind` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `status` TEXT NOT NULL,
                        `needsReview` INTEGER NOT NULL,
                        `timezone` TEXT,
                        `scheduledAtLocal` TEXT,
                        `scheduledAtUtc` TEXT,
                        `endAtLocal` TEXT,
                        `endAtUtc` TEXT,
                        `schedulePrecision` TEXT NOT NULL,
                        `usedDefaultTime` INTEGER NOT NULL,
                        `location` TEXT,
                        `people` TEXT,
                        `amount` TEXT,
                        `recurrenceText` TEXT
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cached_notification_jobs` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `reminderId` INTEGER NOT NULL,
                        `notifyAtUtc` TEXT NOT NULL,
                        `notificationTitle` TEXT NOT NULL,
                        `notificationBody` TEXT,
                        `channel` TEXT NOT NULL,
                        `status` TEXT NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_reminders ADD COLUMN endAtLocal TEXT")
                db.execSQL("ALTER TABLE cached_reminders ADD COLUMN endAtUtc TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifelogger.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { INSTANCE = it }
            }
    }
}
