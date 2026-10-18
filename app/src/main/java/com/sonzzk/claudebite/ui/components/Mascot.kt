package com.sonzzk.claudebite.ui.components

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.theme.Palette
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Finds Claude-chan's PNG art by name. Names are built at runtime (outfit + mood), so the
 * resource shrinker can't see them; res/raw/keep.xml tells it to keep every `mascot_*` drawable.
 * Results are cached because getIdentifier is slow-ish (it does a string lookup).
 */
private object MascotArtLookup {
    private val cache = ConcurrentHashMap<String, Int>()

    /** Returns a drawable id, or 0 when no PNG exists and the Canvas art should be used. */
    @SuppressLint("DiscouragedApi")
    fun resolve(resources: Resources, packageName: String, outfit: String, mood: MascotMood): Int {
        val m = mood.name.lowercase()
        val o = outfit.lowercase()
        return cache.getOrPut("$o/$m") {
            listOf("mascot_${o}_$m", "mascot_classic_$m", "mascot_${o}_happy", "mascot_classic_happy")
                .firstNotNullOfOrNull { name ->
                    resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
                } ?: 0
        }
    }
}

/**
 * Animated Claude-chan.
 * - idles with a gentle breathing bob
 * - crossfades and does a little squash-and-stretch hop when [mood] changes
 * - bounces and bursts hearts/sparkles when tapped
 * - shows orbiting sparkles and "..." while THINKING
 */
@Composable
fun Mascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    outfit: String = "classic",
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Idle breathing: 0..1..0 forever.
    val idle = rememberInfiniteTransition(label = "idle")
    val breath by idle.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )

    // Mood-change hop: runs 0 -> 1 each time the mood changes (but not on first show).
    val hop = remember { Animatable(1f) }
    var hasShownMood by remember { mutableStateOf(false) }
    LaunchedEffect(mood) {
        if (hasShownMood) {
            hop.snapTo(0f)
            hop.animateTo(1f, tween(520, easing = LinearEasing))
        } else {
            hasShownMood = true
        }
    }

    // Tap bounce scale and particle bursts (each burst has a unique id).
    val tapScale = remember { Animatable(1f) }
    val bursts = remember { mutableStateListOf<Long>() }

    val density = LocalDensity.current
    val bobPx = with(density) { 4.dp.toPx() }
    val hopPx = with(density) { (size * 0.12f).toPx() }

    val hopSin = sin(PI * hop.value).toFloat()
    val squash = sin(2 * PI * hop.value).toFloat() * (1f - hop.value) // stretch up, then squash on landing

    Box(
        modifier
            .size(size)
            .semantics { contentDescription = "Claude-chan looking ${mood.name.lowercase()}" }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        bursts.add(System.nanoTime())
                        scope.launch {
                            tapScale.snapTo(0.86f)
                            tapScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
                        }
                        onClick()
                    }
                } else Modifier
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f) // squash from the feet
                    translationY = -bobPx * breath - hopPx * hopSin
                    scaleX = tapScale.value * (1f - 0.08f * squash) * (1f + 0.008f * breath)
                    scaleY = tapScale.value * (1f + 0.10f * squash) * (1f + 0.018f * breath)
                },
        ) {
            Crossfade(targetState = mood, animationSpec = tween(260), label = "moodFade") { m ->
                val resId = remember(outfit, m) {
                    MascotArtLookup.resolve(context.resources, context.packageName, outfit, m)
                }
                if (resId != 0) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MascotArt(m, Modifier.fillMaxSize())
                }
            }
        }

        if (mood == MascotMood.THINKING) ThinkingEffect(Modifier.fillMaxSize())

        bursts.forEach { id ->
            key(id) { ParticleBurst(seed = id, onFinished = { bursts.remove(id) }, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/** Sparkles orbiting the head and three bouncing dots, shown while Claude-chan thinks. */
@Composable
private fun ThinkingEffect(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "thinking")
    val orbit by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "orbit")
    val dots by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dots")
    Canvas(modifier) {
        val u = size.minDimension
        val c = Offset(size.width / 2f, size.height * 0.42f)
        repeat(3) { i ->
            val a = 2 * PI * (orbit + i / 3f)
            val pos = Offset(c.x + (cos(a) * u * 0.46f).toFloat(), c.y + (sin(a) * u * 0.14f).toFloat() - u * 0.22f)
            drawSparkle(pos, u * 0.035f, Palette.Butter, points = 4, rotationDeg = orbit * 360f)
        }
        // "..." in a thought puff at the top right.
        val base = Offset(size.width * 0.86f, size.height * 0.12f)
        drawCircle(Palette.Ivory, radius = u * 0.11f, center = base)
        drawCircle(Palette.Ivory, radius = u * 0.025f, center = base + Offset(-u * 0.09f, u * 0.12f))
        repeat(3) { i ->
            val phase = ((dots - i).let { if (it < 0) it + 3 else it }).coerceIn(0f, 1f)
            val lift = sin(PI * phase).toFloat() * u * 0.025f
            drawCircle(Palette.ClaudeOrange, radius = u * 0.017f, center = base + Offset((i - 1) * u * 0.05f, -lift))
        }
    }
}

/** One tap burst: hearts and sparkles float up, drift sideways and fade out. */
@Composable
private fun ParticleBurst(seed: Long, onFinished: () -> Unit, modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    val particles = remember(seed) {
        val r = Random(seed)
        List(9) {
            BurstParticle(
                startX = 0.3f + r.nextFloat() * 0.4f,
                drift = (r.nextFloat() - 0.5f) * 0.6f,
                rise = 0.35f + r.nextFloat() * 0.35f,
                size = 0.06f + r.nextFloat() * 0.05f,
                heart = r.nextBoolean(),
                delay = r.nextFloat() * 0.25f,
            )
        }
    }
    LaunchedEffect(seed) {
        progress.animateTo(1f, tween(1300, easing = LinearEasing))
        onFinished()
    }
    Canvas(modifier) {
        val u = size.minDimension
        particles.forEach { pt ->
            val local = ((progress.value - pt.delay) / (1f - pt.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val x = size.width * pt.startX + pt.drift * u * local
            val y = size.height * 0.45f - pt.rise * u * local
            val alpha = (1f - local) * (if (local < 0.15f) local / 0.15f else 1f)
            val center = Offset(x, y)
            if (pt.heart) drawHeart(center, pt.size * u, Palette.SakuraPink, alpha)
            else drawSparkle(center, pt.size * u * 0.6f, Palette.Butter, points = 4, rotationDeg = local * 180f, alpha = alpha)
        }
    }
}

private data class BurstParticle(
    val startX: Float,
    val drift: Float,
    val rise: Float,
    val size: Float,
    val heart: Boolean,
    val delay: Float,
)

/** Rounded bubble with a little tail on the left, pointing at Claude-chan. */
private class BubbleShape(private val tailWidth: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            addRoundRect(RoundRect(tailWidth, 0f, size.width, size.height, CornerRadius(corner, corner)))
            val tailTop = minOf(corner * 0.6f, size.height / 2f - tailWidth)
            moveTo(tailWidth + 1f, tailTop)
            lineTo(0f, tailTop + tailWidth * 0.9f)
            lineTo(tailWidth + 1f, tailTop + tailWidth * 1.8f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Claude-chan's speech bubble: pops in and types its text out letter by letter.
 * The hidden part of the text is drawn transparent so the bubble never changes size while typing.
 */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    var visibleChars by remember { mutableStateOf(0) }
    val pop = remember { Animatable(0.85f) }
    LaunchedEffect(text) {
        visibleChars = 0
        launch {
            pop.snapTo(0.85f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        // Speed up for long texts so the user never waits more than ~1.5s.
        val step = (1500L / text.length.coerceAtLeast(1)).coerceIn(8L, 28L)
        while (visibleChars < text.length) {
            delay(step)
            visibleChars++
        }
    }
    val density = LocalDensity.current
    val shape = remember(density) { with(density) { BubbleShape(10.dp.toPx(), 20.dp.toPx()) } }
    val colors = MaterialTheme.colorScheme
    val shown = text.take(visibleChars)
    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            transformOrigin = TransformOrigin(0f, 0.3f)
        },
        shape = shape,
        color = colors.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, colors.primary.copy(alpha = 0.35f)),
    ) {
        Text(
            text = buildAnnotatedString {
                append(shown)
                withStyle(SpanStyle(color = Color.Transparent)) { append(text.drop(visibleChars)) }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        )
    }
}
