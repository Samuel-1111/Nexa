package com.nexa.data

import androidx.room.withTransaction
import com.nexa.core.common.EntityId
import com.nexa.core.common.SystemClock
import com.nexa.core.database.NexaDatabase
import com.nexa.core.database.OutboxOperationEntity
import com.nexa.core.model.Memory
import com.nexa.core.model.MemoryCategory
import com.nexa.core.model.MemoryStatus
import com.nexa.domain.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalMemoryRepository(private val database: NexaDatabase) : MemoryRepository {
    override fun observeAll(): Flow<List<Memory>> = database.memoryDao().observeAll().map { rows ->
        rows.map { Memory(EntityId(it.id), it.content, MemoryCategory.valueOf(it.category), MemoryStatus.valueOf(it.status)) }
    }

    override suspend fun approve(id: EntityId) = changeStatus(id, "APPROVE") { row, now ->
        database.memoryDao().approve(row.id, now)
    }

    override suspend fun reject(id: EntityId) = changeStatus(id, "REJECT") { row, now ->
        database.memoryDao().reject(row.id, now)
    }

    override suspend fun edit(id: EntityId, content: String, category: MemoryCategory) {
        val row = database.memoryDao().get(id.value) ?: return
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            database.memoryDao().edit(id.value, content.trim(), category.name, now)
            database.outboxDao().upsert(outbox(id.value, row.serverVersion, now))
        }
    }

    override suspend fun delete(id: EntityId) {
        val row = database.memoryDao().get(id.value) ?: return
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            database.memoryDao().softDelete(id.value, now)
            database.outboxDao().upsert(outbox(id.value, row.serverVersion, now))
        }
    }

    private suspend fun changeStatus(id: EntityId, operation: String, action: suspend (com.nexa.core.database.MemoryEntity, Long) -> Unit) {
        val row = database.memoryDao().get(id.value) ?: return
        val now = SystemClock.now().toEpochMilli()
        database.withTransaction {
            action(row, now)
            database.outboxDao().upsert(outbox(id.value, row.serverVersion, now, operation))
        }
    }

    private fun outbox(id: String, version: Long, now: Long, operation: String = "UPSERT") =
        OutboxOperationEntity(
            id = EntityId.new().value,
            entityType = "MEMORY",
            entityId = id,
            operationType = operation,
            baseServerVersion = version,
            payloadJson = "{\"id\":\"$id\"}",
            state = "PENDING",
            attemptCount = 0,
            nextAttemptAtEpochMs = null,
            lastErrorCode = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
}
