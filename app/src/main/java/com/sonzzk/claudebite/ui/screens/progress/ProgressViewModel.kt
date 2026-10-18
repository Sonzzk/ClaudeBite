package com.sonzzk.claudebite.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.DayTotals
import com.sonzzk.claudebite.data.db.WeightLog
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.UserProfile
import com.sonzzk.claudebite.domain.NutritionCalculator
import com.sonzzk.claudebite.domain.Streaks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The chart time windows the user can pick. */
enum class ProgressRange(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
    QUARTER(90, "90 days"),
}

/** Average grams per logged day. */
data class MacroAverages(val calories: Int, val proteinG: Float, val carbsG: Float, val fatG: Float)

data class ProgressUiState(
    val loading: Boolean = true,
    val range: ProgressRange = ProgressRange.WEEK,
    val today: LocalDate = LocalDate.now(),
    val profile: UserProfile = UserProfile(),
    val targets: DailyTargets = NutritionCalculator.targets(UserProfile()),
    /** Every logged day ever (small: one row per day), so streaks and totals need no extra queries. */
    val allTotals: List<DayTotals> = emptyList(),
    val weights: List<WeightLog> = emptyList(),
) {
    val caloriesByDate: Map<LocalDate, Int> by lazy {
        allTotals.mapNotNull { t -> runCatching { LocalDate.parse(t.date) }.getOrNull()?.let { it to t.calories } }.toMap()
    }

    val rangeStart: LocalDate get() = today.minusDays(range.days - 1L)

    /** Calories for each day in the range (0 for days with nothing logged), oldest first. */
    val rangeCalories: List<Int>
        get() = (0 until range.days).map { caloriesByDate[rangeStart.plusDays(it.toLong())] ?: 0 }

    val rangeTotals: List<DayTotals>
        get() = allTotals.filter { it.date >= rangeStart.toString() }

    val averages: MacroAverages?
        get() {
            val days = rangeTotals
            if (days.isEmpty()) return null
            val n = days.size
            return MacroAverages(
                calories = days.sumOf { it.calories } / n,
                proteinG = days.sumOf { it.proteinG.toDouble() }.toFloat() / n,
                carbsG = days.sumOf { it.carbsG.toDouble() }.toFloat() / n,
                fatG = days.sumOf { it.fatG.toDouble() }.toFloat() / n,
            )
        }

    /** Weigh-ins inside the range; falls back to all of them so the chart is never needlessly empty. */
    val rangeWeights: List<WeightLog>
        get() = weights.filter { it.date >= rangeStart.toString() }.takeIf { it.size >= 2 } ?: weights

    /** Latest weight minus the very first weigh-in, in kg (negative = lost). */
    val weightChangeKg: Float?
        get() = if (weights.size < 2) null else weights.last().weightKg - weights.first().weightKg

    val totalFoods: Int get() = allTotals.sumOf { it.entries }
    val daysLogged: Int get() = allTotals.size
    val currentStreak: Int get() = Streaks.current(allTotals.map { it.date }.sortedDescending(), today)

    /** Longest run of consecutive logged days ever. */
    val bestStreak: Int
        get() {
            val dates = caloriesByDate.keys.sorted()
            var best = 0
            var run = 0
            var prev: LocalDate? = null
            for (d in dates) {
                run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
            return best
        }
}

class ProgressViewModel(private val repository: HealthRepository) : ViewModel() {

    private val range = MutableStateFlow(ProgressRange.WEEK)

    val state: StateFlow<ProgressUiState> = combine(
        range,
        repository.profiles.profile,
        repository.dailyTotalsSince("0000-01-01"),
        repository.weights(),
    ) { r, profile, totals, weights ->
        ProgressUiState(
            loading = false,
            range = r,
            today = LocalDate.now(),
            profile = profile,
            targets = NutritionCalculator.targets(profile),
            allTotals = totals,
            weights = weights,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun setRange(value: ProgressRange) {
        range.value = value
    }

    fun logWeight(kg: Float) {
        viewModelScope.launch { repository.logWeight(kg) }
    }
}
