package com.nexa.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexa.feature.assistant.AssistantScreen
import com.nexa.feature.memory.MemoryScreen
import com.nexa.feature.onboarding.AuthScreen
import com.nexa.feature.onboarding.AuthViewModel
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
    val authViewModel: AuthViewModel = hiltViewModel()
    val sessionStatus by authViewModel.sessionStatus.collectAsState(initial = SessionStatus.Initializing)

    when (sessionStatus) {
        is SessionStatus.Authenticated -> AuthenticatedApp()
        SessionStatus.Initializing -> LoadingAuth()
        is SessionStatus.RefreshFailure -> AuthScreen(authViewModel)
        is SessionStatus.NotAuthenticated -> AuthScreen(authViewModel)
    }
}

@Composable
private fun LoadingAuth() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthenticatedApp() {
    val navController = rememberNavController()
    Scaffold(bottomBar = { NexaBottomBar(navController) }) { padding ->
        NavHost(navController = navController, startDestination = TopLevelDestination.Today.route, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(TopLevelDestination.Today.route) { TodayRoute() }
            composable(TopLevelDestination.Assistant.route) { AssistantScreen() }
            composable(TopLevelDestination.Organizer.route) { OrganizerScreen() }
            composable(TopLevelDestination.Settings.route) { SettingsScreen(onOpenMemoryCenter = { navController.navigate("memory") }) }
            composable("memory") { MemoryScreen() }
        }
    }
}

@Composable
private fun NexaBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val icons = mapOf(
        TopLevelDestination.Today to Icons.Filled.Home,
        TopLevelDestination.Assistant to Icons.Filled.Chat,
        TopLevelDestination.Organizer to Icons.Filled.CheckCircle,
        TopLevelDestination.Settings to Icons.Filled.Settings,
    )
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                } },
                icon = { Icon(icons.getValue(destination), contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
