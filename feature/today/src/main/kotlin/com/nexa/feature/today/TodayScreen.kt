package com.nexa.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexa.core.designsystem.NexaColors
import com.nexa.core.model.TaskStatus
import java.time.format.DateTimeFormatter

@Composable
fun TodayRoute(
    onOpenAssistant: () -> Unit = {},
    onOpenOrganizer: (String) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    Scaffold { padding ->
        when (state) {
            TodayUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is TodayUiState.Loaded -> {
                val data = state as TodayUiState.Loaded
                Column(
                    Modifier.fillMaxSize().background(NexaColors.Background).padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("NEXA", style = MaterialTheme.typography.titleLarge, color = NexaColors.Primary, fontWeight = FontWeight.ExtraBold)
                            Text("Your Personal Assistant", style = MaterialTheme.typography.labelSmall, color = NexaColors.PrimaryDark)
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                            Icon(Icons.Default.Person, null, Modifier.padding(7.dp), tint = NexaColors.Primary)
                        }
                    }
                    Column {
                        Text(viewModel.greeting(), style = MaterialTheme.typography.titleMedium)
                        Text("$displayName ☀️", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")), style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
                    }

                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.EventBlueBg)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = NexaColors.Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Today is a new opportunity to build the life you want.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.Surface)) {
                        Column(Modifier.padding(11.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Daily Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, Modifier.weight(1f))
                                TextButton(onClick = { onOpenOrganizer("OVERVIEW") }, contentPadding = PaddingValues(0.dp)) { Text("View all") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OverviewCard("Tasks", data.tasks.count { it.status == TaskStatus.OPEN }, Icons.Default.CheckCircle, NexaColors.TaskGreenBg) { onOpenOrganizer("TASKS") }
                                OverviewCard("Reminders", data.reminders.size, Icons.Default.Notifications, NexaColors.ReminderOrangeBg) { onOpenOrganizer("REMINDERS") }
                                OverviewCard("Events", data.eventCount, Icons.Default.CalendarMonth, NexaColors.EventBlueBg) { onOpenOrganizer("EVENTS") }
                                OverviewCard("Notes", data.noteCount, Icons.Default.Note, NexaColors.NoteVioletBg) { onOpenOrganizer("NOTES") }
                            }
                        }
                    }

                    CompactSection(
                        title = "Today's Tasks",
                        viewAll = { onOpenOrganizer("TASKS") },
                        empty = data.tasks.isEmpty(),
                    ) {
                        data.tasks.take(2).forEach { task ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = task.status == TaskStatus.COMPLETED,
                                    onCheckedChange = { viewModel.completeTask(task) },
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(task.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }

                    CompactSection(
                        title = "Upcoming Reminders",
                        viewAll = { onOpenOrganizer("REMINDERS") },
                        empty = data.reminders.isEmpty(),
                    ) {
                        data.reminders.take(2).forEach { reminder ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsNone, null, Modifier.size(20.dp), tint = NexaColors.ReminderOrange)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(reminder.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(reminder.triggerAt.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE • h:mm a")), style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onOpenAssistant,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Icon(Icons.Default.Mic, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Talk to NEXA", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.OverviewCard(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, background: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.weight(1f).height(76.dp), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, Modifier.size(18.dp), tint = NexaColors.Primary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CompactSection(title: String, viewAll: () -> Unit, empty: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.Surface)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, Modifier.weight(1f))
                TextButton(onClick = viewAll, contentPadding = PaddingValues(0.dp)) { Text("View all") }
            }
            if (empty) Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = NexaColors.OnSurfaceMuted)
            else content()
        }
    }
}
