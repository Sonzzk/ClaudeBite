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

/**
 * Outfits for Claude-chan, unlocked by level. Painted outfits have mascot_<id>_<mood> art; pixel outfits
 * point at one sprite from Amos's sheet ([pixelSprite]) and fall back to the classic poses for other moods.
 */
data class Outfit(val id: String, val name: String, val unlockLevel: Int, val pixelSprite: String? = null)

object Outfits {
    private fun px(id: String, name: String, level: Int, sprite: Int) = Outfit(id, name, level, "px_outfit_%02d".format(sprite))

    val all = listOf(
        Outfit("classic", "Classic Hoodie", 1),
        px("bomber", "Night Bomber", 2, 0),
        Outfit("chef", "Chef Apron", 3),
        px("sakura", "Sakura Coat", 4, 3),
        Outfit("gym", "Gym Outfit", 5),
        px("sunset", "Sunset Jacket", 6, 4),
        px("school", "School Day", 7, 5),
        Outfit("pajamas", "Cozy Pajamas", 8),
        px("cloud", "Cloud Fluff", 9, 1),
        px("midnight", "Midnight Coat", 10, 2),
        px("lofi", "Lo-fi Hoodie", 11, 6),
        Outfit("idol", "Idol Stage Outfit", 12),
        px("beret", "Artist Beret", 13, 7),
        px("explorer", "Explorer", 14, 9),
        px("angel", "Angel Coat", 15, 10),
        px("sailor", "Night Sailor", 16, 11),
        px("sundress", "Sundress", 17, 12),
        px("detective", "Detective", 18, 13),
        px("streamer", "Streamer", 19, 14),
        px("casual", "Weekend Casual", 20, 15),
        px("bandit", "Bandit Cap", 22, 16),
        px("maid", "Cat Café Maid", 24, 17),
        px("bonnet", "Frilly Bonnet", 26, 20),
        px("witch", "Little Witch", 28, 19),
        px("cathood", "Cat Hood", 30, 8),
        px("onesie", "Cat Onesie", 35, 18),
    )

    fun byId(id: String): Outfit? = all.firstOrNull { it.id == id }

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
