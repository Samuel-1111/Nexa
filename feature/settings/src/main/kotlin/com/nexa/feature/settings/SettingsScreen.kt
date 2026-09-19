package com.nexa.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

private data class Plan(
    val name: String,
    val price: String,
    val tagline: String,
    val aiLimit: String,
    val voiceLimit: String,
    val automationLimit: String,
    val memoryLimit: String,
    val features: List<String>,
)

private val plans = listOf(
    Plan(
        name = "Basic",
        price = "₦1,000/month",
        tagline = "A strong everyday NEXA assistant without unlimited AI usage.",
        aiLimit = "150 AI assistant requests/month",
        voiceLimit = "75 voice requests/month",
        automationLimit = "20 active automations",
        memoryLimit = "Up to 250 saved memories",
        features = listOf(
            "High-quality Gemini AI assistance",
            "Create and manage tasks, notes and reminders",
            "Voice input and natural spoken replies",
            "Background automations",
            "Personal memory and context",
            "Today daily planning",
            "Cloud account sync",
        ),
    ),
    Plan(
        name = "Pro",
        price = "₦3,000/month",
        tagline = "For people who want NEXA working harder throughout the day.",
        aiLimit = "750 AI assistant requests/month",
        voiceLimit = "300 voice requests/month",
        automationLimit = "100 active automations",
        memoryLimit = "Up to 1,000 saved memories",
        features = listOf(
            "Everything in Basic",
            "High-quality Gemini AI with a much higher usage allowance",
            "Advanced recurring automations",
            "Expanded memory and context",
            "Advanced daily summaries",
            "Premium voice experience",
            "Priority-level assistant usage",
        ),
    ),
    Plan(
        name = "Executive",
        price = "₦5,000/month",
        tagline = "The complete NEXA experience for maximum assistant usage.",
        aiLimit = "UNLIMITED AI assistant usage",
        voiceLimit = "UNLIMITED voice requests",
        automationLimit = "UNLIMITED active automations",
        memoryLimit = "UNLIMITED saved memories",
        features = listOf(
            "Everything in Pro",
            "Unlimited high-quality Gemini AI assistance",
            "Unlimited voice assistant usage",
            "Unlimited background automations",
            "Unlimited personal memory",
            "Maximum NEXA personalization",
            "Highest usage allowance",
        ),
    ),
)

@Composable
fun SettingsScreen(onOpenMemoryCenter: () -> Unit = {}) {
    var memoryEnabled by remember { mutableStateOf(true) }
    var voiceRepliesEnabled by remember { mutableStateOf(true) }
    var femaleVoice by remember { mutableStateOf(true) }
    var speechRate by remember { mutableFloatStateOf(1.12f) }
    var selectedPlan by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Make NEXA work the way you want.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsCard("Profile", "Samuel", "Your name is used for a more personal NEXA experience.")

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SUBSCRIPTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Choose what NEXA should be able to do for you", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Your first 3 days are free. Review exactly what each plan includes before choosing a paid plan. Every tier uses the same high-quality AI model; the difference is how much you can use it and how much NEXA can do for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrialCard()
                plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        selected = selectedPlan == plan.name,
                        onSelect = { selectedPlan = plan.name },
                    )
                }
            }
        }

        ToggleCard("Memory", "Allow NEXA to remember approved information.", memoryEnabled) { memoryEnabled = it }
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Memory Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Review, edit or delete what NEXA remembers.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onOpenMemoryCenter) { Text("Open") }
            }
        }
        ToggleCard("Voice replies", "Let NEXA speak responses through your phone speaker.", voiceRepliesEnabled) { voiceRepliesEnabled = it }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)),
        ) {
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
private fun TrialCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("3-DAY FREE TRIAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Try NEXA before paying", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("25 AI requests, 10 voice requests and up to 3 active automations during the trial.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(plan.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(plan.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Text("WHAT YOU GET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            plan.features.forEach { feature ->
                Text("✓ $feature", style = MaterialTheme.typography.bodyMedium)
            }

            Text("USAGE & LIMITS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("• ${plan.aiLimit}", style = MaterialTheme.typography.bodySmall)
            Text("• ${plan.voiceLimit}", style = MaterialTheme.typography.bodySmall)
            Text("• ${plan.automationLimit}", style = MaterialTheme.typography.bodySmall)
            Text("• ${plan.memoryLimit}", style = MaterialTheme.typography.bodySmall)

            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected) "${plan.name} selected" else "Choose ${plan.name}")
            }
        }
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
