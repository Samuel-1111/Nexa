package com.nexa.app.di

import android.content.Context
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
    @Provides @Singleton
    fun provideNexaDatabase(@ApplicationContext context: Context): NexaDatabase =
        NexaDatabase.getInstance(context)

    @Provides fun provideTaskDao(db: NexaDatabase) = db.taskDao()
    @Provides fun provideReminderDao(db: NexaDatabase) = db.reminderDao()
    @Provides fun provideNoteDao(db: NexaDatabase) = db.noteDao()
    @Provides fun provideMemoryDao(db: NexaDatabase) = db.memoryDao()
    @Provides fun provideOutboxDao(db: NexaDatabase) = db.outboxDao()
    @Provides fun provideCalendarEventDao(db: NexaDatabase) = db.calendarEventDao()
}
