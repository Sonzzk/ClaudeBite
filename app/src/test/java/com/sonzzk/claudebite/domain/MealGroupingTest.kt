package com.sonzzk.claudebite.domain

import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealGroupingTest {
    private fun entry(id: Long, kcal: Int, group: Long? = null, mealName: String? = null) = FoodEntry(
        id = id, date = "2026-11-16", timestamp = id, mealType = MealType.LUNCH, name = "food$id",
        portion = "", calories = kcal, proteinG = 1f, carbsG = 2f, fatG = 3f,
        mealGroupId = group, mealName = mealName,
    )

    @Test
    fun `four ingredients logged together become one meal line`() {
        val entries = listOf(
            entry(1, 100, group = 50, mealName = "Rice bowl"),
            entry(2, 200, group = 50, mealName = "Rice bowl"),
            entry(3, 150, group = 50, mealName = "Rice bowl"),
            entry(4, 90, group = 50, mealName = "Rice bowl"),
        )
        val lines = MealGrouping.lines(entries)
        assertEquals(1, lines.size)
        val meal = lines.single() as GroupedMeal
        assertEquals("Rice bowl", meal.name)
        assertEquals(540, meal.calories)
        assertEquals(4, meal.entries.size)
    }

    @Test
    fun `stand-alone foods and meals keep their order`() {
        val entries = listOf(
            entry(1, 80),
            entry(2, 100, group = 7, mealName = "Toast"),
            entry(3, 50),
            entry(4, 120, group = 7, mealName = "Toast"),
        )
        val lines = MealGrouping.lines(entries)
        assertEquals(3, lines.size)
        assertTrue(lines[0] is SingleFood)
        assertEquals(220, (lines[1] as GroupedMeal).calories)
        assertTrue(lines[2] is SingleFood)
    }
}
