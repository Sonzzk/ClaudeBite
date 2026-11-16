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
import com.sonzzk.claudebite.domain.GroupedMeal
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
fun HomeScreen(onLogFood: (MealType?, String) -> Unit, onOpenSettings: () -> Unit, onOpenChat: () -> Unit) {
    val vm = appViewModel { c -> HomeViewModel(c.repository, c.tools, c.game) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var editingMeal by remember { mutableStateOf<GroupedMeal?>(null) }

    fun deleteWithUndo(entries: List<FoodEntry>, label: String) {
        vm.hide(entries)
        scope.launch {
            try {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = "Removed $label",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) vm.undoDelete(entries) else vm.commitDelete(entries)
            } catch (e: CancellationException) {
                // Screen left while the snackbar was showing: the user didn't undo, so delete for real.
                vm.commitDelete(entries)
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
            item(key = "dates") {
                DateStrip(selected = java.time.LocalDate.parse(state.date), onSelect = vm::selectDate)
            }
            item(key = "hero") {
                HeroCard(state = state, onOpenChat = onOpenChat)
            }
            if (state.isToday && (state.note?.sleepHours == null || state.note?.mood == null)) {
                item(key = "checkin") {
                    CheckInCard(onSave = vm::saveCheckIn)
                }
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
                        onAdd = { if (state.isToday) vm.addWater(it) },
                        onUndo = { if (state.isToday) vm.undoWater() },
                    )
                }
            }
            if (state.isToday && java.time.LocalTime.now().hour >= 14) {
                item(key = "snacks") {
                    SnackIdeasCard(
                        kcalLeft = state.budget - state.eaten,
                        proteinLeft = state.targets.proteinG - state.protein,
                        hideCalories = com.sonzzk.claudebite.ui.components.LocalAppSettings.current.hideCalories,
                        onPick = { onLogFood(MealType.SNACK, state.date) },
                    )
                }
            }
            item(key = "mealsTitle") {
                Text(
                    if (state.isToday) "Today's meals" else "Meals on ${prettyDate(state.date)}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(MealType.entries, key = { "meal_${it.name}" }) { meal ->
                MealSection(
                    meal = meal,
                    lines = state.linesByMeal[meal].orEmpty(),
                    onAdd = { onLogFood(meal, state.date) },
                    copyLabel = state.previousDay.filter { it.mealType == meal }
                        .takeIf { it.isNotEmpty() && state.linesByMeal[meal].isNullOrEmpty() }
                        ?.let { prev -> "Same as yesterday (${prev.sumOf { it.calories }} kcal)" },
                    onCopy = { vm.copyFromPreviousDay(meal) },
                    onDelete = { line ->
                        val label = (line as? GroupedMeal)?.name ?: line.entries.first().name
                        deleteWithUndo(line.entries, label)
                    },
                    onOpenEntry = { editing = it },
                    onEditMeal = { editingMeal = it },
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
                deleteWithUndo(listOf(entry), entry.name)
            },
        )
    }

    editingMeal?.let { meal ->
        EditMealSheet(
            meal = meal,
            onDismiss = { editingMeal = null },
            onSave = { name, type, favorite ->
                vm.updateMeal(meal, name, type, favorite)
                editingMeal = null
            },
            onDelete = {
                editingMeal = null
                deleteWithUndo(meal.entries, meal.name)
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

/** "Mon 14 Nov" for a yyyy-MM-dd date. */
internal fun prettyDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
}.getOrDefault(iso)
