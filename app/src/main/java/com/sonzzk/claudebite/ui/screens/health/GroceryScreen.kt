package com.sonzzk.claudebite.ui.screens.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.ui.LocalAppContainer
import kotlinx.coroutines.launch

/** Shopping list: add items by hand or pull every ingredient from your saved meals. */
@Composable
fun GroceryScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val items by remember { c.tools.groceries() }.collectAsState(initial = emptyList())
    val meals by remember { c.repository.recentMeals() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    SubScreen("Grocery list", onBack, actions = {
        if (items.any { it.checked }) TextButton(onClick = { scope.launch { c.tools.clearChecked() } }) { Text("Clear done") }
    }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it.take(60) }, placeholder = { Text("Add item") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { val t = text; text = ""; scope.launch { c.tools.addGrocery(t) } }, enabled = text.isNotBlank()) { Text("Add") }
                }
            }
            if (meals.isNotEmpty()) {
                item {
                    Text("Add ingredients from a saved meal:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(meals, key = { it.key }) { m ->
                            FilterChip(selected = false, onClick = {
                                scope.launch {
                                    val n = c.tools.addMealsToGroceries(listOf(m))
                                    message = if (n == 0) "Everything from ${m.name} is already on the list" else "Added $n items from ${m.name}"
                                }
                            }, label = { Text(m.name) })
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
            if (items.isEmpty()) item { Text("Your list is empty. Claude-chan suggests: eggs, greek yogurt, berries, rice, chicken~", modifier = Modifier.padding(8.dp)) }
            items(items, key = { it.id }) { g ->
                Row(Modifier.fillMaxWidth().clickable { scope.launch { c.tools.toggleGrocery(g) } }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = g.checked, onCheckedChange = { scope.launch { c.tools.toggleGrocery(g) } })
                    Text(
                        g.name,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (g.checked) TextDecoration.LineThrough else null,
                        color = if (g.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    g.fromMeal?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
