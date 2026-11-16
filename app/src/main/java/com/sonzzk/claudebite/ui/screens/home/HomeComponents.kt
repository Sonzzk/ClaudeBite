package com.sonzzk.claudebite.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    MascotMood.SHY to "Eep! That tickles~",
    MascotMood.HAPPY to "Hehe, hi there!",
    MascotMood.EXCITED to "You're doing amazing today!",
    MascotMood.WAVING to "Hi hi! I'm always here for you~",
    MascotMood.LOVE to "Aww, you're sweet. Let's keep going together!",
    MascotMood.PROUD to "Remember: progress, not perfection!",
    MascotMood.CONFUSED to "Hm? Did you need something?",
    MascotMood.EXCITED to "Let's eat something colorful today!",
    MascotMood.HAPPY to "Have you had some water lately? Tap a glass!",
)

private val pokeTooMuch = listOf(
    "Hey! Stop poking me! >_<",
    "I'm not a button, you know!",
    "Mou~ my halo is getting dizzy!",
    "Poke me one more time and I'm hiding your snacks!",
)

/** Claude-chan (tap her! the Mascot itself does the heart burst) next to the calorie ring, with her line and a chat shortcut. */
@Composable
internal fun HeroCard(state: HomeUiState, onOpenChat: () -> Unit) {
    val snapshot = state.mascotSnapshot
    var pokes by remember { mutableIntStateOf(0) }
    var reaction by remember { mutableStateOf<Pair<MascotMood, String>?>(null) }
    val recentPokes = remember { ArrayDeque<Long>() }
    val feedback = com.sonzzk.claudebite.ui.components.rememberFeedback()
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
                            val now = System.currentTimeMillis()
                            recentPokes.addLast(now)
                            while (recentPokes.isNotEmpty() && now - recentPokes.first() > 3_000) recentPokes.removeFirst()
                            if (recentPokes.size >= 5) {
                                reaction = MascotMood.POUTY to pokeTooMuch[pokes % pokeTooMuch.size]
                                recentPokes.clear()
                                feedback.pout()
                            } else {
                                reaction = pokeReactions[pokes % pokeReactions.size]
                                feedback.pop()
                            }
                            pokes++
                        },
                    )
                }
                CalorieRing(eaten = state.eaten, target = state.budget, size = 150.dp)
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


/** The last two weeks as tappable day chips, newest on the right. Lets you view and log past days. */
@Composable
internal fun DateStrip(selected: java.time.LocalDate, onSelect: (java.time.LocalDate) -> Unit) {
    val today = java.time.LocalDate.now()
    val days = remember(today) { (13 downTo 0).map { today.minusDays(it.toLong()) } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = days.size - 1)
    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(days.size) { i ->
            val d = days[i]
            val isSel = d == selected
            val bg = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier
                    .width(46.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(bg)
                    .clickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (d == today) "Today" else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1,
                )
                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
            }
        }
    }
}

/** Morning check-in: how long and how well you slept, and your mood. Two taps, zero tokens. */
@Composable
internal fun CheckInCard(onSave: (Float?, Int?, Int?) -> Unit) {
    var hours by remember { mutableStateOf<Float?>(null) }
    var mood by remember { mutableStateOf<Int?>(null) }
    HomeCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Morning check-in ☀️", style = MaterialTheme.typography.titleMedium)
            Text("How long did you sleep?", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val opts = listOf(4f, 5f, 6f, 7f, 8f, 9f, 10f)
                items(opts.size) { i ->
                    val h = opts[i]
                    androidx.compose.material3.FilterChip(
                        selected = hours == h,
                        onClick = { hours = h },
                        label = { Text(if (h == 4f) "≤4h" else if (h == 10f) "10h+" else "${h.toInt()}h") },
                    )
                }
            }
            Text("And your mood?", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("😫", "😕", "😐", "🙂", "😄").forEachIndexed { i, e ->
                    androidx.compose.material3.FilterChip(selected = mood == i + 1, onClick = { mood = i + 1 }, label = { Text(e) })
                }
            }
            androidx.compose.material3.Button(
                onClick = {
                    val quality = hours?.let { h -> when { h < 5 -> 1; h < 6 -> 2; h < 7 -> 3; h < 8.5 -> 4; else -> 5 } }
                    onSave(hours, quality, mood)
                },
                enabled = hours != null || mood != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
