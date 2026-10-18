package com.sonzzk.claudebite.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.data.prefs.UserProfile
import com.sonzzk.claudebite.domain.NutritionCalculator
import com.sonzzk.claudebite.ui.components.SoftRed
import com.sonzzk.claudebite.ui.components.UnitFormat
import com.sonzzk.claudebite.ui.screens.onboarding.ActivityPicker
import com.sonzzk.claudebite.ui.screens.onboarding.AgePicker
import com.sonzzk.claudebite.ui.screens.onboarding.DietPicker
import com.sonzzk.claudebite.ui.screens.onboarding.GoalPicker
import com.sonzzk.claudebite.ui.screens.onboarding.HeightPicker
import com.sonzzk.claudebite.ui.screens.onboarding.MeasureSlider
import com.sonzzk.claudebite.ui.screens.onboarding.PacePicker
import com.sonzzk.claudebite.ui.screens.onboarding.SexPicker
import com.sonzzk.claudebite.ui.screens.onboarding.TargetsPreview
import com.sonzzk.claudebite.ui.screens.onboarding.UnitToggle
import com.sonzzk.claudebite.ui.screens.onboarding.WeightPicker
import kotlin.math.roundToInt

/** A row showing "Title ..... current value  v" that expands to reveal an editor. */
@Composable
private fun ExpandableSetting(title: String, value: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { open = !open }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/** Edit every profile field. Targets below update live as things change. */
@Composable
internal fun ProfileSection(profile: UserProfile, update: ((UserProfile) -> UserProfile) -> Unit) {
    val units = profile.units
    // Local text state keeps the cursor stable while typing; every change is still saved right away.
    var name by rememberSaveable { mutableStateOf(profile.name) }
    SettingsCard("Profile & goals", "🌱") {
        OutlinedTextField(
            value = name,
            onValueChange = { v ->
                name = v.take(30)
                update { it.copy(name = name) }
            },
            label = { Text("Name") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        ExpandableSetting("Sex & age", "${profile.sex.name.lowercase().replaceFirstChar { it.uppercase() }}, ${profile.age}") {
            SexPicker(profile.sex, { s -> update { it.copy(sex = s) } })
            AgePicker(profile.age, { a -> update { it.copy(age = a) } })
        }
        ExpandableSetting("Height", UnitFormat.height(profile.heightCm, units)) {
            HeightPicker(profile.heightCm, units, { h -> update { it.copy(heightCm = h) } })
        }
        ExpandableSetting("Weight", UnitFormat.weight(profile.weightKg, units)) {
            WeightPicker("Current weight", profile.weightKg, units, { w -> update { it.copy(weightKg = w) } })
            Text(
                "Tip: logging your weight on the Progress tab also updates this and keeps your chart going.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpandableSetting("Activity", profile.activity.name.lowercase().replaceFirstChar { it.uppercase() }) {
            ActivityPicker(profile.activity, { a -> update { it.copy(activity = a) } })
        }
        ExpandableSetting("Goal", profile.goal.label) {
            GoalPicker(profile.goal, { g -> update { it.copy(goal = g) } })
            if (profile.goal != Goal.MAINTAIN) {
                WeightPicker("Target weight", profile.targetWeightKg, units, { t -> update { it.copy(targetWeightKg = t) } })
                PacePicker(profile.pace, units, { p -> update { it.copy(pace = p) } })
            }
        }
        ExpandableSetting("Food style", profile.diet.label) {
            DietPicker(profile.diet, { d -> update { it.copy(diet = d) } })
        }
        ExpandableSetting("Water goal", "${profile.waterGoalMl} ml") {
            MeasureSlider(
                "Daily water", profile.waterGoalMl.toFloat(), 1000f..5000f, 250f,
                format = { "${it.roundToInt()} ml" },
                onChange = { ml -> update { it.copy(waterGoalMl = ml.roundToInt()) } },
            )
            TextButton(onClick = { update { it.copy(waterGoalMl = NutritionCalculator.suggestedWaterMl(it.weightKg)) } }) {
                Text("Use suggested (${NutritionCalculator.suggestedWaterMl(profile.weightKg)} ml)")
            }
        }
        CustomCalorieTarget(profile, update)
        Text("Your daily targets", style = MaterialTheme.typography.titleSmall)
        TargetsPreview(NutritionCalculator.targets(profile))
    }
}

/** Optional manual calorie override (0 = use the calculated value). */
@Composable
private fun CustomCalorieTarget(profile: UserProfile, update: ((UserProfile) -> UserProfile) -> Unit) {
    val enabled = profile.customCalorieTarget > 0
    var text by remember(enabled) { mutableStateOf(if (enabled) profile.customCalorieTarget.toString() else "") }
    val calculated = NutritionCalculator.targets(profile.copy(customCalorieTarget = 0)).calories
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Custom calorie target", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Calculated for you: $calculated kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { on -> update { it.copy(customCalorieTarget = if (on) calculated else 0) } },
        )
    }
    AnimatedVisibility(enabled) {
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(5)
                val kcal = text.toIntOrNull()
                if (kcal != null && kcal in 800..6000) update { it.copy(customCalorieTarget = kcal) }
            },
            label = { Text("kcal per day") },
            supportingText = { Text("Between 800 and 6000. Talk to a professional before going very low.") },
            isError = text.toIntOrNull()?.let { it !in 800..6000 } ?: true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun UnitsSection(profile: UserProfile, update: ((UserProfile) -> UserProfile) -> Unit) {
    SettingsCard("Units", "📏") {
        UnitToggle(profile.units, { u -> update { it.copy(units = u) } })
        Text(
            "Everything is stored in metric; this only changes how numbers are shown.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Reset everything" with a confirmation dialog. */
@Composable
internal fun DataSection(onWipe: (forgetKey: Boolean) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var forgetKey by rememberSaveable { mutableStateOf(false) }
    SettingsCard("Data", "🗂️") {
        Text(
            "All your data lives only on this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = { confirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftRed),
        ) {
            Icon(Icons.Rounded.DeleteForever, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Reset everything")
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = SoftRed) },
            title = { Text("Reset everything?") },
            text = {
                Column {
                    Text("This deletes all foods, water, weights, chats, XP, badges and your profile. It can't be undone.")
                    Row(
                        Modifier.fillMaxWidth().clickable { forgetKey = !forgetKey },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = forgetKey, onCheckedChange = { forgetKey = it })
                        Text("Also forget my API key")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = false
                        onWipe(forgetKey)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep my data") } },
        )
    }
}

@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    SettingsCard("About", "✨") {
        Text("ClaudeBite v$version", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Claude-chan is a fan-made mascot. Nutrition estimates are approximate and not medical advice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
