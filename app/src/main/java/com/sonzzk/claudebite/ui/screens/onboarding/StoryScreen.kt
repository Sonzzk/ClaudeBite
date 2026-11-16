package com.sonzzk.claudebite.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.PixelSprite
import com.sonzzk.claudebite.ui.components.SpeechBubble
import com.sonzzk.claudebite.ui.components.rememberFeedback
import kotlinx.coroutines.launch

private data class Panel(val sprite: String, val line: String)

private val panels = listOf(
    Panel("px_misc_04", "Oh! H-hi there! I'm Claude-chan~ You found me!"),
    Panel("px_pose_10", "I'm a little food spirit who lives in your phone. I love snacks. Like, a LOT."),
    Panel("px_prop_14", "Show me what you eat: a photo, a barcode, or just tell me. I'll do all the math!"),
    Panel("px_misc_00", "No judging, ever. We'll level up, decorate my room and beat a boss or two together. Ready?"),
)

/** A short pixel-art story before the first-time setup. Tap to continue; Skip jumps to setup. */
@Composable
fun StoryScreen(onFinished: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val fb = rememberFeedback()
    var page by rememberSaveable { mutableIntStateOf(0) }
    fun finish() {
        scope.launch { c.profiles.updateSettings { it.copy(seenStory = true) } }
        onFinished()
    }
    fun next() {
        fb.tap()
        if (page < panels.lastIndex) page++ else finish()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() }
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.TopEnd)) { Text("Skip") }
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "story",
            modifier = Modifier.align(Alignment.Center),
        ) { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(
                    Modifier.size(240.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { PixelSprite(panels[p].sprite, Modifier.size(200.dp)) }
                SpeechBubble(panels[p].line)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                panels.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == page) 10.dp else 8.dp).clip(CircleShape)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { next() }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                Text(if (page < panels.lastIndex) "Next" else "Let's go!")
            }
        }
    }
}
