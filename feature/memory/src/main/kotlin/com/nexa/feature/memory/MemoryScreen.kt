package com.nexa.feature.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexa.core.model.Memory

@Composable
fun MemoryScreen(viewModel: MemoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    MemoryContent(
        state = state,
        onApprove = viewModel::approve,
        onDismiss = viewModel::dismiss,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun MemoryContent(
    state: MemoryUiState,
    onApprove: (com.nexa.core.common.EntityId) -> Unit,
    onDismiss: (com.nexa.core.common.EntityId) -> Unit,
    onDelete: (com.nexa.core.common.EntityId) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Memory Center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "NEXA only remembers what you approve. Nothing here becomes permanent without your say-so.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.suggested.isNotEmpty()) {
            item {
                Text(
                    "NEEDS YOUR APPROVAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.suggested, key = { it.id.value }) { memory ->
                SuggestionCard(memory, onApprove = { onApprove(memory.id) }, onDismiss = { onDismiss(memory.id) })
            }
        }

        item {
            Text(
                "SAVED MEMORIES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.active.isEmpty()) {
            item { Text("NEXA hasn't saved any memories yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(state.active, key = { it.id.value }) { memory ->
            ActiveMemoryCard(memory, onDelete = { onDelete(memory.id) })
        }
    }
}

@Composable
private fun SuggestionCard(memory: Memory, onApprove: () -> Unit, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(memory.category.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(memory.content, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onApprove) { Text("Save") }
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun ActiveMemoryCard(memory: Memory, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(memory.category.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(memory.content, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete memory") }
        }
    }
}
