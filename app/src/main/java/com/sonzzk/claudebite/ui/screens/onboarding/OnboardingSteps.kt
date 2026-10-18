package com.sonzzk.claudebite.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.ui.components.AnimatedCounter
import com.sonzzk.claudebite.ui.components.UnitFormat

/** Draws the body of one onboarding step. Called inside a scrolling Column. */
@Composable
internal fun StepContent(step: OnboardingStep, state: OnboardingUiState, vm: OnboardingViewModel) {
    val p = state.profile
    when (step) {
        OnboardingStep.WELCOME -> WelcomeStep()
        OnboardingStep.NAME -> {
            StepTitle("What's your name?", "Just a first name or nickname is perfect.")
            OutlinedTextField(
                value = p.name,
                onValueChange = { v -> vm.updateProfile { it.copy(name = v.take(30)) } },
                placeholder = { Text("Your name") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.titleLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { vm.next() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OnboardingStep.BASICS -> {
            StepTitle("About you", "Used only to calculate your energy needs.")
            SexPicker(p.sex, { s -> vm.updateProfile { it.copy(sex = s) } })
            AgePicker(p.age, { a -> vm.updateProfile { it.copy(age = a) } })
        }
        OnboardingStep.BODY -> {
            StepTitle("Height & weight", "We store metric behind the scenes; pick what you like.")
            BodyPicker(p, { updated -> vm.updateProfile { updated } })
        }
        OnboardingStep.ACTIVITY -> {
            StepTitle("Activity level", "Count workouts and your job. Being honest gives the best plan!")
            ActivityPicker(p.activity, { a -> vm.updateProfile { it.copy(activity = a) } })
        }
        OnboardingStep.GOAL -> {
            StepTitle("Your goal", null)
            GoalPicker(p.goal, vm::selectGoal)
            AnimatedVisibility(p.goal != Goal.MAINTAIN) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spacer(Modifier.width(1.dp))
                    WeightPicker("Target weight", p.targetWeightKg, p.units, { t -> vm.updateProfile { it.copy(targetWeightKg = t) } })
                    Text("Pace", style = MaterialTheme.typography.titleMedium)
                    PacePicker(p.pace, p.units, { pace -> vm.updateProfile { it.copy(pace = pace) } })
                }
            }
        }
        OnboardingStep.DIET -> {
            StepTitle("Food style", "I'll suggest foods that fit.")
            DietPicker(p.diet, { d -> vm.updateProfile { it.copy(diet = d) } })
        }
        OnboardingStep.PLAN -> PlanStep(state)
        OnboardingStep.API_KEY -> ApiKeyStep(state.apiKey, vm::setApiKey)
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String?) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text("Welcome to ClaudeBite!", style = MaterialTheme.typography.headlineMedium)
    listOf(
        "📸" to "Snap your food and I'll estimate the calories",
        "💬" to "Ask me anything about food, fat loss and muscle",
        "🎮" to "Level up, keep streaks and unlock cute outfits for me",
    ).forEach { (emoji, text) ->
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 28.sp)
                Spacer(Modifier.width(14.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** The big reveal: calories and macros count up, plus how long the goal should take. */
@Composable
private fun PlanStep(state: OnboardingUiState) {
    val p = state.finalProfile
    val t = state.targets
    StepTitle("Your plan", "Based on a ${t.tdee} kcal/day burn (BMR ${t.bmr}).")
    TargetsPreview(t)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            val weeks = state.weeksToGoal
            if (weeks == null) {
                Text("🌿 Goal: stay balanced and energized", style = MaterialTheme.typography.titleMedium)
                Text("We'll keep your weight steady and focus on good food.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("🏁 About ", style = MaterialTheme.typography.titleMedium)
                    AnimatedCounter(weeks, style = MaterialTheme.typography.headlineSmall, durationMs = 1400)
                    Text(" weeks", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "to reach ${UnitFormat.weight(p.targetWeightKg, p.units)} at a ${p.pace.label.lowercase()} pace. " +
                        "Slow and steady wins!",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Text(
        "You can change any of this later in Settings. Estimates are approximate and not medical advice.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Explains the bring-your-own-key model and lets the user paste a key now. */
@Composable
private fun ApiKeyStep(apiKey: String, onChange: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var visible by rememberSaveable { mutableStateOf(false) }
    StepTitle("Claude-chan's brain", null)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🧠 I run on Claude, using your own Anthropic API key.", style = MaterialTheme.typography.bodyLarge)
            Text("1. Open console.anthropic.com and sign in", style = MaterialTheme.typography.bodyMedium)
            Text("2. Go to API keys and create a key", style = MaterialTheme.typography.bodyMedium)
            Text("3. Paste it below (it starts with sk-ant-)", style = MaterialTheme.typography.bodyMedium)
            Text(
                "It's pay-as-you-go: you only pay for what you use, and I pick cheap models and short prompts " +
                    "to keep it to cents. Your key stays encrypted on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { uriHandler.openUri("https://console.anthropic.com/settings/keys") }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Open console.anthropic.com")
            }
        }
    }
    OutlinedTextField(
        value = apiKey,
        onValueChange = onChange,
        label = { Text("Anthropic API key") },
        placeholder = { Text("sk-ant-...") },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide key" else "Show key",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
