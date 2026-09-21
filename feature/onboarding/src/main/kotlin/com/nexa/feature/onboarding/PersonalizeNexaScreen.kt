package com.nexa.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PersonalizeNexaScreen(
    onComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var name by rememberSaveable { mutableStateOf("") }
    var assistantName by rememberSaveable { mutableStateOf("NEXA") }
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to NEXA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Let’s personalize your assistant before you begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What is your name?") },
            placeholder = { Text("Your name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = assistantName,
            onValueChange = { assistantName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What would you like to call your PA?") },
            placeholder = { Text("NEXA") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        Text("You can change these later in Account & Profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.saveOnboardingProfile(name, assistantName, onComplete) },
            enabled = !busy && name.trim().length >= 2 && assistantName.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Continue", fontWeight = FontWeight.Bold)
        }
    }
}
