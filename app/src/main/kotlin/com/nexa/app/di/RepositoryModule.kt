package com.nexa.app.di

import com.nexa.core.database.NexaDatabase
import com.nexa.core.notifications.ReminderScheduler
import com.nexa.data.LocalCalendarEventRepository
import com.nexa.data.LocalMemoryRepository
import com.nexa.data.LocalNoteRepository
import com.nexa.data.LocalReminderRepository
import com.nexa.data.LocalTaskRepository
import com.nexa.domain.CalendarEventRepository
import com.nexa.domain.CommandInterpreter
import com.nexa.domain.DeterministicCommandInterpreter
import com.nexa.domain.MemoryRepository
import com.nexa.domain.NoteRepository
import com.nexa.domain.ReminderRepository
import com.nexa.domain.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides @Singleton
    fun provideTaskRepository(db: NexaDatabase): TaskRepository = LocalTaskRepository(db)

    @Provides @Singleton
    fun provideReminderRepository(db: NexaDatabase, scheduler: ReminderScheduler): ReminderRepository = LocalReminderRepository(db, scheduler)

    @Provides @Singleton
    fun provideNoteRepository(db: NexaDatabase): NoteRepository = LocalNoteRepository(db)

    @Provides @Singleton
    fun provideMemoryRepository(db: NexaDatabase): MemoryRepository = LocalMemoryRepository(db)

    @Provides @Singleton
    fun provideCalendarEventRepository(db: NexaDatabase): CalendarEventRepository = LocalCalendarEventRepository(db)

    @Provides @Singleton
    fun provideCommandInterpreter(): CommandInterpreter = DeterministicCommandInterpreter()
}
