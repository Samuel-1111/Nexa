package com.nexa.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var memoryEnabled by remember { mutableStateOf(true) }
    var automationsEnabled by remember { mutableStateOf(true) }
    var voiceRepliesEnabled by remember { mutableStateOf(true) }
    var femaleVoice by remember { mutableStateOf(true) }
    var speechRate by remember { mutableFloatStateOf(1.12f) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Make NEXA work the way you want.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SettingsCard("Profile", "Samuel", "Your name is used for a more personal NEXA experience.")
        SettingsCard("Subscription", "Free plan", "Manage your plan and future NEXA features.")

        ToggleCard("Automations", "Let NEXA handle approved background actions automatically.", automationsEnabled) { automationsEnabled = it }
        ToggleCard("Memory", "Allow NEXA to remember approved information.", memoryEnabled) { memoryEnabled = it }
        ToggleCard("Voice replies", "Let NEXA speak responses through your phone speaker.", voiceRepliesEnabled) { voiceRepliesEnabled = it }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VOICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(if (femaleVoice) "Human female voice" else "Human male voice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Choose how NEXA sounds when speaking.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Female")
                    Switch(checked = femaleVoice, onCheckedChange = { femaleVoice = it })
                    Text("Male")
                }
                Text("Speech speed: ${"%.2f".format(speechRate)}×", style = MaterialTheme.typography.labelMedium)
                Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = .95f..1.30f)
                Text("Fast, natural and conversational — not robotic.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SettingsCard("Privacy", "Permission-first", "NEXA should ask before using sensitive device capabilities.")
    }
}

@Composable
private fun ToggleCard(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsCard(title: String, value: String, detail: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))) {
        Column(Modifier.padding(18.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
