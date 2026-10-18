package com.sonzzk.claudebite.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.Reward
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.domain.Outfits
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.random.Random

/** One floating "+10 XP" pill; [id] keeps each pill's animation separate. */
private data class XpPill(val id: Long, val amount: Int)

/** A big celebration card (level up or badge) waiting in line to be shown. */
private data class Celebration(val id: Long, val reward: Reward)

/**
 * Shows the fun stuff from HealthRepository.rewards above every screen:
 * small floating XP pills, a confetti level-up card and badge cards.
 * When nothing is showing it contains no clickable nodes, so touches pass through to the app.
 */
@Composable
fun RewardOverlay(modifier: Modifier = Modifier) {
    val repository = LocalAppContainer.current.repository
    val pills = remember { mutableStateListOf<XpPill>() }
    val queue = remember { mutableStateListOf<Celebration>() }

    LaunchedEffect(repository) {
        repository.rewards.collect { reward ->
            val id = System.nanoTime()
            when (reward) {
                is Reward.XpGained -> pills.add(XpPill(id, reward.amount))
                is Reward.LevelUp, is Reward.BadgeUnlocked -> queue.add(Celebration(id, reward))
            }
        }
    }

    val current = queue.firstOrNull()
    // Auto-dismiss the front card after a few seconds.
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(4200)
            queue.remove(current)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (current?.reward is Reward.LevelUp) {
            key(current.id) { Confetti(Modifier.fillMaxSize()) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f) togetherWith
                        (scaleOut(targetScale = 0.8f) + fadeOut())
                },
                contentKey = { it?.id },
                label = "celebration",
            ) { celebration ->
                if (celebration != null) {
                    CelebrationCard(celebration.reward, onDismiss = { queue.remove(celebration) })
                }
            }
            // XP pills stack under the card, each floating up and fading on its own.
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.TopCenter) {
                pills.forEachIndexed { index, pill ->
                    key(pill.id) {
                        FloatingXpPill(pill.amount, stackIndex = index, onFinished = { pills.remove(pill) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingXpPill(amount: Int, stackIndex: Int, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1600, easing = LinearEasing))
        onFinished()
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = Palette.Butter,
        contentColor = Palette.Ink,
        shadowElevation = 4.dp,
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            translationY = stackIndex * 44.dp.toPx() + 40.dp.toPx() * (1f - p) - 20.dp.toPx()
            alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
            scaleX = pop.value
            scaleY = pop.value
        },
    ) {
        Text(
            "+$amount XP ✨",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CelebrationCard(reward: Reward, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (reward) {
                is Reward.LevelUp -> {
                    Mascot(MascotMood.EXCITED, size = 84.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LEVEL UP!", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        Text("Level ${reward.newLevel}!", style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
                        val outfit = Outfits.all.firstOrNull { it.unlockLevel == reward.newLevel }
                        if (outfit != null) {
                            Spacer(Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(50), color = colors.secondaryContainer) {
                                Text(
                                    "New outfit unlocked: ${outfit.name}!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            Text("You're getting stronger every day~", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                is Reward.BadgeUnlocked -> {
                    Mascot(MascotMood.EXCITED, size = 84.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("BADGE UNLOCKED 🏅", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        Text(reward.badge.title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        Text(reward.badge.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                is Reward.XpGained -> Text("+${reward.amount} XP", textAlign = TextAlign.Center)
            }
        }
    }
}

/** Colorful paper confetti falling across the screen once. */
@Composable
private fun Confetti(modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    val pieces = remember {
        val r = Random(System.nanoTime())
        val palette = listOf(Palette.ClaudeOrange, Palette.SakuraPink, Palette.Mint, Palette.Butter, Palette.Sky, Palette.Lavender)
        List(70) {
            ConfettiPiece(
                x = r.nextFloat(),
                drift = (r.nextFloat() - 0.5f) * 0.3f,
                speed = 0.7f + r.nextFloat() * 0.6f,
                delay = r.nextFloat() * 0.3f,
                spin = (r.nextFloat() - 0.5f) * 1440f,
                color = palette[r.nextInt(palette.size)],
                w = 6f + r.nextFloat() * 6f,
                h = 10f + r.nextFloat() * 8f,
            )
        }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    Canvas(modifier) {
        val dp = density
        pieces.forEach { piece ->
            val t = ((progress.value - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach
            val y = -20f * dp + (size.height + 40f * dp) * t * piece.speed
            val x = size.width * piece.x + size.width * piece.drift * t
            val alpha = if (t > 0.85f) (1f - t) / 0.15f else 1f
            val center = Offset(x, y)
            rotate(piece.spin * t, pivot = center) {
                drawRect(
                    color = piece.color,
                    topLeft = Offset(x - piece.w * dp / 2, y - piece.h * dp / 2),
                    size = Size(piece.w * dp, piece.h * dp * (0.5f + 0.5f * kotlin.math.abs(kotlin.math.cos(t * 12f)))),
                    alpha = alpha,
                )
            }
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val drift: Float,
    val speed: Float,
    val delay: Float,
    val spin: Float,
    val color: androidx.compose.ui.graphics.Color,
    val w: Float,
    val h: Float,
)
