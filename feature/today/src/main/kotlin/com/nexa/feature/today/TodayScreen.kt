package com.nexa.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexa.core.common.greetingForNow

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold { padding ->
        when (state) {
            is TodayUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is TodayUiState.Loaded -> {
                val loaded = state as TodayUiState.Loaded
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Text("NEXA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(greetingForNow("Samuel"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Here’s what matters today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("YOUR DAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(if (loaded.tasks.isEmpty()) "A clear day" else loaded.tasks.size.toString() + " priorities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (loaded.tasks.isEmpty()) "Nothing urgent is waiting for you." else "NEXA will keep them in view.")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) { Text(loaded.tasks.size.toString(), Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (loaded.tasks.isEmpty()) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                            Text("Your day is clear. Nice. ✨", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(loaded.tasks) { task ->
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(false, { viewModel.completeTask(task) })
                                        Text(task.title, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
