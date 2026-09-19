package com.nexa.feature.organizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.core.model.Note
import com.nexa.core.model.NoteSource
import com.nexa.core.model.Priority
import com.nexa.core.model.Reminder
import com.nexa.core.model.Task
import com.nexa.core.designsystem.NexaColors
import com.nexa.domain.NoteRepository
import com.nexa.domain.ReminderRepository
import com.nexa.domain.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrganizerState(
    val tasks: List<Task> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val notes: List<Note> = emptyList(),
)

@HiltViewModel
class OrganizerViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {
    val state: StateFlow<OrganizerState> = combine(
        taskRepository.observeOpenTasks(),
        reminderRepository.observeUpcoming(),
        noteRepository.observeRecent(),
    ) { tasks, reminders, notes -> OrganizerState(tasks, reminders, notes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrganizerState())

    fun add(type: String, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            when (type) {
                "TASK" -> taskRepository.create(value.trim(), Priority.NONE)
                "REMINDER" -> reminderRepository.create(value.trim(), Instant.now().plusSeconds(3600), java.time.ZoneId.systemDefault().id)
                "NOTE" -> noteRepository.create(value.trim(), NoteSource.TEXT)
            }
        }
    }

    fun complete(task: Task) {
        viewModelScope.launch { taskRepository.complete(task.id) }
    }
}

@Composable
fun OrganizerScreen(viewModel: OrganizerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var type by rememberSaveable { mutableStateOf("TASK") }
    var input by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Organizer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Tasks • Reminders • Notes", color = NexaColors.OnSurfaceMuted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TASK" to "Task", "REMINDER" to "Reminder", "NOTE" to "Note").forEach { (value, label) ->
                FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a ${type.lowercase()}…") },
                shape = RoundedCornerShape(18.dp),
                maxLines = 2,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.add(type, input); input = "" }, enabled = input.isNotBlank(), shape = RoundedCornerShape(18.dp)) { Text("Add") }
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { Text("Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.tasks.isEmpty()) item { EmptyRow("No open tasks yet.") }
            items(state.tasks, key = { it.id.value }) { task ->
                OrganizerRow(Icons.Default.CheckCircle, task.title, task.dueAt?.toString() ?: "No due time") { viewModel.complete(task) }
            }
            item { Spacer(Modifier.height(6.dp)); Text("Reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.reminders.isEmpty()) item { EmptyRow("No upcoming reminders.") }
            items(state.reminders, key = { it.id.value }) { reminder -> OrganizerRow(Icons.Default.Notifications, reminder.title, reminder.triggerAt.toString()) }
            item { Spacer(Modifier.height(6.dp)); Text("Notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.notes.isEmpty()) item { EmptyRow("No notes yet.") }
            items(state.notes, key = { it.id.value }) { note -> OrganizerRow(Icons.Default.Note, note.title ?: "Note", note.body.take(120)) }
        }
    }
}

@Composable
private fun OrganizerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: (() -> Unit)? = null,
) {
    Card(onClick = onClick ?: {}, enabled = onClick != null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = NexaColors.Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = NexaColors.OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, color = NexaColors.OnSurfaceMuted, modifier = Modifier.padding(vertical = 8.dp))
}
