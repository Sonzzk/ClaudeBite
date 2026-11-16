package com.sonzzk.claudebite.ui.screens.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.QuestStatus
import com.sonzzk.claudebite.data.db.GameState
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.PixelSprite
import com.sonzzk.claudebite.ui.components.rememberFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Play tab: coins, daily chest, quests, the weekly boss, and doors to the closet, room, shop, Dex, stickers and mini-game. */
@Composable
fun PlayScreen(onOpen: (String) -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val fb = rememberFeedback()
    val state by remember { c.game.state }.collectAsState(initial = GameState())
    val today = LocalDate.now()
    val quests by remember(today) { c.game.quests(today) }.collectAsState(initial = emptyList())
    var chestResult by remember { mutableStateOf<String?>(null) }
    val chestOpenedToday = state.lastChestDay == today.toString()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Play", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
            CoinPill(state.coins)
        }

        // Daily chest
        GameCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(if (chestOpenedToday) "px_misc_04" else "px_misc_08", Modifier.size(84.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily chest", style = MaterialTheme.typography.titleMedium)
                    Text(
                        chestResult ?: if (chestOpenedToday) "Come back tomorrow for another one~" else "Claude-chan is hiding something in a box...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!chestOpenedToday) {
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = {
                            scope.launch {
                                val r = c.game.openChest() ?: return@launch
                                fb.coin()
                                chestResult = "+${r.first} Bites!" + if (r.second) " And a streak freeze ❄️" else ""
                            }
                        }, shape = RoundedCornerShape(50)) { Text("Open") }
                    }
                }
            }
        }

        // Quests
        val dailies = quests.filter { !it.def.weekly }
        val boss = quests.firstOrNull { it.def.weekly }
        GameCard {
            Text("Today's quests", style = MaterialTheme.typography.titleMedium)
            Text("New ones every morning. Finish them for Bites and XP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            dailies.forEach { q ->
                QuestRow(q) { scope.launch { if (c.game.claim(q)) fb.quest() } }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (boss != null) {
            GameCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(boss.def.bossSprite ?: "px_prop_15", Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Weekly boss", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(boss.def.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                QuestRow(boss, hp = true) { scope.launch { if (c.game.claim(boss)) fb.levelUp() } }
            }
        }

        // Doors
        Text("Claude-chan's world", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Door("Closet", "px_outfit_19", Modifier.weight(1f)) { onOpen(PlayRoutes.CLOSET) }
            Door("Room", "px_misc_00", Modifier.weight(1f)) { onOpen(PlayRoutes.ROOM) }
            Door("Shop", "px_prop_12", Modifier.weight(1f)) { onOpen(PlayRoutes.SHOP) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Door("Food Dex", "px_prop_10", Modifier.weight(1f)) { onOpen(PlayRoutes.DEX) }
            Door("Stickers", "px_emote_00", Modifier.weight(1f)) { onOpen(PlayRoutes.STICKERS) }
            Door("Snack catch", "px_prop_07", Modifier.weight(1f)) { onOpen(PlayRoutes.MINIGAME) }
        }
    }
}

object PlayRoutes {
    const val PLAY = "play"
    const val CLOSET = "closet"
    const val ROOM = "room"
    const val SHOP = "shop"
    const val DEX = "dex"
    const val STICKERS = "stickers"
    const val MINIGAME = "minigame"
}

@Composable
internal fun CoinPill(coins: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelSprite("px_icon_17", Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("$coins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(" Bites", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun GameCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun QuestRow(q: QuestStatus, hp: Boolean = false, onClaim: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!hp) Text(q.def.title, style = MaterialTheme.typography.titleSmall)
                Text(q.def.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                q.claimed -> Text("Done ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                q.done -> Button(onClick = onClaim, shape = RoundedCornerShape(50)) { Text("Claim +${q.def.coins}") }
                else -> Text("+${q.def.coins}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        val frac = q.progress.toFloat() / q.def.goal
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { if (hp) 1f - frac else frac },
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                color = if (hp) Color(0xFFE5534B) else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (hp) "HP ${q.def.goal - q.progress}/${q.def.goal}" else "${q.progress}/${q.def.goal}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Door(label: String, sprite: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelSprite(sprite, Modifier.size(56.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Back-arrow wrapper reused by the game sub-screens. */
@Composable
internal fun OutlinedPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), modifier = modifier) { Text(text) }
}
