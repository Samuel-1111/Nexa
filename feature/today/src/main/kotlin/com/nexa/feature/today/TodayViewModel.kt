package com.nexa.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.core.model.Priority
import com.nexa.core.model.Reminder
import com.nexa.core.model.Task
import com.nexa.domain.ReminderRepository
import com.nexa.domain.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Loaded(val tasks: List<Task>, val reminders: List<Reminder>) : TodayUiState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        taskRepository.observeOpenTasks(),
        reminderRepository.observeUpcoming(),
    ) { tasks, reminders -> TodayUiState.Loaded(tasks, reminders) as TodayUiState }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), TodayUiState.Loading)

    fun addQuickTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskRepository.create(title = title, priority = Priority.NONE) }
    }

    fun completeTask(task: Task) {
        viewModelScope.launch { taskRepository.complete(task.id) }
    }
}
