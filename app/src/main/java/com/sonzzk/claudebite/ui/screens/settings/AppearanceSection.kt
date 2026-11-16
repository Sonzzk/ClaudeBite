package com.sonzzk.claudebite.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.prefs.AppSettings
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.LocalAppSettings
import kotlinx.coroutines.launch

/** Mascot style, theme, sounds, haptics, hide calories, reminders. Saved instantly. */
@Composable
internal fun AppearanceSection() {
    val container = LocalAppContainer.current
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch { container.profiles.updateSettings(change) }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) update { it.copy(reminders = false) } }
    SettingsCard("Look & feel", "🎨") {
        ChoiceRow("Claude-chan style", listOf("pixel" to "Pixel", "painted" to "Painted"), settings.mascotStyle) { v ->
            update { it.copy(mascotStyle = v) }
        }
        ChoiceRow("Theme", listOf("system" to "Auto", "light" to "Light", "dark" to "Dark"), settings.theme) { v ->
            update { it.copy(theme = v) }
        }
        ToggleRow("Pixel font", settings.pixelFont, "Retro letters for titles") { v -> update { it.copy(pixelFont = v) } }
        ToggleRow("8-bit sounds", settings.sounds) { v -> update { it.copy(sounds = v) } }
        ToggleRow("Vibration", settings.haptics) { v -> update { it.copy(haptics = v) } }
        ToggleRow("Hide calorie numbers", settings.hideCalories, "Shows food and macros without kcal") { v ->
            update { it.copy(hideCalories = v) }
        }
        ToggleRow("Meal reminders", settings.reminders, "At most 2 a day, around your usual meal times") { v ->
            update { it.copy(reminders = v) }
            if (v && android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = selected == value, onClick = { onPick(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
