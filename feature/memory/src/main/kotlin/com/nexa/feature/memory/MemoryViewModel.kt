package com.nexa.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.core.common.EntityId
import com.nexa.core.model.Memory
import com.nexa.core.model.MemoryStatus
import com.nexa.domain.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val suggested: List<Memory> = emptyList(),
    val active: List<Memory> = emptyList(),
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: MemoryRepository,
) : ViewModel() {

    val uiState: StateFlow<MemoryUiState> = repository.observeAll()
        .map { all ->
            MemoryUiState(
                suggested = all.filter { it.status == MemoryStatus.SUGGESTED },
                active = all.filter { it.status == MemoryStatus.ACTIVE },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryUiState())

    fun approve(id: EntityId) = viewModelScope.launch { repository.approve(id) }
    fun dismiss(id: EntityId) = viewModelScope.launch { repository.reject(id) }
    fun delete(id: EntityId) = viewModelScope.launch { repository.delete(id) }
}
