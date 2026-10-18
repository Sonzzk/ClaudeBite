package com.sonzzk.claudebite.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.domain.LevelInfo
import com.sonzzk.claudebite.domain.MascotBrain
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.components.CalorieRing
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble
import com.sonzzk.claudebite.ui.components.StatChip
import com.sonzzk.claudebite.ui.components.XpBar
import com.sonzzk.claudebite.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Hello, night owl"
}

/** Greeting, level chip with a thin XP bar, streak flame and the settings gear. */
@Composable
internal fun HomeTopBar(name: String, level: LevelInfo, streak: Int, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${greeting(LocalTime.now().hour)},",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${name.ifBlank { "friend" }}!",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
        }
        LevelChip(level)
        Spacer(Modifier.width(6.dp))
        StatChip(Icons.Rounded.LocalFireDepartment, "$streak", tint = if (streak > 0) Palette.ClaudeOrange else MaterialTheme.colorScheme.outline)
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun LevelChip(level: LevelInfo) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Lv ${level.level}", style = MaterialTheme.typography.labelLarge)
            XpBar(level, Modifier.width(40.dp), height = 4.dp)
        }
    }
}

/** A few cute things Claude-chan says when you poke her. Costs zero tokens. */
private val pokeReactions = listOf(
    MascotMood.HAPPY to "Hehe, that tickles!",
    MascotMood.EXCITED to "You're doing amazing today!",
    MascotMood.WAVING to "Hi hi! I'm always here for you~",
    MascotMood.PROUD to "Remember: progress, not perfection!",
    MascotMood.EXCITED to "Let's eat something colorful today!",
    MascotMood.HAPPY to "Have you had some water lately? Tap a glass!",
)

/** Claude-chan (tap her!) next to the calorie ring, with her line and a chat shortcut. */
@Composable
internal fun HeroCard(state: HomeUiState, onOpenChat: () -> Unit) {
    val snapshot = state.mascotSnapshot
    var pokes by remember { mutableIntStateOf(0) }
    var reaction by remember { mutableStateOf<Pair<MascotMood, String>?>(null) }
    LaunchedEffect(pokes) {
        if (pokes > 0) {
            delay(3_500)
            reaction = null
        }
    }
    val mood = reaction?.first ?: MascotBrain.mood(snapshot)
    val line = reaction?.second ?: MascotBrain.line(snapshot)

    HomeCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Mascot(
                        mood = mood,
                        size = 130.dp,
                        outfit = state.progression.equippedOutfit,
                        onClick = {
                            reaction = pokeReactions[pokes % pokeReactions.size]
                            pokes++
                        },
                    )
                    HeartBurst(trigger = pokes)
                }
                CalorieRing(eaten = state.eaten, target = state.targets.calories, size = 150.dp)
            }
            AnimatedContent(
                targetState = line,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "mascotLine",
            ) { text -> SpeechBubble(text, Modifier.fillMaxWidth()) }
            FilledTonalButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask me anything")
            }
        }
    }
}

/** Six little hearts that fly up and fade every time [trigger] changes. */
@Composable
private fun HeartBurst(trigger: Int) {
    if (trigger == 0) return
    key(trigger) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        Box(Modifier.size(1.dp), contentAlignment = Alignment.Center) {
            repeat(6) { i ->
                val angle = Math.toRadians(-90.0 + (i - 2.5) * 24.0)
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = if (i % 2 == 0) Palette.SakuraPink else Palette.Protein,
                    modifier = Modifier
                        .offset {
                            val distance = (40 + 50 * progress.value).dp.toPx()
                            IntOffset((cos(angle) * distance).roundToInt(), (sin(angle) * distance).roundToInt())
                        }
                        .graphicsLayer {
                            alpha = 1f - progress.value
                            scaleX = 0.6f + 0.6f * progress.value
                            scaleY = scaleX
                        }
                        .requiredSize(18.dp),
                )
            }
        }
    }
}
