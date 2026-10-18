package com.sonzzk.claudebite.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.Progression
import com.sonzzk.claudebite.data.prefs.UserProfile
import com.sonzzk.claudebite.domain.LevelInfo
import com.sonzzk.claudebite.domain.Leveling
import com.sonzzk.claudebite.domain.MascotBrain
import com.sonzzk.claudebite.domain.NutritionCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Everything the Today screen shows, in one immutable snapshot. */
data class HomeUiState(
    val loading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val targets: DailyTargets = NutritionCalculator.targets(UserProfile()),
    val progression: Progression = Progression(),
    val entries: List<FoodEntry> = emptyList(),
    val waterMl: Int = 0,
    val streak: Int = 0,
    val daysAway: Int = 0,
) {
    val eaten: Int get() = entries.sumOf { it.calories }
    val protein: Float get() = entries.sumOf { it.proteinG.toDouble() }.toFloat()
    val carbs: Float get() = entries.sumOf { it.carbsG.toDouble() }.toFloat()
    val fat: Float get() = entries.sumOf { it.fatG.toDouble() }.toFloat()
    val level: LevelInfo get() = Leveling.info(progression.xp)
    val byMeal: Map<MealType, List<FoodEntry>> get() = entries.groupBy { it.mealType }

    /** Input for MascotBrain, which picks Claude-chan's mood and line for free (no AI call). */
    val mascotSnapshot: MascotBrain.Snapshot
        get() = MascotBrain.Snapshot(
            name = profile.name,
            hour = LocalTime.now().hour,
            caloriesEaten = eaten,
            calorieTarget = targets.calories,
            entriesToday = entries.size,
            waterMl = waterMl,
            waterGoalMl = profile.waterGoalMl,
            streak = streak,
            daysAway = daysAway,
        )
}

class HomeViewModel(private val repository: HealthRepository) : ViewModel() {

    private val today = repository.today()
    private val daysAway = MutableStateFlow(0)

    /** Entries swiped away but not deleted yet, so "Undo" can bring them back without touching the database. */
    private val pendingDeletes = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<HomeUiState> = combine(
        combine(repository.profiles.profile, repository.profiles.progression) { p, g -> p to g },
        repository.entriesFor(today),
        repository.waterToday(),
        pendingDeletes,
        daysAway,
    ) { (profile, progression), entries, water, pending, away ->
        HomeUiState(
            loading = false,
            profile = profile,
            targets = NutritionCalculator.targets(profile),
            progression = progression,
            entries = entries.filterNot { it.id in pending },
            waterMl = water,
            streak = repository.currentStreak(),
            daysAway = away,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // How long was the user away? Claude-chan says "I missed you" after 3+ days. Then remember today.
        viewModelScope.launch {
            val todayEpoch = LocalDate.now().toEpochDay()
            val last = repository.profiles.progression.first().lastOpenedEpochDay
            daysAway.value = if (last == 0L) 0 else (todayEpoch - last).toInt().coerceAtLeast(0)
            repository.profiles.markOpened(todayEpoch)
        }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { repository.addWater(ml) }
    }
    fun undoWater() {
        viewModelScope.launch { repository.undoWater() }
    }

    /** Hides an entry right away; call [commitDelete] or [undoDelete] once the snackbar is gone. */
    fun hideEntry(entry: FoodEntry) {
        pendingDeletes.update { it + entry.id }
    }

    fun undoDelete(entry: FoodEntry) {
        pendingDeletes.update { it - entry.id }
    }

    fun commitDelete(entry: FoodEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
            pendingDeletes.update { it - entry.id }
        }
    }

    fun updateEntry(entry: FoodEntry) {
        viewModelScope.launch { repository.updateEntry(entry) }
    }
}
