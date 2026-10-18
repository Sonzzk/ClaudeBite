package com.sonzzk.claudebite.domain

import kotlin.math.sqrt

/** XP rewards. Kept here so the whole economy can be tuned in one place. */
object Xp {
    const val LOG_FOOD = 10
    const val LOG_PHOTO_BONUS = 5
    const val HEALTHY_FOOD_BONUS = 5 // health score >= 8
    const val LOG_WATER = 2
    const val LOG_WEIGHT = 15
    const val CHAT = 3
    const val HIT_CALORIE_GOAL = 25
    const val STREAK_DAY = 20
}

data class LevelInfo(val level: Int, val xpIntoLevel: Int, val xpForNextLevel: Int) {
    val progress: Float get() = xpIntoLevel.toFloat() / xpForNextLevel
}

object Leveling {
    /** Level n needs 50*n^2 total XP: fast early levels, slower later. */
    fun totalXpFor(level: Int): Int = 50 * (level - 1) * (level - 1)

    fun info(totalXp: Int): LevelInfo {
        val level = (sqrt(totalXp / 50.0).toInt() + 1).coerceAtLeast(1)
        val start = totalXpFor(level)
        val next = totalXpFor(level + 1)
        return LevelInfo(level, totalXp - start, next - start)
    }
}

/** Outfits for Claude-chan, unlocked by level. The mascot art has one variant per outfit id. */
data class Outfit(val id: String, val name: String, val unlockLevel: Int)

object Outfits {
    val all = listOf(
        Outfit("classic", "Classic Hoodie", 1),
        Outfit("chef", "Chef Apron", 3),
        Outfit("gym", "Gym Outfit", 5),
        Outfit("pajamas", "Cozy Pajamas", 8),
        Outfit("idol", "Idol Stage Outfit", 12),
    )

    fun unlocked(level: Int) = all.filter { it.unlockLevel <= level }
}

/** Badges. Checked after each log; unlocking gives a celebration animation. */
enum class Badge(val title: String, val description: String) {
    FIRST_BITE("First Bite", "Log your first food"),
    SHUTTERBUG("Shutterbug", "Log 10 meals with a photo"),
    STREAK_3("Warming Up", "3-day logging streak"),
    STREAK_7("On Fire", "7-day logging streak"),
    STREAK_30("Unstoppable", "30-day logging streak"),
    HYDRATED("Hydrated", "Hit your water goal"),
    GREEN_PLATE("Green Plate", "Log a food with a 10/10 health score"),
    ON_TARGET("Bullseye", "Finish a day within 100 kcal of your goal"),
    PROTEIN_PRO("Protein Pro", "Hit your protein goal"),
    CHATTY("Best Friends", "Chat with Claude-chan 20 times"),
    WEIGH_IN("Weigh-In", "Log your weight"),
}

object Streaks {
    /**
     * Current streak from a list of logged dates (yyyy-MM-dd, newest first).
     * The streak survives if today is not logged yet but yesterday was.
     */
    fun current(datesDesc: List<String>, today: java.time.LocalDate): Int {
        val set = datesDesc.toHashSet()
        var day = if (set.contains(today.toString())) today else today.minusDays(1)
        var count = 0
        while (set.contains(day.toString())) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
