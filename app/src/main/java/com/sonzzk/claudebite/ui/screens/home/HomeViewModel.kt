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
import com.sonzzk.claudebite.domain.GroupedMeal
import com.sonzzk.claudebite.domain.Leveling
import com.sonzzk.claudebite.domain.MealGrouping
import com.sonzzk.claudebite.domain.MealLine
import com.sonzzk.claudebite.domain.MascotBrain
import com.sonzzk.claudebite.domain.NutritionCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    /** The day being viewed (yyyy-MM-dd). Today by default; the date strip picks earlier days. */
    val date: String = LocalDate.now().toString(),
    val isToday: Boolean = true,
    /** The previous day's entries, for "Copy yesterday's lunch". */
    val previousDay: List<FoodEntry> = emptyList(),
    /** Calories burned in workouts that day; added to the food budget. */
    val burned: Int = 0,
    val note: com.sonzzk.claudebite.data.db.DayNote? = null,
) {
    val budget: Int get() = targets.calories + burned
    val eaten: Int get() = entries.sumOf { it.calories }
    val protein: Float get() = entries.sumOf { it.proteinG.toDouble() }.toFloat()
    val carbs: Float get() = entries.sumOf { it.carbsG.toDouble() }.toFloat()
    val fat: Float get() = entries.sumOf { it.fatG.toDouble() }.toFloat()
    val level: LevelInfo get() = Leveling.info(progression.xp)
    /** Each meal slot's lines; ingredients logged together are folded into one [GroupedMeal]. */
    val linesByMeal: Map<MealType, List<MealLine>>
        get() = entries.groupBy { it.mealType }.mapValues { MealGrouping.lines(it.value) }

    /** Input for MascotBrain, which picks Claude-chan's mood and line for free (no AI call). */
    val mascotSnapshot: MascotBrain.Snapshot
        get() = MascotBrain.Snapshot(
            name = profile.name,
            hour = LocalTime.now().hour,
            caloriesEaten = eaten,
            calorieTarget = budget,
            entriesToday = entries.size,
            waterMl = waterMl,
            waterGoalMl = profile.waterGoalMl,
            streak = streak,
            daysAway = daysAway,
        )
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: HealthRepository,
    private val tools: com.sonzzk.claudebite.data.HealthToolsRepository,
    private val game: com.sonzzk.claudebite.data.GameRepository? = null,
) : ViewModel() {

    private val daysAway = MutableStateFlow(0)
    private val selectedDate = MutableStateFlow(LocalDate.now())

    /** Follows the real date across midnight while the user is on "today". */
    private val today = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(LocalDate.now())
            kotlinx.coroutines.delay(60_000)
        }
    }.distinctUntilChanged()

    private val viewedDate = combine(selectedDate, today) { sel, now -> if (sel.isAfter(now)) now else sel }

    /** Entries swiped away but not deleted yet, so "Undo" can bring them back without touching the database. */
    private val pendingDeletes = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<HomeUiState> = combine(
        combine(repository.profiles.profile, repository.profiles.progression) { p, g -> p to g },
        viewedDate.flatMapLatest { d ->
            combine(
                repository.entriesFor(d.toString()),
                repository.entriesFor(d.minusDays(1).toString()),
                repository.waterFor(d.toString()),
                tools.burnedOn(d.toString()),
                tools.note(d.toString()),
            ) { e, prev, w, burn, note -> DayData(d, e, prev, w, burn, note) }
        },
        pendingDeletes,
        daysAway,
    ) { (profile, progression), day, pending, away ->
        HomeUiState(
            loading = false,
            profile = profile,
            targets = NutritionCalculator.targets(profile),
            progression = progression,
            entries = day.entries.filterNot { it.id in pending },
            waterMl = day.water,
            streak = repository.currentStreak(),
            daysAway = away,
            date = day.date.toString(),
            isToday = day.date == LocalDate.now(),
            previousDay = day.previous,
            burned = day.burned,
            note = day.note,
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

    /** Morning check-in: sleep hours + quality, and mood. Merges with what's already saved for that day. */
    fun saveCheckIn(sleepHours: Float?, sleepQuality: Int?, mood: Int?) {
        viewModelScope.launch {
            val d = state.value.date
            if (state.value.isToday) game?.onCheckIn()
            val old = state.value.note ?: com.sonzzk.claudebite.data.db.DayNote(date = d)
            tools.putNote(
                old.copy(
                    sleepHours = sleepHours ?: old.sleepHours,
                    sleepQuality = sleepQuality ?: old.sleepQuality,
                    mood = mood ?: old.mood,
                ),
            )
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun copyFromPreviousDay(meal: MealType) {
        val d = LocalDate.parse(state.value.date)
        viewModelScope.launch { repository.copyMealSlot(d.minusDays(1).toString(), d.toString(), meal) }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { repository.addWater(ml) }
    }
    fun undoWater() {
        viewModelScope.launch { repository.undoWater() }
    }

    /** Hides entries right away; call [commitDelete] or [undoDelete] once the snackbar is gone. */
    fun hide(entries: List<FoodEntry>) {
        pendingDeletes.update { it + entries.map { e -> e.id } }
    }

    fun undoDelete(entries: List<FoodEntry>) {
        pendingDeletes.update { it - entries.map { e -> e.id }.toSet() }
    }

    fun commitDelete(entries: List<FoodEntry>) {
        viewModelScope.launch {
            repository.deleteEntries(entries)
            pendingDeletes.update { it - entries.map { e -> e.id }.toSet() }
        }
    }

    fun updateMeal(meal: GroupedMeal, name: String, mealType: MealType, favorite: Boolean) {
        viewModelScope.launch {
            repository.updateMeal(meal.groupId, name, mealType)
            if (favorite != meal.isFavorite) repository.setMealFavorite(meal.groupId, favorite)
        }
    }

    fun updateEntry(entry: FoodEntry) {
        viewModelScope.launch { repository.updateEntry(entry) }
    }
}

private data class DayData(
    val date: LocalDate,
    val entries: List<FoodEntry>,
    val previous: List<FoodEntry>,
    val water: Int,
    val burned: Int,
    val note: com.sonzzk.claudebite.data.db.DayNote?,
)
