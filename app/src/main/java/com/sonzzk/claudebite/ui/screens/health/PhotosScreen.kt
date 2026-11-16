package com.sonzzk.claudebite.ui.screens.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonzzk.claudebite.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Progress photos stay on the phone. Pick two to compare with a before/after slider. */
@Composable
fun PhotosScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val photos by remember { c.tools.photos() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf<Long>() }
    var split by remember { mutableFloatStateOf(0.5f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "progress").apply { mkdirs() }
                val file = File(dir, "${System.currentTimeMillis()}.jpg")
                runCatching {
                    file.writeBytes(com.sonzzk.claudebite.ai.ImageCompressor.fromUri(context, uri))
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) c.tools.addPhoto(path, c.profiles.profile.first().weightKg)
        }
    }

    SubScreen("Progress photos", onBack, actions = {
        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null); Text(" Add")
        }
    }) {
        val pair = selected.mapNotNull { id -> photos.firstOrNull { it.id == id } }.sortedBy { it.date }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (pair.size == 2) {
                item(span = { GridItemSpan(3) }) {
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(MaterialTheme.shapes.large)) {
                            AsyncImage(File(pair[1].path), "After", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            AsyncImage(
                                File(pair[0].path), "Before", contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().drawWithContent {
                                    clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                                },
                            )
                            Canvas(Modifier.fillMaxSize()) {
                                drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(size.width * split, 0f),
                                    end = androidx.compose.ui.geometry.Offset(size.width * split, size.height), strokeWidth = 6f)
                            }
                        }
                        Slider(split, { split = it })
                        Row {
                            Text("Before ${pair[0].date}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text("After ${pair[1].date}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("No photos yet. A photo every couple of weeks shows changes the scale can't!", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Add first photo") }
                    }
                }
            } else {
                item(span = { GridItemSpan(3) }) {
                    Text("Tap two photos to compare them. Select one to delete it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(photos, key = { it.id }) { p ->
                val isSel = p.id in selected
                Box(
                    Modifier.aspectRatio(0.75f).clip(MaterialTheme.shapes.medium)
                        .border(if (isSel) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                        .clickable {
                            if (isSel) selected.remove(p.id) else {
                                if (selected.size >= 2) selected.removeAt(0)
                                selected.add(p.id)
                            }
                        },
                ) {
                    AsyncImage(File(p.path), p.date, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text(
                        p.date.substring(5), color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.4f)).padding(4.dp),
                    )
                }
            }
            if (selected.size == 1) {
                item(span = { GridItemSpan(3) }) {
                    TextButton(onClick = {
                        val p = photos.firstOrNull { it.id == selected.first() } ?: return@TextButton
                        selected.clear()
                        scope.launch { c.tools.deletePhoto(p) }
                    }) { Text("Delete selected photo") }
                }
            }
        }
    }
}
