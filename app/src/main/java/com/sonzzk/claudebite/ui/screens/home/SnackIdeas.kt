package com.sonzzk.claudebite.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.food.CommonFoods
import com.sonzzk.claudebite.data.food.FoodProduct
import kotlin.math.roundToInt

/**
 * "What's left today": up to 4 snack ideas from the built-in food list that fit the calories left.
 * Picks protein-rich foods when protein is still low, fibre-rich ones otherwise. Zero tokens.
 */
object SnackPicker {
    fun ideas(kcalLeft: Int, proteinLeft: Float, limit: Int = 4): List<FoodProduct> {
        if (kcalLeft < 80) return emptyList()
        val fits = CommonFoods.all.filter {
            val kcal = it.kcal100 * it.servingG / 100f
            kcal in 40f..kcalLeft.toFloat() && it.kcal100 < 450
        }
        val ranked = if (proteinLeft > 15f) fits.sortedByDescending { it.protein100 / it.kcal100.coerceAtLeast(1f) }
        else fits.sortedByDescending { (it.fiber100 + it.protein100 / 2) / it.kcal100.coerceAtLeast(1f) }
        return ranked.take(limit)
    }
}

@Composable
internal fun SnackIdeasCard(kcalLeft: Int, proteinLeft: Float, hideCalories: Boolean, onPick: () -> Unit) {
    val ideas = SnackPicker.ideas(kcalLeft, proteinLeft)
    if (ideas.isEmpty()) return
    HomeCard {
        Column {
            Text("Room for a snack?", style = MaterialTheme.typography.titleMedium)
            Text(
                if (proteinLeft > 15f) "Still ${proteinLeft.roundToInt()} g protein to go. These would help~"
                else if (hideCalories) "A few ideas that fit what's left today." else "About $kcalLeft kcal left today. Some ideas:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ideas.forEach { f ->
                    val kcal = (f.kcal100 * f.servingG / 100f).roundToInt()
                    AssistChip(onClick = onPick, label = { Text(if (hideCalories) "${f.name} · ${f.servingLabel}" else "${f.name} · $kcal kcal") })
                }
            }
        }
    }
}
