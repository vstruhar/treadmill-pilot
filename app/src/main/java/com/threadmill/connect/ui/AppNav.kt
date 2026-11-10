package com.threadmill.connect.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.threadmill.connect.TreadmillApp

@Composable
fun AppNav(app: TreadmillApp) {
    val nav = rememberNavController()
    val run by app.runController.ui.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showTabs = route == "workouts" || route == "history" || route == "stats" || route == "settings"
    LaunchedEffect(run.savedRunId) {
        val id = run.savedRunId ?: return@LaunchedEffect
        nav.navigate("summary/$id") {
            popUpTo("run") { inclusive = true }
            launchSingleTop = true
        }
        app.runController.consumeSavedRun()
    }
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = CardInk) {
                    Tab(nav, "workouts", "Workouts", Icons.AutoMirrored.Filled.DirectionsRun, route)
                    Tab(nav, "history", "History", Icons.Filled.History, route)
                    Tab(nav, "stats", "Stats", Icons.Filled.BarChart, route)
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
        if (run.active && route != "run" && !route.startsWith("summary")) {
            TextButton(onClick = { nav.navigate("run") { launchSingleTop = true } }) {
                Text("Run in progress", color = Accent)
            }
        }
        NavHost(nav, startDestination = "workouts", modifier = Modifier.weight(1f)) {
            composable("workouts") { WorkoutsScreen(nav) }
            composable("history") { HistoryScreen(nav) }
            composable("stats") { StatsScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("pair") { TreadmillPickerScreen(nav) }
            composable("workout/{id}") { entry ->
                WorkoutDetailScreen(entry.arguments?.getString("id").orEmpty(), nav)
            }
            composable("editor/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                EditorScreen(if (id == "new") null else id, nav)
            }
            composable("run") { RunScreen(app, nav) }
            composable("summary/{id}") { entry ->
                SummaryScreen(entry.arguments?.getString("id").orEmpty(), nav)
            }
        }
        }
    }
}

@Composable
private fun RowScope.Tab(
    nav: NavHostController,
    route: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    current: String,
) {
    NavigationBarItem(
        selected = current == route,
        onClick = {
            if (route == "workouts") {
                nav.popBackStack("workouts", inclusive = false)
            } else {
                nav.navigate(route) {
                    popUpTo("workouts")
                    launchSingleTop = true
                }
            }
        },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Ink,
            selectedTextColor = Accent,
            indicatorColor = Accent,
            unselectedIconColor = Mist,
            unselectedTextColor = Mist,
        ),
    )
}
