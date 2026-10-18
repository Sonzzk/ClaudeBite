package com.sonzzk.claudebite.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.sonzzk.claudebite.domain.MascotMood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Claude-chan drawn entirely with Compose Canvas.
 * This is the fallback used when no PNG art exists in res/drawable-nodpi/ (see Mascot.kt).
 * Everything is drawn in "unit" coordinates: 0..1 of the smaller side, so it scales to any size.
 */

private object ArtColors {
    val HairDark = Color(0xFF7A4A2E)
    val Hair = Color(0xFFA0623C)
    val HairLight = Color(0xFFC07A4C)
    val Skin = Color(0xFFFFE8DA)
    val SkinShade = Color(0xFFF6D3C0)
    val EyeDark = Color(0xFF4A2A1C)
    val Iris = Color(0xFFC8643C)
    val IrisLight = Color(0xFFF0A070)
    val Blush = Color(0xFFFF9AAE)
    val Mouth = Color(0xFF8A3A2E)
    val MouthInside = Color(0xFFE0707A)
    val Clip = Color(0xFFD97757)
    val ClipLight = Color(0xFFFFB38F)
}

/** The Canvas-drawn chibi face. Blinks on its own every few seconds. */
@Composable
fun MascotArt(mood: MascotMood, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "blink")
    // 1 = eyes open, dips to 0 briefly for a blink every ~3.6s.
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                1f at 3300
                0.05f at 3420
                1f at 3550
            },
        ),
        label = "blinkValue",
    )
    val sparkleSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "clipSpin",
    )
    Canvas(modifier) {
        val u = size.minDimension
        val origin = Offset((size.width - u) / 2f, (size.height - u) / 2f)
        fun p(x: Float, y: Float) = Offset(origin.x + x * u, origin.y + y * u)
        drawBackHair(::p, u)
        drawFace(::p, u)
        drawBangs(::p, u)
        drawBlush(::p, u, mood)
        drawEyes(::p, u, mood, blink)
        drawBrows(::p, u, mood)
        drawMouth(::p, u, mood)
        // The Claude sparkle hair clip, slowly turning.
        drawSparkle(p(0.76f, 0.24f), 0.085f * u, ArtColors.Clip, points = 8, innerRatio = 0.45f, rotationDeg = sparkleSpin / 8f)
        drawSparkle(p(0.76f, 0.24f), 0.035f * u, ArtColors.ClipLight, points = 8, innerRatio = 0.5f, rotationDeg = sparkleSpin / 8f)
    }
}

private typealias P = (Float, Float) -> Offset

private fun DrawScope.drawBackHair(p: P, u: Float) {
    // Long hair behind the face: a big rounded shape that falls to the shoulders.
    val back = Path().apply {
        moveTo(p(0.12f, 0.50f).x, p(0.12f, 0.50f).y)
        cubicTo(p(0.08f, 0.10f).x, p(0.08f, 0.10f).y, p(0.92f, 0.10f).x, p(0.92f, 0.10f).y, p(0.88f, 0.50f).x, p(0.88f, 0.50f).y)
        cubicTo(p(0.90f, 0.75f).x, p(0.90f, 0.75f).y, p(0.92f, 0.92f).x, p(0.92f, 0.92f).y, p(0.80f, 0.96f).x, p(0.80f, 0.96f).y)
        lineTo(p(0.20f, 0.96f).x, p(0.20f, 0.96f).y)
        cubicTo(p(0.08f, 0.92f).x, p(0.08f, 0.92f).y, p(0.10f, 0.75f).x, p(0.10f, 0.75f).y, p(0.12f, 0.50f).x, p(0.12f, 0.50f).y)
        close()
    }
    drawPath(back, Brush.verticalGradient(listOf(ArtColors.Hair, ArtColors.HairDark), startY = p(0f, 0.1f).y, endY = p(0f, 0.96f).y))
}

private fun DrawScope.drawFace(p: P, u: Float) {
    val tl = p(0.20f, 0.30f)
    drawOval(ArtColors.SkinShade, topLeft = tl + Offset(0f, 0.01f * u), size = Size(0.60f * u, 0.56f * u))
    drawOval(ArtColors.Skin, topLeft = tl, size = Size(0.60f * u, 0.55f * u))
}

private fun DrawScope.drawBangs(p: P, u: Float) {
    // A hair cap over the forehead with scalloped tips (the classic anime bangs).
    val cap = Path().apply {
        moveTo(p(0.17f, 0.52f).x, p(0.17f, 0.52f).y)
        cubicTo(p(0.12f, 0.14f).x, p(0.12f, 0.14f).y, p(0.88f, 0.14f).x, p(0.88f, 0.14f).y, p(0.83f, 0.52f).x, p(0.83f, 0.52f).y)
        // Tips from right to left.
        val tips = listOf(0.83f to 0.52f, 0.72f to 0.44f, 0.62f to 0.47f, 0.50f to 0.41f, 0.40f to 0.47f, 0.29f to 0.43f, 0.17f to 0.52f)
        for (i in 0 until tips.size - 1) {
            val (x1, y1) = tips[i]
            val (x2, y2) = tips[i + 1]
            val mid = p((x1 + x2) / 2f, minOf(y1, y2) - 0.06f)
            quadraticTo(mid.x, mid.y, p(x2, y2).x, p(x2, y2).y)
        }
        close()
    }
    drawPath(cap, Brush.verticalGradient(listOf(ArtColors.HairLight, ArtColors.Hair), startY = p(0f, 0.16f).y, endY = p(0f, 0.50f).y))
    // A soft shine streak on the hair.
    drawArc(
        color = Color.White.copy(alpha = 0.28f), startAngle = 200f, sweepAngle = 70f, useCenter = false,
        topLeft = p(0.28f, 0.20f), size = Size(0.44f * u, 0.30f * u),
        style = Stroke(width = 0.025f * u, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawBlush(p: P, u: Float, mood: MascotMood) {
    val alpha = when (mood) {
        MascotMood.EXCITED, MascotMood.PROUD -> 0.75f
        MascotMood.SLEEPY, MascotMood.WORRIED -> 0.35f
        else -> 0.55f
    }
    listOf(0.30f, 0.70f).forEach { x ->
        drawOval(ArtColors.Blush.copy(alpha = alpha), topLeft = p(x - 0.07f, 0.66f), size = Size(0.14f * u, 0.06f * u))
    }
}

private fun DrawScope.drawEyes(p: P, u: Float, mood: MascotMood, blink: Float) {
    val eyes = listOf(p(0.37f, 0.60f), p(0.63f, 0.60f))
    val lineStroke = Stroke(width = 0.022f * u, cap = StrokeCap.Round)
    when (mood) {
        // Happy closed eyes: ^ ^
        MascotMood.PROUD -> eyes.forEach { c ->
            drawArc(ArtColors.EyeDark, 200f, 140f, false, topLeft = c - Offset(0.06f * u, 0.03f * u), size = Size(0.12f * u, 0.10f * u), style = lineStroke)
        }
        // Sleepy closed eyes: gentle downward curves.
        MascotMood.SLEEPY -> eyes.forEach { c ->
            drawArc(ArtColors.EyeDark, 20f, 140f, false, topLeft = c - Offset(0.06f * u, 0.06f * u), size = Size(0.12f * u, 0.08f * u), style = lineStroke)
        }
        else -> {
            val look = when (mood) {
                MascotMood.THINKING -> Offset(0.018f * u, -0.022f * u)
                MascotMood.HUNGRY -> Offset(0f, 0.01f * u)
                else -> Offset.Zero
            }
            val scale = if (mood == MascotMood.EXCITED) 1.12f else 1f
            val w = 0.105f * u * scale
            val h = 0.14f * u * scale * blink.coerceIn(0.05f, 1f)
            eyes.forEach { c ->
                val tl = Offset(c.x - w / 2, c.y - h / 2)
                drawOval(ArtColors.EyeDark, topLeft = tl, size = Size(w, h))
                if (blink > 0.4f) {
                    // Iris with a warm gradient, then big + small highlights for the "shiny" anime look.
                    val irisTl = tl + Offset(w * 0.12f, h * 0.18f) + look
                    drawOval(
                        Brush.verticalGradient(listOf(ArtColors.Iris, ArtColors.IrisLight), startY = irisTl.y, endY = irisTl.y + h * 0.78f),
                        topLeft = irisTl, size = Size(w * 0.76f, h * 0.76f),
                    )
                    drawOval(ArtColors.EyeDark, topLeft = irisTl + Offset(w * 0.22f, h * 0.2f), size = Size(w * 0.32f, h * 0.36f))
                    if (mood == MascotMood.EXCITED) {
                        drawSparkle(c + look + Offset(-w * 0.12f, -h * 0.14f), w * 0.30f, Color.White, points = 4, innerRatio = 0.35f)
                    } else {
                        drawCircle(Color.White, radius = w * 0.17f, center = c + look + Offset(-w * 0.15f, -h * 0.18f))
                    }
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.08f, center = c + look + Offset(w * 0.18f, h * 0.2f))
                }
                // Top lash line.
                drawArc(ArtColors.EyeDark, 195f, 150f, false, topLeft = tl - Offset(0.01f * u, 0.004f * u), size = Size(w + 0.02f * u, h * 0.9f), style = Stroke(0.016f * u, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawBrows(p: P, u: Float, mood: MascotMood) {
    val stroke = 0.014f * u
    val color = ArtColors.HairDark
    when (mood) {
        MascotMood.WORRIED -> {
            drawLine(color, p(0.31f, 0.49f), p(0.42f, 0.46f), stroke, StrokeCap.Round)
            drawLine(color, p(0.58f, 0.46f), p(0.69f, 0.49f), stroke, StrokeCap.Round)
        }
        MascotMood.THINKING -> {
            drawLine(color, p(0.31f, 0.48f), p(0.42f, 0.48f), stroke, StrokeCap.Round)
            drawLine(color, p(0.58f, 0.46f), p(0.69f, 0.44f), stroke, StrokeCap.Round)
        }
        else -> Unit // Brows hide under the bangs in the other moods.
    }
}

private fun DrawScope.drawMouth(p: P, u: Float, mood: MascotMood) {
    val stroke = Stroke(width = 0.016f * u, cap = StrokeCap.Round)
    when (mood) {
        MascotMood.EXCITED, MascotMood.WAVING -> {
            // Open happy "D" mouth.
            val m = Path().apply {
                moveTo(p(0.45f, 0.715f).x, p(0.45f, 0.715f).y)
                lineTo(p(0.55f, 0.715f).x, p(0.55f, 0.715f).y)
                quadraticTo(p(0.55f, 0.78f).x, p(0.55f, 0.78f).y, p(0.50f, 0.78f).x, p(0.50f, 0.78f).y)
                quadraticTo(p(0.45f, 0.78f).x, p(0.45f, 0.78f).y, p(0.45f, 0.715f).x, p(0.45f, 0.715f).y)
                close()
            }
            drawPath(m, ArtColors.MouthInside)
            drawPath(m, ArtColors.Mouth, style = Stroke(0.01f * u))
        }
        MascotMood.HUNGRY, MascotMood.THINKING -> {
            drawOval(ArtColors.MouthInside, topLeft = p(0.475f, 0.72f), size = Size(0.05f * u, 0.045f * u))
            drawOval(ArtColors.Mouth, topLeft = p(0.475f, 0.72f), size = Size(0.05f * u, 0.045f * u), style = Stroke(0.01f * u))
        }
        MascotMood.WORRIED -> {
            // Little wobbly line.
            val m = Path().apply {
                moveTo(p(0.45f, 0.74f).x, p(0.45f, 0.74f).y)
                quadraticTo(p(0.475f, 0.72f).x, p(0.475f, 0.72f).y, p(0.50f, 0.74f).x, p(0.50f, 0.74f).y)
                quadraticTo(p(0.525f, 0.76f).x, p(0.525f, 0.76f).y, p(0.55f, 0.74f).x, p(0.55f, 0.74f).y)
            }
            drawPath(m, ArtColors.Mouth, style = stroke)
        }
        MascotMood.SLEEPY -> drawOval(ArtColors.Mouth, topLeft = p(0.485f, 0.73f), size = Size(0.03f * u, 0.02f * u), style = stroke)
        else -> {
            // Cat-like "w" smile.
            val m = Path().apply {
                moveTo(p(0.45f, 0.72f).x, p(0.45f, 0.72f).y)
                quadraticTo(p(0.475f, 0.755f).x, p(0.475f, 0.755f).y, p(0.50f, 0.725f).x, p(0.50f, 0.725f).y)
                quadraticTo(p(0.525f, 0.755f).x, p(0.525f, 0.755f).y, p(0.55f, 0.72f).x, p(0.55f, 0.72f).y)
            }
            drawPath(m, ArtColors.Mouth, style = stroke)
        }
    }
}

/** A star/sparkle with [points] tips. 4 points = twinkle, 8 points = the Claude starburst. */
internal fun DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
    points: Int = 4,
    innerRatio: Float = 0.4f,
    rotationDeg: Float = 0f,
    alpha: Float = 1f,
) {
    val path = Path()
    val total = points * 2
    for (i in 0 until total) {
        val r = if (i % 2 == 0) radius else radius * innerRatio
        val angle = (i.toFloat() / total) * 2 * PI - PI / 2
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    rotate(rotationDeg, pivot = center) { drawPath(path, color, alpha = alpha) }
}

/** A small heart centered on [center]; [size] is its width. */
internal fun DrawScope.drawHeart(center: Offset, size: Float, color: Color, alpha: Float = 1f) {
    val s = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y + s * 0.9f)
        cubicTo(center.x - s * 1.4f, center.y - s * 0.1f, center.x - s * 0.6f, center.y - s * 1.2f, center.x, center.y - s * 0.4f)
        cubicTo(center.x + s * 0.6f, center.y - s * 1.2f, center.x + s * 1.4f, center.y - s * 0.1f, center.x, center.y + s * 0.9f)
        close()
    }
    drawPath(path, color, alpha = alpha)
}
