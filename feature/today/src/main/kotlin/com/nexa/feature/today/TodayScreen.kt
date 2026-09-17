package com.nexa.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    TodayScreen(state = state, onCompleteTask = viewModel::completeTask)
}

@Composable
private fun TodayScreen(state: TodayUiState, onCompleteTask: (com.nexa.core.model.Task) -> Unit) {
    Scaffold { padding ->
        when (state) {
            is TodayUiState.Loading -> Text("Loading...", modifier = Modifier.padding(padding).padding(16.dp))
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
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (state.tasks.isEmpty()) "Your day is clear. Nice." else "${state.tasks.size} things on your list",
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.tasks) { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { onCompleteTask(task) })
                    Text(task.title)
                }
            }
        }
    }
}
