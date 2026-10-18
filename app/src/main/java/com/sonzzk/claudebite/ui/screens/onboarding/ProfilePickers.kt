package com.sonzzk.claudebite.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonzzk.claudebite.data.prefs.ActivityLevel
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.DietStyle
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.data.prefs.Pace
import com.sonzzk.claudebite.data.prefs.Sex
import com.sonzzk.claudebite.data.prefs.UnitSystem
import com.sonzzk.claudebite.data.prefs.UserProfile
import com.sonzzk.claudebite.ui.components.AnimatedCounter
import com.sonzzk.claudebite.ui.components.UnitFormat
import com.sonzzk.claudebite.ui.theme.Palette
import kotlin.math.roundToInt

// Reusable profile pickers. Onboarding uses them step by step; Settings reuses them to edit the profile.

/** A big, tap-friendly card that bounces up a little and gets an orange border when selected. */
@Composable
fun ChoiceCard(
    title: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val scale by animateFloatAsState(
        if (selected) 1.03f else 1f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "choiceScale",
    )
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "choiceColor",
    )
    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * A labeled slider with -/+ buttons for fine tuning. Works in *display* units: the caller converts
 * (e.g. pounds to kg) in [onChange]. Values snap to [step].
 */
@Composable
fun MeasureSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange(snap(value - step)) }) {
                Icon(Icons.Rounded.Remove, contentDescription = "Less")
            }
            Text(
                format(value),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            FilledTonalIconButton(onClick = { onChange(snap(value + step)) }) {
                Icon(Icons.Rounded.Add, contentDescription = "More")
            }
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = { onChange(snap(it)) }, valueRange = range)
    }
}

/** Metric / Imperial switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitToggle(units: UnitSystem, onChange: (UnitSystem) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(UnitSystem.METRIC to "Metric (kg, cm)", UnitSystem.IMPERIAL to "Imperial (lb, ft)")
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (unit, label) ->
            SegmentedButton(
                selected = units == unit,
                onClick = { onChange(unit) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label) }
        }
    }
}

@Composable
fun SexPicker(sex: Sex, onChange: (Sex) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard("Female", "♀️", sex == Sex.FEMALE, { onChange(Sex.FEMALE) }, Modifier.weight(1f))
        ChoiceCard("Male", "♂️", sex == Sex.MALE, { onChange(Sex.MALE) }, Modifier.weight(1f))
    }
}

@Composable
fun AgePicker(age: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    MeasureSlider("Age", age.toFloat(), 13f..90f, 1f, { "${it.roundToInt()} years" }, { onChange(it.roundToInt()) }, modifier)
}

@Composable
fun HeightPicker(heightCm: Float, units: UnitSystem, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    if (units == UnitSystem.IMPERIAL) {
        MeasureSlider(
            "Height", heightCm / 2.54f, 48f..90f, 1f,
            format = { UnitFormat.height(it * 2.54f, units) },
            onChange = { onChange(it * 2.54f) }, modifier = modifier,
        )
    } else {
        MeasureSlider("Height", heightCm, 120f..230f, 1f, { "${it.roundToInt()} cm" }, onChange, modifier)
    }
}

/** Weight picker that shows kg or lb but always reports kg. */
@Composable
fun WeightPicker(label: String, weightKg: Float, units: UnitSystem, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    if (units == UnitSystem.IMPERIAL) {
        MeasureSlider(
            label, UnitFormat.kgToLb(weightKg), 70f..440f, 1f,
            format = { "${it.roundToInt()} lb" },
            onChange = { onChange(UnitFormat.lbToKg(it)) }, modifier = modifier,
        )
    } else {
        MeasureSlider(label, weightKg, 30f..200f, 0.5f, { "%.1f kg".format(it) }, onChange, modifier)
    }
}

/** Units toggle + height + weight, all in one block. */
@Composable
fun BodyPicker(profile: UserProfile, onChange: (UserProfile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UnitToggle(profile.units, { onChange(profile.copy(units = it)) })
        HeightPicker(profile.heightCm, profile.units, { onChange(profile.copy(heightCm = it)) })
        WeightPicker("Current weight", profile.weightKg, profile.units, { onChange(profile.copy(weightKg = it)) })
    }
}

private fun ActivityLevel.emoji() = when (this) {
    ActivityLevel.SEDENTARY -> "🛋️"
    ActivityLevel.LIGHT -> "🚶"
    ActivityLevel.MODERATE -> "🚴"
    ActivityLevel.ACTIVE -> "🏋️"
    ActivityLevel.ATHLETE -> "🏅"
}

@Composable
fun ActivityPicker(selected: ActivityLevel, onSelect: (ActivityLevel) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActivityLevel.entries.forEach { level ->
            ChoiceCard(
                title = level.name.lowercase().replaceFirstChar { it.uppercase() },
                subtitle = level.label,
                emoji = level.emoji(),
                selected = selected == level,
                onClick = { onSelect(level) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Goal.emoji() = when (this) {
    Goal.LOSE_FAT -> "🔥"
    Goal.MAINTAIN -> "🌿"
    Goal.BUILD_MUSCLE -> "💪"
    Goal.RECOMP -> "⚖️"
}

private fun Goal.subtitle() = when (this) {
    Goal.LOSE_FAT -> "A gentle calorie deficit, lots of protein"
    Goal.MAINTAIN -> "Eat balanced and feel great"
    Goal.BUILD_MUSCLE -> "A small surplus to grow stronger"
    Goal.RECOMP -> "Slight deficit + high protein + training"
}

@Composable
fun GoalPicker(selected: Goal, onSelect: (Goal) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Goal.entries.forEach { goal ->
            ChoiceCard(goal.label, goal.emoji(), selected == goal, { onSelect(goal) }, Modifier.fillMaxWidth(), goal.subtitle())
        }
    }
}

@Composable
fun PacePicker(selected: Pace, units: UnitSystem, onSelect: (Pace) -> Unit, modifier: Modifier = Modifier) {
    val emojis = mapOf(Pace.GENTLE to "🐢", Pace.STEADY to "🐇", Pace.FAST to "🚀")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pace.entries.forEach { pace ->
            ChoiceCard(
                title = pace.label,
                subtitle = "${UnitFormat.weight(pace.kgPerWeek, units, decimals = 2)} per week",
                emoji = emojis.getValue(pace),
                selected = selected == pace,
                onClick = { onSelect(pace) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun DietStyle.emoji() = when (this) {
    DietStyle.ANYTHING -> "🍽️"
    DietStyle.VEGETARIAN -> "🥗"
    DietStyle.VEGAN -> "🌱"
    DietStyle.PESCATARIAN -> "🐟"
    DietStyle.KETO -> "🥑"
    DietStyle.HALAL -> "🌙"
}

@Composable
fun DietPicker(selected: DietStyle, onSelect: (DietStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DietStyle.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { diet ->
                    ChoiceCard(diet.label, diet.emoji(), selected == diet, { onSelect(diet) }, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Calories + macros + water in a cozy grid with counting-up numbers. Used by the plan reveal and Settings. */
@Composable
fun TargetsPreview(targets: DailyTargets, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Daily calories", style = MaterialTheme.typography.labelLarge)
                AnimatedCounter(targets.calories, style = MaterialTheme.typography.displaySmall, suffix = " kcal", durationMs = 1400)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TargetTile("Protein", targets.proteinG, "g", Palette.Protein, Modifier.weight(1f))
            TargetTile("Carbs", targets.carbsG, "g", Palette.Carbs, Modifier.weight(1f))
            TargetTile("Fat", targets.fatG, "g", Palette.Fat, Modifier.weight(1f))
            TargetTile("Water", targets.waterMl, "ml", Palette.Water, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TargetTile(label: String, value: Int, unit: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedCounter(value, style = MaterialTheme.typography.titleMedium, suffix = unit, durationMs = 1400)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
