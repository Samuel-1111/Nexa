package com.nexa.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexa.core.designsystem.NexaColors
import com.nexa.core.model.Priority

@Composable
fun TodayRoute(
    onOpenAssistant: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    Scaffold { padding ->
        when (state) {
            TodayUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is TodayUiState.Loaded -> {
                val data = state as TodayUiState.Loaded
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(NexaColors.Background).padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("NEXA", style = MaterialTheme.typography.headlineMedium, color = NexaColors.Primary, fontWeight = FontWeight.ExtraBold)
                                Text("Your Personal Assistant", color = NexaColors.PrimaryDark)
                            }
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                                Icon(Icons.Default.Person, null, Modifier.padding(10.dp), tint = NexaColors.Primary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Good morning,", style = MaterialTheme.typography.headlineMedium)
                        Text("$displayName ☀️", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")), color = NexaColors.OnSurfaceMuted)
                    }
                    item {
                        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.EventBlueBg)) {
                            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = NexaColors.Primary)
                                Spacer(Modifier.width(14.dp))
                                Text("Today is a new opportunity to build the life you want.", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ChevronRight, null, tint = NexaColors.Primary)
                            }
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.Surface)) {
                            Column(Modifier.padding(18.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Daily Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("View All", color = NexaColors.Primary)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverviewCard("Tasks", data.tasks.size, Icons.Default.CheckCircle, NexaColors.TaskGreenBg)
                                    OverviewCard("Reminders", data.reminders.size, Icons.Default.Notifications, NexaColors.ReminderOrangeBg)
                                    OverviewCard("Event", 0, Icons.Default.CalendarMonth, NexaColors.EventBlueBg)
                                    OverviewCard("Notes", data.noteCount, Icons.Default.Note, NexaColors.NoteVioletBg)
                                }
                            }
                        }
                    }
                    item {
                        SectionCard("Today's Tasks", data.tasks.isEmpty()) {
                            data.tasks.forEach { task ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(false, { viewModel.completeTask(task) })
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, fontWeight = FontWeight.SemiBold)
                                        Text(task.dueAt?.toString() ?: "No time set", color = NexaColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    PriorityChip(task.priority)
                                }
                            }
                        }
                    }
                    item {
                        SectionCard("Upcoming Reminders", data.reminders.isEmpty()) {
                            data.reminders.take(4).forEach { reminder ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.NotificationsNone, null, tint = NexaColors.ReminderOrange)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(reminder.title, fontWeight = FontWeight.SemiBold)
                                        Text(reminder.triggerAt.toString(), color = NexaColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = onOpenAssistant,
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            shape = RoundedCornerShape(32.dp),
                        ) {
                            Icon(Icons.Default.Mic, null)
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("Talk to NEXA", fontWeight = FontWeight.Bold)
                                Text("Tap to speak or hold to listen", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, background: androidx.compose.ui.graphics.Color) {
    Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(10.dp)) {
            Icon(icon, null, tint = NexaColors.Primary)
            Spacer(Modifier.height(6.dp))
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionCard(title: String, empty: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.Surface)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (empty) Text("Nothing here yet.", color = NexaColors.OnSurfaceMuted)
            else content()
        }
    }
}

@Composable
private fun PriorityChip(priority: Priority) {
    val text = priority.name.lowercase().replaceFirstChar { it.uppercase() }
    AssistChip(onClick = {}, label = { Text(text) })
}
