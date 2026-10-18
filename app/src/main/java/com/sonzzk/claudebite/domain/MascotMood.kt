package com.sonzzk.claudebite.domain

/**
 * Claude-chan's expressions. Each one maps to a mascot image (see ui/components/Mascot.kt),
 * so adding a mood means adding art for it.
 */
enum class MascotMood {
    HAPPY,      // default, smiling
    EXCITED,    // sparkly eyes: great food, level up, badge
    THINKING,   // while waiting for Claude
    PROUD,      // goal hit, streak
    WORRIED,    // over calories, very unhealthy food
    SLEEPY,     // late night / user away for days
    HUNGRY,     // nothing logged yet today
    WAVING,     // greeting, onboarding
}

/** Picks a mood and a short line for the home screen without calling the API (zero tokens). */
object MascotBrain {

    data class Snapshot(
        val name: String,
        val hour: Int,
        val caloriesEaten: Int,
        val calorieTarget: Int,
        val entriesToday: Int,
        val waterMl: Int,
        val waterGoalMl: Int,
        val streak: Int,
        val daysAway: Int,
    )

    fun mood(s: Snapshot): MascotMood = when {
        s.daysAway >= 3 -> MascotMood.SLEEPY
        s.caloriesEaten > s.calorieTarget * 1.15 -> MascotMood.WORRIED
        s.entriesToday == 0 && s.hour >= 10 -> MascotMood.HUNGRY
        s.hour >= 23 || s.hour < 5 -> MascotMood.SLEEPY
        s.caloriesEaten in (s.calorieTarget * 0.9).toInt()..(s.calorieTarget * 1.05).toInt() -> MascotMood.PROUD
        s.streak >= 7 -> MascotMood.EXCITED
        else -> MascotMood.HAPPY
    }

    fun line(s: Snapshot): String {
        val n = s.name.ifBlank { "you" }
        val left = s.calorieTarget - s.caloriesEaten
        return when (mood(s)) {
            MascotMood.SLEEPY -> if (s.daysAway >= 3) "I missed you, $n! Let's get back on track together~"
            else "It's late... a light snack at most, then sleep! Rest builds muscle too."
            MascotMood.WORRIED -> "We went a little over today. No stress! Tomorrow is a fresh start."
            MascotMood.HUNGRY -> "I'm hungry! What did you eat today, $n? Snap a photo for me!"
            MascotMood.PROUD -> "Right on target today! I'm so proud of you, $n!"
            MascotMood.EXCITED -> "${s.streak}-day streak! You're unstoppable!"
            else -> when {
                s.waterMl < s.waterGoalMl / 2 && s.hour >= 14 -> "Don't forget to drink water! Tap the glass for me~"
                left > 0 -> "$left kcal left today. Let's make them count!"
                else -> "Hi $n! Ready to log something yummy?"
            }
        }
    }
}
