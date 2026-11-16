package com.sonzzk.claudebite.ui.screens.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonzzk.claudebite.data.db.GameState
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.PixelSprite
import com.sonzzk.claudebite.ui.components.rememberFeedback
import com.sonzzk.claudebite.ui.screens.health.SubScreen
import kotlin.math.abs
import kotlin.random.Random

/** One falling thing. x and y are fractions of the play field (0..1). */
private data class Drop(val id: Long, val x: Float, val y: Float, val emoji: String, val kind: Kind, val speed: Float) {
    enum class Kind { FOOD, STAR, ROCK }
}

private val foods = listOf("🍎", "🥦", "🥕", "🍓", "🥑", "🍌", "🍇", "🍙", "🥚", "🐟", "🍠", "🫐")

/**
 * Snack catch: drag Claude-chan left and right to catch falling food (+1) and stars (+5).
 * Rocks cost a heart; three hearts per round. The first 3 rounds a day pay Bites.
 */
@Composable
fun MiniGameScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val fb = rememberFeedback()
    val density = LocalDensity.current
    val state by remember { c.game.state }.collectAsState(initial = GameState())

    var running by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var hearts by remember { mutableIntStateOf(3) }
    var playerX by remember { mutableFloatStateOf(0.5f) }
    var drops by remember { mutableStateOf(listOf<Drop>()) }
    var field by remember { mutableStateOf(IntSize.Zero) }
    var reward by remember { mutableStateOf<Int?>(null) }
    var flash by remember { mutableStateOf("") }
    var round by remember { mutableIntStateOf(0) }

    fun start() {
        score = 0; hearts = 3; drops = emptyList(); playerX = 0.5f; over = false; reward = null; running = true; round++
    }

    // Keyed on the round number (not on running), so finishing a round doesn't cancel the payout below.
    LaunchedEffect(round) {
        if (round == 0) return@LaunchedEffect
        var last = 0L
        var spawnIn = 0f
        var elapsed = 0f
        var nextId = 0L
        while (running) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                elapsed += dt
                val difficulty = 1f + elapsed / 25f
                spawnIn -= dt
                val list = ArrayList<Drop>(drops.size + 1)
                if (spawnIn <= 0f) {
                    spawnIn = (0.9f / difficulty).coerceAtLeast(0.28f)
                    val roll = Random.nextInt(100)
                    val kind = when {
                        roll < 6 -> Drop.Kind.STAR
                        roll < 6 + (18 + elapsed).toInt().coerceAtMost(35) -> Drop.Kind.ROCK
                        else -> Drop.Kind.FOOD
                    }
                    val emoji = when (kind) {
                        Drop.Kind.STAR -> "⭐"
                        Drop.Kind.ROCK -> "🪨"
                        Drop.Kind.FOOD -> foods.random()
                    }
                    list += Drop(nextId++, Random.nextFloat() * 0.84f + 0.08f, -0.08f, emoji, kind, (0.28f + Random.nextFloat() * 0.12f) * difficulty)
                }
                for (d in drops) {
                    val y = d.y + d.speed * dt
                    val caught = y in 0.78f..0.9f && abs(d.x - playerX) < 0.11f
                    when {
                        caught -> when (d.kind) {
                            Drop.Kind.FOOD -> { score += 1; flash = "+1" }
                            Drop.Kind.STAR -> { score += 5; flash = "+5 ✨"; fb.coin() }
                            Drop.Kind.ROCK -> { hearts -= 1; flash = "Ouch!"; fb.pout() }
                        }
                        y > 1.05f -> Unit
                        else -> list += d.copy(y = y)
                    }
                }
                drops = list
                if (hearts <= 0) {
                    running = false
                    over = true
                }
            }
        }
        if (over) {
            val coins = c.game.finishMiniGame(score)
            reward = coins
            if (coins > 0) fb.success()
        }
    }

    SubScreen("Snack catch", onBack, actions = { CoinPill(state.coins, Modifier.padding(end = 12.dp)) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Score $score", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("❤️".repeat(hearts.coerceAtLeast(0)) + "🤍".repeat((3 - hearts).coerceAtLeast(0)))
            Spacer(Modifier.size(12.dp))
            Text("Best ${maxOf(state.bestMiniGame, if (over) score else 0)}", style = MaterialTheme.typography.labelLarge)
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFBFE3FF), Color(0xFFFFE6D1))))
                .onSizeChanged { field = it }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (size.width > 0) playerX = (playerX + drag.x / size.width).coerceIn(0.06f, 0.94f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> if (size.width > 0) playerX = (pos.x / size.width).coerceIn(0.06f, 0.94f) }
                },
        ) {
            val w = field.width.toFloat()
            val h = field.height.toFloat()
            val itemPx = with(density) { 36.dp.toPx() }
            drops.forEach { d ->
                Text(
                    d.emoji,
                    fontSize = 30.sp,
                    modifier = Modifier.offset { IntOffset((d.x * w - itemPx / 2).toInt(), (d.y * h).toInt()) },
                )
            }
            val playerPx = with(density) { 72.dp.toPx() }
            PixelSprite(
                "px_expr_00",
                Modifier
                    .size(72.dp)
                    .offset { IntOffset((playerX * w - playerPx / 2).toInt(), (0.82f * h).toInt()) },
            )
            if (running && flash.isNotEmpty()) {
                Text(flash, style = MaterialTheme.typography.titleLarge, color = Color(0xFF6B3E26), modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            }
            if (!running) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelSprite(if (over) "px_expr_05" else "px_pose_00", Modifier.size(96.dp))
                    if (over) {
                        Text("Round over! You caught $score.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (val r = reward) {
                                null -> "Counting..."
                                0 -> "No Bites this time (3 paid rounds a day), but it still counts for your best!"
                                else -> "+$r Bites for you~"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text("Catch the food and stars, dodge the rocks!", style = MaterialTheme.typography.titleMedium)
                        Text("Drag or tap to move Claude-chan.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { start() }, shape = RoundedCornerShape(50)) { Text(if (over) "Play again" else "Start") }
                }
            }
        }
    }
}
