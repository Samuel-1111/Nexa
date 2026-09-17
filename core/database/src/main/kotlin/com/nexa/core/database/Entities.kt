package com.nexa.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks", indices = [Index(value = ["status", "dueAtEpochMs"]), Index(value = ["updatedAtEpochMs"]), Index(value = ["ownerId"])])
data class TaskEntity(
    @PrimaryKey val id: String, val ownerId: String?, val title: String, val body: String?,
    val status: String, val priority: String, val dueAtEpochMs: Long?, val dueTimezoneId: String?,
    val completedAtEpochMs: Long?, val createdAtEpochMs: Long, val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?, val serverVersion: Long = 0, val syncState: String,
)

@Entity(tableName = "reminders", indices = [Index(value = ["triggerAtEpochMs", "scheduleState"]), Index(value = ["taskId"]), Index(value = ["ownerId"])])
data class ReminderEntity(
    @PrimaryKey val id: String, val ownerId: String?, val taskId: String?, val title: String, val body: String?,
    val triggerAtEpochMs: Long, val timezoneId: String, val timeSemantics: String, val deliveryPrecision: String,
    val notificationRequestCode: Int, val scheduleState: String, val deliveredAtEpochMs: Long?,
    val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val deletedAtEpochMs: Long?,
    val serverVersion: Long = 0, val syncState: String,
)

@Entity(tableName = "notes", indices = [Index(value = ["updatedAtEpochMs"]), Index(value = ["ownerId"])])
data class NoteEntity(
    @PrimaryKey val id: String, val ownerId: String?, val title: String?, val body: String, val source: String,
    val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val deletedAtEpochMs: Long?,
    val serverVersion: Long = 0, val syncState: String,
)

@Entity(tableName = "memories", indices = [Index(value = ["updatedAtEpochMs"]), Index(value = ["ownerId"])])
data class MemoryEntity(
    @PrimaryKey val id: String, val ownerId: String?, val content: String, val category: String, val status: String,
    val sourceType: String, val sourceEntityId: String?, val consentedAtEpochMs: Long?,
    val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val deletedAtEpochMs: Long?,
    val serverVersion: Long = 0, val syncState: String,
)

@Entity(tableName = "outbox_operations", indices = [Index(value = ["state", "nextAttemptAtEpochMs"]), Index(value = ["entityType", "entityId"])])
data class OutboxOperationEntity(
    @PrimaryKey val id: String, val entityType: String, val entityId: String, val operationType: String,
    val baseServerVersion: Long, val payloadJson: String, val state: String, val attemptCount: Int,
    val nextAttemptAtEpochMs: Long?, val lastErrorCode: String?, val createdAtEpochMs: Long, val updatedAtEpochMs: Long,
)
