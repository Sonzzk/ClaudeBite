package com.sonzzk.claudebite.ai

import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.ChatMessage
import com.sonzzk.claudebite.data.prefs.SecureSettings

/** PLACEHOLDER: the real implementation (Anthropic SDK, model routing, prompts) replaces this file. */
class ClaudeCoach(
    private val settings: SecureSettings,
    private val repository: HealthRepository,
) : AiCoach {
    override suspend fun analyzeFoodPhoto(jpegBytes: ByteArray, userHint: String?, context: CoachContext): FoodAnalysis = TODO()
    override suspend fun analyzeFoodText(description: String, context: CoachContext): FoodAnalysis = TODO()
    override suspend fun chat(history: List<ChatMessage>, userMessage: String, context: CoachContext, onPartial: (String) -> Unit): ChatReply = TODO()
}
