package com.nexa.data

import com.nexa.core.common.EntityId
import com.nexa.core.common.SystemClock
import com.nexa.core.database.NexaDatabase
import com.nexa.core.model.Memory
import com.nexa.core.model.MemoryCategory
import com.nexa.core.model.MemoryStatus
import com.nexa.domain.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local-first Memory Center repository. Sync-outbox integration is
 * intentionally deferred here (unlike Task/Reminder/Note) -- memories carry
 * a status transition (SUGGESTED -> ACTIVE/REJECTED) that the outbox schema
 * doesn't yet distinguish from a plain upsert; wiring it blind would risk
 * silently syncing a rejection as an approval. Flagged as follow-up, not
 * done here to avoid guessing at outbox semantics that touch shared code.
 */
class LocalMemoryRepository(private val database: NexaDatabase) : MemoryRepository {

    override fun observeAll(): Flow<List<Memory>> = database.memoryDao().observeAll().map { rows ->
        rows.map { Memory(EntityId(it.id), it.content, MemoryCategory.valueOf(it.category), MemoryStatus.valueOf(it.status)) }
    }

    override suspend fun approve(id: EntityId) {
        database.memoryDao().approve(id.value, SystemClock.now().toEpochMilli())
    }

    override suspend fun reject(id: EntityId) {
        database.memoryDao().reject(id.value, SystemClock.now().toEpochMilli())
    }

    override suspend fun edit(id: EntityId, content: String, category: MemoryCategory) {
        database.memoryDao().edit(id.value, content, category.name, SystemClock.now().toEpochMilli())
    }

    override suspend fun delete(id: EntityId) {
        database.memoryDao().softDelete(id.value, SystemClock.now().toEpochMilli())
    }
}
