package com.sonzzk.claudebite.ui.screens.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.prefs.FastingState
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.rememberFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Simple fasting timer (12/14/16/18 h). Optional and never pushed: Claude-chan only cheers. */
@Composable
fun FastingScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val fb = rememberFeedback()
    val fast by remember { c.profiles.fasting }.collectAsState(initial = FastingState())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var goal by remember { mutableIntStateOf(16) }
    LaunchedEffect(fast.active) {
        while (fast.active) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedMs = if (fast.active) (now - fast.startedAt).coerceAtLeast(0) else 0L
    val goalMs = fast.goalHours * 3_600_000L
    val progress = if (fast.active) (elapsedMs.toFloat() / goalMs).coerceIn(0f, 1f) else 0f
    val done = fast.active && elapsedMs >= goalMs
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    SubScreen("Fasting timer", onBack) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    val sw = 20.dp.toPx()
                    val tl = Offset(sw / 2, sw / 2)
                    val sz = Size(size.width - sw, size.height - sw)
                    drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(sw))
                    if (progress > 0f) drawArc(primary, -90f, 360f * progress, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(
                        when {
                            done -> MascotMood.CELEBRATE
                            fast.active -> MascotMood.SLEEPY
                            else -> MascotMood.HAPPY
                        },
                        size = 96.dp,
                    )
                    Text(if (fast.active) hms(elapsedMs) else "Not fasting", style = MaterialTheme.typography.headlineSmall)
                    if (fast.active) Text("of ${fast.goalHours} h", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                when {
                    done -> "You did it! ${fast.goalHours} hours~ Break your fast with something with protein ♡"
                    fast.active -> "${hms((goalMs - elapsedMs).coerceAtLeast(0))} to go. Water and tea are fine!"
                    else -> "Pick a length and start when you finish your last meal. Totally optional!"
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (!fast.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 14, 16, 18).forEach { h ->
                        FilterChip(selected = goal == h, onClick = { goal = h }, label = { Text("$h h") })
                    }
                }
                Button(
                    onClick = { scope.launch { c.profiles.startFast(goal); fb.success() } },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start fast") }
            } else {
                OutlinedButton(
                    onClick = { scope.launch { c.profiles.endFast(); fb.pop() } },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (done) "Finish fast" else "End early (that's okay!)") }
            }
        }
    }
}

private fun hms(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
