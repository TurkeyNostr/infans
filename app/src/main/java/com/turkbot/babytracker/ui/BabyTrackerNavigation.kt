package com.turkbot.babytracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.turkbot.babytracker.BabyTrackerApp
import com.turkbot.babytracker.ui.screens.*
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Feed : Screen("feed", "Feed", Icons.Default.Restaurant)
    data object Sleep : Screen("sleep", "Sleep", Icons.Default.Bedtime)
    data object Weight : Screen("weight", "Weight", Icons.Default.MonitorWeight)
    data object Milestones : Screen("milestones", "Milestones", Icons.Default.EmojiEvents)
    data object Charts : Screen("charts", "Charts", Icons.Default.ShowChart)
    data object Summary : Screen("summary", "Summary", Icons.Default.Dashboard)
    data object Messages : Screen("messages", "Messages", Icons.Default.Message)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyTrackerNavigation(app: BabyTrackerApp) {
    val navController = rememberNavController()
    val viewModel: BabyViewModel = viewModel(factory = BabyViewModelFactory(app))

    val screens = listOf(
        Screen.Feed, Screen.Sleep, Screen.Weight, Screen.Milestones,
        Screen.Charts, Screen.Summary, Screen.Messages
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Baby Tracker") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Feed.route) { FeedScreen(viewModel) }
            composable(Screen.Sleep.route) { SleepScreen(viewModel) }
            composable(Screen.Weight.route) { WeightScreen(viewModel) }
            composable(Screen.Milestones.route) { MilestonesScreen(viewModel) }
            composable(Screen.Charts.route) { ChartsScreen(viewModel) }
            composable(Screen.Summary.route) { SummaryScreen(viewModel) }
            composable(Screen.Messages.route) { MessagesScreen(viewModel, app.nostrManager) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel, app.nostrManager) }
        }
    }
}
