package com.nexa.feature.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AssistantScreen() {
    var text by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "NEXA",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "How can I help?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Tell me what you need. I can help you plan, remember and act.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "TRY SAYING",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "“Remind me to call Dad at 6 PM.”",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(listOf("Plan my day", "Add a reminder", "Take a note")) { suggestion ->
                AssistChip(onClick = { text = suggestion }, label = { Text(suggestion) })
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message NEXA…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { },
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice input")
            }
        }

        Button(
            onClick = { },
            enabled = text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Ask NEXA", fontWeight = FontWeight.SemiBold)
        }
    }
}
