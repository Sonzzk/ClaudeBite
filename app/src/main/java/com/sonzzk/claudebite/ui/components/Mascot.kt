package com.sonzzk.claudebite.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.domain.MascotMood

/** PLACEHOLDER: animated Claude-chan. */
@Composable
fun Mascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    outfit: String = "classic",
    onClick: (() -> Unit)? = null,
) {
    Text("Claude-chan ($mood)", modifier.size(size))
}

/** PLACEHOLDER: speech bubble with typewriter text. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier)
}
