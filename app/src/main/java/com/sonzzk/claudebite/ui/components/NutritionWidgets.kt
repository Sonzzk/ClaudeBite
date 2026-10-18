package com.sonzzk.claudebite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.UnitSystem
import com.sonzzk.claudebite.domain.LevelInfo
import com.sonzzk.claudebite.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/** Soft red used for "over target" states. Gentle on purpose: going over is not a failure. */
val SoftRed = Color(0xFFE57373)

/** Color for a Claude-chan health score (1-10): green is great, red is "maybe sometimes". */
fun healthScoreColor(score: Int): Color = when {
    score >= 8 -> Color(0xFF4CAF7A)
    score >= 6 -> Color(0xFF9CCC65)
    score >= 4 -> Palette.Carbs
    else -> SoftRed
}

/** Color for a letter grade, A (green) through F (red). */
fun gradeColor(grade: String): Color = when (grade.trim().uppercase().firstOrNull()) {
    'A' -> Color(0xFF4CAF7A)
    'B' -> Color(0xFF9CCC65)
    'C' -> Palette.Carbs
    'D' -> Color(0xFFF08A4B)
    else -> Color(0xFFE5534B)
}

/** Converts and formats body measurements. We always store metric and only convert for display. */
object UnitFormat {
    private const val LB_PER_KG = 2.20462f

    fun kgToLb(kg: Float): Float = kg * LB_PER_KG
    fun lbToKg(lb: Float): Float = lb / LB_PER_KG
    fun weightUnit(units: UnitSystem): String = if (units == UnitSystem.IMPERIAL) "lb" else "kg"

    /** Weight in the user's unit, without the unit label. */
    fun weightValue(kg: Float, units: UnitSystem): Float = if (units == UnitSystem.IMPERIAL) kgToLb(kg) else kg

    fun weight(kg: Float, units: UnitSystem, decimals: Int = 1): String =
        "%.${decimals}f %s".format(weightValue(kg, units), weightUnit(units))

    fun height(cm: Float, units: UnitSystem): String {
        if (units == UnitSystem.METRIC) return "${cm.roundToInt()} cm"
        val totalInches = (cm / 2.54f).roundToInt()
        return "${totalInches / 12}' ${totalInches % 12}\""
    }

    /** Parses "72.5" or "72,5" (comma decimal locales) into a float. */
    fun parse(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()
}

/** A number that counts smoothly to [value] (from 0 on first show, then between values). */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    prefix: String = "",
    suffix: String = "",
    durationMs: Int = 900,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateIntAsState(
        targetValue = if (started) value else 0,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "counter",
    )
    Text("$prefix$shown$suffix", modifier, style = style, color = color)
}

/**
 * The big calorie gauge: a thick rounded 270-degree arc that fills as you eat.
 * When you go over, a soft red arc grows on top and the center says "kcal over".
 */
@Composable
fun CalorieRing(
    eaten: Int,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    strokeWidth: Dp = 18.dp,
) {
    val safeTarget = target.coerceAtLeast(1)
    val ratio = eaten / safeTarget.toFloat()
    val bouncy = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessVeryLow)
    val fill by animateFloatAsState(ratio.coerceIn(0f, 1f), bouncy, label = "ringFill")
    val overflow by animateFloatAsState((ratio - 1f).coerceIn(0f, 1f), bouncy, label = "ringOverflow")
    val over = eaten > target
    val track = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val topLeft = Offset(sw / 2, sw / 2)
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val stroke = Stroke(width = sw, cap = StrokeCap.Round)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = stroke)
            if (fill > 0.002f) drawArc(primary, 135f, 270f * fill, false, topLeft, arcSize, style = stroke)
            if (overflow > 0.002f) drawArc(SoftRed, 135f, 270f * overflow, false, topLeft, arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedCounter(
                value = abs(target - eaten),
                style = MaterialTheme.typography.headlineMedium,
                color = if (over) SoftRed else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (over) "kcal over" else "kcal left",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) SoftRed else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$eaten / $target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One macro (protein/carbs/fat) as a label, "eaten / target g" and a chunky rounded bar. */
@Composable
fun MacroBar(label: String, eaten: Float, target: Int, color: Color, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = if (target <= 0) 0f else (eaten / target).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "macro",
    )
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "${eaten.roundToInt()} / ${target}g",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Protein, carbs and fat side by side in their signature colors. */
@Composable
fun MacroRow(protein: Float, carbs: Float, fat: Float, targets: DailyTargets, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MacroBar("Protein", protein, targets.proteinG, Palette.Protein, Modifier.weight(1f))
        MacroBar("Carbs", carbs, targets.carbsG, Palette.Carbs, Modifier.weight(1f))
        MacroBar("Fat", fat, targets.fatG, Palette.Fat, Modifier.weight(1f))
    }
}

private const val GLASS_ML = 250

/**
 * A row of water glasses that fill with a wobbly animated wave.
 * Tap any glass for +250 ml, long-press for +500 ml; the undo button removes the last log.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterTracker(
    ml: Int,
    goalMl: Int,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glasses = ceil(goalMl.coerceAtLeast(GLASS_ML) / GLASS_ML.toFloat()).toInt().coerceIn(4, 16)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = Palette.Water)
            Spacer(Modifier.width(6.dp))
            Text("Water", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            AnimatedCounter(
                value = ml,
                suffix = " / $goalMl ml",
                style = MaterialTheme.typography.labelLarge,
                color = if (ml >= goalMl) Palette.Water else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onUndo, enabled = ml > 0) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo last water")
            }
        }
        // All glasses share one row so the card never wraps awkwardly.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(glasses) { index ->
                val fill = ((ml - index * GLASS_ML) / GLASS_ML.toFloat()).coerceIn(0f, 1f)
                WaterGlass(
                    fill = fill,
                    onTap = { onAdd(GLASS_ML) },
                    onLongPress = { onAdd(GLASS_ML * 2) },
                    modifier = Modifier.weight(1f).widthIn(max = 34.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap a glass for +250 ml · hold for +500 ml",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One glass drawn on a Canvas; the water surface is a sine wave that keeps sloshing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WaterGlass(fill: Float, onTap: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(fill, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), label = "glass")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "wavePhase",
    )
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        val w = size.width
        val h = size.height
        val glass = Path().apply {
            moveTo(w * 0.08f, h * 0.04f)
            lineTo(w * 0.92f, h * 0.04f)
            lineTo(w * 0.80f, h * 0.96f)
            lineTo(w * 0.20f, h * 0.96f)
            close()
        }
        if (level > 0.002f) {
            clipPath(glass) {
                val surface = h * 0.96f - h * 0.92f * level
                val amplitude = if (level >= 0.995f) 0f else h * 0.035f
                val wave = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, surface)
                    var x = 0f
                    while (x <= w) {
                        lineTo(x, surface + amplitude * sin(x / w * 2f * PI.toFloat() + phase))
                        x += 2f
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(wave, Palette.Water.copy(alpha = 0.85f))
            }
        }
        drawPath(glass, outline, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** A small pill with an icon and text, e.g. a streak flame or "Lv 3". */
@Composable
fun StatChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Circle with a letter grade that pops in with a springy scale every time the grade changes. */
@Composable
fun GradeBadge(grade: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(grade) {
        scale.snapTo(0f)
        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(gradeColor(grade), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            grade.trim().take(2).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.48f).sp,
        )
    }
}

/** A chunky rounded XP progress bar with a springy fill. */
@Composable
fun XpBar(info: LevelInfo, modifier: Modifier = Modifier, height: Dp = 8.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val progress by animateFloatAsState(
        info.progress.coerceIn(0f, 1f),
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "xp",
    )
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(height)
                .clip(CircleShape)
                .background(color),
        )
    }
}
