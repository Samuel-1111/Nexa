package com.nexa.app.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.nexa.feature.assistant.AssistantScreen
import com.nexa.feature.memory.MemoryScreen
import com.nexa.feature.onboarding.AuthScreen
import com.nexa.feature.onboarding.AuthViewModel
import com.nexa.feature.onboarding.PersonalizeNexaScreen
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
    var onboardingComplete by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var subscriptionActive by rememberSaveable { mutableStateOf<Boolean?>(null) }

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

    when (sessionStatus) {
        is SessionStatus.Authenticated -> {
            authMode = null
            LaunchedEffect(Unit) {
                onboardingComplete = authViewModel.isOnboardingComplete()
                if (onboardingComplete == true) {
                    while (true) {
                        subscriptionActive = authViewModel.hasActiveSubscription()
                        kotlinx.coroutines.delay(60_000)
                    }
                }
            }
            when {
                onboardingComplete == false -> PersonalizeNexaScreen(
                    onComplete = { onboardingComplete = true; subscriptionActive = null },
                    viewModel = authViewModel,
                )
                onboardingComplete == true && subscriptionActive == false -> SubscriptionRequiredScreen(authViewModel)
                onboardingComplete == true -> AuthenticatedApp(authViewModel)
                else -> LoadingAuth()
            }
        }
        SessionStatus.Initializing -> LoadingAuth()
        is SessionStatus.RefreshFailure,
        is SessionStatus.NotAuthenticated -> AuthScreen(
            initialCreateAccount = authMode == "create",
            viewModel = authViewModel,
            onSignedIn = { /* sessionStatus drives navigation */ },
            onBack = {
                authMode = null
                prefs.edit().remove("landing_seen").apply()
                landingVisible = true
            },
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
            composable(TopLevelDestination.Today.route) { TodayRoute(onOpenAssistant = { navController.navigate(TopLevelDestination.Assistant.route) }, onOpenOrganizer = { section -> navController.navigate("organizer/" + section) }) }
            composable(TopLevelDestination.Assistant.route) { AssistantScreen() }
            composable("organizer/{section}") { entry -> OrganizerScreen(entry.arguments?.getString("section") ?: "OVERVIEW") }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(
                    onOpenMemoryCenter = { navController.navigate("memory") },
                    onSignOut = { authViewModel.signOut() },
                    onSubscription = { /* subscription gate is handled at the app boundary */ },
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
                    val route = if (destination == TopLevelDestination.Organizer) "organizer/OVERVIEW" else destination.route
                navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icons.getValue(destination), destination.label) },
                label = { Text(destination.label, fontSize = 11.sp, maxLines = 1) },
            )
        }
    }
}


@Composable
private fun SubscriptionRequiredScreen(authViewModel: AuthViewModel) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your NEXA trial has ended", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Choose a plan to continue using NEXA. Your saved information stays on your device and in your account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlanCard("Essential", "₦1,000 / month", "150 AI requests • 75 voice requests • 20 automations", "ESSENTIAL", authViewModel)
        PlanCard("Pro", "₦3,000 / month", "750 AI requests • 300 voice requests • 100 automations", "PRO", authViewModel)
        PlanCard("Executive", "₦5,000 / month", "Unlimited AI • 100+ automations • highest limits", "EXECUTIVE", authViewModel)
        Text("Payment activation is protected by the NEXA server; no payment is marked successful from the app alone.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanCard(title: String, price: String, detail: String, plan: String, authViewModel: AuthViewModel) {
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(price, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val result = authViewModel.initiateSubscription(plan)
                            result.rrr?.let {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://login.remita.net/remita/ecomm/finalize.reg?rrr=" + it)))
                            }
                        } finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Continue with " + title)
            }
        }
    }
}
