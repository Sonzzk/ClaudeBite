package com.sonzzk.claudebite.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.prefs.UnitSystem
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.components.AnimatedCounter
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble
import com.sonzzk.claudebite.ui.components.UnitFormat
import com.sonzzk.claudebite.ui.theme.Palette

/** Rounded card with a title and a short subtitle, used for every chart. */
@Composable
internal fun ProgressCard(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** Current streak, best streak and total foods, each counting up. */
@Composable
internal fun StatsRow(state: ProgressUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Icons.Rounded.LocalFireDepartment, Palette.ClaudeOrange, state.currentStreak, "day streak", Modifier.weight(1f))
        StatTile(Icons.Rounded.EmojiEvents, Palette.Butter, state.bestStreak, "best streak", Modifier.weight(1f))
        StatTile(Icons.Rounded.Restaurant, Palette.Mint, state.totalFoods, "foods logged", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(icon: ImageVector, tint: Color, value: Int, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint)
            AnimatedCounter(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full card with Claude-chan cheering the user on when there's nothing to chart yet. */
@Composable
internal fun EmptyStateCard(mood: MascotMood, text: String) {
    ProgressCard("Nothing here yet", null) { EmptyStateInline(mood, text) }
}

@Composable
internal fun EmptyStateInline(mood: MascotMood, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(mood = mood, size = 90.dp)
        Spacer(Modifier.width(8.dp))
        SpeechBubble(text, Modifier.weight(1f))
    }
}

/** Number field + unit for today's weight. Saves in kg no matter what the user typed in. */
@Composable
internal fun LogWeightDialog(currentKg: Float, units: UnitSystem, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var text by remember { mutableStateOf("%.1f".format(UnitFormat.weightValue(currentKg, units))) }
    val parsed = UnitFormat.parse(text)
    val kg = parsed?.let { if (units == UnitSystem.IMPERIAL) UnitFormat.lbToKg(it) else it }
    val valid = kg != null && kg in 25f..350f

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Mascot(mood = MascotMood.HAPPY, size = 72.dp) },
        title = { Text("Log today's weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tip: weigh yourself in the morning, after the bathroom, for the most consistent numbers.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    singleLine = true,
                    isError = !valid,
                    suffix = { Text(UnitFormat.weightUnit(units)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { kg?.let(onSave) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
