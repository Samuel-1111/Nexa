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
import com.nexa.core.designsystem.NexaColors
import com.nexa.core.model.TaskStatus
import java.time.format.DateTimeFormatter

@Composable
fun TodayRoute(
    onOpenAssistant: () -> Unit = {},
    onOpenOrganizer: (String) -> Unit = {},
    viewModel: TodayViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val assistantName by viewModel.assistantName.collectAsState()

    Scaffold { padding ->
        when (state) {
            TodayUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is TodayUiState.Loaded -> {
                val data = state as TodayUiState.Loaded
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(NexaColors.Background)
                        .padding(padding)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Compact header: everything from NEXA to the bottom action fits
                    // on one normal phone screen without vertical scrolling.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                assistantName,
                                style = MaterialTheme.typography.titleLarge,
                                color = NexaColors.Primary,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "Your Personal Assistant",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexaColors.PrimaryDark,
                            )
                        }
                        Card(shape = RoundedCornerShape(50), colors = CardDefaults.cardColors(containerColor = NexaColors.Surface)) {
                            Icon(Icons.Default.Person, null, Modifier.padding(6.dp), tint = NexaColors.Primary)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(viewModel.greeting(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                            style = MaterialTheme.typography.labelSmall,
                            color = NexaColors.OnSurfaceMuted,
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NexaColors.EventBlueBg),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = NexaColors.Primary)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Today is a new opportunity to build the life you want.",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                            )
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp), tint = NexaColors.Primary)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NexaColors.Surface),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Daily Overview",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { onOpenOrganizer("OVERVIEW") },
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                ) { Text("View all") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                OverviewCard("Tasks", data.tasks.size, Icons.Default.CheckCircle, NexaColors.TaskGreenBg) {
                                    onOpenOrganizer("TASKS")
                                }
                                OverviewCard("Reminders", data.reminders.size, Icons.Default.Notifications, NexaColors.ReminderOrangeBg) {
                                    onOpenOrganizer("REMINDERS")
                                }
                                OverviewCard("Events", data.eventCount, Icons.Default.CalendarMonth, NexaColors.EventBlueBg) {
                                    onOpenOrganizer("EVENTS")
                                }
                                OverviewCard("Notes", data.noteCount, Icons.Default.Note, NexaColors.NoteVioletBg) {
                                    onOpenOrganizer("NOTES")
                                }
                            }
                        }
                    }

                    CompactSection(
                        title = "Today's Tasks",
                        viewAll = { onOpenOrganizer("TASKS") },
                        empty = data.tasks.isEmpty(),
                    ) {
                        data.tasks.take(2).forEach { task ->
                            Row(
                                Modifier.fillMaxWidth().height(34.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = task.status == TaskStatus.COMPLETED,
                                    onCheckedChange = { viewModel.toggleTask(task) },
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                TextButton(
                                    onClick = { onOpenOrganizer("TASKS") },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        text = task.title,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                    )
                                }
                                if (task.status == TaskStatus.COMPLETED) {
                                    Text("Done", style = MaterialTheme.typography.labelSmall, color = NexaColors.Primary)
                                }
                            }
                        }
                    }

                    CompactSection(
                        title = "Upcoming Reminders",
                        viewAll = { onOpenOrganizer("REMINDERS") },
                        empty = data.reminders.isEmpty(),
                    ) {
                        data.reminders.take(2).forEach { reminder ->
                            Card(
                                onClick = { onOpenOrganizer("REMINDERS") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = NexaColors.Background),
                            ) {
                            Row(
                                Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.NotificationsNone, null, Modifier.size(18.dp), tint = NexaColors.ReminderOrange)
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(reminder.title, fontWeight = FontWeight.SemiBold, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        reminder.triggerAt.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE • h:mm a")),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexaColors.OnSurfaceMuted,
                                    )
                                }
                            }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = onOpenAssistant,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Icon(Icons.Default.Mic, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Talk to $assistantName", fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.OverviewCard(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(62.dp),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = NexaColors.Primary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(2.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CompactSection(
    title: String,
    viewAll: () -> Unit,
    empty: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NexaColors.Surface),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = viewAll,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                ) { Text("View all") }
            }
            if (empty) {
                Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = NexaColors.OnSurfaceMuted)
            } else {
                content()
            }
        }
    }
}
