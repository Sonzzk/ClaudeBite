package com.sonzzk.claudebite.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonzzk.claudebite.data.db.ChatRole
import com.sonzzk.claudebite.domain.MascotMood
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.appViewModel
import com.sonzzk.claudebite.ui.components.Mascot
import com.sonzzk.claudebite.ui.components.SpeechBubble
import kotlinx.coroutines.flow.map

private val quickPrompts = listOf(
    "What should I eat for dinner?",
    "Make me a 3-day workout plan",
    "How much protein do I need?",
    "Rate my day so far",
    "Healthy snack ideas",
)

/** Chat with Claude-chan, the health coach. Replies stream in live. */
@Composable
fun ChatScreen() {
    val vm = appViewModel { c -> ChatViewModel(c.coach, c.repository) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val waiting by vm.waiting.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()

    val profiles = LocalAppContainer.current.profiles
    val outfitFlow = remember(profiles) { profiles.progression.map { it.equippedOutfit } }
    val outfit by outfitFlow.collectAsStateWithLifecycle(initialValue = "classic")

    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // The list is drawn bottom-up (reverseLayout), so index 0 is the newest thing.
    LaunchedEffect(messages.size, failure, partial == null) {
        if (messages.isNotEmpty() || partial != null || failure != null) listState.animateScrollToItem(0)
    }

    fun send(text: String) {
        vm.send(text)
        input = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        ChatHeader(
            mood = if (waiting) MascotMood.THINKING else MascotMood.HAPPY,
            subtitle = if (waiting) "typing..." else "Your health coach",
            outfit = outfit,
            onClear = { confirmClear = true },
            canClear = messages.isNotEmpty(),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !waiting && failure == null) {
                EmptyChat(outfit)
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    failure?.let { f ->
                        item(key = "failure") { ChatFailureCard(f, onRetry = vm::retry, onDismiss = vm::dismissFailure) }
                    }
                    partial?.let { text ->
                        item(key = "partial") {
                            if (text.isEmpty()) {
                                AssistantBubble(text = "", model = null, outfit = outfit) { TypingDots() }
                            } else {
                                AssistantBubble(text = text, model = null, outfit = outfit)
                            }
                        }
                    }
                    items(messages.asReversed(), key = { it.id }) { msg ->
                        if (msg.role == ChatRole.USER) UserBubble(msg.text)
                        else AssistantBubble(msg.text, msg.model, outfit)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = input.isEmpty() && !waiting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickPrompts) { prompt ->
                    AssistChip(
                        onClick = { send(prompt) },
                        label = { Text(prompt) },
                        shape = RoundedCornerShape(50),
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1000) },
                placeholder = { Text("Ask Claude-chan anything~") },
                maxLines = 4,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank() && !waiting) send(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { send(input) },
                enabled = input.isNotBlank() && !waiting,
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat?") },
            text = { Text("This deletes our whole conversation. Your food log and progress stay safe.") },
            confirmButton = {
                TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun ChatHeader(mood: MascotMood, subtitle: String, outfit: String, onClear: () -> Unit, canClear: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mascot(mood, size = 56.dp, outfit = outfit)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Claude-chan", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Clear chat") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                    enabled = canClear,
                    onClick = { menuOpen = false; onClear() },
                )
            }
        }
    }
}

@Composable
private fun EmptyChat(outfit: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Mascot(MascotMood.WAVING, size = 170.dp, outfit = outfit)
        Spacer(Modifier.size(12.dp))
        SpeechBubble(
            "Hi hi! I'm Claude-chan, your health coach~ Ask me about food, workouts or your goals. " +
                "I can see what you've eaten today, so my tips are just for you! ✨",
        )
    }
}
