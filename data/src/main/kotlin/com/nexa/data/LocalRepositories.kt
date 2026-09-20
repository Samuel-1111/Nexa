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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    private val reminderScheduler: ReminderScheduler,
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    private suspend fun pullRemoteData(userId: String) {
        val remoteTasks = supabase.postgrest.from("tasks").select { filter { eq("owner_id", userId) } }.decodeList<JsonObject>()
        for (row in remoteTasks) {
            val id = row.string("id") ?: continue
            val localTask = database.taskDao().get(id)
            if (!shouldApplyRemote(localTask?.syncState)) continue
            database.taskDao().upsert(
                TaskEntity(
                    id = id,
                    ownerId = userId,
                    title = row.string("title").orEmpty(),
                    body = row.string("body"),
                    status = row.string("status") ?: "OPEN",
                    priority = row.string("priority") ?: "NONE",
                    dueAtEpochMs = row.instant("due_at"),
                    dueTimezoneId = row.string("due_timezone"),
                    completedAtEpochMs = row.instant("completed_at"),
                    createdAtEpochMs = row.instant("created_at") ?: System.currentTimeMillis(),
                    updatedAtEpochMs = row.instant("updated_at") ?: System.currentTimeMillis(),
                    deletedAtEpochMs = row.instant("deleted_at"),
                    serverVersion = row.long("server_version") ?: 1L,
                    syncState = "SYNCED",
                ),
            )
        }

        val remoteReminders = supabase.postgrest.from("reminders").select { filter { eq("owner_id", userId) } }.decodeList<JsonObject>()
        val now = System.currentTimeMillis()
        for (row in remoteReminders) {
            val id = row.string("id") ?: continue
            val trigger = row.instant("trigger_at") ?: continue
            val scheduleState = row.string("schedule_state") ?: "UNSCHEDULED"
            val reminder = com.nexa.core.model.Reminder(
                EntityId(id),
                row.string("task_id")?.let(::EntityId),
                row.string("title").orEmpty(),
                row.string("body"),
                Instant.ofEpochMilli(trigger),
                row.string("timezone") ?: "UTC",
                ReminderScheduleState.valueOf(scheduleState),
                ReminderPrecision.STANDARD,
            )
            val localReminder = database.reminderDao().get(id)
            if (!shouldApplyRemote(localReminder?.syncState)) continue
            database.reminderDao().upsert(
                ReminderEntity(
                    id = id,
                    ownerId = userId,
                    taskId = row.string("task_id"),
                    title = row.string("title").orEmpty(),
                    body = row.string("body"),
                    triggerAtEpochMs = trigger,
                    timezoneId = row.string("timezone") ?: "UTC",
                    timeSemantics = "FIXED_INSTANT",
                    deliveryPrecision = "STANDARD",
                    notificationRequestCode = id.hashCode(),
                    scheduleState = if (trigger > now && scheduleState != "CANCELED" && scheduleState != "DELIVERED") "SCHEDULED" else scheduleState,
                    deliveredAtEpochMs = row.instant("delivered_at"),
                    createdAtEpochMs = row.instant("created_at") ?: now,
                    updatedAtEpochMs = row.instant("updated_at") ?: now,
                    deletedAtEpochMs = row.instant("deleted_at"),
                    serverVersion = row.long("server_version") ?: 1L,
                    syncState = "SYNCED",
                ),
            )
            if (trigger > now && scheduleState != "CANCELED" && scheduleState != "DELIVERED") reminderScheduler.schedule(reminder)
        }

        val remoteNotes = supabase.postgrest.from("notes").select { filter { eq("owner_id", userId) } }.decodeList<JsonObject>()
        for (row in remoteNotes) {
            val id = row.string("id") ?: continue
            val localNote = database.noteDao().get(id)
            if (!shouldApplyRemote(localNote?.syncState)) continue
            database.noteDao().upsert(
                NoteEntity(
                    id = id,
                    ownerId = userId,
                    title = row.string("title"),
                    body = row.string("body").orEmpty(),
                    source = row.string("source") ?: "TEXT",
                    createdAtEpochMs = row.instant("created_at") ?: now,
                    updatedAtEpochMs = row.instant("updated_at") ?: now,
                    deletedAtEpochMs = row.instant("deleted_at"),
                    serverVersion = row.long("server_version") ?: 1L,
                    syncState = "SYNCED",
                ),
            )
        }

        val remoteMemories = supabase.postgrest.from("memories").select { filter { eq("owner_id", userId) } }.decodeList<JsonObject>()
        for (row in remoteMemories) {
            val id = row.string("id") ?: continue
            val localMemory = database.memoryDao().get(id)
            if (!shouldApplyRemote(localMemory?.syncState)) continue
            database.memoryDao().upsert(
                com.nexa.core.database.MemoryEntity(
                    id = id,
                    ownerId = userId,
                    content = row.string("content").orEmpty(),
                    category = row.string("category") ?: "OTHER",
                    status = row.string("status") ?: "SUGGESTED",
                    sourceType = row.string("source_type") ?: "SYNC",
                    sourceEntityId = row.string("source_entity_id"),
                    consentedAtEpochMs = row.instant("consented_at"),
                    createdAtEpochMs = row.instant("created_at") ?: now,
                    updatedAtEpochMs = row.instant("updated_at") ?: now,
                    deletedAtEpochMs = row.instant("deleted_at"),
                    serverVersion = row.long("server_version") ?: 1L,
                    syncState = "SYNCED",
                ),
            )
        }
    }

    // Remote pulls may only replace records that are already synchronized.
    // Pending/conflicted/error local changes stay local until their outbox
    // operation succeeds, preventing a background pull from silently losing work.
    private fun shouldApplyRemote(localSyncState: String?): Boolean =
        localSyncState == null || localSyncState == "SYNCED"

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()
    private fun JsonObject.instant(name: String): Long? = string(name)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    override suspend fun doWork(): Result {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return Result.retry()
        val pending = database.outboxDao().pending(System.currentTimeMillis())

        var hadFailure = false
        for (operation in pending) {
            try {
                when (operation.entityType) {
                    "TASK" -> {
                        val entity = database.taskDao().get(operation.entityId)
                        if (entity == null) {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.postgrest.from("tasks").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", JsonPrimitive(entity.id))
                            put("owner_id", JsonPrimitive(userId))
                            put("title", JsonPrimitive(entity.title))
                            put("body", JsonPrimitive(entity.body))
                            put("status", JsonPrimitive(entity.status))
                            put("priority", JsonPrimitive(entity.priority))
                            entity.dueAtEpochMs?.let { put("due_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(it).toString()))) }
                            entity.dueTimezoneId?.let { put("due_timezone", JsonPrimitive(it)) }
                            entity.completedAtEpochMs?.let { put("completed_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(it).toString()))) }
                            put("server_version", JsonPrimitive(maxOf(1L, entity.serverVersion)))
                            put("created_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())))
                            put("updated_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())))
                            entity.deletedAtEpochMs?.let { put("deleted_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(it).toString()))) }
                        })
                        database.taskDao().upsert(entity.copy(ownerId = userId, serverVersion = maxOf(1L, entity.serverVersion), syncState = "SYNCED"))
                    }
                    "REMINDER" -> {
                        val entity = database.reminderDao().get(operation.entityId)
                        if (entity == null) {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.postgrest.from("reminders").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", JsonPrimitive(entity.id))
                            put("owner_id", JsonPrimitive(userId))
                            entity.taskId?.let { put("task_id", JsonPrimitive(it)) }
                            put("title", JsonPrimitive(entity.title))
                            put("body", JsonPrimitive(entity.body))
                            put("trigger_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.triggerAtEpochMs).toString())))
                            put("timezone", JsonPrimitive(entity.timezoneId))
                            put("schedule_state", JsonPrimitive(entity.scheduleState))
                            put("server_version", JsonPrimitive(maxOf(1L, entity.serverVersion)))
                            put("created_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())))
                            put("updated_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())))
                            entity.deletedAtEpochMs?.let { put("deleted_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(it).toString()))) }
                        })
                        database.reminderDao().upsert(entity.copy(ownerId = userId, serverVersion = maxOf(1L, entity.serverVersion), syncState = "SYNCED"))
                    }
                    "NOTE" -> {
                        val entity = database.noteDao().get(operation.entityId)
                        if (entity == null) {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.postgrest.from("notes").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", JsonPrimitive(entity.id))
                            put("owner_id", JsonPrimitive(userId))
                            put("title", JsonPrimitive(entity.title))
                            put("body", JsonPrimitive(entity.body))
                            put("source", JsonPrimitive(entity.source))
                            put("server_version", JsonPrimitive(maxOf(1L, entity.serverVersion)))
                            put("created_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())))
                            put("updated_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())))
                            entity.deletedAtEpochMs?.let { put("deleted_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(it).toString()))) }
                        })
                        database.noteDao().upsert(entity.copy(ownerId = userId, serverVersion = maxOf(1L, entity.serverVersion), syncState = "SYNCED"))
                    }
                    "MEMORY" -> {
                        val entity = database.memoryDao().get(operation.entityId)
                        if (entity == null) {
                            database.outboxDao().markDone(operation.id, System.currentTimeMillis())
                            continue
                        }
                        supabase.postgrest.from("memories").upsert(kotlinx.serialization.json.buildJsonObject {
                            put("id", JsonPrimitive(entity.id))
                            put("owner_id", JsonPrimitive(userId))
                            put("content", JsonPrimitive(entity.content))
                            put("category", JsonPrimitive(entity.category))
                            put("status", JsonPrimitive(entity.status))
                            put("source_type", JsonPrimitive(entity.sourceType))
                            entity.sourceEntityId?.let { put("source_entity_id", JsonPrimitive(it)) }
                            entity.consentedAtEpochMs?.let { put("consented_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(it).toString()))) }
                            put("server_version", JsonPrimitive(maxOf(1L, entity.serverVersion)))
                            put("created_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.createdAtEpochMs).toString())))
                            put("updated_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(entity.updatedAtEpochMs).toString())))
                            entity.deletedAtEpochMs?.let { put("deleted_at", JsonPrimitive(JsonPrimitive(java.time.Instant.ofEpochMilli(it).toString()))) }
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
                hadFailure = true
            }
        }
        if (hadFailure) return Result.retry()
        return try {
            pullRemoteData(userId)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
