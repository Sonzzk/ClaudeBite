package com.sonzzk.claudebite.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.components.Mascot
import kotlin.math.PI
import kotlin.math.sin

/** The user's message: right side, Claude orange. */
@Composable
internal fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(start = 56.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

/**
 * Claude-chan's message: left side with a tiny avatar, simple markdown, and the model that answered
 * underneath (so the user can see the router picking cheap Haiku vs. smarter Sonnet).
 */
@Composable
internal fun AssistantBubble(text: String, model: String?, outfit: String, content: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val styled = remember(text, colors.primary) { renderMarkdown(text, colors.primary) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Mascot(MascotMood.HAPPY, size = 36.dp, outfit = outfit)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.padding(end = 40.dp)) {
            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp),
                color = colors.surface,
                contentColor = colors.onSurface,
                shadowElevation = 1.dp,
            ) {
                if (content != null) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { content() }
                } else {
                    Text(styled, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            if (model != null) {
                Text(
                    shortModelName(model),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                )
            }
        }
    }
}

/** "claude-haiku-4-5-20251001" -> "haiku". Unknown names are shown as they are. */
internal fun shortModelName(model: String): String {
    val lower = model.lowercase()
    return listOf("haiku", "sonnet", "opus").firstOrNull { it in lower } ?: model
}

/** Three bouncing dots while Claude-chan is typing but no text has arrived yet. */
@Composable
internal fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "typingPhase")
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.padding(vertical = 6.dp).size(width = 44.dp, height = 14.dp)) {
        val r = size.height / 4f
        repeat(3) { i ->
            val local = ((phase - i * 0.18f) % 1f + 1f) % 1f
            val lift = if (local < 0.5f) sin(local * 2 * PI).toFloat() * size.height * 0.3f else 0f
            drawCircle(color, radius = r, center = Offset(r + i * (size.width - 2 * r) / 2f, size.height / 2f - lift))
        }
    }
}

/** Friendly error card in the chat, with retry (or Settings advice when there's no API key). */
@Composable
internal fun ChatFailureCard(failure: ChatFailure, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(MascotMood.WORRIED, size = 64.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(failure.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (failure.noApiKey) {
                    Text(
                        "Your Anthropic API key goes in Settings (the gear on the Today screen). Food logging by hand still works without it!",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!failure.noApiKey) {
                        Button(onClick = onRetry, shape = RoundedCornerShape(50)) { Text("Try again") }
                    }
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
        }
    }
}

private val inlinePattern = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""")

/**
 * Tiny markdown renderer for chat replies (no extra library needed):
 * `# headings`, `- bullets`, `1. numbered lists`, `**bold**`, `*italic*` and `code`.
 */
internal fun renderMarkdown(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.trimEnd().lines()
    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trimStart()
        val heading = Regex("^(#{1,6})\\s+(.*)").find(trimmed)
        val bullet = Regex("^[-*•+]\\s+(.*)").find(trimmed)
        when {
            heading != null -> {
                val level = heading.groupValues[1].length
                val size = when (level) { 1 -> 20.sp; 2 -> 18.sp; else -> 16.sp }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size, color = accent)) {
                    appendInline(heading.groupValues[2])
                }
            }
            bullet != null -> {
                val indent = "    ".repeat(((line.length - trimmed.length) / 2).coerceAtMost(3))
                append(indent)
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append("•  ") }
                appendInline(bullet.groupValues[1])
            }
            trimmed == "---" || trimmed == "***" -> append("─────────")
            else -> appendInline(line)
        }
        if (index < lines.lastIndex) append('\n')
    }
}

/** Appends one line, turning **bold**, *italic* and `code` into styles. */
private fun AnnotatedString.Builder.appendInline(line: String) {
    var cursor = 0
    for (match in inlinePattern.findAll(line)) {
        append(line.substring(cursor, match.range.first))
        val (bold, boldAlt, code, italic) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(bold) }
            boldAlt.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(boldAlt) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(italic) }
        }
        cursor = match.range.last + 1
    }
    append(line.substring(cursor))
}
