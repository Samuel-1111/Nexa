package com.nexa.app.di

import android.content.Context
import androidx.room.Room
import com.nexa.core.database.NexaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNexaDatabase(@ApplicationContext context: Context): NexaDatabase =
        Room.databaseBuilder(context, NexaDatabase::class.java, "nexa.db")
            // No destructive fallback: an unhandled migration should surface
            // as a crash we notice in testing, never as silent data loss.
            .build()

    @Provides
    fun provideTaskDao(db: NexaDatabase) = db.taskDao()

    @Provides
    fun provideReminderDao(db: NexaDatabase) = db.reminderDao()

    @Provides
    fun provideNoteDao(db: NexaDatabase) = db.noteDao()

    @Provides
    fun provideMemoryDao(db: NexaDatabase) = db.memoryDao()

    @Provides
    fun provideOutboxDao(db: NexaDatabase) = db.outboxDao()
}
