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
import com.nexa.core.notifications.ReminderScheduler
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

class LocalReminderRepository(
    private val database: NexaDatabase,
    private val scheduler: ReminderScheduler,
) : ReminderRepository {
    override fun observeUpcoming(): Flow<List<Reminder>> = database.reminderDao().observeActive().map { rows ->
        rows.map { Reminder(EntityId(it.id), it.taskId?.let(::EntityId), it.title, it.body, Instant.ofEpochMilli(it.triggerAtEpochMs), it.timezoneId, ReminderScheduleState.valueOf(it.scheduleState), ReminderPrecision.valueOf(it.deliveryPrecision)) }
    }
    override suspend fun create(title: String, triggerAt: Instant, timezoneId: String): Reminder {
        val id = EntityId.new()
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            database.reminderDao().upsert(ReminderEntity(id.value, null, null, title, null, triggerAt.toEpochMilli(), timezoneId, "FIXED_INSTANT", "STANDARD", id.value.hashCode(), "SCHEDULED", null, now, now, null, 0, "PENDING"))
            database.outboxDao().upsert(OutboxOperationEntity(EntityId.new().value, "REMINDER", id.value, "UPSERT", 0, "{\"id\":\"${id.value}\"}", "PENDING", 0, null, null, now, now))
        }
        val reminder = Reminder(id, title = title, triggerAt = triggerAt, timezoneId = timezoneId, scheduleState = ReminderScheduleState.SCHEDULED)
        scheduler.schedule(reminder)
        return reminder
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


@androidx.hilt.work.HiltWorker
class NexaSyncWorker @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted appContext: android.content.Context,
    @dagger.assisted.Assisted workerParams: androidx.work.WorkerParameters,
    private val database: NexaDatabase,
    private val supabase: io.github.jan.supabase.SupabaseClient,
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return Result.retry()
        val pending = database.outboxDao().pending(System.currentTimeMillis())
        if (pending.isEmpty()) return Result.success()

        for (operation in pending) {
            try {
                when (operation.entityType) {
                    "TASK" -> {
                        val entity = database.taskDao().get(operation.entityId) ?: run {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.from("tasks").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", entity.id)
                            put("owner_id", userId)
                            put("title", entity.title)
                            put("body", entity.body)
                            put("status", entity.status)
                            put("priority", entity.priority)
                            entity.dueAtEpochMs?.let { put("due_at", java.time.Instant.ofEpochMilli(it).toString()) }
                            entity.dueTimezoneId?.let { put("due_timezone", it) }
                            entity.completedAtEpochMs?.let { put("completed_at", java.time.Instant.ofEpochMilli(it).toString()) }
                            put("server_version", maxOf(1L, entity.serverVersion))
                            put("created_at", java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())
                            put("updated_at", java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())
                            entity.deletedAtEpochMs?.let { put("deleted_at", java.time.Instant.ofEpochMilli(it).toString()) }
                        })
                        database.taskDao().upsert(entity.copy(ownerId = userId, serverVersion = maxOf(1L, entity.serverVersion), syncState = "SYNCED"))
                    }
                    "REMINDER" -> {
                        val entity = database.reminderDao().get(operation.entityId) ?: run {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.from("reminders").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", entity.id)
                            put("owner_id", userId)
                            entity.taskId?.let { put("task_id", it) }
                            put("title", entity.title)
                            put("body", entity.body)
                            put("trigger_at", java.time.Instant.ofEpochMilli(entity.triggerAtEpochMs).toString())
                            put("timezone", entity.timezoneId)
                            put("schedule_state", entity.scheduleState)
                            put("server_version", maxOf(1L, entity.serverVersion))
                            put("created_at", java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())
                            put("updated_at", java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())
                            entity.deletedAtEpochMs?.let { put("deleted_at", java.time.Instant.ofEpochMilli(it).toString()) }
                        })
                        database.reminderDao().upsert(entity.copy(ownerId = userId, serverVersion = maxOf(1L, entity.serverVersion), syncState = "SYNCED"))
                    }
                    "NOTE" -> {
                        val entity = database.noteDao().get(operation.entityId) ?: run {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.from("notes").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", entity.id)
                            put("owner_id", userId)
                            put("title", entity.title)
                            put("body", entity.body)
                            put("source", entity.source)
                            put("server_version", maxOf(1L, entity.serverVersion))
                            put("created_at", java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())
                            put("updated_at", java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())
                            entity.deletedAtEpochMs?.let { put("deleted_at", java.time.Instant.ofEpochMilli(it).toString()) }
                        })
                        database.noteDao().upsert(entity.copy(ownerId = userId, serverVersion = maxOf(1L, entity.serverVersion), syncState = "SYNCED"))
                    }
                    "MEMORY" -> {
                        val entity = database.memoryDao().get(operation.entityId) ?: run {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.from("memories").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", entity.id)
                            put("owner_id", userId)
                            put("content", entity.content)
                            put("category", entity.category)
                            put("status", entity.status)
                            put("source_type", entity.sourceType)
                            entity.sourceEntityId?.let { put("source_entity_id", it) }
                            entity.consentedAtEpochMs?.let { put("consented_at", java.time.Instant.ofEpochMilli(it).toString()) }
                            put("server_version", maxOf(1L, entity.serverVersion))
                            put("created_at", java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())
                            put("updated_at", java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())
                            entity.deletedAtEpochMs?.let { put("deleted_at", java.time.Instant.ofEpochMilli(it).toString()) }
                        })
                        database.memoryDao().upsert(entity.copy(ownerId = userId, serverVersion = maxOf(1L, entity.serverVersion), syncState = "SYNCED"))
                    }
                    else -> database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                }
                database.outboxDao().markDone(operation.id, System.currentTimeMillis())
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                val delay = (30_000L * (1L shl operation.attemptCount.coerceAtMost(5))).coerceAtMost(30 * 60_000L)
                database.outboxDao().markRetry(operation.id, e.message?.take(240) ?: "sync_failed", now + delay, now)
            }
        }
        return Result.success()
    }
}
