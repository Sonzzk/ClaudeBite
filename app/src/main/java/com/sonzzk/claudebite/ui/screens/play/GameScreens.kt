package com.sonzzk.claudebite.ui.screens.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.db.DexEntry
import com.sonzzk.claudebite.data.db.GameState
import com.sonzzk.claudebite.domain.Letter
import com.sonzzk.claudebite.domain.Letters
import com.sonzzk.claudebite.domain.Shop
import com.sonzzk.claudebite.domain.ShopItem
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.PixelSprite
import com.sonzzk.claudebite.ui.components.rememberFeedback
import com.sonzzk.claudebite.ui.screens.health.SubScreen
import kotlinx.coroutines.launch

// ---------------- Shop ----------------

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val fb = rememberFeedback()
    val state by remember { c.game.state }.collectAsState(initial = GameState())
    val owned by remember { c.game.inventory }.collectAsState(initial = emptySet())
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val items = when (tab) {
        0 -> Shop.decor
        1 -> Shop.stickers
        else -> listOf(Shop.freeze)
    }
    SubScreen("Coin shop", onBack, actions = { CoinPill(state.coins, Modifier.padding(end = 12.dp)) }) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Room decor", "Stickers", "Extras").forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
            }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                val have = item.id in owned
                ShopTile(item, have, extra = if (item.kind == ShopItem.Kind.FREEZE) "You have ${state.streakFreezes}/2" else null) {
                    scope.launch {
                        if (c.game.buy(item)) {
                            fb.coin()
                            message = "Bought ${item.name}! ✨"
                        } else {
                            message = if (state.coins < item.price) "Not enough Bites yet. Quests and the daily chest give more~" else "You can't hold more of that."
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopTile(item: ShopItem, owned: Boolean, extra: String?, onBuy: () -> Unit) {
    GameCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelSprite(item.sprite, Modifier.size(56.dp))
            Spacer(Modifier.height(4.dp))
            Text(item.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            extra?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            if (owned) {
                Text("Owned ✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onBuy, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("${item.price} Bites") }
            }
        }
    }
}

// ---------------- Room ----------------

/** Claude-chan's room: a 3x3 grid. The middle is hers; the other 8 spots hold decorations you bought. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(onBack: () -> Unit, onShop: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val fb = rememberFeedback()
    val state by remember { c.game.state }.collectAsState(initial = GameState())
    val owned by remember { c.game.inventory }.collectAsState(initial = emptySet())
    val layout = c.game.layout(state)
    var picking by remember { mutableStateOf<Int?>(null) }
    val decor = Shop.decor.filter { it.id in owned }

    SubScreen("Claude-chan's room", onBack) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFFFFE3CF), 0.62f to Color(0xFFFFD2B3),
                            0.62f to Color(0xFFC98B62), 1f to Color(0xFFB0744E),
                        ),
                    ),
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    for (row in 0 until 3) {
                        Row(Modifier.weight(1f)) {
                            for (col in 0 until 3) {
                                val slot = row * 3 + col
                                Box(
                                    Modifier.weight(1f).fillMaxSize().padding(4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(enabled = slot != 4) { picking = slot },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (slot == 4) {
                                        PixelSprite("px_misc_00", Modifier.fillMaxSize())
                                    } else {
                                        val id = layout[slot]
                                        val item = id?.let { Shop.byId(it) }
                                        if (item != null) {
                                            PixelSprite(item.sprite, Modifier.fillMaxSize(0.8f))
                                        } else {
                                            Box(
                                                Modifier.size(28.dp).border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) { Text("+", color = Color.White.copy(alpha = 0.8f)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (decor.isEmpty()) "The room is a bit empty... Buy decorations in the shop with Bites from quests!"
                else "Tap a spot to put something there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onShop, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) { Text("Go to the shop") }
        }
    }

    val slot = picking
    if (slot != null) {
        ModalBottomSheet(onDismissRequest = { picking = null }) {
            Column(Modifier.padding(16.dp)) {
                Text("Place something", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (decor.isEmpty()) Text("You don't own any decorations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(320.dp),
                ) {
                    if (layout[slot] != null) {
                        item(span = { GridItemSpan(4) }) {
                            TextButton(onClick = { scope.launch { c.game.place(slot, null) }; picking = null }) { Text("Clear this spot") }
                        }
                    }
                    items(decor, key = { it.id }) { item ->
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                scope.launch { c.game.place(slot, item.id); fb.pop() }
                                picking = null
                            }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PixelSprite(item.sprite, Modifier.size(48.dp))
                            Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Food Dex ----------------

@Composable
fun DexScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val dex by remember { c.game.dex }.collectAsState(initial = emptyList())
    var sortByCount by remember { mutableStateOf(false) }
    val list = if (sortByCount) dex.sortedByDescending { it.timesLogged } else dex
    val next = listOf(10, 25, 50, 100, 200, 500).firstOrNull { it > dex.size } ?: (dex.size + 100)
    SubScreen("Food Dex", onBack) {
        GameCard(Modifier.padding(horizontal = 16.dp), container = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite("px_prop_10", Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${dex.size} foods discovered", style = MaterialTheme.typography.titleMedium)
                    Text("Next milestone: $next. Every new food you log gets an entry.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !sortByCount, onClick = { sortByCount = false }, label = { Text("Newest") })
            FilterChip(selected = sortByCount, onClick = { sortByCount = true }, label = { Text("Most eaten") })
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list.size) { i -> DexTile(dex.size - dex.indexOf(list[i]), list[i]) }
        }
    }
}

@Composable
private fun DexTile(number: Int, e: DexEntry) {
    GameCard {
        Text("#%03d".format(number), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(e.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text("Eaten ${e.timesLogged}×", style = MaterialTheme.typography.bodySmall)
        if (e.bestScore > 0) Text("Best score ${e.bestScore}/10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text("Found ${e.firstDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- Sticker book & letters ----------------

@Composable
fun StickersScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val state by remember { c.game.state }.collectAsState(initial = GameState())
    val owned by remember { c.game.inventory }.collectAsState(initial = emptySet())
    val letters = c.game.unlockedLetters(state)
    var reading by remember { mutableStateOf<Letter?>(null) }
    val stickersOwned = Shop.stickers.count { it.id in owned }
    SubScreen("Stickers & letters", onBack) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                Text("Sticker book  $stickersOwned/${Shop.stickers.size}", style = MaterialTheme.typography.titleMedium)
            }
            items(Shop.stickers, key = { it.id }) { s ->
                val have = s.id in owned
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelSprite(s.sprite, Modifier.fillMaxSize().padding(6.dp).alpha(if (have) 1f else 0.12f))
                    if (!have) Text("?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(span = { GridItemSpan(3) }) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("Letters from Claude-chan  ${letters.size}/${Letters.all.size}", style = MaterialTheme.typography.titleMedium)
                    Text("Each letter also comes with a free sticker.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(Letters.all, key = { it.id }, span = { GridItemSpan(3) }) { l ->
                val open = l.id in letters
                GameCard(Modifier.clickable(enabled = open) { reading = l }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (open) "💌" else "🔒", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (open) l.title else "Sealed letter", style = MaterialTheme.typography.titleSmall)
                            Text(if (open) "Tap to read" else l.unlockHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    reading?.let { l ->
        AlertDialog(
            onDismissRequest = { reading = null },
            icon = { PixelSprite("px_portrait", Modifier.size(72.dp)) },
            title = { Text(l.title) },
            text = { Text(l.body, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { reading = null }) { Text("♡") } },
        )
    }
}
