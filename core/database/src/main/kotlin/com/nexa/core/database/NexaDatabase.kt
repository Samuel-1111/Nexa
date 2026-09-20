package com.nexa.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.Room

@Database(
    entities = [TaskEntity::class, ReminderEntity::class, NoteEntity::class, MemoryEntity::class, OutboxOperationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun memoryDao(): MemoryDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile private var INSTANCE: NexaDatabase? = null

        fun getInstance(context: Context): NexaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    "nexa.db",
                ).build().also { INSTANCE = it }
            }
    }
}
