package com.nexa.data

import androidx.room.withTransaction
import com.nexa.core.common.EntityId
import com.nexa.core.common.SystemClock
import com.nexa.core.database.NexaDatabase
import com.nexa.core.database.NoteEntity
import com.nexa.core.database.OutboxOperationEntity
import com.nexa.core.database.ReminderEntity
import com.nexa.core.database.TaskEntity
import com.nexa.core.model.Note
import com.nexa.core.model.NoteSource
import com.nexa.core.model.Priority
import com.nexa.core.model.Reminder
import com.nexa.core.model.ReminderPrecision
import com.nexa.core.model.ReminderScheduleState
import com.nexa.core.model.Task
import com.nexa.core.model.TaskStatus
import com.nexa.domain.NoteRepository
import com.nexa.domain.ReminderRepository
import com.nexa.domain.TaskRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalTaskRepository(private val database: NexaDatabase) : TaskRepository {
    override fun observeOpenTasks(): Flow<List<Task>> = database.taskDao().observeOpen().map { rows ->
        rows.map { Task(EntityId(it.id), it.title, it.body, TaskStatus.valueOf(it.status), Priority.valueOf(it.priority), it.dueAtEpochMs?.let(Instant::ofEpochMilli), it.completedAtEpochMs?.let(Instant::ofEpochMilli)) }
    }

    override suspend fun create(title: String, priority: Priority, dueAt: Instant?): Task {
        val id = EntityId.new()
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            database.taskDao().upsert(TaskEntity(id.value, null, title, null, "OPEN", priority.name, dueAt?.toEpochMilli(), null, null, now, now, null, 0, "PENDING"))
            database.outboxDao().upsert(OutboxOperationEntity(EntityId.new().value, "TASK", id.value, "UPSERT", 0, "{\"id\":\"${id.value}\"}", "PENDING", 0, null, null, now, now))
        }
        return Task(id, title, priority = priority, dueAt = dueAt)
    }

    override suspend fun complete(id: EntityId) {
        val existing = database.taskDao().get(id.value) ?: return
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            database.taskDao().upsert(existing.copy(status = "COMPLETED", completedAtEpochMs = now, updatedAtEpochMs = now, syncState = "PENDING"))
            database.outboxDao().upsert(OutboxOperationEntity(EntityId.new().value, "TASK", id.value, "UPSERT", existing.serverVersion, "{\"id\":\"${id.value}\"}", "PENDING", 0, null, null, now, now))
        }
    }
}

class LocalReminderRepository(private val database: NexaDatabase) : ReminderRepository {
    override fun observeUpcoming(): Flow<List<Reminder>> = database.reminderDao().observeActive().map { rows ->
        rows.map { Reminder(EntityId(it.id), it.taskId?.let(::EntityId), it.title, it.body, Instant.ofEpochMilli(it.triggerAtEpochMs), it.timezoneId, ReminderScheduleState.valueOf(it.scheduleState), ReminderPrecision.valueOf(it.deliveryPrecision)) }
    }

    override suspend fun create(title: String, triggerAt: Instant, timezoneId: String): Reminder {
        val id = EntityId.new()
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            database.reminderDao().upsert(ReminderEntity(id.value, null, null, title, null, triggerAt.toEpochMilli(), timezoneId, "FIXED_INSTANT", "STANDARD", id.value.hashCode(), "UNSCHEDULED", null, now, now, null, 0, "PENDING"))
            database.outboxDao().upsert(OutboxOperationEntity(EntityId.new().value, "REMINDER", id.value, "UPSERT", 0, "{\"id\":\"${id.value}\"}", "PENDING", 0, null, null, now, now))
        }
        return Reminder(id, title = title, triggerAt = triggerAt, timezoneId = timezoneId)
    }
}

class LocalNoteRepository(private val database: NexaDatabase) : NoteRepository {
    override fun observeRecent(): Flow<List<Note>> = database.noteDao().observeRecent().map { rows ->
        rows.map { Note(EntityId(it.id), it.title, it.body, NoteSource.valueOf(it.source)) }
    }

    override suspend fun create(body: String, source: NoteSource): Note {
        val id = EntityId.new()
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            database.noteDao().upsert(NoteEntity(id.value, null, null, body, source.name, now, now, null, 0, "PENDING"))
            database.outboxDao().upsert(OutboxOperationEntity(EntityId.new().value, "NOTE", id.value, "UPSERT", 0, "{\"id\":\"${id.value}\"}", "PENDING", 0, null, null, now, now))
        }
        return Note(id, body = body, source = source)
    }
}
