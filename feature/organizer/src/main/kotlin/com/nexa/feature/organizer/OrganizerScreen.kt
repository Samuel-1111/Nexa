package com.nexa.feature.organizer

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.nexa.core.designsystem.NexaColors
import com.nexa.core.model.*
import com.nexa.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class OrganizerSection { TASKS, REMINDERS, NOTES, EVENTS }
data class OrganizerState(val tasks: List<Task> = emptyList(), val reminders: List<Reminder> = emptyList(), val notes: List<Note> = emptyList(), val events: List<CalendarEvent> = emptyList())

@HiltViewModel
class OrganizerViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val notes: NoteRepository,
    private val events: CalendarEventRepository,
) : ViewModel() {
    val state = combine(tasks.observeAllTasks(), reminders.observeUpcoming(), notes.observeRecent(), events.observeUpcoming()) { t, r, n, e -> OrganizerState(t, r, n, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrganizerState())
    fun toggle(task: Task) = viewModelScope.launch { tasks.toggleComplete(task.id) }
    fun addTask(title: String, priority: Priority, due: Instant?) = viewModelScope.launch { if (title.isNotBlank()) tasks.create(title.trim(), priority, due) }
    fun addReminder(title: String, body: String, at: Instant) = viewModelScope.launch { if (title.isNotBlank()) reminders.create(title.trim(), at, ZoneId.systemDefault().id, body.trim().ifBlank { null }) }
    fun addNote(title: String, body: String, ref: String) = viewModelScope.launch { if (body.isNotBlank()) notes.create(title.trim().ifBlank { null }, body.trim(), NoteSource.TEXT, ref.trim().ifBlank { null }) }
    fun addEvent(title: String, description: String, location: String, start: Instant, end: Instant) = viewModelScope.launch { if (title.isNotBlank()) events.create(title.trim(), description.trim().ifBlank { null }, location.trim().ifBlank { null }, start, end) }
}

@Composable
fun OrganizerScreen(initialSection: String = "OVERVIEW", viewModel: OrganizerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var section by rememberSaveable(initialSection) { mutableStateOf(runCatching { OrganizerSection.valueOf(initialSection) }.getOrDefault(OrganizerSection.TASKS)) }
    var showOverview by rememberSaveable(initialSection) { mutableStateOf(initialSection == "OVERVIEW") }
    var taskDialog by rememberSaveable { mutableStateOf(false) }
    var reminderDialog by rememberSaveable { mutableStateOf(false) }
    var noteDialog by rememberSaveable { mutableStateOf(false) }
    var eventDialog by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun prepareReminderCreation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
        }
        reminderDialog = true
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("Organizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tasks • Reminders • Notes • Events", style = MaterialTheme.typography.bodySmall, color = NexaColors.OnSurfaceMuted)
        Spacer(Modifier.height(7.dp))
        val visibleSections = listOf(OrganizerSection.TASKS, OrganizerSection.REMINDERS, OrganizerSection.EVENTS)
        ScrollableTabRow(selectedTabIndex = visibleSections.indexOf(if (section == OrganizerSection.NOTES) OrganizerSection.EVENTS else section).coerceAtLeast(0), edgePadding = 0.dp) {
            visibleSections.forEach { item ->
                Tab(selected = section == item, onClick = { section = item; showOverview = false }, text = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }, icon = { Icon(when(item){ OrganizerSection.TASKS->Icons.Default.CheckCircle; OrganizerSection.REMINDERS->Icons.Default.Alarm; OrganizerSection.NOTES->Icons.Default.Note; OrganizerSection.EVENTS->Icons.Default.CalendarMonth }, null, Modifier.size(18.dp)) })
            }
        }
        if (showOverview) {
            OrganizerOverview(state, onSection = { section = it; showOverview = false })
        } else when(section) {
            OrganizerSection.TASKS -> TaskSection(state.tasks, { taskDialog = true }, viewModel::toggle)
            OrganizerSection.REMINDERS -> ReminderSection(state.reminders, { prepareReminderCreation() })
            OrganizerSection.NOTES -> NoteSection(state.notes, { noteDialog = true })
            OrganizerSection.EVENTS -> EventSection(state.events, { eventDialog = true }, { section = OrganizerSection.NOTES })
        }
    }
    if(taskDialog) TaskDialog({taskDialog=false}) { t,p,d -> viewModel.addTask(t,p,d); taskDialog=false }
    if(reminderDialog) ReminderDialog({reminderDialog=false}) { t,b,d -> viewModel.addReminder(t,b,d); reminderDialog=false }
    if(noteDialog) NoteDialog({noteDialog=false}) { t,b,r -> viewModel.addNote(t,b,r); noteDialog=false }
    if(eventDialog) EventDialog({eventDialog=false}) { t,d,l,s,e -> viewModel.addEvent(t,d,l,s,e); eventDialog=false }
}

@Composable
private fun OrganizerOverview(state: OrganizerState, onSection: (OrganizerSection) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.EventBlueBg)) {
        Column(Modifier.padding(12.dp)) {
            Text("All organizer items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Jump directly to what you want to manage.", style = MaterialTheme.typography.bodySmall, color = NexaColors.OnSurfaceMuted)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryButton("Tasks", state.tasks.size, Icons.Default.CheckCircle) { onSection(OrganizerSection.TASKS) }
                SummaryButton("Reminders", state.reminders.size, Icons.Default.Alarm) { onSection(OrganizerSection.REMINDERS) }
                SummaryButton("Notes", state.notes.size, Icons.Default.Note) { onSection(OrganizerSection.NOTES) }
                SummaryButton("Events", state.events.size, Icons.Default.CalendarMonth) { onSection(OrganizerSection.EVENTS) }
            }
        }
    }
}

@Composable
private fun RowScope.SummaryButton(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(7.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = NexaColors.Primary)
            Text(count.toString(), fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}


@Composable
private fun TaskSection(tasks: List<Task>, add: () -> Unit, toggle: (Task) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(tasks.count { it.status == TaskStatus.OPEN }.toString() + " open • " + tasks.count { it.status == TaskStatus.COMPLETED } + " completed", style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
            }
            FilledTonalButton(onClick = add) { Icon(Icons.Default.Add, null); Text("Task") }
        }
        Spacer(Modifier.height(7.dp))
        if (tasks.isEmpty()) EmptyState("No tasks yet", "Add a task and keep it here until you finish it.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(tasks, key = { it.id.value }) { task ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.status == TaskStatus.COMPLETED, onCheckedChange = { toggle(task) })
                        Column(Modifier.weight(1f)) {
                            Text(task.title, fontWeight = FontWeight.SemiBold)
                            val dueAt = task.dueAt
                            Text(
                                if (task.status == TaskStatus.COMPLETED) "Completed"
                                else if (dueAt != null) dueAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a"))
                                else "No due date",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexaColors.OnSurfaceMuted,
                            )
                        }
                        AssistChip(onClick = {}, label = { Text(task.priority.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderSection(reminders: List<Reminder>, add: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Scheduled alarms and notifications", style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
            }
            FilledTonalButton(onClick = add) { Icon(Icons.Default.Add, null); Text("Reminder") }
        }
        Spacer(Modifier.height(7.dp))
        if (reminders.isEmpty()) EmptyState("No reminders", "Create a reminder with a date and alarm time.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(reminders, key = { it.id.value }) { reminder ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Alarm, null, tint = NexaColors.ReminderOrange)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(reminder.title, fontWeight = FontWeight.SemiBold)
                            Text(reminder.triggerAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMM d • h:mm a")), style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
                            reminder.body?.let { Text(it, maxLines = 2, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteSection(notes: List<Note>, add: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Subject, body and reference", style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
            }
            FilledTonalButton(onClick = add) { Icon(Icons.Default.Add, null); Text("Note") }
        }
        Spacer(Modifier.height(7.dp))
        if (notes.isEmpty()) EmptyState("No notes", "Capture ideas, details and references.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(notes, key = { it.id.value }) { note ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(note.title ?: "Untitled note", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(note.body, maxLines = 4)
                        note.reference?.let { Text("Reference: " + it, style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSection(events: List<CalendarEvent>, add: () -> Unit, openNotes: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Title, date, time and location", style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = openNotes) { Text("Notes") }
                FilledTonalButton(onClick = add) { Icon(Icons.Default.Add, null); Text("Event") }
            }
        }
        Spacer(Modifier.height(7.dp))
        if (events.isEmpty()) EmptyState("No events", "Add meetings, appointments or plans.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(events, key = { it.id.value }) { event ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, null, tint = NexaColors.Primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, fontWeight = FontWeight.SemiBold)
                            Text(event.startsAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a")), style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
                            event.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun EmptyState(title:String,body:String){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=NexaColors.EventBlueBg)){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.Bold);Text(body,style=MaterialTheme.typography.bodySmall,color=NexaColors.OnSurfaceMuted)}}}

@Composable private fun DateTimeButton(value: Instant,label:String,onChange:(Instant)->Unit){ val context=androidx.compose.ui.platform.LocalContext.current; OutlinedButton(onClick={val c=Calendar.getInstance().apply{timeInMillis=value.toEpochMilli()};DatePickerDialog(context,{_,y,m,d->TimePickerDialog(context,{_,h,min->onChange(Calendar.getInstance().apply{set(y,m,d,h,min,0)}.toInstant())},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),false).show()},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()},modifier=Modifier.fillMaxWidth()){Text(label+": "+value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a")))} }

@Composable private fun TaskDialog(onDismiss:()->Unit,onSave:(String,Priority,Instant?)->Unit){var title by rememberSaveable{mutableStateOf("")};var p by rememberSaveable{mutableStateOf(Priority.NONE)};var due by remember{mutableStateOf<Instant?>(null)};AlertDialog(onDismissRequest=onDismiss,title={Text("New task")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(title,{title=it},label={Text("Task")},singleLine=true,modifier=Modifier.fillMaxWidth());Text("Priority",style=MaterialTheme.typography.labelMedium);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){Priority.entries.forEach{q->FilterChip(p==q,{p=q},label={Text(q.name.lowercase().replaceFirstChar{it.uppercase()})})}};due?.let{DateTimeButton(it,"Due"){due=it}} ?: OutlinedButton(onClick={due=Instant.now().plusSeconds(3600)},modifier=Modifier.fillMaxWidth()){Text("Add due date & time")}}},confirmButton={Button(onClick={onSave(title,p,due)},enabled=title.isNotBlank()){Text("Add task")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}

@Composable private fun ReminderDialog(onDismiss:()->Unit,onSave:(String,String,Instant)->Unit){var title by rememberSaveable{mutableStateOf("")};var body by rememberSaveable{mutableStateOf("")};var at by remember{mutableStateOf(Instant.now().plusSeconds(3600))};AlertDialog(onDismissRequest=onDismiss,title={Text("New reminder")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(title,{title=it},label={Text("Title")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(body,{body=it},label={Text("Details")},minLines=2,modifier=Modifier.fillMaxWidth());DateTimeButton(at,"Alarm time"){at=it};Text("NEXA will schedule an alarm and notification.",style=MaterialTheme.typography.labelSmall,color=NexaColors.OnSurfaceMuted)}},confirmButton={Button(onClick={onSave(title,body,at)},enabled=title.isNotBlank()){Text("Set reminder")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}

@Composable private fun NoteDialog(onDismiss:()->Unit,onSave:(String,String,String)->Unit){var title by rememberSaveable{mutableStateOf("")};var body by rememberSaveable{mutableStateOf("")};var ref by rememberSaveable{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("New note")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(title,{title=it},label={Text("Subject")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(body,{body=it},label={Text("Body")},minLines=5,modifier=Modifier.fillMaxWidth());OutlinedTextField(ref,{ref=it},label={Text("Reference (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())}},confirmButton={Button(onClick={onSave(title,body,ref)},enabled=body.isNotBlank()){Text("Save note")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}

@Composable private fun EventDialog(onDismiss:()->Unit,onSave:(String,String,String,Instant,Instant)->Unit){var title by rememberSaveable{mutableStateOf("")};var desc by rememberSaveable{mutableStateOf("")};var loc by rememberSaveable{mutableStateOf("")};var start by remember{mutableStateOf(Instant.now().plusSeconds(3600))};var end by remember{mutableStateOf(Instant.now().plusSeconds(7200))};AlertDialog(onDismissRequest=onDismiss,title={Text("New event")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(title,{title=it},label={Text("Event title")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(desc,{desc=it},label={Text("Description")},minLines=2,modifier=Modifier.fillMaxWidth());OutlinedTextField(loc,{loc=it},label={Text("Location")},singleLine=true,modifier=Modifier.fillMaxWidth());DateTimeButton(start,"Starts"){start=it};DateTimeButton(end,"Ends"){end=it}}},confirmButton={Button(onClick={onSave(title,desc,loc,start,end)},enabled=title.isNotBlank()&&end.isAfter(start)){Text("Add event")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}