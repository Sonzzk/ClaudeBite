package com.sonzzk.claudebite.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.domain.Insights
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.domain.WeightTrend
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.PixelSprite
import com.sonzzk.claudebite.ui.components.UnitFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Shortcuts to the health tools: workouts, photos, grocery list, fasting timer. */
@Composable
internal fun HealthToolsRow(onOpen: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolTile("Workouts", "px_prop_15", Modifier.weight(1f)) { onOpen("workouts") }
        ToolTile("Photos", "px_prop_14", Modifier.weight(1f)) { onOpen("photos") }
        ToolTile("Groceries", "px_prop_04", Modifier.weight(1f)) { onOpen("groceries") }
        ToolTile("Fasting", "px_prop_18", Modifier.weight(1f)) { onOpen("fasting") }
    }
}

@Composable
private fun ToolTile(label: String, sprite: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(88.dp), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PixelSprite(sprite, Modifier.height(40.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Smoothed weight trend, weekly pace and when you'll reach the goal at this pace. */
@Composable
internal fun WeightForecastCard(state: ProgressUiState) {
    if (state.weights.size < 3) return
    val units = state.profile.units
    val pts = WeightTrend.smooth(state.weights.mapNotNull { w -> runCatching { LocalDate.parse(w.date) }.getOrNull()?.let { it to w.weightKg } })
    val trend = pts.lastOrNull()?.trend ?: return
    val rate = WeightTrend.weeklyRate(pts, state.today)
    val hasGoal = state.profile.goal != Goal.MAINTAIN
    val eta = if (hasGoal) WeightTrend.eta(pts, state.profile.targetWeightKg, state.today) else null
    ProgressCard("Weight trend", "Smoothed so daily water swings don't count") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(if (eta != null) MascotMood.PROUD else MascotMood.HAPPY, size = 72.dp)
            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Trend: ${UnitFormat.weight(trend, units)}", style = MaterialTheme.typography.titleMedium)
                if (rate != null) {
                    val r = UnitFormat.weightValue(abs(rate), units)
                    Text(
                        if (abs(rate) < 0.05f) "Holding steady" else "${if (rate < 0) "-" else "+"}${"%.2f".format(r)} ${UnitFormat.weightUnit(units)} per week",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when {
                    !hasGoal -> {}
                    eta != null -> Text(
                        "At this pace you'll reach ${UnitFormat.weight(state.profile.targetWeightKg, units)} around ${eta.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    else -> Text("Keep logging! I'll forecast your goal date once there's a clear trend.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Patterns from sleep and mood check-ins, computed on the phone. */
@Composable
internal fun InsightsCard(state: ProgressUiState) {
    val c = LocalAppContainer.current
    val notes by remember { c.tools.notes() }.collectAsState(initial = emptyList())
    if (notes.size < 4) return
    val sleep = notes.mapNotNull { n -> n.sleepHours?.let { h -> runCatching { LocalDate.parse(n.date) }.getOrNull()?.let { it to h } } }.toMap()
    val mood = notes.mapNotNull { n -> n.mood?.let { m -> runCatching { LocalDate.parse(n.date) }.getOrNull()?.let { it to m } } }.toMap()
    val lines = listOfNotNull(Insights.sleepVsCalories(sleep, state.caloriesByDate), Insights.moodVsCalories(mood, state.caloriesByDate))
    val avgSleep = sleep.values.takeIf { it.isNotEmpty() }?.average()
    ProgressCard("Claude-chan noticed", "From your morning check-ins") {
        avgSleep?.let { Text("Average sleep: ${"%.1f".format(it)} h", style = MaterialTheme.typography.titleSmall) }
        if (lines.isEmpty()) Text("A few more check-ins and I'll spot your patterns~", style = MaterialTheme.typography.bodyMedium)
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}
