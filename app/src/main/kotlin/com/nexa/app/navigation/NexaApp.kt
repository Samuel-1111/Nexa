package com.nexa.app.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.nexa.feature.assistant.AssistantScreen
import com.nexa.feature.memory.MemoryScreen
import com.nexa.feature.onboarding.AuthScreen
import com.nexa.feature.onboarding.AuthViewModel
import com.nexa.feature.onboarding.OtpScreen
import com.nexa.feature.organizer.OrganizerScreen
import com.nexa.feature.settings.SettingsScreen
import com.nexa.feature.today.TodayRoute
import io.github.jan.supabase.auth.status.SessionStatus

private enum class TopLevelDestination(val route: String, val label: String) {
    Today("today", "Today"),
    Assistant("assistant", "Assistant"),
    Organizer("organizer", "Organizer"),
    Settings("settings", "Settings"),
}

@Composable
fun NexaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nexa_launch", Context.MODE_PRIVATE) }
    var landingVisible by rememberSaveable { mutableStateOf(!prefs.getBoolean("landing_seen", false)) }
    var authMode by rememberSaveable { mutableStateOf<String?>(null) }
    var otpEmail by rememberSaveable { mutableStateOf<String?>(null) }

    val authViewModel: AuthViewModel = hiltViewModel()
    val sessionStatus by authViewModel.sessionStatus.collectAsState(initial = SessionStatus.Initializing)

    // The landing page is a one-time first-launch screen. Once the user chooses
    // Create account or Log in, it is remembered and never shown again unless
    // the app's local data is cleared.
    if (landingVisible) {
        LandingScreen(
            onCreateAccount = {
                prefs.edit().putBoolean("landing_seen", true).apply()
                landingVisible = false
                authMode = "create"
            },
            onLogin = {
                prefs.edit().putBoolean("landing_seen", true).apply()
                landingVisible = false
                authMode = "login"
            },
        )
        return
    }

    if (otpEmail != null) {
        OtpScreen(
            email = otpEmail!!,
            viewModel = authViewModel,
            onBack = { otpEmail = null },
        )
        return
    }

    when (sessionStatus) {
        is SessionStatus.Authenticated -> {
            authMode = null
            AuthenticatedApp(authViewModel)
        }
        SessionStatus.Initializing -> LoadingAuth()
        is SessionStatus.RefreshFailure,
        is SessionStatus.NotAuthenticated -> AuthScreen(
            initialCreateAccount = authMode == "create",
            viewModel = authViewModel,
            onOtpRequested = { otpEmail = it },
        )
    }
}

@Composable
private fun LandingScreen(
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(1.dp))

        Column(
            Modifier.fillMaxWidth().widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(44.dp))
            Text(
                "NEXA",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Your Personal Assistant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Think it. Say it. NEXA helps you get it done.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.fillMaxWidth().widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCreateAccount,
                Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Create account", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onLogin,
                Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Log in", fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "Built by Olanlokun Samuel Ajibola • Samzy Technology",
            Modifier.fillMaxWidth().widthIn(max = 560.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingAuth() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthenticatedApp(authViewModel: AuthViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { NexaBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController,
            startDestination = TopLevelDestination.Today.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(TopLevelDestination.Today.route) { TodayRoute() }
            composable(TopLevelDestination.Assistant.route) { AssistantScreen() }
            composable(TopLevelDestination.Organizer.route) { OrganizerScreen() }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(
                    onOpenMemoryCenter = { navController.navigate("memory") },
                    onSignOut = { authViewModel.signOut() },
                )
            }
            composable("memory") { MemoryScreen() }
        }
    }
}

@Composable
private fun NexaBottomBar(navController: NavHostController) {
    val current = navController.currentBackStackEntryAsState().value?.destination
    val icons = mapOf(
        TopLevelDestination.Today to Icons.Filled.Home,
        TopLevelDestination.Assistant to Icons.Filled.Chat,
        TopLevelDestination.Organizer to Icons.Filled.CheckCircle,
        TopLevelDestination.Settings to Icons.Filled.Settings,
    )

    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = current?.hierarchy?.any { it.route == destination.route } == true,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icons.getValue(destination), destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
