package com.sonzzk.claudebite.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.appViewModel
import com.sonzzk.claudebite.ui.components.CalorieBarChart
import com.sonzzk.claudebite.ui.components.LegendDot
import com.sonzzk.claudebite.ui.components.MacroDonut
import com.sonzzk.claudebite.ui.components.SoftRed
import com.sonzzk.claudebite.ui.components.StreakHeatmap
import com.sonzzk.claudebite.ui.components.UnitFormat
import com.sonzzk.claudebite.ui.components.WeightLineChart
import com.sonzzk.claudebite.ui.theme.Palette
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

/** Charts and stats: calories vs target, macros, weight trend, streak calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen() {
    val vm = appViewModel { c -> ProgressViewModel(c.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    val units = state.profile.units

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your progress", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ProgressRange.entries.forEachIndexed { i, r ->
                SegmentedButton(
                    selected = state.range == r,
                    onClick = { vm.setRange(r) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ProgressRange.entries.size),
                ) { Text(r.label) }
            }
        }

        StatsRow(state)

        if (!state.loading && state.allTotals.isEmpty()) {
            EmptyStateCard(MascotMood.HUNGRY, "Log your first meal and I'll draw you the cutest charts!")
        } else {
            ProgressCard("Calories", "Dashed line = your ${state.targets.calories} kcal target") {
                CalorieBarChart(state.rangeCalories, state.targets.calories)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(state.rangeStart.format(shortDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, "On target")
                    LegendDot(SoftRed, "Over")
                }
            }

            state.averages?.let { avg ->
                ProgressCard("Average day", "Over the days you logged in this range") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MacroDonut(avg.proteinG, avg.carbsG, avg.fatG)
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${avg.calories} kcal", style = MaterialTheme.typography.titleLarge)
                            LegendDot(Palette.Protein, "Protein ${avg.proteinG.roundToInt()}g / ${state.targets.proteinG}g")
                            LegendDot(Palette.Carbs, "Carbs ${avg.carbsG.roundToInt()}g / ${state.targets.carbsG}g")
                            LegendDot(Palette.Fat, "Fat ${avg.fatG.roundToInt()}g / ${state.targets.fatG}g")
                        }
                    }
                }
            }
        }

        ProgressCard("Weight", weightSubtitle(state)) {
            if (state.weights.isEmpty()) {
                EmptyStateInline(MascotMood.HAPPY, "Step on the scale and log your weight so we can see your progress!")
            } else {
                val goal = if (state.profile.goal == Goal.MAINTAIN) null else UnitFormat.weightValue(state.profile.targetWeightKg, units)
                WeightLineChart(state.rangeWeights.map { UnitFormat.weightValue(it.weightKg, units) }, goal)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, "Weight")
                    if (goal != null) LegendDot(Palette.Mint, "Goal ${UnitFormat.weight(state.profile.targetWeightKg, units)}")
                }
            }
            Spacer(Modifier.height(4.dp))
            ExtendedFloatingActionButton(
                onClick = { showWeightDialog = true },
                icon = { Icon(Icons.Rounded.MonitorWeight, contentDescription = null) },
                text = { Text("Log weight") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.align(Alignment.End),
            )
        }

        ProgressCard("Streak calendar", "Last 10 weeks · darker = closer to your target") {
            StreakHeatmap(state.caloriesByDate, state.targets.calories, state.today)
        }
    }

    if (showWeightDialog) {
        LogWeightDialog(
            currentKg = state.weights.lastOrNull()?.weightKg ?: state.profile.weightKg,
            units = units,
            onDismiss = { showWeightDialog = false },
            onSave = { kg ->
                vm.logWeight(kg)
                showWeightDialog = false
            },
        )
    }
}

private fun weightSubtitle(state: ProgressUiState): String {
    val change = state.weightChangeKg ?: return "Log a few weigh-ins to see your trend"
    val units = state.profile.units
    val value = UnitFormat.weightValue(kotlin.math.abs(change), units)
    val sign = if (change < 0) "-" else if (change > 0) "+" else "±"
    return "$sign${"%.1f".format(value)} ${UnitFormat.weightUnit(units)} since you started"
}
