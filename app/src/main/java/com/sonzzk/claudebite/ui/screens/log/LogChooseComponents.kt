package com.sonzzk.claudebite.ui.screens.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble
import com.sonzzk.claudebite.ui.theme.Palette
import kotlin.math.roundToInt

/** The first step: meal type, photo / gallery / describe / quick add, and one-tap recent foods. */
@Composable
internal fun ChooseContent(
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    outfit: String,
    hint: String,
    onHint: (String) -> Unit,
    cameraDenied: Boolean,
    quickAddOpen: Boolean,
    onQuickAddOpen: (Boolean) -> Unit,
    recent: List<FoodEntry>,
    onSnap: () -> Unit,
    onPick: () -> Unit,
    onDescribe: (String) -> Unit,
    onQuickAdd: (String, Int, Float, Float, Float) -> Unit,
    onRelog: (FoodEntry) -> Unit,
    onToggleFavorite: (FoodEntry) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(MascotMood.HUNGRY, size = 96.dp, outfit = outfit)
                Spacer(Modifier.width(8.dp))
                SpeechBubble("What are we eating? Show me, show me~!", Modifier.weight(1f))
            }
        }
        item { MealChips(mealType, onMealType) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigActionButton(
                    "Snap a photo", Icons.Rounded.CameraAlt,
                    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary,
                    onSnap, Modifier.weight(1f),
                )
                BigActionButton(
                    "Pick from gallery", Icons.Rounded.PhotoLibrary,
                    MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                    onPick, Modifier.weight(1f),
                )
            }
        }
        item {
            Column {
                OutlinedTextField(
                    value = hint,
                    onValueChange = { onHint(it.take(120)) },
                    label = { Text("Photo hint (optional)") },
                    placeholder = { Text("e.g. it's a large portion") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (cameraDenied) {
                    Text(
                        "I need camera permission to snap photos. You can still pick one from the gallery!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp),
                    )
                }
            }
        }
        item {
            CozyCard {
                SectionTitle(Icons.Rounded.EditNote, "Describe it")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(400) },
                    placeholder = { Text("2 eggs, toast with butter, coffee with milk") },
                    minLines = 2,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onDescribe(description) },
                    enabled = description.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ask Claude-chan ✨") }
            }
        }
        item {
            CozyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(Icons.Rounded.Add, "Quick add", Modifier.weight(1f))
                    Text("0 tokens", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    IconButton(onClick = { onQuickAddOpen(!quickAddOpen) }) {
                        Icon(if (quickAddOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = "Toggle quick add")
                    }
                }
                AnimatedVisibility(quickAddOpen, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(Modifier.padding(top = 8.dp)) {
                        ManualFoodForm("Log it!", onQuickAdd)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Text(
                    "Recent & favorites",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(recent, key = { it.id }) { entry ->
                RecentFoodRow(entry, onRelog = { onRelog(entry) }, onToggleFavorite = { onToggleFavorite(entry) })
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** A chunky, squishy button: shrinks a bit while pressed and springs back. */
@Composable
private fun BigActionButton(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.45f), label = "press")
    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.height(120.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.height(36.dp).width(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One recent food: tap "+" to log it again instantly (no AI call). */
@Composable
private fun RecentFoodRow(entry: FoodEntry, onRelog: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (entry.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (entry.isFavorite) "Unfavorite" else "Favorite",
                    tint = if (entry.isFavorite) Palette.Butter else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.portion} · ${entry.calories} kcal · P ${entry.proteinG.roundToInt()}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRelog) {
                Icon(Icons.Rounded.AddCircle, contentDescription = "Log ${entry.name} again", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
