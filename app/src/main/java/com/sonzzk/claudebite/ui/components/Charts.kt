package com.sonzzk.claudebite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Animates 0 -> 1 every time [key] changes, so charts "grow" when their data changes. */
@Composable
private fun rememberGrowAnimation(key: Any?): Animatable<Float, *> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    return anim
}

/**
 * Daily calories as rounded bars with a dashed target line.
 * Bars within 5% of the target are orange; bars above are soft red.
 */
@Composable
fun CalorieBarChart(values: List<Int>, target: Int, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val grow = rememberGrowAnimation(values)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val maxValue = maxOf(values.max(), target, 1) * 1.1f
        val slot = size.width / values.size
        val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        values.forEachIndexed { i, kcal ->
            val x = slot * i + (slot - barWidth) / 2
            if (kcal <= 0) {
                drawRoundRect(empty, Offset(x, size.height - 4.dp.toPx()), Size(barWidth, 4.dp.toPx()), CornerRadius(4.dp.toPx()))
            } else {
                val h = size.height * (kcal / maxValue) * grow.value
                val color = if (kcal > target * 1.05f) SoftRed else primary
                drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(barWidth / 3))
            }
        }
        val y = size.height * (1 - target / maxValue)
        drawLine(
            lineColor, Offset(0f, y), Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            cap = StrokeCap.Round,
        )
    }
}

/** Weight over time as a smooth-ish line with dots, plus a dashed mint goal line. Values are in display units. */
@Composable
fun WeightLineChart(values: List<Float>, goal: Float?, modifier: Modifier = Modifier, height: Dp = 170.dp) {
    val grow = rememberGrowAnimation(values)
    val primary = MaterialTheme.colorScheme.primary
    val dotFill = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val all = if (goal != null) values + goal else values
        val minV = all.min() - 1f
        val maxV = all.max() + 1f
        val range = (maxV - minV).coerceAtLeast(0.1f)
        val pad = 8.dp.toPx()
        fun yOf(v: Float) = pad + (size.height - 2 * pad) * (1 - (v - minV) / range)
        fun xOf(i: Int) = if (values.size == 1) size.width / 2 else pad + (size.width - 2 * pad) * i / (values.size - 1)

        if (goal != null) {
            drawLine(
                Palette.Mint, Offset(0f, yOf(goal)), Offset(size.width, yOf(goal)),
                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
        // Only draw the first part of the line while the grow animation runs.
        val visible = ((values.size - 1) * grow.value).toInt() + 1
        val path = Path()
        for (i in 0 until visible) {
            if (i == 0) path.moveTo(xOf(i), yOf(values[i])) else path.lineTo(xOf(i), yOf(values[i]))
        }
        drawPath(path, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (i in 0 until visible) {
            val c = Offset(xOf(i), yOf(values[i]))
            drawCircle(dotFill, radius = 5.dp.toPx(), center = c)
            drawCircle(primary, radius = 5.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** Donut of where calories came from: protein (4 kcal/g), carbs (4) and fat (9). */
@Composable
fun MacroDonut(proteinG: Float, carbsG: Float, fatG: Float, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val grow = rememberGrowAnimation(Triple(proteinG, carbsG, fatG))
    val track = MaterialTheme.colorScheme.surfaceVariant
    val parts = listOf(proteinG * 4 to Palette.Protein, carbsG * 4 to Palette.Carbs, fatG * 9 to Palette.Fat)
    val total = parts.sumOf { it.first.toDouble() }.toFloat()
    Canvas(modifier.size(size)) {
        val sw = 18.dp.toPx()
        val topLeft = Offset(sw / 2, sw / 2)
        val arcSize = Size(this.size.width - sw, this.size.height - sw)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
        if (total <= 0f) return@Canvas
        var start = -90f
        parts.forEach { (kcal, color) ->
            val sweep = 360f * (kcal / total) * grow.value
            if (sweep > 0.5f) drawArc(color, start, (sweep - 2f).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

/**
 * GitHub-style calendar: one column per week (Mon-Sun), orange intensity by calories logged.
 * Empty days are plain; days near the target are the strongest orange.
 */
@Composable
fun StreakHeatmap(
    caloriesByDate: Map<LocalDate, Int>,
    target: Int,
    today: LocalDate,
    modifier: Modifier = Modifier,
    weeks: Int = 10,
) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(weeks) { w ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { d ->
                        val date = firstMonday.plusDays((w * 7 + d).toLong())
                        val kcal = caloriesByDate[date] ?: 0
                        val color = when {
                            date.isAfter(today) -> Color.Transparent
                            kcal <= 0 -> empty
                            else -> primary.copy(alpha = 0.3f + 0.7f * (kcal / target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f))
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(color, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("less ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0f, 0.35f, 0.65f, 1f).forEach { a ->
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (a == 0f) empty else primary.copy(alpha = a), RoundedCornerShape(3.dp)),
                )
                Box(Modifier.size(2.dp))
            }
            Text(" more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tiny legend dot + label, used under charts. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Box(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
