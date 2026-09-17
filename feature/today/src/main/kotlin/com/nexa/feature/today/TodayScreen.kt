package com.nexa.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexa.core.common.greetingForNow

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    TodayScreen(state = state, onCompleteTask = viewModel::completeTask)
}

@Composable
private fun TodayScreen(state: TodayUiState, onCompleteTask: (com.nexa.core.model.Task) -> Unit) {
    Scaffold { padding ->
        when (state) {
            is TodayUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            is TodayUiState.Loaded -> TodayContent(state, onCompleteTask, padding)
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState.Loaded,
    onCompleteTask: (com.nexa.core.model.Task) -> Unit,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("NEXA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(greetingForNow("Samuel"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Here’s what matters today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("YOUR DAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(if (state.tasks.isEmpty()) "A clear day" else "${state.tasks.size} priorities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (state.tasks.isEmpty()) "Nothing urgent is waiting for you." else "NEXA will keep them in view.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("${state.tasks.size}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (state.tasks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
            ) { Text("Your day is clear. Nice. ✨", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.tasks) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            Checkbox(checked = false, onCheckedChange = { onCompleteTask(task) })
                            Text(task.title, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
