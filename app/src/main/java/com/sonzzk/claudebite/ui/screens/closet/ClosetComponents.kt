package com.sonzzk.claudebite.ui.screens.closet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonzzk.claudebite.domain.Badge
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.domain.Outfit
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Five hearts that fill up with Claude-chan's affection (0-100). Logging consistently raises it. */
@Composable
internal fun AffectionMeter(affection: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { i ->
                val fill = ((affection - i * 20) / 20f).coerceIn(0f, 1f)
                val scale by animateFloatAsState(
                    if (fill >= 1f) 1.1f else 1f,
                    spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow),
                    label = "heart",
                )
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = Palette.SakuraPink, modifier = Modifier.size(28.dp))
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Palette.Protein,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                alpha = fill
                                scaleX = scale * (0.4f + 0.6f * fill)
                                scaleY = scaleX
                            },
                    )
                }
            }
        }
        Text(
            when {
                affection >= 90 -> "Claude-chan adores you!"
                affection >= 60 -> "Claude-chan is really fond of you"
                affection >= 30 -> "Claude-chan likes spending time with you"
                else -> "Claude-chan misses you... log a meal together?"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** One outfit: a mini Claude-chan wearing it, or a greyed-out locked card. */
@Composable
internal fun OutfitCard(outfit: Outfit, unlocked: Boolean, equipped: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        if (equipped) 1.04f else 1f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "outfitScale",
    )
    Card(
        onClick = onClick,
        enabled = unlocked,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (equipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (equipped) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.alpha(if (unlocked) 1f else 0.3f)) {
                    Mascot(mood = MascotMood.HAPPY, size = 96.dp, outfit = outfit.id)
                }
                if (!unlocked) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Locked", modifier = Modifier.padding(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(outfit.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(
                when {
                    equipped -> "Wearing now ✨"
                    unlocked -> "Tap to wear"
                    else -> "Unlocks at Lv ${outfit.unlockLevel}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (equipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Badge.emoji() = when (this) {
    Badge.FIRST_BITE -> "🍙"
    Badge.SHUTTERBUG -> "📸"
    Badge.STREAK_3 -> "🔥"
    Badge.STREAK_7 -> "☄️"
    Badge.STREAK_30 -> "👑"
    Badge.HYDRATED -> "💧"
    Badge.GREEN_PLATE -> "🥦"
    Badge.ON_TARGET -> "🎯"
    Badge.PROTEIN_PRO -> "💪"
    Badge.CHATTY -> "💕"
    Badge.WEIGH_IN -> "⚖️"
}

private val badgeDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

/** A badge: colorful with its unlock date, or a faded silhouette with how to earn it. */
@Composable
internal fun BadgeTile(badge: Badge, unlockedAt: Long?, modifier: Modifier = Modifier) {
    val unlocked = unlockedAt != null
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(
                        if (unlocked) Palette.Butter.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge.emoji(), fontSize = 26.sp, modifier = Modifier.alpha(if (unlocked) 1f else 0.25f))
                if (!unlocked) Icon(Icons.Rounded.Lock, contentDescription = "Locked", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(badge.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
            Text(
                if (unlockedAt != null) {
                    Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(badgeDate)
                } else {
                    badge.description
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
            )
        }
    }
}

/** Sparkles that shoot out in a circle whenever [trigger] changes (used when equipping an outfit). */
@Composable
internal fun SparkleBurst(trigger: Int) {
    if (trigger == 0) return
    key(trigger) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        Box(Modifier.size(1.dp), contentAlignment = Alignment.Center) {
            repeat(8) { i ->
                val angle = Math.toRadians(i * 45.0)
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (i % 2 == 0) Palette.Butter else Palette.ClaudeOrange,
                    modifier = Modifier
                        .offset {
                            val distance = (30 + 90 * progress.value).dp.toPx()
                            IntOffset((cos(angle) * distance).roundToInt(), (sin(angle) * distance).roundToInt())
                        }
                        .graphicsLayer {
                            alpha = 1f - progress.value
                            rotationZ = 180f * progress.value
                            scaleX = 0.5f + progress.value
                            scaleY = scaleX
                        }
                        .requiredSize(22.dp),
                )
            }
        }
    }
}
