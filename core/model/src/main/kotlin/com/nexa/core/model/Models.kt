package com.nexa.core.model

import com.nexa.core.common.EntityId
import java.time.Instant

enum class TaskStatus { OPEN, COMPLETED, ARCHIVED }
enum class Priority { NONE, LOW, MEDIUM, HIGH }
enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, CONFLICT, ERROR }
enum class ReminderScheduleState { UNSCHEDULED, SCHEDULED, DELIVERED, CANCELED, ERROR }
enum class ReminderTimeSemantics { FIXED_INSTANT, FLOATING_LOCAL }
enum class ReminderPrecision { STANDARD, EXACT_IF_ALLOWED }
enum class NoteSource { TEXT, VOICE_TRANSCRIPT, AI_GENERATED }
// Must stay in lockstep with the `memories.category` check constraint in
// supabase/migrations — this is the sync contract between Room and Postgres.
enum class MemoryCategory { PREFERENCE, PERSON, GOAL, ROUTINE, IMPORTANT_DATE, WORK, SCHOOL, WRITING_STYLE, OTHER }
enum class MemoryStatus { SUGGESTED, ACTIVE, REJECTED, DELETED }

data class Task(
    val id: EntityId,
    val title: String,
    val body: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val priority: Priority = Priority.NONE,
    val dueAt: Instant? = null,
    val completedAt: Instant? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class Reminder(
    val id: EntityId,
    val taskId: EntityId? = null,
    val title: String,
    val body: String? = null,
    val triggerAt: Instant,
    val timezoneId: String,
    val scheduleState: ReminderScheduleState = ReminderScheduleState.UNSCHEDULED,
    val precision: ReminderPrecision = ReminderPrecision.STANDARD,
)

data class Note(
    val id: EntityId,
    val title: String? = null,
    val body: String,
    val source: NoteSource,
)

data class Memory(
    val id: EntityId,
    val content: String,
    val category: MemoryCategory,
    val status: MemoryStatus,
)
