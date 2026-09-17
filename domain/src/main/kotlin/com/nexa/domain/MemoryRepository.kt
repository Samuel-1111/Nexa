package com.nexa.domain

import com.nexa.core.common.EntityId
import com.nexa.core.model.Memory
import com.nexa.core.model.MemoryCategory
import kotlinx.coroutines.flow.Flow

/**
 * Memory Center backing interface. Spec requirement: memory must be
 * explicitly approved by the user -- nothing here ever silently promotes a
 * SUGGESTED memory to ACTIVE on its own.
 */
interface MemoryRepository {
    fun observeAll(): Flow<List<Memory>>
    suspend fun approve(id: EntityId)
    suspend fun reject(id: EntityId)
    suspend fun edit(id: EntityId, content: String, category: MemoryCategory)
    suspend fun delete(id: EntityId)
}
