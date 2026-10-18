package com.sonzzk.claudebite.ui.screens.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.components.GradeBadge
import com.sonzzk.claudebite.ui.components.MacroBar
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble
import com.sonzzk.claudebite.ui.theme.Palette
import java.io.File
import kotlin.math.roundToInt

/** Review step: Claude-chan's verdict, editable items with portion sliders, totals and Save. */
@Composable
internal fun ReviewContent(
    review: LogStage.Review,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    targets: DailyTargets?,
    outfit: String,
    onMultiplier: (Long, Float) -> Unit,
    onRemove: (Long) -> Unit,
    onAddItem: (String, Int, Float, Float, Float) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    val analysis = review.analysis
    var addOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (review.photoPath != null) {
                item {
                    AsyncImage(
                        model = File(review.photoPath),
                        contentDescription = "Your food photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp)),
                    )
                }
            }
            item { VerdictCard(analysis.grade, analysis.healthScore, analysis.comment, outfit) }
            if (analysis.tip.isNotBlank()) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)) {
                        Text(
                            "💡 ${analysis.tip}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        )
                    }
                }
            }
            if (analysis.confidence < 0.6f) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = Palette.Butter.copy(alpha = 0.35f)) {
                        Text(
                            "🤔 I'm not totally sure about this one. Please check the items and portions below!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        )
                    }
                }
            }
            item { MealChips(mealType, onMealType) }
            items(review.items, key = { it.key }) { item ->
                ItemCard(item, onMultiplier = { onMultiplier(item.key, it) }, onRemove = { onRemove(item.key) })
            }
            item {
                CozyCard {
                    TextButton(onClick = { addOpen = !addOpen }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add item")
                    }
                    AnimatedVisibility(addOpen, enter = expandVertically(), exit = shrinkVertically()) {
                        ManualFoodForm("Add") { name, kcal, p, c, f ->
                            onAddItem(name, kcal, p, c, f)
                            addOpen = false
                        }
                    }
                }
            }
            item { TotalsCard(review, targets) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Try again")
            }
            Button(
                onClick = onSave,
                enabled = review.items.isNotEmpty(),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1.6f).height(52.dp),
            ) { Text("Save ✨") }
        }
    }
}

/** Grade badge, score and Claude-chan reacting to the meal. */
@Composable
private fun VerdictCard(grade: String, score: Int, comment: String, outfit: String) {
    val mood = when {
        score >= 8 -> MascotMood.EXCITED
        score >= 5 -> MascotMood.HAPPY
        else -> MascotMood.WORRIED
    }
    CozyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradeBadge(grade, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Health score", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$score / 10", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(mood, size = 88.dp, outfit = outfit)
            Spacer(Modifier.width(8.dp))
            SpeechBubble(comment, Modifier.weight(1f))
        }
    }
}

/** One food with its numbers, a portion slider (0.25x - 3x) and a remove button. */
@Composable
private fun ItemCard(item: EditableItem, onMultiplier: (Float) -> Unit, onRemove: () -> Unit) {
    val scaled = item.scaled
    val kcal by animateIntAsState(scaled.calories, label = "kcal")
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.base.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.base.portion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$kcal kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${item.base.name}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroText("P", scaled.proteinG, Palette.Protein)
                MacroText("C", scaled.carbsG, Palette.Carbs)
                MacroText("F", scaled.fatG, Palette.Fat)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "×${"%.2f".format(item.multiplier).trimEnd('0').trimEnd('.', ',')}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(52.dp),
                )
                Slider(
                    value = item.multiplier,
                    // Snap to quarter portions so numbers stay tidy.
                    onValueChange = { onMultiplier((it * 4).roundToInt() / 4f) },
                    valueRange = 0.25f..3f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MacroText(label: String, grams: Float, color: androidx.compose.ui.graphics.Color) {
    Text(
        "$label ${grams.roundToInt()}g",
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

/** Meal totals: calories and three macro bars against the daily targets. */
@Composable
private fun TotalsCard(review: LogStage.Review, targets: DailyTargets?) {
    val items = review.total
    val kcal by animateIntAsState(items.sumOf { it.calories }, label = "totalKcal")
    val protein = items.sumOf { it.proteinG.toDouble() }.toFloat()
    val carbs = items.sumOf { it.carbsG.toDouble() }.toFloat()
    val fat = items.sumOf { it.fatG.toDouble() }.toFloat()
    CozyCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("This meal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$kcal kcal", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (targets != null) {
            Text(
                "${(kcal * 100f / targets.calories.coerceAtLeast(1)).roundToInt()}% of your daily ${targets.calories} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroBar("Protein", protein, targets?.proteinG ?: 0, Palette.Protein)
            MacroBar("Carbs", carbs, targets?.carbsG ?: 0, Palette.Carbs)
            MacroBar("Fat", fat, targets?.fatG ?: 0, Palette.Fat)
        }
    }
}
