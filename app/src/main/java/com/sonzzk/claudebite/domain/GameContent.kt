package com.sonzzk.claudebite.domain

import java.time.LocalDate
import java.time.temporal.IsoFields

/** Things the app reports to the game layer. Each one can move quests forward. */
enum class GameMetric {
    MEAL_LOGGED, PHOTO_LOGGED, HEALTHY_MEAL, WATER_GOAL_DAY, PROTEIN_GOAL_DAY, WORKOUT, CHECKIN_DAY, NEW_FOOD, CHAT, WEIGH_IN, ALL_MEALS_DAY,
}

data class QuestDef(
    val id: String,
    val title: String,
    val description: String,
    val metric: GameMetric,
    val goal: Int,
    val coins: Int,
    val xp: Int,
    val weekly: Boolean = false,
    /** Boss challenges have a sprite and a name. */
    val bossSprite: String? = null,
)

object Quests {
    val dailyPool = listOf(
        QuestDef("d_log2", "Two bites", "Log 2 meals", GameMetric.MEAL_LOGGED, 2, 15, 15),
        QuestDef("d_log3", "Three square meals", "Log 3 meals", GameMetric.MEAL_LOGGED, 3, 20, 20),
        QuestDef("d_photo", "Food photographer", "Log a meal with a photo", GameMetric.PHOTO_LOGGED, 1, 15, 10),
        QuestDef("d_healthy", "Green plate", "Log a meal scoring 8+", GameMetric.HEALTHY_MEAL, 1, 20, 15),
        QuestDef("d_water", "Hydration station", "Hit your water goal", GameMetric.WATER_GOAL_DAY, 1, 15, 10),
        QuestDef("d_protein", "Protein power", "Hit your protein goal", GameMetric.PROTEIN_GOAL_DAY, 1, 20, 15),
        QuestDef("d_move", "Get moving", "Log a workout", GameMetric.WORKOUT, 1, 20, 15),
        QuestDef("d_checkin", "Good morning!", "Do the morning check-in", GameMetric.CHECKIN_DAY, 1, 10, 5),
        QuestDef("d_new", "Food explorer", "Log a food you've never logged", GameMetric.NEW_FOOD, 1, 15, 10),
        QuestDef("d_chat", "Chit-chat", "Ask Claude-chan something", GameMetric.CHAT, 1, 10, 5),
    )

    val bosses = listOf(
        QuestDef("b_protein", "Protein Dragon", "Hit your protein goal on 5 days this week", GameMetric.PROTEIN_GOAL_DAY, 5, 120, 100, true, "px_prop_15"),
        QuestDef("b_water", "Desert Golem", "Hit your water goal on 5 days this week", GameMetric.WATER_GOAL_DAY, 5, 100, 80, true, "px_prop_18"),
        QuestDef("b_move", "Couch Kraken", "Log 3 workouts this week", GameMetric.WORKOUT, 3, 120, 100, true, "px_prop_07"),
        QuestDef("b_logger", "Forgetful Fox", "Log all your main meals on 5 days", GameMetric.ALL_MEALS_DAY, 5, 120, 100, true, "px_prop_02"),
        QuestDef("b_explorer", "Mystery Chef", "Log 7 new foods this week", GameMetric.NEW_FOOD, 7, 100, 80, true, "px_prop_06"),
        QuestDef("b_green", "Junk Goblin", "Log 6 meals scoring 8+ this week", GameMetric.HEALTHY_MEAL, 6, 120, 100, true, "px_prop_09"),
    )

    fun dayPeriod(date: LocalDate) = date.toString()
    fun weekPeriod(date: LocalDate) = "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    /** 3 dailies, the same all day, different tomorrow. */
    fun dailiesFor(date: LocalDate): List<QuestDef> {
        val r = java.util.Random(date.toEpochDay() * 7919L)
        return dailyPool.shuffled(r).take(3)
    }

    fun bossFor(date: LocalDate): QuestDef {
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + date.get(IsoFields.WEEK_BASED_YEAR) * 53
        return bosses[Math.floorMod(week, bosses.size)]
    }
}

/** Shop items bought with coins ("Bites"). Decorations go in Claude-chan's room; stickers fill the sticker book. */
data class ShopItem(val id: String, val name: String, val price: Int, val sprite: String, val kind: Kind) {
    enum class Kind { DECOR, STICKER, FREEZE }
}

object Shop {
    private fun decor(i: Int, name: String, price: Int) = ShopItem("decor_$i", name, price, "px_prop_%02d".format(i), ShopItem.Kind.DECOR)
    private fun sticker(i: Int, price: Int) = ShopItem("sticker_$i", "Sticker #${i + 1}", price, "px_emote_%02d".format(i), ShopItem.Kind.STICKER)

    val decor = listOf(
        decor(0, "Laptop", 60), decor(1, "Phone", 40), decor(2, "Cat plush", 80), decor(3, "Boba", 30),
        decor(4, "Burger & fries", 50), decor(5, "Pillow", 40), decor(6, "Cake", 60), decor(7, "Cat controller", 70),
        decor(8, "Heart cushion", 50), decor(9, "Pancake stack", 50), decor(10, "Notebook", 40), decor(11, "Spellbook", 70),
        decor(12, "Gift box", 40), decor(13, "Bouquet", 60), decor(14, "Camera", 70), decor(15, "Hero sword", 120),
        decor(16, "Twinkles", 90), decor(17, "Star cushion", 80), decor(18, "Lantern", 70), decor(19, "Night lantern", 70),
    )
    val stickers = (0 until 18).map { sticker(it, 25 + (it % 4) * 10) }
    val freeze = ShopItem("freeze", "Streak freeze", 100, "px_icon_04", ShopItem.Kind.FREEZE)
    val all = decor + stickers + freeze
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Little letters from Claude-chan, unlocked by milestones. Collectable and re-readable. */
data class Letter(val id: String, val title: String, val unlockHint: String, val body: String)

object Letters {
    val all = listOf(
        Letter("first", "Nice to meet you!", "Log your first meal",
            "Hi hi! I'm so happy you're here. I'll help with the boring math so you can focus on feeling good. Let's be a team~ ♡"),
        Letter("streak7", "One whole week!", "Reach a 7-day streak",
            "Seven days in a row! Habits are built exactly like this, one small log at a time. I'm really proud of you."),
        Letter("level5", "Level 5 already?", "Reach level 5",
            "You're getting the hang of this! Did you know most people quit tracking in the first week? Not you. Not us!"),
        Letter("dex25", "Food explorer", "Discover 25 foods",
            "25 different foods! Variety keeps your gut and your taste buds happy. What should we try next?"),
        Letter("workout5", "Strong and steady", "Log 5 workouts",
            "Five workouts! Remember to eat enough protein so all that effort turns into strength~"),
        Letter("streak30", "A month together", "Reach a 30-day streak",
            "Thirty days... I don't even know what to say. Thank you for showing up for yourself every day. ♡"),
        Letter("level15", "Halfway to legend", "Reach level 15",
            "Level 15! You've unlocked so many outfits for me. Which one is your favourite? (Mine's the witch hat.)"),
        Letter("quests20", "Quest master", "Finish 20 quests",
            "Twenty quests cleared! You're basically the hero of this little app. I'll write a song about you. Maybe."),
    )
}
