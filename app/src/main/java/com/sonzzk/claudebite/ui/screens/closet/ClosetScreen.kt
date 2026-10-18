package com.sonzzk.claudebite.ui.screens.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonzzk.claudebite.domain.Badge
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.domain.Outfits
import com.sonzzk.claudebite.ui.appViewModel
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.XpBar
import kotlinx.coroutines.delay

/** Claude-chan's closet: dress her up with outfits unlocked by leveling, and admire your badges. */
@Composable
fun ClosetScreen() {
    val vm = appViewModel { c -> ClosetViewModel(c.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var sparkles by remember { mutableIntStateOf(0) }
    var excited by remember { mutableIntStateOf(0) }
    LaunchedEffect(excited) {
        if (excited > 0) {
            delay(2_000)
            excited = 0
        }
    }
    val level = state.level
    val equipped = state.progression.equippedOutfit

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Claude-chan's closet", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Mascot(
                        mood = if (excited > 0) MascotMood.EXCITED else MascotMood.HAPPY,
                        size = 200.dp,
                        outfit = equipped,
                        onClick = { excited++ },
                    )
                    SparkleBurst(trigger = sparkles)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    Outfits.all.firstOrNull { it.id == equipped }?.name ?: "Classic Hoodie",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                AffectionMeter(state.progression.affection)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lv ${level.level}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${level.xpIntoLevel} / ${level.xpForNextLevel} XP to Lv ${level.level + 1}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                XpBar(level, Modifier.fillMaxWidth(), height = 12.dp)
            }
        }

        Text("Outfits", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        Outfits.all.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { outfit ->
                    OutfitCard(
                        outfit = outfit,
                        unlocked = state.isUnlocked(outfit),
                        equipped = outfit.id == equipped,
                        onClick = {
                            if (vm.equip(outfit)) {
                                sparkles++
                                excited++
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Text(
            "Badges · ${state.unlockedBadges.size}/${Badge.entries.size}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Badge.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { badge ->
                    BadgeTile(badge, state.unlockedBadges[badge.name], Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
