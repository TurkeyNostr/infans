/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent sync.
 *
 * Copyright (c) 2026 Turkey
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for full license details.
 */

package com.turkbot.babytracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.turkbot.babytracker.BabyTrackerApp
import com.turkbot.babytracker.ui.screens.ChartsScreen
import com.turkbot.babytracker.ui.screens.DiaperScreen
import com.turkbot.babytracker.ui.screens.FeedScreen
import com.turkbot.babytracker.ui.screens.HealthScreen
import com.turkbot.babytracker.ui.screens.MilestonesScreen
import com.turkbot.babytracker.ui.screens.NotesScreen
import com.turkbot.babytracker.ui.screens.OnboardingScreen
import com.turkbot.babytracker.ui.screens.PumpingScreen
import com.turkbot.babytracker.ui.screens.SettingsScreen
import com.turkbot.babytracker.ui.screens.SleepScreen
import com.turkbot.babytracker.ui.screens.SummaryScreen
import com.turkbot.babytracker.ui.screens.WeightScreen
import com.turkbot.babytracker.ui.screens.isOnboardingComplete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.ui.viewmodel.BabyViewModelFactory

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Feed : Screen("feed", "Feed", Icons.Filled.Restaurant, Icons.Outlined.Restaurant)
    data object Sleep : Screen("sleep", "Sleep", Icons.Filled.Bedtime, Icons.Outlined.Bedtime)
    data object Weight : Screen("weight", "Weight", Icons.Filled.MonitorWeight, Icons.Outlined.MonitorWeight)
    data object Summary : Screen("summary", "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    data object Notes : Screen("notes", "Notes", Icons.AutoMirrored.Filled.Note, Icons.AutoMirrored.Outlined.Note)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyTrackerNavigation(app: BabyTrackerApp) {
    val navController = rememberNavController()
    val viewModel: BabyViewModel = viewModel(factory = BabyViewModelFactory(app))
    val context = LocalContext.current
    val showOnboarding = remember { mutableStateOf(!isOnboardingComplete(context)) }

    if (showOnboarding.value) {
        OnboardingScreen(
            viewModel = viewModel,
            nostrManager = app.nostrManager,
            onComplete = { showOnboarding.value = false }
        )
        return
    }

    // Summary is the start destination — it's the dashboard/home
    val screens = listOf(
        Screen.Summary, Screen.Feed, Screen.Sleep, Screen.Weight, Screen.Notes
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Infans") },
                actions = {
                    IconButton(onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                    // Only pop the start destination inclusively when we're
                                    // already on it — that recreates it fresh. When coming from
                                    // a non-tab screen like Settings, keep it on the stack so
                                    // we land on Home, not a restored wrong tab.
                                    val startRoute = navController.graph.findStartDestination().route
                                    if (screen.route == startRoute &&
                                        currentDestination?.route == startRoute) {
                                        inclusive = true
                                    }
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
            startDestination = Screen.Summary.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Summary.route) {
                SummaryScreen(
                    viewModel,
                    onNavigateToWeight = { navController.navigate("weight") },
                    onNavigateToCharts = { navController.navigate("charts") },
                    onNavigateToMilestones = { navController.navigate("milestones") },
                    onNavigateToDiaper = { navController.navigate("diaper") },
                    onNavigateToPumping = { navController.navigate("pumping") },
                    onNavigateToHealth = { navController.navigate("health") }
                )
            }
            composable(Screen.Feed.route) { FeedScreen(viewModel) }
            composable(Screen.Sleep.route) { SleepScreen(viewModel) }
            composable(Screen.Weight.route) { WeightScreen(viewModel) }
            composable("milestones") { MilestonesScreen(viewModel) }
            composable("charts") { ChartsScreen(viewModel) }
            composable("diaper") { DiaperScreen(viewModel, onSaved = { navController.popBackStack() }) }
            composable("pumping") { PumpingScreen(viewModel, onSaved = { navController.popBackStack() }) }
            composable("health") { HealthScreen(viewModel, onSaved = { navController.popBackStack() }) }
            composable(Screen.Notes.route) { NotesScreen(viewModel, app.nostrManager) }
            composable("settings") { SettingsScreen(viewModel, app.nostrManager) }
        }
    }
}
