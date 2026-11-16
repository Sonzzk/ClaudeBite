package com.sonzzk.claudebite.ui.screens.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonzzk.claudebite.data.db.Workout
import com.sonzzk.claudebite.data.db.WorkoutSet
import com.sonzzk.claudebite.data.db.WorkoutType
import com.sonzzk.claudebite.domain.Burn
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble
import com.sonzzk.claudebite.ui.components.rememberFeedback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Workout log: sessions with sets and reps. Burned calories are added to that day's food budget. */
@Composable
fun WorkoutsScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val workouts by remember { c.tools.workoutsWithSets() }.collectAsState(initial = emptyList())
    val profile by remember { c.profiles.profile }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedback()
    var adding by remember { mutableStateOf(false) }

    SubScreen("Workouts", onBack) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mascot(if (workouts.isEmpty()) MascotMood.WAVING else MascotMood.PROUD, size = 84.dp)
                        Spacer(Modifier.width(8.dp))
                        SpeechBubble(
                            if (workouts.isEmpty()) "Log a workout and I'll add the calories you burned to today's budget!"
                            else "${workouts.size} workouts logged! Muscles are made in the kitchen AND the gym~",
                            Modifier.weight(1f),
                        )
                    }
                }
                items(workouts, key = { it.workout.id }) { ws ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(ws.workout.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${ws.workout.date} · ${ws.workout.durationMin} min · ${ws.workout.caloriesBurned} kcal burned",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { scope.launch { c.tools.deleteWorkout(ws.workout) } }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete workout")
                                }
                            }
                            ws.sets.groupBy { it.exercise }.forEach { (exercise, sets) ->
                                Text(
                                    "$exercise: " + sets.joinToString("  ") { s -> "${s.reps}×${if (s.weightKg > 0) "${s.weightKg.fmt()}kg" else "BW"}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (ws.workout.notes.isNotBlank()) Text(ws.workout.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Log workout") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding(),
            )
        }
    }

    if (adding) {
        AddWorkoutSheet(
            weightKg = profile?.weightKg ?: 70f,
            loadExercises = { c.tools.recentExercises() },
            onDismiss = { adding = false },
            onSave = { w, sets ->
                scope.launch {
                    c.tools.saveWorkout(w, sets)
                    c.game.onWorkoutLogged()
                }
                feedback.success()
                adding = false
            },
        )
    }
}

private val presets = listOf(
    "Push day" to WorkoutType.STRENGTH, "Pull day" to WorkoutType.STRENGTH, "Leg day" to WorkoutType.STRENGTH,
    "Full body" to WorkoutType.STRENGTH, "Run" to WorkoutType.CARDIO, "Walk" to WorkoutType.CARDIO,
    "Cycling" to WorkoutType.CARDIO, "Swim" to WorkoutType.CARDIO, "Football" to WorkoutType.SPORT, "Yoga" to WorkoutType.OTHER,
)

private class SetDraft(exercise: String, reps: String, weight: String) {
    var exercise by mutableStateOf(exercise)
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddWorkoutSheet(
    weightKg: Float,
    loadExercises: suspend () -> List<String>,
    onDismiss: () -> Unit,
    onSave: (Workout, List<WorkoutSet>) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("Push day") }
    var type by remember { mutableStateOf(WorkoutType.STRENGTH) }
    var minutes by remember { mutableIntStateOf(45) }
    var intensity by remember { mutableIntStateOf(2) }
    var notes by remember { mutableStateOf("") }
    val sets = remember { mutableStateListOf<SetDraft>() }
    var known by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) { known = loadExercises() }
    val burned = Burn.estimate(type, minutes, weightKg, intensity)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Log workout", style = MaterialTheme.typography.titleLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (n, t) ->
                    FilterChip(selected = name == n, onClick = { name = n; type = t }, label = { Text(n) })
                }
            }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkoutType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            Text("$minutes min", style = MaterialTheme.typography.labelLarge)
            Slider(value = minutes.toFloat(), onValueChange = { minutes = (it / 5).roundToInt() * 5 }, valueRange = 5f..180f)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Intensity")
                listOf(1 to "Easy", 2 to "Medium", 3 to "Hard").forEach { (v, l) ->
                    FilterChip(selected = intensity == v, onClick = { intensity = v }, label = { Text(l) })
                }
            }
            Text("≈ $burned kcal burned (added to that day's budget)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)

            if (type == WorkoutType.STRENGTH) {
                Text("Sets", style = MaterialTheme.typography.titleMedium)
                sets.forEachIndexed { i, s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(s.exercise, { s.exercise = it.take(30) }, label = { Text("Exercise") }, singleLine = true, modifier = Modifier.weight(2f))
                        OutlinedTextField(s.reps, { s.reps = it.filter(Char::isDigit).take(3) }, label = { Text("Reps") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(s.weight, { s.weight = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text("kg") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        IconButton(onClick = { sets.removeAt(i) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Delete, contentDescription = "Remove set") }
                    }
                }
                Row {
                    TextButton(onClick = {
                        val last = sets.lastOrNull()
                        sets.add(SetDraft(last?.exercise ?: known.firstOrNull() ?: "Bench press", last?.reps ?: "8", last?.weight ?: ""))
                    }) { Text("+ Add set") }
                    if (known.isNotEmpty()) {
                        TextButton(onClick = { sets.add(SetDraft(known.random(), "10", "")) }) { Text("+ Recent exercise") }
                    }
                }
            }
            OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        Workout(date = java.time.LocalDate.now().toString(), timestamp = now, name = name.ifBlank { "Workout" }, type = type,
                            durationMin = minutes, caloriesBurned = burned, notes = notes.trim()),
                        sets.mapNotNull { s ->
                            val reps = s.reps.toIntOrNull() ?: return@mapNotNull null
                            WorkoutSet(workoutId = 0, exercise = s.exercise.ifBlank { "Exercise" }, reps = reps, weightKg = s.weight.toFloatOrNull() ?: 0f, position = 0)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save workout 💪") }
        }
    }
}

private fun Float.fmt(): String = if (this % 1f == 0f) roundToInt().toString() else "%.1f".format(this)
