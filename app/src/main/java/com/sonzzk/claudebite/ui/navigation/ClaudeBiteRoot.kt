package com.sonzzk.claudebite.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sonzzk.claudebite.AppContainer
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.RewardOverlay
import com.sonzzk.claudebite.ui.screens.chat.ChatScreen
import com.sonzzk.claudebite.ui.screens.closet.ClosetScreen
import com.sonzzk.claudebite.ui.screens.home.HomeScreen
import com.sonzzk.claudebite.ui.screens.log.LogFoodScreen
import com.sonzzk.claudebite.ui.screens.onboarding.OnboardingScreen
import com.sonzzk.claudebite.ui.screens.progress.ProgressScreen
import com.sonzzk.claudebite.ui.screens.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LOG = "log?meal={meal}"
    const val CHAT = "chat"
    const val PROGRESS = "progress"
    const val CLOSET = "closet"
    const val SETTINGS = "settings"

    fun log(meal: MealType?) = "log?meal=${meal?.name ?: ""}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Today", Icons.Rounded.Home),
    Tab(Routes.CHAT, "Claude-chan", Icons.AutoMirrored.Rounded.Chat),
    Tab(Routes.PROGRESS, "Progress", Icons.Rounded.Insights),
    Tab(Routes.CLOSET, "Closet", Icons.Rounded.Checkroom),
)

@Composable
fun ClaudeBiteRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val profileFlow = remember { container.profiles.profile }
        val profile by profileFlow.collectAsState(initial = null)
        val loaded = profile ?: return@CompositionLocalProvider // splash stays until DataStore loads

        val nav = rememberNavController()
        val start = if (loaded.onboarded) Routes.HOME else Routes.ONBOARDING
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val showBar = tabs.any { it.route == currentRoute }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = { if (showBar) BottomBar(nav, currentRoute) },
                floatingActionButton = {
                    if (showBar) {
                        FloatingActionButton(
                            onClick = { nav.navigate(Routes.log(null)) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(),
                        ) { Icon(Icons.Rounded.CameraAlt, contentDescription = "Log food") }
                    }
                },
            ) { padding ->
                NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onFinished = {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        })
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onLogFood = { meal -> nav.navigate(Routes.log(meal)) },
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                            onOpenChat = { nav.navigateTab(Routes.CHAT) },
                        )
                    }
                    composable(
                        Routes.LOG,
                        arguments = listOf(navArgument("meal") { type = NavType.StringType; defaultValue = "" }),
                    ) { entry ->
                        val meal = entry.arguments?.getString("meal")?.takeIf { it.isNotBlank() }
                            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                        LogFoodScreen(initialMeal = meal, onDone = { nav.popBackStack() })
                    }
                    composable(Routes.CHAT) { ChatScreen() }
                    composable(Routes.PROGRESS) { ProgressScreen() }
                    composable(Routes.CLOSET) { ClosetScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
                }
            }
            // XP popups, level-ups and badge celebrations float above every screen.
            RewardOverlay(Modifier.align(Alignment.TopCenter))
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(nav: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { nav.navigateTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
