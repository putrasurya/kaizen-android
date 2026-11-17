package com.putrasurya.kaizen.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.putrasurya.kaizen.core.ui.ComingSoon
import com.putrasurya.kaizen.habits.ui.HabitsScreen
import com.putrasurya.kaizen.more.MoreScreen
import com.putrasurya.kaizen.reminder.ui.ReminderTestScreen
import com.putrasurya.kaizen.timers.ui.FocusHistoryScreen
import com.putrasurya.kaizen.timers.ui.TimersScreen

/** The five bottom-bar tabs (ADR-003). Timers first: the most used feature. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Timers("timers", "Timers", Icons.Filled.PlayArrow),
    Habits("habits", "Habits", Icons.Filled.Favorite),
    Todos("todos", "Todos", Icons.Filled.CheckCircle),
    Journal("journal", "Journal", Icons.Filled.Edit),
    More("more", "More", Icons.Filled.Menu),
}

/** Screens opened from the More tab. */
object MoreRoutes {
    const val NOTES = "notes"
    const val MILESTONES = "milestones"
    const val REMINDER_TEST = "reminder-test"
}

@Composable
fun KaizenRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                // Standard bottom-bar behaviour: one copy of each tab,
                                // and each tab remembers where you were.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Timers.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Timers.route) {
                TimersScreen(onOpenHistory = { id -> navController.navigate("timer-history/$id") })
            }
            composable(
                "timer-history/{timerId}",
                arguments = listOf(navArgument("timerId") { type = NavType.LongType }),
            ) { entry ->
                FocusHistoryScreen(
                    timerId = entry.arguments?.getLong("timerId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Tab.Habits.route) { HabitsScreen() }
            composable(Tab.Todos.route) { ComingSoon("Todos", "Phase 4") }
            composable(Tab.Journal.route) { ComingSoon("Journal", "Phase 5") }
            composable(Tab.More.route) {
                MoreScreen(onOpen = { route -> navController.navigate(route) })
            }
            composable(MoreRoutes.NOTES) { ComingSoon("Notes", "Phase 6") }
            composable(MoreRoutes.MILESTONES) { ComingSoon("Milestones", "Phase 6") }
            composable(MoreRoutes.REMINDER_TEST) { ReminderTestScreen() }
        }
    }
}
