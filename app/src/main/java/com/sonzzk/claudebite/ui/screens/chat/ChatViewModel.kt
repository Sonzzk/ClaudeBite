package com.sonzzk.claudebite.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonzzk.claudebite.ai.AiCoach
import com.sonzzk.claudebite.ai.AiError
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.ChatMessage
import com.sonzzk.claudebite.data.db.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A failed reply, shown as a friendly card with a retry button. */
data class ChatFailure(val message: String, val noApiKey: Boolean)

/**
 * Chat with Claude-chan.
 * Token saving: only the last 10 messages go to Claude as history, plus the short CoachContext.
 */
class ChatViewModel(
    private val coach: AiCoach,
    private val repository: HealthRepository,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> =
        repository.chatMessages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The reply as it streams in; null when Claude-chan isn't typing. */
    private val _partial = MutableStateFlow<String?>(null)
    val partial: StateFlow<String?> = _partial.asStateFlow()

    private val _waiting = MutableStateFlow(false)
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()

    private val _failure = MutableStateFlow<ChatFailure?>(null)
    val failure: StateFlow<ChatFailure?> = _failure.asStateFlow()

    private var job: Job? = null
    /** Text of the last user message that didn't get an answer, for "Try again". */
    private var unanswered: String? = null

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _waiting.value) return
        job = viewModelScope.launch {
            // History is read BEFORE saving the new message, so it is sent once (as userMessage), not twice.
            val history = repository.recentChat(HISTORY_SIZE)
            repository.addChat(ChatMessage(timestamp = System.currentTimeMillis(), role = ChatRole.USER, text = clean))
            ask(history, clean)
        }
    }

    /** Re-asks the last unanswered message without saving it a second time. */
    fun retry() {
        val text = unanswered ?: return
        if (_waiting.value) return
        job = viewModelScope.launch {
            // The unanswered message is already the newest one in the database: leave it out of history.
            val history = repository.recentChat(HISTORY_SIZE + 1).let { list ->
                if (list.lastOrNull()?.let { it.role == ChatRole.USER && it.text == text } == true) list.dropLast(1) else list
            }.takeLast(HISTORY_SIZE)
            ask(history, text)
        }
    }

    fun dismissFailure() { _failure.value = null }

    fun clearChat() {
        job?.cancel()
        viewModelScope.launch {
            repository.clearChat()
            unanswered = null
            _failure.value = null
        }
    }

    private suspend fun ask(history: List<ChatMessage>, text: String) {
        _failure.value = null
        _waiting.value = true
        _partial.value = ""
        unanswered = text
        try {
            val reply = coach.chat(history, text, repository.coachContext(), onPartial = { _partial.value = it })
            val saved = ChatMessage(timestamp = System.currentTimeMillis(), role = ChatRole.ASSISTANT, text = reply.text, model = reply.model)
            repository.addChat(saved)
            unanswered = null
            // Keep the streaming bubble until the saved message shows up, so it doesn't flicker.
            withTimeoutOrNull(1_000) {
                messages.first { list -> list.any { it.role == ChatRole.ASSISTANT && it.timestamp == saved.timestamp } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiError) {
            _failure.value = ChatFailure(e.message ?: "Something went wrong.", noApiKey = e is AiError.NoApiKey)
        } catch (e: Exception) {
            _failure.value = ChatFailure("Oops, something went wrong: ${e.message ?: "unknown error"}", noApiKey = false)
        } finally {
            _partial.value = null
            _waiting.value = false
        }
    }

    private companion object {
        const val HISTORY_SIZE = 10
    }
}
