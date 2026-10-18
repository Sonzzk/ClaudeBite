package com.sonzzk.claudebite.ai

import com.sonzzk.claudebite.data.prefs.ModelMode

/** How hard the model should think. Mapped to the SDK's `output_config.effort` in [ClaudeCoach]. */
enum class Effort { LOW, MEDIUM, HIGH }

/**
 * The model, effort and output cap for one request.
 * [maxTokens] is only a ceiling: we pay for the tokens actually produced, so leaving headroom for
 * Claude's (adaptive) thinking costs nothing but avoids cut-off answers.
 */
data class ModelChoice(val modelId: String, val effort: Effort, val maxTokens: Long)

/**
 * Picks the cheapest model that does each job well (pure Kotlin, unit-tested).
 *
 * - Haiku 5.5: fast and very cheap. Great for text food logs and quick chat.
 * - Sonnet 5.5: stronger vision, so portion estimates from photos are better. Also used for plans.
 * - Opus 5.5: the strongest; only in "Best quality" mode.
 */
object ModelRouter {
    const val HAIKU = "claude-haiku-5-5"
    const val SONNET = "claude-sonnet-5-5"
    const val OPUS = "claude-opus-5-5"

    // Output caps per job. Thinking tokens count toward max_tokens, so these leave room for it.
    const val FOOD_MAX_TOKENS = 2_048L
    const val CHAT_SIMPLE_MAX_TOKENS = 1_024L
    const val CHAT_COMPLEX_MAX_TOKENS = 4_096L
    /** Opus at medium/high effort thinks more, so it gets a bit more room. */
    const val BEST_EXTRA_TOKENS = 2_048L

    fun route(task: AiTask, mode: ModelMode): ModelChoice = when (mode) {
        ModelMode.AUTO -> when (task) {
            AiTask.FOOD_TEXT -> ModelChoice(HAIKU, Effort.LOW, FOOD_MAX_TOKENS)
            AiTask.FOOD_PHOTO -> ModelChoice(SONNET, Effort.LOW, FOOD_MAX_TOKENS)
            AiTask.CHAT_SIMPLE -> ModelChoice(HAIKU, Effort.LOW, CHAT_SIMPLE_MAX_TOKENS)
            AiTask.CHAT_COMPLEX -> ModelChoice(SONNET, Effort.MEDIUM, CHAT_COMPLEX_MAX_TOKENS)
        }
        ModelMode.ECONOMY -> ModelChoice(HAIKU, Effort.LOW, baseTokens(task))
        ModelMode.BEST -> ModelChoice(
            modelId = OPUS,
            effort = if (task == AiTask.CHAT_COMPLEX) Effort.HIGH else Effort.MEDIUM,
            maxTokens = baseTokens(task) + BEST_EXTRA_TOKENS,
        )
    }

    private fun baseTokens(task: AiTask): Long = when (task) {
        AiTask.FOOD_PHOTO, AiTask.FOOD_TEXT -> FOOD_MAX_TOKENS
        AiTask.CHAT_SIMPLE -> CHAT_SIMPLE_MAX_TOKENS
        AiTask.CHAT_COMPLEX -> CHAT_COMPLEX_MAX_TOKENS
    }

    /** Messages longer than this are treated as "complex" (they usually need a longer answer). */
    const val LONG_MESSAGE_CHARS = 220

    private val complexWords = Regex(
        "\\b(plan|plans|planning|program|programs|programme|programmes|schedule|schedules|" +
            "routine|routines|recipe|recipes|week|weeks|weekly)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val complexPhrases = Regex(
        "\\b(meal plan|meal prep plan|explain why|explain how|how does|how do .{0,40} work)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Cheap keyword heuristic: does this chat message need a bigger model?
     * "Give me a 3-day meal plan" -> CHAT_COMPLEX, "Is a banana ok before gym?" -> CHAT_SIMPLE.
     */
    fun classifyChat(message: String): AiTask {
        val text = message.trim()
        return when {
            text.length > LONG_MESSAGE_CHARS -> AiTask.CHAT_COMPLEX
            complexWords.containsMatchIn(text) -> AiTask.CHAT_COMPLEX
            complexPhrases.containsMatchIn(text) -> AiTask.CHAT_COMPLEX
            else -> AiTask.CHAT_SIMPLE
        }
    }

    /**
     * Whether we opt this model into server-side refusal fallbacks (`fallbacks: "default"`).
     * Sonnet 5.5 and Opus 5.5 support it; Haiku 5.5 has no server-side fallback, so we never send it there.
     */
    fun supportsServerFallback(modelId: String): Boolean =
        modelId.startsWith(SONNET) || modelId.startsWith(OPUS)
}
