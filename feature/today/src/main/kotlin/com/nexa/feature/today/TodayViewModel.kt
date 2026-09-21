package com.nexa.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.core.model.Priority
import com.nexa.core.model.Reminder
import com.nexa.core.model.Task
import com.nexa.domain.CalendarEventRepository
import com.nexa.core.network.AuthRepository
import com.nexa.domain.NoteRepository
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
    data class Loaded(val tasks: List<Task>, val reminders: List<Reminder>, val eventCount: Int, val noteCount: Int) : TodayUiState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val authRepository: AuthRepository,
    private val noteRepository: NoteRepository,
    private val eventRepository: CalendarEventRepository,
) : ViewModel() {
    private val _displayName = MutableStateFlow("there")
    val displayName: StateFlow<String> = _displayName

    fun greeting(): String = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning,"
        in 12..16 -> "Good afternoon,"
        else -> "Good evening,"
    }

    init {
        viewModelScope.launch {
            _displayName.value = authRepository.currentDisplayName() ?: "there"
        }
    }

    val uiState: StateFlow<TodayUiState> = combine(
        taskRepository.observeOpenTasks(),
        reminderRepository.observeUpcoming(),
        noteRepository.observeRecent(),
        eventRepository.observeUpcoming(),
    ) { tasks, reminders, notes, events ->
        val today = java.time.LocalDate.now()
        TodayUiState.Loaded(tasks, reminders, events.count { it.startsAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }, notes.size)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), TodayUiState.Loading)

    fun addQuickTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskRepository.create(title = title, priority = Priority.NONE) }
    }

    fun completeTask(task: Task) {
        viewModelScope.launch { taskRepository.complete(task.id) }
    }
}
