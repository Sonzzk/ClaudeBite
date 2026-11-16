package com.sonzzk.claudebite.domain

import com.sonzzk.claudebite.data.db.DayTotals
import com.sonzzk.claudebite.data.db.EntrySource
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.system.Reminders
import com.sonzzk.claudebite.ui.screens.home.SnackPicker
import com.sonzzk.claudebite.ui.screens.progress.WeeklyReport
import com.sonzzk.claudebite.ui.screens.settings.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GameAndToolsTest {
    private val day = LocalDate.of(2026, 11, 16)

    @Test fun dailyQuestsAreStableForADayAndUnique() {
        val a = Quests.dailiesFor(day)
        assertEquals(a, Quests.dailiesFor(day))
        assertEquals(3, a.map { it.id }.toSet().size)
    }

    @Test fun bossChangesWeekly() {
        assertEquals(Quests.bossFor(day), Quests.bossFor(day.plusDays(1)))
        assertNotEquals(Quests.weekPeriod(day), Quests.weekPeriod(day.plusDays(7)))
    }

    @Test fun shopIdsAreUnique() {
        assertEquals(Shop.all.size, Shop.all.map { it.id }.toSet().size)
    }

    @Test fun remindersAtMostTwoAndOnlyWhenMissing() {
        assertEquals("lunch", Reminders.due(12, emptySet(), emptySet()))
        assertNull(Reminders.due(12, setOf(MealType.LUNCH), emptySet()))
        assertNull(Reminders.due(13, emptySet(), setOf("lunch")))
        assertEquals("dinner", Reminders.due(19, emptySet(), setOf("lunch")))
        assertNull(Reminders.due(9, emptySet(), emptySet()))
        assertNull(Reminders.due(20, emptySet(), setOf("lunch", "dinner")))
    }

    @Test fun snackIdeasFitWhatIsLeft() {
        val ideas = SnackPicker.ideas(200, 30f)
        assertTrue(ideas.isNotEmpty())
        assertTrue(ideas.all { it.kcal100 * it.servingG / 100f <= 200f })
        assertTrue(SnackPicker.ideas(50, 0f).isEmpty())
    }

    @Test fun weeklyReportAverages() {
        val t = listOf(
            DayTotals("2026-11-16", 2000, 120f, 0f, 0f, 3),
            DayTotals("2026-11-15", 1800, 80f, 0f, 0f, 2),
            DayTotals("2026-11-01", 3000, 50f, 0f, 0f, 2),
        )
        val w = WeeklyReport.summarize(t, day.minusDays(6), day, 100)
        assertEquals(2, w.daysLogged)
        assertEquals(1900, w.avgKcal)
        assertEquals(100, w.avgProtein)
        assertEquals(1, w.proteinDays)
    }

    @Test fun csvEscapesCommasAndQuotes() {
        val e = FoodEntry(date = "2026-11-16", timestamp = 0, mealType = MealType.LUNCH, name = "Rice, \"jasmine\"", portion = "1 cup",
            calories = 200, proteinG = 4f, carbsG = 45f, fatG = 0.4f, source = EntrySource.MANUAL)
        val csv = Csv.format(listOf(e)).lines()
        assertTrue(csv[1].contains("\"Rice, \"\"jasmine\"\"\""))
        assertTrue(csv[1].startsWith("2026-11-16,lunch,,"))
    }
}
