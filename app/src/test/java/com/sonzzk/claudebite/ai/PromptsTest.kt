package com.sonzzk.claudebite.ai

import com.sonzzk.claudebite.data.db.ChatMessage
import com.sonzzk.claudebite.data.db.ChatRole
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.data.prefs.DietStyle
import com.sonzzk.claudebite.data.prefs.Goal
import com.sonzzk.claudebite.data.prefs.Pace
import com.sonzzk.claudebite.data.prefs.Sex
import com.sonzzk.claudebite.data.prefs.UnitSystem
import com.sonzzk.claudebite.data.prefs.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    private val profile = UserProfile(
        name = "Amos", sex = Sex.FEMALE, age = 25, heightCm = 170f, weightKg = 70f, targetWeightKg = 65f,
        goal = Goal.LOSE_FAT, pace = Pace.STEADY, diet = DietStyle.VEGAN,
    )
    private val targets = DailyTargets(calories = 1800, proteinG = 120, carbsG = 180, fatG = 60, waterMl = 2500, bmr = 1450, tdee = 2250)
    private val context = CoachContext(
        profile = profile, targets = targets, eatenToday = 950, proteinToday = 40.4f, carbsToday = 100f, fatToday = 30f,
        waterTodayMl = 1200, streak = 3, foodsToday = listOf("oatmeal", "chicken salad"),
    )

    @Test
    fun contextLine_isCompactAndComplete() {
        val line = Prompts.contextLine(context)
        assertEquals(
            "[User] Amos, F 25y 170cm 70kg -> 65kg | goal: Lose fat (Steady) | diet: Vegan | " +
                "target 1800 kcal P120 C180 F60 | today 950 kcal P40 C100 F30, 850 kcal left, " +
                "water 1200/2500 ml, streak 3d | ate: oatmeal, chicken salad",
            line,
        )
        assertFalse(line.contains("\n"))
    }

    @Test
    fun contextLine_imperialOverBudgetNoNameNoFoods() {
        val ctx = context.copy(
            profile = profile.copy(name = " ", units = UnitSystem.IMPERIAL, diet = DietStyle.ANYTHING, sex = Sex.MALE),
            eatenToday = 2000,
            foodsToday = emptyList(),
        )
        val line = Prompts.contextLine(ctx)
        assertTrue(line, line.startsWith("[User] M 25y 5'7\" 154lb -> 143lb |"))
        assertTrue(line.contains("units: imperial"))
        assertTrue(line.contains("200 kcal over"))
        assertFalse(line.contains("diet:"))
        assertFalse(line.contains("ate:"))
    }

    @Test
    fun contextLine_limitsFoodList() {
        val ctx = context.copy(foodsToday = (1..12).map { "food$it" })
        val line = Prompts.contextLine(ctx)
        assertTrue(line.endsWith("ate: food5, food6, food7, food8, food9, food10, food11, food12 (+4 more)"))
    }

    @Test
    fun systemPrompts_areStable() {
        // No dynamic data (dates, numbers from the user) may leak into cached system prompts.
        assertFalse(Prompts.CHAT_SYSTEM.contains("[User]"))
        assertFalse(Prompts.FOOD_SYSTEM.contains("[User]"))
    }

    private fun msg(role: ChatRole, text: String) = ChatMessage(timestamp = 0L, role = role, text = text)

    @Test
    fun buildTurns_startsWithUserAndAlternates() {
        val history = listOf(
            msg(ChatRole.ASSISTANT, "Hi! I'm Claude-chan~"),
            msg(ChatRole.USER, "hello"),
            msg(ChatRole.USER, "are you there?"),
            msg(ChatRole.ASSISTANT, "Yes!"),
            msg(ChatRole.ASSISTANT, " "),
        )
        val turns = Prompts.buildTurns(history, "What should I eat?")
        assertEquals(
            listOf(
                Prompts.Turn(true, "hello\n\nare you there?"),
                Prompts.Turn(false, "Yes!"),
                Prompts.Turn(true, "What should I eat?"),
            ),
            turns,
        )
    }

    @Test
    fun buildTurns_dropsDuplicateOfNewMessage() {
        val history = listOf(msg(ChatRole.USER, "hi"), msg(ChatRole.ASSISTANT, "hey"), msg(ChatRole.USER, "snack ideas?"))
        val turns = Prompts.buildTurns(history, "snack ideas?")
        assertEquals(3, turns.size)
        assertEquals(Prompts.Turn(true, "snack ideas?"), turns.last())
    }

    @Test
    fun buildTurns_mergesUnansweredUserMessage() {
        val history = listOf(msg(ChatRole.USER, "first try"))
        assertEquals(listOf(Prompts.Turn(true, "first try\n\nsecond try")), Prompts.buildTurns(history, "second try"))
    }

    @Test
    fun buildTurns_emptyHistory() {
        assertEquals(listOf(Prompts.Turn(true, "hi")), Prompts.buildTurns(emptyList(), " hi "))
    }
}
