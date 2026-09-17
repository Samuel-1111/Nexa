package com.nexa.domain

import com.nexa.core.common.EntityId
import com.nexa.core.model.Note
import com.nexa.core.model.Priority
import com.nexa.core.model.Reminder
import com.nexa.core.model.Task
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeOpenTasks(): Flow<List<Task>>
    suspend fun create(title: String, priority: Priority = Priority.NONE, dueAt: Instant? = null): Task
    suspend fun complete(id: EntityId)
}

interface ReminderRepository {
    fun observeUpcoming(): Flow<List<Reminder>>
    suspend fun create(title: String, triggerAt: Instant, timezoneId: String): Reminder
}

interface NoteRepository {
    fun observeRecent(): Flow<List<Note>>
    suspend fun create(body: String, source: com.nexa.core.model.NoteSource): Note
}
