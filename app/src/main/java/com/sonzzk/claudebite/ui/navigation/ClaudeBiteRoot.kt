package com.sonzzk.claudebite.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.runtime.LaunchedEffect
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
import com.sonzzk.claudebite.ui.screens.health.GroceryScreen
import com.sonzzk.claudebite.ui.screens.health.SubScreen
import com.sonzzk.claudebite.ui.screens.play.DexScreen
import com.sonzzk.claudebite.ui.screens.play.MiniGameScreen
import com.sonzzk.claudebite.ui.screens.play.PlayRoutes
import com.sonzzk.claudebite.ui.screens.play.PlayScreen
import com.sonzzk.claudebite.ui.screens.play.RoomScreen
import com.sonzzk.claudebite.ui.screens.play.ShopScreen
import com.sonzzk.claudebite.ui.screens.play.StickersScreen
import com.sonzzk.claudebite.ui.screens.health.PhotosScreen
import com.sonzzk.claudebite.ui.screens.health.WorkoutsScreen
import com.sonzzk.claudebite.ui.screens.home.HomeScreen
import com.sonzzk.claudebite.ui.screens.log.LogFoodScreen
import com.sonzzk.claudebite.ui.screens.onboarding.OnboardingScreen
import com.sonzzk.claudebite.ui.screens.onboarding.StoryScreen
import com.sonzzk.claudebite.ui.screens.health.FastingScreen
import com.sonzzk.claudebite.ui.screens.progress.ProgressScreen
import com.sonzzk.claudebite.ui.screens.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val STORY = "story"
    const val FASTING = "fasting"
    const val HOME = "home"
    const val LOG = "log?meal={meal}&date={date}"
    const val CHAT = "chat"
    const val PROGRESS = "progress"
    const val CLOSET = "closet"
    const val SETTINGS = "settings"
    const val WORKOUTS = "workouts"
    const val PHOTOS = "photos"
    const val GROCERIES = "groceries"

    fun log(meal: MealType?, date: String? = null) = "log?meal=${meal?.name ?: ""}&date=${date ?: ""}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Today", Icons.Rounded.Home),
    Tab(Routes.CHAT, "Claude-chan", Icons.AutoMirrored.Rounded.Chat),
    Tab(Routes.PROGRESS, "Progress", Icons.Rounded.Insights),
    Tab(PlayRoutes.PLAY, "Play", Icons.Rounded.SportsEsports),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClaudeBiteRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val profileFlow = remember { container.profiles.profile }
        val profile by profileFlow.collectAsState(initial = null)
        val loaded = profile ?: return@CompositionLocalProvider // splash stays until DataStore loads

        val nav = rememberNavController()
        // Decide the start screen once; later changes are handled by the effect below.
        val start = remember { if (loaded.onboarded) Routes.HOME else Routes.STORY }
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        // Hide the bottom bar while typing so the chat input sits right above the keyboard.
        val imeVisible = WindowInsets.isImeVisible
        val showBar = tabs.any { it.route == currentRoute } && !imeVisible

        // "Reset everything" in Settings clears the profile: send the user back to onboarding.
        LaunchedEffect(loaded.onboarded) {
            if (!loaded.onboarded && nav.currentDestination?.route != Routes.ONBOARDING && nav.currentDestination?.route != Routes.STORY) {
                nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = { if (showBar) BottomBar(nav, currentRoute) },
                floatingActionButton = {
                    // The camera button lives on Today only, so it never covers the chat input or lists.
                    if (showBar && currentRoute == Routes.HOME) {
                        FloatingActionButton(
                            onClick = { nav.navigate(Routes.log(null)) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(),
                        ) { Icon(Icons.Rounded.CameraAlt, contentDescription = "Log food") }
                    }
                },
            ) { padding ->
                NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                    composable(Routes.STORY) {
                        StoryScreen(onFinished = { nav.navigate(Routes.ONBOARDING) { popUpTo(Routes.STORY) { inclusive = true } } })
                    }
                    composable(Routes.FASTING) { FastingScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onFinished = {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        })
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onLogFood = { meal, date -> nav.navigate(Routes.log(meal, date)) },
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                            onOpenChat = { nav.navigateTab(Routes.CHAT) },
                        )
                    }
                    composable(
                        Routes.LOG,
                        arguments = listOf(
                            navArgument("meal") { type = NavType.StringType; defaultValue = "" },
                            navArgument("date") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        val meal = entry.arguments?.getString("meal")?.takeIf { it.isNotBlank() }
                            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                        val date = entry.arguments?.getString("date")?.takeIf { it.isNotBlank() }
                        LogFoodScreen(initialMeal = meal, date = date, onDone = { nav.popBackStack() })
                    }
                    composable(Routes.CHAT) { ChatScreen() }
                    composable(Routes.PROGRESS) { ProgressScreen(onOpen = { nav.navigate(it) }) }
                    composable(Routes.WORKOUTS) { WorkoutsScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.PHOTOS) { PhotosScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.GROCERIES) { GroceryScreen(onBack = { nav.popBackStack() }) }
                    composable(PlayRoutes.PLAY) { PlayScreen(onOpen = { nav.navigate(it) }) }
                    composable(Routes.CLOSET) { SubScreen("Closet", onBack = { nav.popBackStack() }) { ClosetScreen() } }
                    composable(PlayRoutes.ROOM) { RoomScreen(onBack = { nav.popBackStack() }, onShop = { nav.navigate(PlayRoutes.SHOP) }) }
                    composable(PlayRoutes.SHOP) { ShopScreen(onBack = { nav.popBackStack() }) }
                    composable(PlayRoutes.DEX) { DexScreen(onBack = { nav.popBackStack() }) }
                    composable(PlayRoutes.STICKERS) { StickersScreen(onBack = { nav.popBackStack() }) }
                    composable(PlayRoutes.MINIGAME) { MiniGameScreen(onBack = { nav.popBackStack() }) }
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
