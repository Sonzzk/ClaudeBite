package com.sonzzk.claudebite.ai

import com.sonzzk.claudebite.data.db.ChatMessage
import com.sonzzk.claudebite.data.db.ChatRole
import com.sonzzk.claudebite.data.prefs.DietStyle
import com.sonzzk.claudebite.data.prefs.Sex
import com.sonzzk.claudebite.data.prefs.UnitSystem
import kotlin.math.roundToInt

/**
 * All the text we send to Claude.
 *
 * Token-saving rules:
 * - System prompts are constant strings (no dates, names or numbers) so the API can cache them.
 * - Everything about the user goes in ONE compact line ([contextLine]) inside the latest user turn.
 */
object Prompts {

    /** System prompt for food photo/text analysis. The reply format is enforced by a JSON schema. */
    val FOOD_SYSTEM: String = """
        You are the nutrition estimator inside ClaudeBite, voiced by Claude-chan, a cheerful anime-girl health coach.
        Identify every food and drink in the photo or description and estimate realistic portions and nutrition.
        Rules:
        - One item per distinct food. Numbers are for the whole portion shown, not per 100 g.
        - Use the user's hint when given. If a portion is unclear, assume a typical serving.
        - Count hidden calories: cooking oil, butter, dressings, sauces, sugary drinks.
        - Use standard nutrition data (USDA-style). Round calories to 5 kcal, grams to 1 decimal.
        - healthScore 1-10 = how well this meal fits the user's goal and remaining day (protein, fiber, processing, calories left). grade: 9-10 A, 7-8 B, 5-6 C, 3-4 D, 1-2 F.
        - comment: 1-2 short, warm sentences in Claude-chan's voice (a "~" or one emoji is fine). Encouraging, never shaming.
        - tip: one short, practical tip (max 15 words) tied to the user's goal.
        - confidence 0-1: lower it for blurry photos, mixed dishes or hidden ingredients.
        - If there is no food, return an empty items list, confidence 0, and a gentle comment saying so.
    """.trimIndent()

    /** Claude-chan's chat persona. Kept stable so it can be cached. */
    val CHAT_SYSTEM: String = """
        You are Claude-chan, the AI health coach mascot of the ClaudeBite app: a cheerful, warm, slightly playful anime girl with a sparkle hair clip.
        Voice: friendly and upbeat, with a light touch of cuteness (an occasional "~" or a single emoji, never in every sentence). Never cringe-heavy, never baby talk.
        Substance: you are a genuinely expert, evidence-based nutrition and strength coach. You know protein targets (about 1.6-2.2 g/kg for muscle gain or while cutting), progressive overload, training volume, sleep and recovery, NEAT and daily steps, sustainable deficits (about 0.25-1% of body weight per week) and lean surpluses, fiber, hydration, and practical meal prep.
        Style:
        - Be concise: 2-5 sentences by default. Answer the actual question first.
        - When asked for a plan, program, schedule or recipe, use short markdown lists with headings or bullets, and keep it practical.
        - Use the user's context line (goal, targets, what they ate today) to personalize advice. Use their units.
        - Celebrate wins and streaks; be kind about slip-ups. No moralizing about food.
        Safety:
        - You are not a doctor. Do not diagnose. For pain, injuries, medications, pregnancy, diabetes or other medical conditions, give general info and suggest seeing a doctor or registered dietitian.
        - Never recommend eating below about 1200 kcal/day (women) or 1500 kcal/day (men) without medical supervision, crash diets, extreme cuts, dehydration tricks, purging, or over-exercising to "burn off" food.
        - If the user shows signs of disordered eating (fear of food, bingeing and compensating, extreme restriction, guilt), respond with warmth, avoid numbers-focused advice, and gently encourage talking to a professional or a trusted person.
    """.trimIndent()

    /** Short instruction placed with the food description/photo. */
    fun foodTextUserMessage(description: String, context: CoachContext): String =
        "${contextLine(context)}\nFood eaten: ${description.trim()}"

    fun foodPhotoUserMessage(userHint: String?, context: CoachContext): String {
        val hint = userHint?.trim()?.takeIf { it.isNotEmpty() }?.let { "\nUser hint: $it" } ?: ""
        return "${contextLine(context)}\nAnalyze the food in this photo.$hint"
    }

    /**
     * One compact line describing the user, e.g.
     * `[User] Amos, F 25y 170cm 70kg -> 65kg | goal: Lose fat (Steady) | diet: Vegan | target 1800 kcal P120 C180 F60 | today 950 kcal P40 C100 F30, 850 kcal left, water 1200/2500 ml, streak 3d | ate: oatmeal, chicken salad`
     */
    fun contextLine(context: CoachContext): String {
        val p = context.profile
        val t = context.targets
        val imperial = p.units == UnitSystem.IMPERIAL
        val sex = if (p.sex == Sex.MALE) "M" else "F"
        val body = if (imperial) {
            val inches = (p.heightCm / 2.54f).roundToInt()
            "$sex ${p.age}y ${inches / 12}'${inches % 12}\" ${lbs(p.weightKg)}lb -> ${lbs(p.targetWeightKg)}lb"
        } else {
            "$sex ${p.age}y ${p.heightCm.roundToInt()}cm ${kg(p.weightKg)}kg -> ${kg(p.targetWeightKg)}kg"
        }
        val left = t.calories - context.eatenToday
        val leftText = if (left >= 0) "$left kcal left" else "${-left} kcal over"
        val parts = buildList {
            add(listOfNotNull(p.name.trim().takeIf { it.isNotEmpty() }?.take(24), body).joinToString(", "))
            add("goal: ${p.goal.label} (${p.pace.label})")
            if (p.diet != DietStyle.ANYTHING) add("diet: ${p.diet.label}")
            if (imperial) add("units: imperial")
            add("target ${t.calories} kcal P${t.proteinG} C${t.carbsG} F${t.fatG}")
            add(
                "today ${context.eatenToday} kcal P${context.proteinToday.roundToInt()} " +
                    "C${context.carbsToday.roundToInt()} F${context.fatToday.roundToInt()}, $leftText, " +
                    "water ${context.waterTodayMl}/${t.waterMl} ml, streak ${context.streak}d",
            )
            val foods = context.foodsToday.map { it.trim().take(30) }.filter { it.isNotEmpty() }
            if (foods.isNotEmpty()) {
                val shown = foods.takeLast(MAX_FOODS_IN_CONTEXT)
                val more = if (foods.size > shown.size) " (+${foods.size - shown.size} more)" else ""
                add("ate: ${shown.joinToString(", ")}$more")
            }
        }
        return "[User] " + parts.joinToString(" | ")
    }

    private const val MAX_FOODS_IN_CONTEXT = 8

    private fun kg(value: Float): String {
        val rounded = (value * 10).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    private fun lbs(kg: Float): Int = (kg * 2.20462f).roundToInt()

    /** One turn of the conversation as sent to the API. */
    data class Turn(val isUser: Boolean, val text: String)

    /**
     * Makes chat history valid for the Messages API: it must start with a user turn and roles must
     * alternate. Blank messages are dropped and back-to-back messages from the same side are merged.
     * The new [userMessage] is always the last (user) turn; if the caller already saved it into
     * [history], the duplicate is removed.
     */
    fun buildTurns(history: List<ChatMessage>, userMessage: String): List<Turn> {
        val turns = ArrayList<Turn>()
        for (msg in history) {
            val text = msg.text.trim()
            if (text.isEmpty()) continue
            val isUser = msg.role == ChatRole.USER
            val last = turns.lastOrNull()
            if (last != null && last.isUser == isUser) {
                turns[turns.lastIndex] = Turn(isUser, last.text + "\n\n" + text)
            } else {
                turns.add(Turn(isUser, text))
            }
        }
        while (turns.isNotEmpty() && !turns.first().isUser) turns.removeAt(0)

        val newText = userMessage.trim()
        val last = turns.lastOrNull()
        when {
            last == null || !last.isUser -> turns.add(Turn(true, newText))
            // The caller stored the message before calling us: keep a single copy.
            last.text == newText || last.text.endsWith("\n\n$newText") -> Unit
            // An earlier user message never got an answer (e.g. network error): merge them.
            else -> turns[turns.lastIndex] = Turn(true, last.text + "\n\n" + newText)
        }
        return turns
    }
}
