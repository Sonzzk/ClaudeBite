package com.sonzzk.claudebite.ui.screens.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.food.FoodProduct
import kotlin.math.roundToInt

/** Search the built-in list, your recent foods and Open Food Facts. Picking a food asks how much. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodSearchSheet(
    query: String,
    results: List<FoodProduct>,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onPick: (FoodProduct, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picked by remember { mutableStateOf<FoodProduct?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).navigationBarsPadding()) {
            val p = picked
            if (p == null) {
                Text("Search foods", style = MaterialTheme.typography.titleLarge)
                Text("0 tokens · built-in list, your foods and Open Food Facts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                    placeholder = { Text("e.g. greek yogurt, banana, oreo") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 460.dp).padding(top = 8.dp)) {
                    items(results, key = { it.displayName + it.source + (it.barcode ?: "") }) { food ->
                        Row(
                            Modifier.fillMaxWidth().clickable { picked = food }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(food.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${food.servingLabel} · ${(food.kcal100 * food.servingG / 100).roundToInt()} kcal · P ${(food.protein100 * food.servingG / 100).roundToInt()}g" +
                                        when (food.source) {
                                            FoodProduct.Source.RECENT -> " · yours"
                                            FoodProduct.Source.OPEN_FOOD_FACTS -> " · OFF"
                                            else -> ""
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (results.isEmpty() && query.isNotBlank() && !searching) {
                        item { Text("No matches yet. Try another word, or describe it to Claude-chan!", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp)) }
                    }
                }
            } else {
                PortionPicker(p, onConfirm = { grams -> onPick(p, grams) }, onBack = { picked = null })
            }
        }
    }
}

/** How much did you eat? Serving chips + a grams slider + exact grams field. */
@Composable
internal fun PortionPicker(food: FoodProduct, onConfirm: (Float) -> Unit, onBack: () -> Unit) {
    var grams by remember(food) { mutableFloatStateOf(food.servingG) }
    var text by remember(food) { mutableStateOf(food.servingG.roundToInt().toString()) }
    val item = food.toItem(grams)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(food.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 2)
        Text("${item.calories} kcal · P ${item.proteinG.roundToInt()}g · C ${item.carbsG.roundToInt()}g · F ${item.fatG.roundToInt()}g",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val chips = buildList {
                if (food.servingLabel != "100 g") add(food.servingLabel to food.servingG)
                add("½" to food.servingG / 2); add("2×" to food.servingG * 2); add("100 g" to 100f)
            }
            chips.forEach { (label, g) ->
                FilterChip(selected = kotlin.math.abs(grams - g) < 0.5f, onClick = { grams = g; text = g.roundToInt().toString() }, label = { Text(label, maxLines = 1) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = grams.coerceIn(5f, maxOf(600f, food.servingG * 3)),
                onValueChange = { grams = (it / 5f).roundToInt() * 5f; text = grams.roundToInt().toString() },
                valueRange = 5f..maxOf(600f, food.servingG * 3),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(4); text.toFloatOrNull()?.let { grams = it } },
                suffix = { Text("g") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
        }
        Text("Tip: a palm of meat ≈ 100 g, a fist of rice ≈ 150 g, a thumb of butter or peanut butter ≈ 15 g.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = { onConfirm(grams) }, enabled = grams > 0f, modifier = Modifier.weight(1f)) { Text("Add") }
        }
    }
}
