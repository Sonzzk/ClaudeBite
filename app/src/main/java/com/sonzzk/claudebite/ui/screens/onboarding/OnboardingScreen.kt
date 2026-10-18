package com.sonzzk.claudebite.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonzzk.claudebite.ui.appViewModel
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble

/**
 * First-run flow: Claude-chan walks the user through a few playful steps, then reveals their plan.
 * Note: the root Scaffold already pads for the status/navigation bars, so we only add IME padding here.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val vm = appViewModel { c -> OnboardingViewModel(c.repository, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        // Top bar: back arrow + progress dots.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp)) {
                androidx.compose.animation.AnimatedVisibility(visible = !state.isFirst) {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                }
            }
            ProgressDots(current = state.step.ordinal, total = OnboardingStep.entries.size, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        // Claude-chan + her line for this step. She is bigger on the welcome page.
        val mascotSize by animateDpAsState(
            if (state.step == OnboardingStep.WELCOME) 170.dp else 104.dp,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
            label = "mascotSize",
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(mood = state.step.mood, size = mascotSize)
            Spacer(Modifier.width(8.dp))
            AnimatedContent(targetState = mascotLine(state), label = "line", modifier = Modifier.weight(1f)) { line ->
                SpeechBubble(line, Modifier.fillMaxWidth())
            }
        }

        // The step itself slides in from the side we are moving towards.
        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it * dir } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it * dir } + fadeOut())
                    .using(SizeTransform(clip = false))
            },
            label = "step",
        ) { step ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepContent(step, state, vm)
            }
        }

        BottomActions(state, vm, onFinished)
    }
}

@Composable
private fun BottomActions(state: OnboardingUiState, vm: OnboardingViewModel, onFinished: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.step == OnboardingStep.API_KEY) {
            Button(
                onClick = { vm.finish(saveKey = true, onDone = onFinished) },
                enabled = state.apiKey.isNotBlank() && !state.saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Save key & start")
            }
            TextButton(onClick = { vm.finish(saveKey = false, onDone = onFinished) }, enabled = !state.saving) {
                Text("Skip, I'll add it later")
            }
        } else {
            Button(
                onClick = vm::next,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    when (state.step) {
                        OnboardingStep.WELCOME -> "Let's start!"
                        OnboardingStep.DIET -> "Show me my plan!"
                        OnboardingStep.PLAN -> "Sounds great!"
                        else -> "Next"
                    },
                )
            }
        }
    }
}

/** Little dots showing where we are; the current one stretches into a pill. */
@Composable
private fun ProgressDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val width by animateDpAsState(
                if (i == current) 22.dp else 8.dp,
                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "dot",
            )
            val color by animateColorAsState(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                label = "dotColor",
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

private fun mascotLine(state: OnboardingUiState): String {
    val name = state.profile.name.trim().ifBlank { "friend" }
    return when (state.step) {
        OnboardingStep.WELCOME -> "Hi! I'm Claude-chan, your health buddy~"
        OnboardingStep.NAME -> "First things first... what should I call you?"
        OnboardingStep.BASICS -> "Nice to meet you, $name! A couple of basics so my math is right."
        OnboardingStep.BODY -> "Height and weight help me find your energy needs. No judging, promise!"
        OnboardingStep.ACTIVITY -> "How active is a normal week for you?"
        OnboardingStep.GOAL -> "What's our big goal together?"
        OnboardingStep.DIET -> "Any food style I should keep in mind?"
        OnboardingStep.PLAN -> "Ta-da! Here's the plan I made just for you, $name!"
        OnboardingStep.API_KEY -> "Last thing: my brain! I think using your own Anthropic API key."
    }
}
