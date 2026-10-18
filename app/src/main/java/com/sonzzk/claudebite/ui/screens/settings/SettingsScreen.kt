package com.sonzzk.claudebite.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonzzk.claudebite.data.prefs.ModelMode
import com.sonzzk.claudebite.ui.appViewModel
import com.sonzzk.claudebite.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * Settings: API key + model, token usage/cost, profile & goals, units, data reset and about.
 * The root Scaffold already pads for system bars, so the TopAppBar gets zero window insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c -> SettingsViewModel(c.repository, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (!state.loaded) return@Column
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrainSection(state, vm)
            UsageSection(state, vm::resetUsage)
            ProfileSection(state.profile, vm::updateProfile)
            UnitsSection(state.profile, vm::updateProfile)
            DataSection(onWipe = vm::wipeEverything)
            AboutSection()
        }
    }
}

/** Rounded card with an emoji + title header. Every settings group uses it. */
@Composable
internal fun SettingsCard(title: String, emoji: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$emoji  $title", style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Suppress("DEPRECATION") // LocalClipboardManager is simpler than the new suspend Clipboard API for plain text.
@Composable
private fun BrainSection(state: SettingsUiState, vm: SettingsViewModel) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var keyText by rememberSaveable(state.apiKey) { mutableStateOf(state.apiKey) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2_000)
            justSaved = false
        }
    }

    SettingsCard("Claude-chan's brain", "🧠") {
        Text(
            "Claude-chan thinks with Claude, using your own Anthropic API key. It's pay-as-you-go, and the app " +
                "uses cheap models and short prompts to keep costs tiny.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "How to get a key: open console.anthropic.com → sign in → API keys → Create key, then paste it here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uriHandler.openUri("https://console.anthropic.com/settings/keys") }) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Open console.anthropic.com")
        }
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            label = { Text("Anthropic API key") },
            placeholder = { Text("sk-ant-...") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                Row {
                    IconButton(onClick = { clipboard.getText()?.text?.let { keyText = it.trim() } }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                    }
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (visible) "Hide key" else "Show key",
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                vm.saveApiKey(keyText)
                justSaved = true
            }, enabled = keyText.trim() != state.apiKey) { Text("Save key") }
            Spacer(Modifier.width(12.dp))
            AnimatedVisibility(justSaved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Palette.Mint)
                    Spacer(Modifier.width(4.dp))
                    Text("Saved!", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (!justSaved && state.apiKey.isBlank()) {
                Text("No key yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        Text("Model mode", style = MaterialTheme.typography.titleSmall)
        ModelMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = state.modelMode == mode, onClick = { vm.setModelMode(mode) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.modelMode == mode, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UsageSection(state: SettingsUiState, onReset: () -> Unit) {
    SettingsCard("Token usage", "🪙") {
        if (state.usage.isEmpty()) {
            Text("No requests yet. Claude-chan hasn't spent a cent!", style = MaterialTheme.typography.bodyMedium)
        } else {
            state.usage.forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.usage.model, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "in ${formatTokens(row.usage.inputTokens)} · out ${formatTokens(row.usage.outputTokens)} · ${row.usage.requests} requests",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatUsd(row.costUsd), style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total (estimate)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${formatTokens(state.totalInput + state.totalOutput)} tokens · ${state.totalRequests} requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatUsd(state.totalCostUsd), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onReset) { Text("Reset counter") }
        }
    }
}

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

private fun formatUsd(v: Double): String = if (v < 0.01) "$%.4f".format(v) else "$%.2f".format(v)
