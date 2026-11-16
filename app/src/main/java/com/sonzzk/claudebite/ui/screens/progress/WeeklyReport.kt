package com.sonzzk.claudebite.ui.screens.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sonzzk.claudebite.data.db.DayTotals
import com.sonzzk.claudebite.domain.NutritionCalculator
import com.sonzzk.claudebite.system.Steps
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.LocalAppSettings
import java.time.LocalDate
import kotlin.math.roundToInt

/** This week vs last week, in plain words. Zero tokens: it's all local math. */
data class WeekSummary(val daysLogged: Int, val avgKcal: Int, val avgProtein: Int, val proteinDays: Int)

object WeeklyReport {
    fun summarize(totals: List<DayTotals>, from: LocalDate, to: LocalDate, proteinGoal: Int): WeekSummary {
        val days = totals.filter { t -> runCatching { LocalDate.parse(t.date) }.getOrNull()?.let { !it.isBefore(from) && !it.isAfter(to) } == true && t.entries > 0 }
        if (days.isEmpty()) return WeekSummary(0, 0, 0, 0)
        return WeekSummary(
            daysLogged = days.size,
            avgKcal = days.sumOf { it.calories } / days.size,
            avgProtein = (days.sumOf { it.proteinG.toDouble() } / days.size).roundToInt(),
            proteinDays = days.count { it.proteinG >= proteinGoal },
        )
    }
}

@Composable
internal fun WeeklyReportCard() {
    val c = LocalAppContainer.current
    val today = LocalDate.now()
    val totals by remember(today) { c.repository.dailyTotalsSince(today.minusDays(13).toString()) }.collectAsState(initial = emptyList())
    val profile by remember { c.profiles.profile }.collectAsState(initial = null)
    val workouts by remember(today) { c.tools.burnSince(today.minusDays(6).toString()) }.collectAsState(initial = emptyList())
    val p = profile ?: return
    val goal = NutritionCalculator.targets(p).proteinG
    val thisWeek = WeeklyReport.summarize(totals, today.minusDays(6), today, goal)
    val lastWeek = WeeklyReport.summarize(totals, today.minusDays(13), today.minusDays(7), goal)
    if (thisWeek.daysLogged == 0 && lastWeek.daysLogged == 0) return
    val hide = LocalAppSettings.current.hideCalories
    ProgressCard("Weekly report", "Last 7 days, compared with the week before") {
        Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(4f))) {
            Line("📅 Days logged", "${thisWeek.daysLogged}/7", delta(thisWeek.daysLogged, lastWeek.daysLogged, lastWeek.daysLogged > 0))
            if (!hide) Line("🔥 Average day", "${thisWeek.avgKcal} kcal", null)
            Line("💪 Average protein", "${thisWeek.avgProtein} g", delta(thisWeek.avgProtein, lastWeek.avgProtein, lastWeek.daysLogged > 0, "g"))
            Line("🎯 Protein goal hit", "${thisWeek.proteinDays} days", null)
            Line("🏃 Workout days", "${workouts.count { it.calories > 0 }}", null)
            Text(
                when {
                    thisWeek.daysLogged >= 6 -> "Amazing consistency this week! I'm so proud~"
                    thisWeek.daysLogged > lastWeek.daysLogged -> "More days logged than last week. Keep it rolling!"
                    thisWeek.proteinDays >= 4 -> "Protein game is strong this week 💪"
                    else -> "Every log helps. Let's try for one more day this week!"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}


private fun delta(now: Int, before: Int, show: Boolean, unit: String = ""): String? {
    if (!show || now == before) return null
    val d = now - before
    return (if (d > 0) "▲ +$d" else "▼ $d") + unit
}

@Composable
private fun Line(label: String, value: String, change: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
        if (change != null) Text("  $change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Steps from Health Connect, if the phone has it. Offers a one-time permission request. */
@Composable
internal fun StepsCard() {
    val context = LocalContext.current
    if (!remember { Steps.available(context) }) return
    var steps by remember { mutableStateOf<Long?>(null) }
    var asked by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(Steps.permissionContract()) { asked++ }
    LaunchedEffect(asked) { steps = Steps.today(context) }
    ProgressCard("Steps today", "From Health Connect (read only)") {
        val s = steps
        if (s == null) {
            Text("Let me peek at your step count so I can cheer you on~", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { launcher.launch(Steps.permissions) }) { Text("Connect Health Connect") }
        } else {
            Text("%,d steps".format(s), style = MaterialTheme.typography.headlineSmall)
            if (!LocalAppSettings.current.hideCalories) {
                Text("About ${Steps.kcal(s)} kcal from walking", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
