package com.nexa.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexa.feature.today.TodayRoute

// Section 20 of the spec: Today | Assistant | Organizer | Settings.
// Assistant/Organizer/Settings are intentionally minimal placeholders here --
// real screens land in Stage 2/3 -- but the navigation graph itself, the
// bottom bar, and back-stack behavior are real and wired end to end.
private enum class TopLevelDestination(val route: String, val label: String) {
    Today("today", "Today"),
    Assistant("assistant", "Assistant"),
    Organizer("organizer", "Organizer"),
    Settings("settings", "Settings"),
}

@Composable
fun NexaApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { NexaBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.Today.route) { TodayRoute() }
            composable(TopLevelDestination.Assistant.route) { PlaceholderScreen("Assistant — talk or type to NEXA") }
            composable(TopLevelDestination.Organizer.route) { PlaceholderScreen("Organizer — tasks, reminders, notes") }
            composable(TopLevelDestination.Settings.route) { PlaceholderScreen("Settings — profile, memory, subscription") }
        }
    }
}

@Composable
private fun NexaBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    NavigationBar {
        val icons = mapOf(
            TopLevelDestination.Today to Icons.Filled.Home,
            TopLevelDestination.Assistant to Icons.Filled.Chat,
            TopLevelDestination.Organizer to Icons.Filled.CheckCircle,
            TopLevelDestination.Settings to Icons.Filled.Settings,
        )
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icons.getValue(destination), contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(text: String) {
    Text(text, modifier = Modifier.padding(24.dp))
}
