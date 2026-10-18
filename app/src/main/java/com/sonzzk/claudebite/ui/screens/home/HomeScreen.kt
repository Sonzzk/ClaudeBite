package com.sonzzk.claudebite.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.ui.appViewModel
import com.sonzzk.claudebite.ui.components.MacroRow
import com.sonzzk.claudebite.ui.components.WaterTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The "Today" screen: greeting, Claude-chan + calorie ring, macros, water and today's meals.
 * Swipe a food left to delete it (with Undo); tap it to edit.
 */
@Composable
fun HomeScreen(onLogFood: (MealType?) -> Unit, onOpenSettings: () -> Unit, onOpenChat: () -> Unit) {
    val vm = appViewModel { c -> HomeViewModel(c.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<FoodEntry?>(null) }

    fun deleteWithUndo(entry: FoodEntry) {
        vm.hideEntry(entry)
        scope.launch {
            try {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = "Removed ${entry.name}",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) vm.undoDelete(entry) else vm.commitDelete(entry)
            } catch (e: CancellationException) {
                // Screen left while the snackbar was showing: the user didn't undo, so delete for real.
                vm.commitDelete(entry)
                throw e
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "top") {
                HomeTopBar(
                    name = state.profile.name,
                    level = state.level,
                    streak = state.streak,
                    onOpenSettings = onOpenSettings,
                )
            }
            item(key = "hero") {
                HeroCard(state = state, onOpenChat = onOpenChat)
            }
            item(key = "macros") {
                HomeCard {
                    MacroRow(state.protein, state.carbs, state.fat, state.targets)
                }
            }
            item(key = "water") {
                HomeCard {
                    WaterTracker(
                        ml = state.waterMl,
                        goalMl = state.profile.waterGoalMl,
                        onAdd = { vm.addWater(it) },
                        onUndo = { vm.undoWater() },
                    )
                }
            }
            item(key = "mealsTitle") {
                Text(
                    "Today's meals",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(MealType.entries, key = { "meal_${it.name}" }) { meal ->
                MealSection(
                    meal = meal,
                    entries = state.byMeal[meal].orEmpty(),
                    onAdd = { onLogFood(meal) },
                    onDelete = ::deleteWithUndo,
                    onOpen = { editing = it },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }

    editing?.let { entry ->
        EditEntrySheet(
            entry = entry,
            onDismiss = { editing = null },
            onSave = {
                vm.updateEntry(it)
                editing = null
            },
            onDelete = {
                editing = null
                deleteWithUndo(entry)
            },
        )
    }
}

/** Big rounded card used for every block on Home. */
@Composable
internal fun HomeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}
