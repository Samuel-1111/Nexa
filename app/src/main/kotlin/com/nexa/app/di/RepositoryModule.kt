package com.nexa.app.di

import com.nexa.core.database.NexaDatabase
import com.nexa.data.LocalMemoryRepository
import com.nexa.data.LocalNoteRepository
import com.nexa.data.LocalReminderRepository
import com.nexa.data.LocalTaskRepository
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

/**
 * Single place where domain interfaces are bound to their local-first
 * implementations. Swapping TaskRepository's backing implementation later
 * (e.g. to add remote-first reads once sync matures) only touches this file.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTaskRepository(db: NexaDatabase): TaskRepository = LocalTaskRepository(db)

    @Provides
    @Singleton
    fun provideReminderRepository(db: NexaDatabase): ReminderRepository = LocalReminderRepository(db)

    @Provides
    @Singleton
    fun provideNoteRepository(db: NexaDatabase): NoteRepository = LocalNoteRepository(db)

    @Provides
    @Singleton
    fun provideMemoryRepository(db: NexaDatabase): MemoryRepository = LocalMemoryRepository(db)

    @Provides
    @Singleton
    fun provideCommandInterpreter(): CommandInterpreter = DeterministicCommandInterpreter()
}
