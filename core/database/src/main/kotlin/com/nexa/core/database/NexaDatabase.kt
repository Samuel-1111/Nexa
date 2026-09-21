package com.nexa.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.Room

@Database(
    entities = [TaskEntity::class, ReminderEntity::class, NoteEntity::class, MemoryEntity::class, OutboxOperationEntity::class, CalendarEventEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun memoryDao(): MemoryDao
    abstract fun outboxDao(): OutboxDao
    abstract fun calendarEventDao(): CalendarEventDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN reference TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS calendar_events (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT, title TEXT NOT NULL, description TEXT, location TEXT, startsAtEpochMs INTEGER NOT NULL, endsAtEpochMs INTEGER NOT NULL, timezoneId TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, deletedAtEpochMs INTEGER, serverVersion INTEGER NOT NULL, syncState TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_startsAtEpochMs ON calendar_events(startsAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_ownerId ON calendar_events(ownerId)")
            }
        }

        @Volatile private var INSTANCE: NexaDatabase? = null

        fun getInstance(context: Context): NexaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    "nexa.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
