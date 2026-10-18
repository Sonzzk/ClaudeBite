package com.sonzzk.claudebite.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.ui.components.SoftRed
import com.sonzzk.claudebite.ui.components.UnitFormat
import com.sonzzk.claudebite.ui.components.healthScoreColor
import com.sonzzk.claudebite.ui.theme.Palette
import java.io.File
import kotlin.math.roundToInt

private fun MealType.title() = when (this) {
    MealType.BREAKFAST -> "Breakfast"
    MealType.LUNCH -> "Lunch"
    MealType.DINNER -> "Dinner"
    MealType.SNACK -> "Snacks"
}

private fun MealType.emoji() = when (this) {
    MealType.BREAKFAST -> "🍳"
    MealType.LUNCH -> "🥗"
    MealType.DINNER -> "🍛"
    MealType.SNACK -> "🍪"
}

/** One meal card (Breakfast, Lunch...) with its foods and a "+" that opens Log Food for that meal. */
@Composable
internal fun MealSection(
    meal: MealType,
    entries: List<FoodEntry>,
    onAdd: () -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onOpen: (FoodEntry) -> Unit,
) {
    HomeCard(Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.emoji(), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(meal.title(), style = MaterialTheme.typography.titleMedium)
                    val kcal = entries.sumOf { it.calories }
                    Text(
                        if (entries.isEmpty()) "Nothing yet" else "$kcal kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add to ${meal.title()}")
                }
            }
            if (entries.isNotEmpty()) Spacer(Modifier.size(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.forEach { entry ->
                    key(entry.id) {
                        SwipeableEntry(entry, onDelete = { onDelete(entry) }, onClick = { onOpen(entry) })
                    }
                }
            }
        }
    }
}

/** Swipe right-to-left to delete. The list hides the item immediately; the screen shows an Undo snackbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEntry(entry: FoodEntry, onDelete: () -> Unit, onClick: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(SoftRed)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White) }
        },
    ) {
        EntryRow(entry, onClick)
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.photoPath != null) {
            AsyncImage(
                model = File(entry.photoPath),
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small),
            )
        } else {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(entry.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isFavorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Favorite, contentDescription = "Favorite", tint = Palette.SakuraPink, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                entry.portion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("${entry.calories} kcal", style = MaterialTheme.typography.labelLarge)
        entry.healthScore?.let { score ->
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).background(healthScoreColor(score), CircleShape))
        }
    }
}

/** Bottom sheet to fix Claude-chan's estimate: name, portion, calories, macros and favorite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditEntrySheet(
    entry: FoodEntry,
    onDismiss: () -> Unit,
    onSave: (FoodEntry) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(entry.name) }
    var portion by remember { mutableStateOf(entry.portion) }
    var calories by remember { mutableStateOf(entry.calories.toString()) }
    var protein by remember { mutableStateOf(entry.proteinG.format()) }
    var carbs by remember { mutableStateOf(entry.carbsG.format()) }
    var fat by remember { mutableStateOf(entry.fatG.format()) }
    var favorite by remember { mutableStateOf(entry.isFavorite) }
    val caloriesValid = calories.trim().toIntOrNull()?.let { it in 0..10_000 } == true

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edit food", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconToggleButton(checked = favorite, onCheckedChange = { favorite = it }) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (favorite) Palette.SakuraPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            }
            entry.mascotComment?.let {
                Text("Claude-chan: \"$it\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(portion, { portion = it }, label = { Text("Portion") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumberField("Calories (kcal)", calories, { calories = it }, KeyboardType.Number, Modifier.fillMaxWidth(), isError = !caloriesValid)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Protein g", protein, { protein = it }, KeyboardType.Decimal, Modifier.weight(1f))
                NumberField("Carbs g", carbs, { carbs = it }, KeyboardType.Decimal, Modifier.weight(1f))
                NumberField("Fat g", fat, { fat = it }, KeyboardType.Decimal, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(
                            entry.copy(
                                name = name.trim().ifBlank { entry.name },
                                portion = portion.trim(),
                                calories = calories.trim().toIntOrNull() ?: entry.calories,
                                proteinG = UnitFormat.parse(protein) ?: entry.proteinG,
                                carbsG = UnitFormat.parse(carbs) ?: entry.carbsG,
                                fatG = UnitFormat.parse(fat) ?: entry.fatG,
                                isFavorite = favorite,
                            ),
                        )
                    },
                    enabled = caloriesValid,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    type: KeyboardType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier,
    )
}

private fun Float.format(): String = if (this % 1f == 0f) roundToInt().toString() else "%.1f".format(this)
