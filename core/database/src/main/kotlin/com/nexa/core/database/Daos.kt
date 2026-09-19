package com.nexa.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAtEpochMs IS NULL AND status = 'OPEN' ORDER BY priority DESC, dueAtEpochMs ASC")
    fun observeOpen(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE deletedAtEpochMs IS NULL AND scheduleState IN ('UNSCHEDULED', 'SCHEDULED') ORDER BY triggerAtEpochMs ASC")
    fun observeActive(): Flow<List<ReminderEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)
    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ReminderEntity?
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAtEpochMs IS NULL ORDER BY updatedAtEpochMs DESC")
    fun observeRecent(): Flow<List<NoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteEntity)
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): NoteEntity?
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE deletedAtEpochMs IS NULL ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<MemoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryEntity)
    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MemoryEntity?
    // Memory Center actions (spec: memory must be explicitly approved, never
    // silently promoted from SUGGESTED to ACTIVE). Each sets status + touches
    // updatedAtEpochMs; approve also stamps consentedAtEpochMs.
    @Query("UPDATE memories SET status = 'ACTIVE', consentedAtEpochMs = :nowEpochMs, updatedAtEpochMs = :nowEpochMs WHERE id = :id")
    suspend fun approve(id: String, nowEpochMs: Long)
    @Query("UPDATE memories SET status = 'REJECTED', updatedAtEpochMs = :nowEpochMs WHERE id = :id")
    suspend fun reject(id: String, nowEpochMs: Long)
    @Query("UPDATE memories SET content = :content, category = :category, updatedAtEpochMs = :nowEpochMs WHERE id = :id")
    suspend fun edit(id: String, content: String, category: String, nowEpochMs: Long)
    @Query("UPDATE memories SET deletedAtEpochMs = :nowEpochMs, updatedAtEpochMs = :nowEpochMs WHERE id = :id")
    suspend fun softDelete(id: String, nowEpochMs: Long)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_operations WHERE state IN ('PENDING', 'RETRY') AND (nextAttemptAtEpochMs IS NULL OR nextAttemptAtEpochMs <= :now) ORDER BY createdAtEpochMs ASC")
    suspend fun pending(now: Long): List<OutboxOperationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OutboxOperationEntity)
    @Query("UPDATE outbox_operations SET state = 'DONE', lastErrorCode = NULL, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markDone(id: String, now: Long)
    @Query("UPDATE outbox_operations SET state = 'RETRY', attemptCount = attemptCount + 1, lastErrorCode = :error, nextAttemptAtEpochMs = :nextAttempt, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markRetry(id: String, error: String, nextAttempt: Long, now: Long)
}
