package com.nexa.feature.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.DisposableEffect
import com.nexa.core.voice.VoiceCaptureController
import com.nexa.core.voice.VoiceCaptureState

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    voiceController: VoiceCaptureController = remember { VoiceCaptureController() },
) {
    var text by rememberSaveable { mutableStateOf("") }
    val reply by viewModel.reply.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val voiceState by voiceController.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) voiceController.start()
        else viewModel.clearError()
    }

    LaunchedEffect(voiceState) {
        val captured = voiceState as? VoiceCaptureState.Captured ?: return@LaunchedEffect
        viewModel.transcribe(captured.audioBase64, captured.mimeType)
        voiceController.clear()
    }

    LaunchedEffect(transcript) {
        val value = transcript
        if (!value.isNullOrBlank()) text = value
    }

    val listening = voiceState is VoiceCaptureState.Listening

    DisposableEffect(Unit) {
        onDispose { voiceController.release() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("NEXA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("How can I help?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Tell me what you need. I can help you plan, remember and act.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!error.isNullOrBlank()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    error!!,
                    Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        reply?.let {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("NEXA", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        transcript?.let {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VOICE TRANSCRIPT — REVIEW BEFORE SENDING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Edit the text below if needed, then tap Ask NEXA. Your voice recording is not stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("TRY SAYING", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("“Remind me to call Dad at 6 PM.”", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.weight(1f))

        LazyRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(listOf("Plan my day", "Add a reminder", "Take a note")) { suggestion ->
                AssistChip(onClick = { text = suggestion }, label = { Text(suggestion) })
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message NEXA…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                enabled = !busy && !listening,
            )
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    if (listening) voiceController.stop()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = !busy,
            ) {
                Text(if (listening) "■" else "🎙", style = MaterialTheme.typography.titleMedium)
            }
        }

        Button(
            onClick = { viewModel.ask(text) },
            enabled = text.isNotBlank() && !busy && !listening,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Ask NEXA", fontWeight = FontWeight.SemiBold)
        }
    }
}
