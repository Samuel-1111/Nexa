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
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAtEpochMs IS NULL ORDER BY updatedAtEpochMs DESC")
    fun observeRecent(): Flow<List<NoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE deletedAtEpochMs IS NULL ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<MemoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryEntity)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_operations WHERE state IN ('PENDING', 'RETRY') AND (nextAttemptAtEpochMs IS NULL OR nextAttemptAtEpochMs <= :now) ORDER BY createdAtEpochMs ASC")
    suspend fun pending(now: Long): List<OutboxOperationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OutboxOperationEntity)
}
