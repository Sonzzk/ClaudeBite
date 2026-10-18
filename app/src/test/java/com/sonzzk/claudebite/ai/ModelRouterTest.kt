package com.sonzzk.claudebite.ai

import com.sonzzk.claudebite.data.prefs.ModelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterTest {

    @Test
    fun autoMode_picksCheapestModelThatFitsEachTask() {
        assertEquals(ModelChoice("claude-haiku-5-5", Effort.LOW, ModelRouter.FOOD_MAX_TOKENS), ModelRouter.route(AiTask.FOOD_TEXT, ModelMode.AUTO))
        assertEquals(ModelChoice("claude-sonnet-5-5", Effort.LOW, ModelRouter.FOOD_MAX_TOKENS), ModelRouter.route(AiTask.FOOD_PHOTO, ModelMode.AUTO))
        assertEquals(ModelChoice("claude-haiku-5-5", Effort.LOW, ModelRouter.CHAT_SIMPLE_MAX_TOKENS), ModelRouter.route(AiTask.CHAT_SIMPLE, ModelMode.AUTO))
        assertEquals(ModelChoice("claude-sonnet-5-5", Effort.MEDIUM, ModelRouter.CHAT_COMPLEX_MAX_TOKENS), ModelRouter.route(AiTask.CHAT_COMPLEX, ModelMode.AUTO))
    }

    @Test
    fun economyMode_alwaysHaikuLow() {
        AiTask.entries.forEach { task ->
            val choice = ModelRouter.route(task, ModelMode.ECONOMY)
            assertEquals("claude-haiku-5-5", choice.modelId)
            assertEquals(Effort.LOW, choice.effort)
        }
    }

    @Test
    fun bestMode_alwaysOpus_highOnlyForComplexChat() {
        AiTask.entries.forEach { task ->
            val choice = ModelRouter.route(task, ModelMode.BEST)
            assertEquals("claude-opus-5-5", choice.modelId)
            val expected = if (task == AiTask.CHAT_COMPLEX) Effort.HIGH else Effort.MEDIUM
            assertEquals(expected, choice.effort)
        }
    }

    @Test
    fun maxTokens_complexChatGetsMostRoom_andBestModeGetsHeadroom() {
        val simple = ModelRouter.route(AiTask.CHAT_SIMPLE, ModelMode.AUTO).maxTokens
        val food = ModelRouter.route(AiTask.FOOD_PHOTO, ModelMode.AUTO).maxTokens
        val complex = ModelRouter.route(AiTask.CHAT_COMPLEX, ModelMode.AUTO).maxTokens
        assertTrue(simple < complex && food < complex)
        AiTask.entries.forEach { task ->
            assertTrue(ModelRouter.route(task, ModelMode.BEST).maxTokens > ModelRouter.route(task, ModelMode.ECONOMY).maxTokens)
        }
    }

    @Test
    fun classifyChat_complexKeywords() {
        listOf(
            "Can you make me a meal plan?",
            "Give me a 3 day workout program",
            "What should my gym schedule look like",
            "A morning routine for fat loss?",
            "Any high protein recipe ideas",
            "What should I eat this week",
            "Explain why protein matters",
            "How does creatine work?",
            "how do carbs and insulin actually work",
        ).forEach { assertEquals(it, AiTask.CHAT_COMPLEX, ModelRouter.classifyChat(it)) }
    }

    @Test
    fun classifyChat_simpleQuestions() {
        listOf(
            "Is a banana ok before the gym?",
            "thanks Claude-chan!",
            "How many calories in an apple",
            "I love plant-based food", // "plant" must not match "plan"
            "Why am I so hungry today?",
        ).forEach { assertEquals(it, AiTask.CHAT_SIMPLE, ModelRouter.classifyChat(it)) }
    }

    @Test
    fun classifyChat_longMessageIsComplex() {
        val long = "I had a long day and ".repeat(15)
        assertTrue(long.trim().length > ModelRouter.LONG_MESSAGE_CHARS)
        assertEquals(AiTask.CHAT_COMPLEX, ModelRouter.classifyChat(long))
    }

    @Test
    fun serverFallback_onlyForSonnetAndOpus() {
        assertTrue(ModelRouter.supportsServerFallback("claude-sonnet-5-5"))
        assertTrue(ModelRouter.supportsServerFallback("claude-opus-5-5"))
        assertFalse(ModelRouter.supportsServerFallback("claude-haiku-5-5"))
    }
}
