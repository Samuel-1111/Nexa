package com.nexa.domain

import com.nexa.core.common.EntityId
import com.nexa.core.model.Note
import com.nexa.core.model.Priority
import com.nexa.core.model.Reminder
import com.nexa.core.model.Task
import com.nexa.core.model.CalendarEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeOpenTasks(): Flow<List<Task>>
    fun observeAllTasks(): Flow<List<Task>>
    suspend fun create(title: String, priority: Priority = Priority.NONE, dueAt: Instant? = null): Task
    suspend fun complete(id: EntityId)
    suspend fun toggleComplete(id: EntityId)
    suspend fun delete(id: EntityId)
}

interface ReminderRepository {
    fun observeUpcoming(): Flow<List<Reminder>>
    suspend fun create(title: String, triggerAt: Instant, timezoneId: String, body: String? = null): Reminder
    suspend fun delete(id: EntityId)
}

interface NoteRepository {
    fun observeRecent(): Flow<List<Note>>
    suspend fun create(title: String? = null, body: String, source: com.nexa.core.model.NoteSource, reference: String? = null): Note
    suspend fun delete(id: EntityId)
}

interface CalendarEventRepository {
    fun observeUpcoming(): Flow<List<CalendarEvent>>
    suspend fun create(title: String, description: String? = null, location: String? = null, startsAt: Instant, endsAt: Instant): CalendarEvent
    suspend fun delete(id: EntityId)
}
