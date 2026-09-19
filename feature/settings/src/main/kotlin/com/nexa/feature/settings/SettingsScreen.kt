package com.nexa.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexa.core.designsystem.NexaColors

@Composable
fun SettingsScreen(
    onOpenMemoryCenter: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Profile • Memory • Subscription", color = NexaColors.OnSurfaceMuted)

        SettingsItem(Icons.Default.Person, "Account & Profile", "Manage your information")
        SettingsItem(Icons.Default.Memory, "Memory", "Your saved information", onClick = onOpenMemoryCenter)
        SettingsItem(Icons.Default.CreditCard, "Subscription", "3-Day Free Trial • Basic ₦1,000/month")
        SettingsItem(Icons.Default.Apps, "Connected Apps", "Link your favourite apps")
        SettingsItem(Icons.Default.Notifications, "Notifications", "Manage reminders and alerts")
        SettingsItem(Icons.Default.Lock, "Privacy & Permissions", "Control what NEXA can access")
        SettingsItem(Icons.Default.HelpOutline, "Help & Support", "Get help and send feedback")
        SettingsItem(Icons.Default.Info, "About NEXA", "Version 0.1.0")

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Log out", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = NexaColors.EventBlueBg) {
                Icon(icon, null, Modifier.padding(10.dp), tint = NexaColors.Primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NexaColors.OnSurfaceMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = NexaColors.OnSurfaceMuted)
        }
    }
}
