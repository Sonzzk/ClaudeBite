package com.sonzzk.claudebite.domain

import com.sonzzk.claudebite.data.db.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthMathTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun `trend smooths a noisy day`() {
        val pts = WeightTrend.smooth(listOf(today.minusDays(2) to 80f, today.minusDays(1) to 80f, today to 82f))
        assertTrue(pts.last().trend < 81f)
    }

    @Test
    fun `losing half a kilo a week reaches the goal in about ten weeks`() {
        val weigh = (0..28).map { today.minusDays((28 - it).toLong()) to (82f - it * 0.5f / 7f) }
        val pts = WeightTrend.smooth(weigh, alpha = 1f)
        val rate = WeightTrend.weeklyRate(pts, today)!!
        assertEquals(-0.5f, rate, 0.05f)
        val eta = WeightTrend.eta(pts, 75f, today)
        assertNotNull(eta)
        val weeks = java.time.temporal.ChronoUnit.DAYS.between(today, eta) / 7.0
        assertEquals(10.0, weeks, 1.0)
    }

    @Test
    fun `no forecast when moving away from the goal`() {
        val weigh = (0..20).map { today.minusDays((20 - it).toLong()) to (80f + it * 0.1f) }
        assertNull(WeightTrend.eta(WeightTrend.smooth(weigh, 1f), 75f, today))
    }

    @Test
    fun `burn estimate is sensible`() {
        // 45 min moderate cardio at 70 kg ~ 386 kcal
        assertEquals(386, Burn.estimate(WorkoutType.CARDIO, 45, 70f))
    }
}
