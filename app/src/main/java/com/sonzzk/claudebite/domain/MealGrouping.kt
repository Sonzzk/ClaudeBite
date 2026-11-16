package com.sonzzk.claudebite.domain

import com.sonzzk.claudebite.data.db.FoodEntry

/** One line in a meal section on Home: either a stand-alone food or a whole meal made of ingredients. */
sealed interface MealLine {
    val key: String
    val entries: List<FoodEntry>
    val calories: Int get() = entries.sumOf { it.calories }
    val proteinG: Float get() = entries.sumOf { it.proteinG.toDouble() }.toFloat()
    val carbsG: Float get() = entries.sumOf { it.carbsG.toDouble() }.toFloat()
    val fatG: Float get() = entries.sumOf { it.fatG.toDouble() }.toFloat()
}

data class SingleFood(val entry: FoodEntry) : MealLine {
    override val key: String get() = "e${entry.id}"
    override val entries: List<FoodEntry> get() = listOf(entry)
}

data class GroupedMeal(val groupId: Long, val name: String, override val entries: List<FoodEntry>) : MealLine {
    override val key: String get() = "g$groupId"
    val photoPath: String? get() = entries.firstNotNullOfOrNull { it.photoPath }
    val healthScore: Int? get() = entries.firstNotNullOfOrNull { it.healthScore }
    val mascotComment: String? get() = entries.firstNotNullOfOrNull { it.mascotComment }
    val isFavorite: Boolean get() = entries.any { it.isFavorite }
}

object MealGrouping {
    /** Groups entries that share a mealGroupId, keeping the order in which each line first appears. */
    fun lines(entries: List<FoodEntry>): List<MealLine> {
        val result = mutableListOf<MealLine>()
        val groups = LinkedHashMap<Long, MutableList<FoodEntry>>()
        val order = mutableListOf<Any>() // FoodEntry or group id
        for (e in entries) {
            val g = e.mealGroupId
            if (g == null) {
                order += e
            } else {
                val list = groups[g]
                if (list == null) {
                    groups[g] = mutableListOf(e)
                    order += g
                } else {
                    list += e
                }
            }
        }
        for (o in order) {
            when (o) {
                is FoodEntry -> result += SingleFood(o)
                is Long -> {
                    val items = groups.getValue(o)
                    result += GroupedMeal(o, items.firstNotNullOfOrNull { it.mealName } ?: items.first().name, items)
                }
            }
        }
        return result
    }

    /** Only the grouped meals, for "Recent meals". */
    fun meals(entries: List<FoodEntry>): List<GroupedMeal> = lines(entries).filterIsInstance<GroupedMeal>()
}
