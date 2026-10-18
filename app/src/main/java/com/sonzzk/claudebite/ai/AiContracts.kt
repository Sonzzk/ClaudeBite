package com.sonzzk.claudebite.ai

import com.sonzzk.claudebite.data.db.ChatMessage
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.UserProfile

/** One food Claude found in a photo or description. */
data class AnalyzedItem(
    val name: String,
    val portion: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fiberG: Float,
    val sugarG: Float,
)

/** Result of a food photo/text analysis. */
data class FoodAnalysis(
    val items: List<AnalyzedItem>,
    /** 1-10: how well this meal fits the user's goal. */
    val healthScore: Int,
    /** A, B, C, D or F, shown as a big badge. */
    val grade: String,
    /** One or two cute sentences from Claude-chan about the meal. */
    val comment: String,
    /** A short, practical tip ("add some greens", "great protein!"). */
    val tip: String,
    /** 0-1, how sure Claude is. Low confidence asks the user to check portions. */
    val confidence: Float,
    val modelUsed: String,
) {
    val totalCalories: Int get() = items.sumOf { it.calories }
    val totalProtein: Float get() = items.sumOf { it.proteinG.toDouble() }.toFloat()
    val totalCarbs: Float get() = items.sumOf { it.carbsG.toDouble() }.toFloat()
    val totalFat: Float get() = items.sumOf { it.fatG.toDouble() }.toFloat()
}

/**
 * A compact summary of the user, sent with chat/food requests.
 * Keeping this short is the main way we save tokens: Claude gets one line instead of the whole database.
 */
data class CoachContext(
    val profile: UserProfile,
    val targets: DailyTargets,
    val eatenToday: Int,
    val proteinToday: Float,
    val carbsToday: Float,
    val fatToday: Float,
    val waterTodayMl: Int,
    val streak: Int,
    /** e.g. "oatmeal, chicken salad" */
    val foodsToday: List<String>,
)

/** Kinds of jobs the AI does; the ModelRouter picks a model per kind. */
enum class AiTask {
    FOOD_PHOTO,     // vision: identify foods + estimate portions
    FOOD_TEXT,      // "2 eggs and toast"
    CHAT_SIMPLE,    // quick questions, encouragement
    CHAT_COMPLEX,   // meal plans, workout programs, detailed explanations
}

sealed class AiError(message: String) : Exception(message) {
    class NoApiKey : AiError("Add your Anthropic API key in Settings so I can think!")
    class InvalidKey : AiError("That API key didn't work. Please check it in Settings.")
    class RateLimited : AiError("I'm getting too many requests. Try again in a moment!")
    class Network : AiError("I can't reach the internet right now.")
    class Refused : AiError("I can't help with that one, sorry!")
    class Other(msg: String) : AiError(msg)
}

/** Everything the UI needs from Claude. Implemented by ClaudeCoach. */
interface AiCoach {
    suspend fun analyzeFoodPhoto(jpegBytes: ByteArray, userHint: String?, context: CoachContext): FoodAnalysis
    suspend fun analyzeFoodText(description: String, context: CoachContext): FoodAnalysis

    /**
     * Sends a chat message. [history] is the recent conversation (already trimmed by the caller to save tokens).
     * [onPartial] receives the growing reply text for a typing effect.
     * Returns the final reply and the model that answered.
     */
    suspend fun chat(
        history: List<ChatMessage>,
        userMessage: String,
        context: CoachContext,
        onPartial: (String) -> Unit = {},
    ): ChatReply
}

data class ChatReply(val text: String, val model: String)
