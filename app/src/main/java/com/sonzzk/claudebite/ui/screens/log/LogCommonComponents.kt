package com.sonzzk.claudebite.ui.screens.log

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble
import kotlinx.coroutines.delay
import java.io.File

/** "Breakfast" instead of "BREAKFAST", plus a cute emoji. */
internal fun MealType.label(): String = when (this) {
    MealType.BREAKFAST -> "🍳 Breakfast"
    MealType.LUNCH -> "🥗 Lunch"
    MealType.DINNER -> "🍲 Dinner"
    MealType.SNACK -> "🍪 Snack"
}

/** A row of chips to pick the meal type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealChips(selected: MealType, onSelect: (MealType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MealType.entries.forEach { meal ->
            FilterChip(
                selected = meal == selected,
                onClick = { onSelect(meal) },
                label = { Text(meal.label()) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** A big rounded card used for every section of the flow. */
@Composable
internal fun CozyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** A numeric text field that only accepts digits and one decimal point. */
@Composable
internal fun NumberField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() || it == '.' }) onValue(new) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}

/**
 * Manual food form (name, kcal, protein/carbs/fat). Used by Quick add and by "+ add item" on the review.
 * Costs zero tokens: nothing is sent to Claude.
 */
@Composable
internal fun ManualFoodForm(
    submitLabel: String,
    onSubmit: (name: String, calories: Int, protein: Float, carbs: Float, fat: Float) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    val calories = kcal.toFloatOrNull()?.toInt()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            label = { Text("Food name") }, singleLine = true,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        NumberField("Calories (kcal)", kcal, { kcal = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Protein g", protein, { protein = it }, Modifier.weight(1f))
            NumberField("Carbs g", carbs, { carbs = it }, Modifier.weight(1f))
            NumberField("Fat g", fat, { fat = it }, Modifier.weight(1f))
        }
        Button(
            onClick = {
                onSubmit(name.trim(), calories ?: 0, protein.toFloatOrNull() ?: 0f, carbs.toFloatOrNull() ?: 0f, fat.toFloatOrNull() ?: 0f)
                name = ""; kcal = ""; protein = ""; carbs = ""; fat = ""
            },
            enabled = name.isNotBlank() && calories != null,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(submitLabel) }
    }
}

private val thinkingLines = listOf(
    "Hmm, let me look closely~",
    "Counting the calories...",
    "Ooh, that looks yummy!",
    "Checking the protein... 💪",
    "Estimating portions, one sec~",
    "Almost done, promise!",
)

/** Claude-chan thinking hard, with rotating cute lines and a cancel button. */
@Composable
internal fun AnalyzingContent(photoPath: String?, outfit: String, onCancel: () -> Unit) {
    var lineIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1900)
            lineIndex = (lineIndex + 1) % thinkingLines.size
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (photoPath != null) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "Your food photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(16.dp))
        }
        Mascot(MascotMood.THINKING, size = 180.dp, outfit = outfit)
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = thinkingLines[lineIndex],
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "thinkingLine",
        ) { line ->
            Text(line, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(50)))
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** A friendly error card. For a missing API key it explains Settings and offers zero-token Quick add. */
@Composable
internal fun FailedContent(
    failure: LogStage.Failed,
    outfit: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onQuickAdd: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Mascot(MascotMood.WORRIED, size = 150.dp, outfit = outfit)
        SpeechBubble(failure.message)
        if (failure.noApiKey) {
            CozyCard {
                Text("No API key yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "To analyze photos and descriptions I need your Anthropic API key. Open Settings (the gear on the Today screen) and paste it there. " +
                        "Until then, you can still log food by hand: it costs nothing!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onQuickAdd, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) { Text("Quick add by hand") }
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) { Text("Back") }
        } else {
            Button(onClick = onRetry, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            OutlinedButton(onClick = onQuickAdd, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) { Text("Quick add by hand instead") }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

/** A quick happy "logged!" moment before the screen closes. */
@Composable
internal fun SavedContent(outfit: String) {
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Mascot(MascotMood.EXCITED, size = 200.dp, outfit = outfit)
        Spacer(Modifier.height(16.dp))
        Text(
            "Yum! Logged~ ✨",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}
